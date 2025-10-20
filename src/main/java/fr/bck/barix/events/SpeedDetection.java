package fr.bck.barix.events;

import fr.bck.barix.BarixConstants;
import fr.bck.barix.alert.DiscordAlerts;
import fr.bck.barix.config.BarixServerConfig;
import fr.bck.barix.lang.Lang;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SpeedDetection {

    private static final class Track {
        Vec3 lastPos;
        long lastMs;
    }

    private final Map<UUID, Track> tracks = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> infractions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastAlert = new ConcurrentHashMap<>();

    // Cache de whitelist dynamique
    private volatile String lastWhitelistRaw = null;
    private volatile Set<String> whitelistNames = Set.of();

    private boolean isWhitelisted(ServerPlayer sp) {
        String raw = BarixServerConfig.SPEED_WHITELIST_PLAYERS.get();
        if (raw == null) raw = "";
        if (!raw.equals(lastWhitelistRaw)) {
            Set<String> parsed = ConcurrentHashMap.newKeySet();
            for (String s : raw.split(",")) {
                String t = s.trim().toLowerCase(Locale.ROOT);
                if (!t.isEmpty()) parsed.add(t);
            }
            whitelistNames = parsed;
            lastWhitelistRaw = raw;
        }
        return whitelistNames.contains(sp.getName().getString().toLowerCase(Locale.ROOT));
    }

    private boolean ignore(Player p) {
        if (!(p instanceof ServerPlayer sp)) return true;
        try { if (sp.hasPermissions(BarixServerConfig.SPEED_IGNORE_PERMISSION_LEVEL.get())) return true; } catch (Throwable ignored) {}
        if (isWhitelisted(sp)) return true;
        if (Boolean.TRUE.equals(BarixServerConfig.SPEED_IGNORE_CREATIVE.get()) && sp.isCreative()) return true;
        if (Boolean.TRUE.equals(BarixServerConfig.SPEED_IGNORE_FLIGHT.get())) {
            try { if (sp.getAbilities().flying) return true; } catch (Throwable ignored) {}
        }
        if (Boolean.TRUE.equals(BarixServerConfig.SPEED_IGNORE_ELYTRA.get())) {
            try { if (sp.isFallFlying()) return true; } catch (Throwable ignored) {}
        }
        if (Boolean.TRUE.equals(BarixServerConfig.SPEED_IGNORE_VEHICLES.get())) {
            try { if (sp.isPassenger()) return true; } catch (Throwable ignored) {}
        }
        if (Boolean.TRUE.equals(BarixServerConfig.SPEED_IGNORE_RIPTIDE.get())) {
            try { if (sp.isAutoSpinAttack()) return true; } catch (Throwable ignored) {}
        }
        return false;
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (!BarixServerConfig.SPEED_ENABLED.get()) return;
        if (e.phase != TickEvent.Phase.END) return;
        Player p = e.player;
        if (ignore(p)) return;
        if (!(p.level() instanceof ServerLevel)) return; // côté serveur uniquement

        UUID uid = p.getUUID();
        long now = System.currentTimeMillis();
        Vec3 pos = p.position();

        Track tr = tracks.computeIfAbsent(uid, k -> new Track());
        if (tr.lastPos == null) {
            tr.lastPos = pos;
            tr.lastMs = now;
            return;
        }

        long dtMs = Math.max(1, now - tr.lastMs);
        double dx = pos.x - tr.lastPos.x;
        double dz = pos.z - tr.lastPos.z;
        double dh = Math.sqrt(dx * dx + dz * dz);

        // Anti-tp: ignorer grands sauts
        double tpThresh = BarixServerConfig.SPEED_MAX_TELEPORT_DELTA.get();
        if (dh > tpThresh || dtMs > 2000) {
            tr.lastPos = pos;
            tr.lastMs = now;
            prune(uid);
            return;
        }

        double mps = dh / (dtMs / 1000.0);

        // Calculer vitesse attendue dynamique
        double expected = BarixServerConfig.SPEED_BASE_MAX_MPS.get();
        boolean sprint = p.isSprinting();
        if (sprint) expected *= BarixServerConfig.SPEED_SPRINT_MULTIPLIER.get();

        int spdLevel = 0;
        try {
            MobEffectInstance eff = p.getEffect(MobEffects.MOVEMENT_SPEED);
            if (eff != null) spdLevel = Math.max(0, eff.getAmplifier() + 1);
        } catch (Throwable ignored) {}
        if (spdLevel > 0) expected *= (1.0 + BarixServerConfig.SPEED_POTION_PER_LEVEL.get() * spdLevel);

        boolean onIce = false;
        try {
            Level lvl = p.level();
            BlockPos feet = p.blockPosition();
            BlockPos below = BlockPos.containing(feet.getX(), Math.floor(p.getY() - 0.2), feet.getZ());
            var b = lvl.getBlockState(below).getBlock();
            onIce = b == Blocks.ICE || b == Blocks.PACKED_ICE || b == Blocks.BLUE_ICE || b.getDescriptionId().toLowerCase(Locale.ROOT).contains("frosted_ice");
        } catch (Throwable ignored) {}
        if (onIce) expected *= BarixServerConfig.SPEED_ICE_MULTIPLIER.get();

        double buffer = BarixServerConfig.SPEED_BUFFER_MPS.get();

        if (mps > expected + buffer) {
            recordInfraction(uid);
            int count = countRecentInfractions(uid);
            // log console bref
            BarixConstants.log.warn("§cSpeed", Lang.tr("barix.anti.speed.suspect", BarixServerConfig.CORE_LOCALE.get(), p.getGameProfile().getName(), String.format(Locale.ROOT, "%.2f", mps), String.format(Locale.ROOT, "%.2f", expected), count));

            if (count >= BarixServerConfig.SPEED_INFRACTIONS_THRESHOLD.get()) {
                long last = lastAlert.getOrDefault(uid, 0L);
                if (now - last >= BarixServerConfig.SPEED_COOLDOWN_SECONDS.get() * 1000L) {
                    lastAlert.put(uid, now);
                    String title = Lang.tr("barix.speed.suspect.title", BarixServerConfig.CORE_LOCALE.get(), p.getGameProfile().getName());
                    String desc = Lang.tr("barix.speed.suspect.desc", BarixServerConfig.CORE_LOCALE.get(), String.format(Locale.ROOT, "%.2f", mps), String.format(Locale.ROOT, "%.2f", expected), String.format(Locale.ROOT, "%.2f", buffer), sprint, spdLevel, onIce, count);
                    String action = Optional.ofNullable(BarixServerConfig.SPEED_ACTION_ON_DETECT.get()).orElse("alert").trim().toLowerCase(Locale.ROOT);
                    switch (action) {
                        case "log" -> BarixConstants.log.warn("§cSpeed", desc);
                        case "alert" -> DiscordAlerts.alert("speed_" + uid, title, desc, 0x33AAFF);
                        case "kick" -> {
                            DiscordAlerts.alert("speed_" + uid, title, desc, 0x33AAFF);
                            if (p instanceof ServerPlayer sp) {
                                String msg = Optional.ofNullable(BarixServerConfig.SPEED_KICK_MESSAGE.get()).orElse("Détection de vitesse suspecte (speed hack).");
                                try { sp.connection.disconnect(Component.literal(msg)); } catch (Throwable t) { BarixConstants.log.error("§cSpeed", Lang.tr("barix.kick.error", BarixServerConfig.CORE_LOCALE.get(), t.getMessage()), t); }
                            }
                        }
                        default -> BarixConstants.log.warn("§cSpeed", Lang.tr("barix.action.unknown", BarixServerConfig.CORE_LOCALE.get(), action));
                    }
                }
            }
        } else {
            prune(uid);
        }

        tr.lastPos = pos;
        tr.lastMs = now;
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
        long cutoff = System.currentTimeMillis() - BarixServerConfig.SPEED_WINDOW_SECONDS.get() * 1000L;
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
