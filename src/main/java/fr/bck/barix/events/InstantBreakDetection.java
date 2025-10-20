package fr.bck.barix.events;

import fr.bck.barix.BarixConstants;
import fr.bck.barix.alert.DiscordAlerts;
import fr.bck.barix.config.BarixServerConfig;
import fr.bck.barix.lang.Lang;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class InstantBreakDetection {

    private static final class BreakContext {
        long startMs;
        BlockPos pos;
        BlockState state;
        double lastSpeed; // issu de BreakSpeed
    }

    // Par joueur: position -> contexte
    private final Map<UUID, Map<BlockPos, BreakContext>> startByPlayer = new ConcurrentHashMap<>();
    // Par joueur: speed la plus récente
    private final Map<UUID, Double> lastSpeedByPlayer = new ConcurrentHashMap<>();

    // Infractions et cooldowns
    private final Map<UUID, List<Long>> infractions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastAlert = new ConcurrentHashMap<>();

    // Cache de whitelist dynamique
    private volatile String lastWhitelistRaw = null;
    private volatile Set<String> whitelistNames = Set.of();

    public InstantBreakDetection() {
        // Ne pas lire la config ici; elle est côté serveur et peut évoluer. Chargement paresseux.
    }

    private boolean isWhitelisted(ServerPlayer sp) {
        String raw = BarixServerConfig.IB_WHITELIST_PLAYERS.get();
        if (raw == null) raw = "";
        if (!raw.equals(lastWhitelistRaw)) {
            Set<String> parsed = ConcurrentHashMap.newKeySet();
            for (String p : raw.split(",")) {
                String t = p.trim().toLowerCase(Locale.ROOT);
                if (!t.isEmpty()) parsed.add(t);
            }
            whitelistNames = parsed;
            lastWhitelistRaw = raw;
        }
        return whitelistNames.contains(sp.getName().getString().toLowerCase(Locale.ROOT));
    }

    private boolean ignore(Player p) {
        if (!(p instanceof ServerPlayer sp)) return true; // server side only
        try {
            if (sp.hasPermissions(BarixServerConfig.IB_IGNORE_PERMISSION_LEVEL.get())) return true;
        } catch (Throwable ignored) {}
        if (isWhitelisted(sp)) return true;
        if (sp.isCreative()) return true; // ignorer créatif
        return false;
    }

    @SubscribeEvent
    public void onLeftClick(PlayerInteractEvent.LeftClickBlock e) {
        if (!BarixServerConfig.IB_ENABLED.get()) return;
        Player p = e.getEntity();
        if (ignore(p)) return;
        Level lvl = e.getLevel();
        BlockPos pos = e.getPos();
        BlockState st = lvl.getBlockState(pos);
        if (st.isAir()) return;
        // Créer contexte
        BreakContext ctx = new BreakContext();
        ctx.startMs = System.currentTimeMillis();
        ctx.pos = pos.immutable();
        ctx.state = st;
        ctx.lastSpeed = lastSpeedByPlayer.getOrDefault(p.getUUID(), 0.0);
        startByPlayer.computeIfAbsent(p.getUUID(), k -> new ConcurrentHashMap<>()).put(ctx.pos, ctx);
    }

    @SubscribeEvent
    public void onBreakSpeed(PlayerEvent.BreakSpeed e) {
        if (!BarixServerConfig.IB_ENABLED.get()) return;
        Player p = e.getEntity();
        if (ignore(p)) return;
        lastSpeedByPlayer.put(p.getUUID(), (double) e.getNewSpeed());
        // Optionnel: si on a un contexte pour ce bloc, y recopier
        Map<BlockPos, BreakContext> map = startByPlayer.get(p.getUUID());
        if (map != null) {
            // Cherche un contexte récent pour la position si dispo
            try {
                BlockPos pos = e.getPosition().orElse(null);
                if (pos != null) {
                    BreakContext ctx = map.get(pos);
                    if (ctx != null) ctx.lastSpeed = e.getNewSpeed();
                }
            } catch (Throwable ignored) {
                // getPosition peut ne pas exister selon versions
            }
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent e) {
        if (!BarixServerConfig.IB_ENABLED.get()) return;
        Player p = e.getPlayer();
        if (p == null || ignore(p)) return;
        UUID uid = p.getUUID();
        BlockPos pos = e.getPos();
        Map<BlockPos, BreakContext> map = startByPlayer.get(uid);
        if (map == null) return;
        BreakContext ctx = map.remove(pos);
        if (ctx == null) return;

        long now = System.currentTimeMillis();
        long observed = now - ctx.startMs;

        // Estimer temps attendu
        Level lvl = (Level) e.getLevel();
        BlockState st = ctx.state != null ? ctx.state : lvl.getBlockState(pos);
        float hardness = st.getDestroySpeed(lvl, pos);
        if (hardness <= 0) return; // ignorer indestructible ou instantané
        double speed = ctx.lastSpeed > 0 ? ctx.lastSpeed : lastSpeedByPlayer.getOrDefault(uid, 0.0);

        // Heuristique: temps attendu ~ facteur * dureté / vitesse
        // Les admins peuvent ajuster via IB_TOLERANCE_FACTOR et IB_TOLERANCE_MS.
        // Multiplions dureté par 2000ms pour un ordre de grandeur, puis divisons par speed.
        double baseMs = (hardness * 2000.0) / Math.max(0.01, speed);
        int minExpected = BarixServerConfig.IB_MIN_EXPECTED_MS.get();
        double expected = Math.max(minExpected, baseMs * (1.0));

        // Appliquer tolérances
        expected = expected * BarixServerConfig.IB_TOLERANCE_FACTOR.get();
        expected = Math.max(0.0, expected - BarixServerConfig.IB_TOLERANCE_MS.get());

        if (observed < expected) {
            recordInfraction(uid);
            int count = countRecentInfractions(uid);
            BarixConstants.log.warn("§cFastBreak", Lang.tr("barix.anti.fastbreak.suspect", BarixServerConfig.CORE_LOCALE.get(), p.getGameProfile().getName(), st.getBlock().toString(), observed, (int) expected, String.format("%.2f", speed), String.format("%.2f", hardness), count));
            if (count >= BarixServerConfig.IB_INFRACTIONS_THRESHOLD.get()) {
                long last = lastAlert.getOrDefault(uid, 0L);
                long cd = BarixServerConfig.IB_COOLDOWN_SECONDS.get() * 1000L;
                long nowMs = System.currentTimeMillis();
                if (nowMs - last > cd) {
                    lastAlert.put(uid, nowMs);
                    // Action
                    String action = Optional.ofNullable(BarixServerConfig.IB_ACTION_ON_DETECT.get()).orElse("alert").trim().toLowerCase(Locale.ROOT);
                    String title = Lang.tr("barix.fastbreak.suspect.title", BarixServerConfig.CORE_LOCALE.get(), p.getGameProfile().getName());
                    String desc = Lang.tr("barix.fastbreak.suspect.desc", BarixServerConfig.CORE_LOCALE.get(), observed, (int) expected, String.format("%.2f", speed), String.format("%.2f", hardness), count);
                    if ("log".equals(action)) {
                        BarixConstants.log.warn("§cFastBreak", desc);
                    } else if ("alert".equals(action)) {
                        DiscordAlerts.alert("ib_" + uid, title, desc, 0xFF5555);
                    } else if ("kick".equals(action)) {
                        DiscordAlerts.alert("ib_" + uid, title, desc, 0xFF5555);
                        if (p instanceof ServerPlayer sp) {
                            String msg = Optional.ofNullable(BarixServerConfig.IB_KICK_MESSAGE.get()).orElse("Comportement de minage suspect (instant break).");
                            try { sp.connection.disconnect(Component.literal(msg)); } catch (Throwable t) { BarixConstants.log.error("§cFastBreak", Lang.tr("barix.kick.error", BarixServerConfig.CORE_LOCALE.get(), t.getMessage()), t); }
                        }
                    } else {
                        BarixConstants.log.warn("§cFastBreak", Lang.tr("barix.action.unknown", BarixServerConfig.CORE_LOCALE.get(), action));
                    }
                }
            }
        }
    }

    private void recordInfraction(UUID uuid) {
        long now = System.currentTimeMillis();
        infractions.compute(uuid, (k, list) -> {
            if (list == null) list = Collections.synchronizedList(new ArrayList<>());
            list.add(now);
            return list;
        });
        prune(uuid);
    }

    private void prune(UUID uuid) {
        List<Long> list = infractions.get(uuid);
        if (list == null) return;
        long cutoff = System.currentTimeMillis() - BarixServerConfig.IB_WINDOW_SECONDS.get() * 1000L;
        synchronized (list) {
            list.removeIf(t -> t < cutoff);
            if (list.isEmpty()) infractions.remove(uuid);
        }
    }

    private int countRecentInfractions(UUID uuid) {
        prune(uuid);
        return infractions.getOrDefault(uuid, Collections.emptyList()).size();
    }
}
