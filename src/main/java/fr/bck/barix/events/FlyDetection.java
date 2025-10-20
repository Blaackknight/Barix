package fr.bck.barix.events;

import fr.bck.barix.BarixConstants;
import fr.bck.barix.alert.DiscordAlerts;
import fr.bck.barix.config.BarixServerConfig;
import fr.bck.barix.lang.Lang;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentMap;
import java.util.Locale;

public class FlyDetection {

    private static final class Track {
        double lastY;
        long lastMs;
    }

    private final ConcurrentMap<UUID, Track> tracks = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, List<Long>> infractions = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Long> lastAlert = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Long> airStart = new ConcurrentHashMap<>();

    // Cache whitelist
    private volatile String lastWhitelistRaw = null;
    private volatile Set<String> whitelistNames = Set.of();

    private boolean isWhitelisted(ServerPlayer sp) {
        String raw = BarixServerConfig.FLY_WHITELIST_PLAYERS.get();
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
        try { if (sp.hasPermissions(BarixServerConfig.FLY_IGNORE_PERMISSION_LEVEL.get())) return true; } catch (Throwable ignored) {}
        if (isWhitelisted(sp)) return true;
        if (Boolean.TRUE.equals(BarixServerConfig.FLY_IGNORE_CREATIVE.get()) && sp.isCreative()) return true;
        if (Boolean.TRUE.equals(BarixServerConfig.FLY_IGNORE_ELYTRA.get())) {
            try { if (sp.isFallFlying()) return true; } catch (Throwable ignored) {}
        }
        if (Boolean.TRUE.equals(BarixServerConfig.FLY_IGNORE_VEHICLE.get())) {
            try { if (sp.isPassenger()) return true; } catch (Throwable ignored) {}
        }
        if (Boolean.TRUE.equals(BarixServerConfig.FLY_IGNORE_LEVITATION_POTION.get())) {
            try { if (sp.getEffect(MobEffects.LEVITATION) != null) return true; } catch (Throwable ignored) {}
        }
        return false;
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (!BarixServerConfig.FLY_ENABLED.get()) return;
        if (e.phase != TickEvent.Phase.END) return;
        Player p = e.player;
        if (ignore(p)) return;
        if (!(p.level() instanceof ServerLevel)) return;

        ServerPlayer sp = (ServerPlayer) p;
        UUID uid = sp.getUUID();
        long now = System.currentTimeMillis();

        // track vertical movement
        Track tr = tracks.computeIfAbsent(uid, k -> new Track());
        Vec3 pos = sp.position();
        double y = pos.y;
        if (tr.lastMs == 0) {
            tr.lastY = y;
            tr.lastMs = now;
            return;
        }
        long dtMs = Math.max(1, now - tr.lastMs);
        double dy = y - tr.lastY;
        double verticalMps = dy / (dtMs / 1000.0);

        boolean onGround;
        try {
            // Vérifie le bloc sous le joueur pour estimer si il touche le sol (côté serveur)
            Level lvl = sp.level();
            BlockPos feet = sp.blockPosition();
            BlockPos below = BlockPos.containing(feet.getX(), Math.floor(sp.getY() - 0.2), feet.getZ());
            try {
                onGround = !lvl.getBlockState(below).isAir();
            } catch (Throwable ignored) {
                onGround = verticalMps <= 0.01;
            }
        } catch (Throwable t) {
            // fallback approximatif
            onGround = verticalMps <= 0.01;
        }

        // gérer airStart
        if (onGround) {
            airStart.remove(uid);
        } else {
            // éviter usage excessif de lambda pour l'analyse statique
            airStart.putIfAbsent(uid, now);
        }

        long airTimeSec = 0L;
        Long start = airStart.get(uid);
        if (start != null) airTimeSec = (now - start) / 1000L;

        double maxV = BarixServerConfig.FLY_MAX_VERTICAL_SPEED.get();
        double buffer = BarixServerConfig.FLY_BUFFER.get();
        int maxAir = BarixServerConfig.FLY_MAX_AIR_TIME_SECONDS.get();

        boolean suspect = false;
        String reason = "";
        if (verticalMps > maxV + buffer) {
            suspect = true;
            reason = String.format(Locale.ROOT, "vertical_speed=%.3f>%.3f(+buff %.3f)", verticalMps, maxV, buffer);
        }
        if (airTimeSec > maxAir) {
            suspect = true;
            if (!reason.isEmpty()) reason += ", ";
            reason += String.format(Locale.ROOT, "air_time=%ds>=%ds", airTimeSec, maxAir);
        }

        if (Boolean.TRUE.equals(BarixServerConfig.FLY_DEBUG.get())) {
            BarixConstants.log.debug("Fly", Lang.tr("barix.fly.debug", BarixServerConfig.CORE_LOCALE.get(),
                    sp.getGameProfile().getName(),
                    String.format(Locale.ROOT, "%.3f", y),
                    String.format(Locale.ROOT, "%.4f", dy),
                    String.format(Locale.ROOT, "%.3f", verticalMps),
                    onGround,
                    airTimeSec,
                    suspect));
        }

        if (suspect) {
            recordInfraction(uid);
            int count = countRecentInfractions(uid);
            BarixConstants.log.warn("§cFly", Lang.tr("barix.anti.fly.suspect", BarixServerConfig.CORE_LOCALE.get(), sp.getGameProfile().getName(), reason, count));

            if (count >= BarixServerConfig.FLY_INFRACTIONS_THRESHOLD.get()) {
                long last = lastAlert.getOrDefault(uid, 0L);
                if (now - last >= BarixServerConfig.FLY_COOLDOWN_SECONDS.get() * 1000L) {
                    lastAlert.put(uid, now);
                    String title = Lang.tr("barix.fly.suspect.title", BarixServerConfig.CORE_LOCALE.get(), sp.getGameProfile().getName());
                    String desc = Lang.tr("barix.fly.suspect.desc", BarixServerConfig.CORE_LOCALE.get(), reason, String.format(Locale.ROOT, "%.3f", verticalMps), airTimeSec, count);
                    String action = Optional.ofNullable(BarixServerConfig.FLY_ACTION_ON_DETECT.get()).orElse("alert").trim().toLowerCase(Locale.ROOT);
                    switch (action) {
                        case "log" -> BarixConstants.log.warn("§cFly", desc);
                        case "alert" -> DiscordAlerts.alert("fly_" + uid, title, desc, 0xFF5555);
                        case "kick" -> {
                            DiscordAlerts.alert("fly_" + uid, title, desc, 0xFF5555);
                            try {
                                String msg = Optional.ofNullable(BarixServerConfig.FLY_KICK_MESSAGE.get()).orElse("Comportement de vol suspect (fly).");
                                sp.connection.disconnect(net.minecraft.network.chat.Component.literal(msg));
                            } catch (Throwable t) {
                                BarixConstants.log.error("§cFly", Lang.tr("barix.kick.error", BarixServerConfig.CORE_LOCALE.get(), t.getMessage()), t);
                            }
                        }
                        default -> BarixConstants.log.warn("§cFly", Lang.tr("barix.action.unknown", BarixServerConfig.CORE_LOCALE.get(), action));
                    }
                }
            }
        } else {
            prune(uid);
        }

        tr.lastY = y;
        tr.lastMs = now;
    }

    private void recordInfraction(UUID uuid) {
        long now = System.currentTimeMillis();
        infractions.compute(uuid, (k, list) -> {
            if (list == null) list = new CopyOnWriteArrayList<>();
            list.add(now);
            return list;
        });
        prune(uuid);
    }

    private void prune(UUID uuid) {
        List<Long> list = infractions.get(uuid);
        if (list == null) return;
        long cutoff = System.currentTimeMillis() - BarixServerConfig.FLY_HIST_WINDOW_SECONDS.get() * 1000L;
        list.removeIf(t -> t < cutoff);
        if (list.isEmpty()) infractions.remove(uuid);
    }

    private int countRecentInfractions(UUID uuid) {
        prune(uuid);
        return infractions.getOrDefault(uuid, Collections.emptyList()).size();
    }
}
