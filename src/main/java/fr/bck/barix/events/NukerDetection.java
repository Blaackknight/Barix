package fr.bck.barix.events;

import fr.bck.barix.BarixConstants;
import fr.bck.barix.alert.DiscordAlerts;
import fr.bck.barix.config.BarixServerConfig;
import fr.bck.barix.lang.Lang;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NukerDetection {

    // Fenêtre glissante d'évènements (timestamps en ms)
    private final Map<UUID, Deque<Long>> windowByPlayer = new ConcurrentHashMap<>();
    // Fenêtre courte 'cluster'
    private final Map<UUID, Deque<Long>> clusterByPlayer = new ConcurrentHashMap<>();
    // Cooldown alertes
    private final Map<UUID, Long> lastAlert = new ConcurrentHashMap<>();

    // Cache de whitelist dynamique
    private volatile String lastWhitelistRaw = null;
    private volatile Set<String> whitelistNames = Set.of();

    public NukerDetection() {
        // Ne plus précharger la whitelist ici
    }

    private boolean isWhitelisted(ServerPlayer sp) {
        String raw = BarixServerConfig.NUKER_WHITELIST_PLAYERS.get();
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
        try { if (sp.hasPermissions(BarixServerConfig.NUKER_IGNORE_PERMISSION_LEVEL.get())) return true; } catch (Throwable ignored) {}
        if (Boolean.TRUE.equals(BarixServerConfig.NUKER_IGNORE_CREATIVE.get()) && sp.isCreative()) return true;
        if (isWhitelisted(sp)) return true;
        return false;
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent e) {
        if (!BarixServerConfig.NUKER_ENABLED.get()) return;
        Player p = e.getPlayer();
        if (p == null || ignore(p)) return;
        UUID uid = p.getUUID();

        // Reach check
        BlockPos bp = e.getPos();
        double bx = bp.getX() + 0.5, by = bp.getY() + 0.5, bz = bp.getZ() + 0.5;
        double dx = p.getX() - bx, dy = p.getY() - by, dz = p.getZ() - bz;
        double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
        double maxReach = BarixServerConfig.NUKER_MAX_REACH_DISTANCE.get() + BarixServerConfig.NUKER_REACH_BUFFER.get();
        boolean reachOk = dist <= maxReach;

        long now = System.currentTimeMillis();
        int winSec = BarixServerConfig.NUKER_WINDOW_SECONDS.get();
        long winCut = now - winSec * 1000L;
        int clusterMs = BarixServerConfig.NUKER_CLUSTER_MS.get();
        long clusterCut = now - clusterMs;

        // Mettre à jour fenêtres
        Deque<Long> win = windowByPlayer.computeIfAbsent(uid, k -> new ArrayDeque<>());
        Deque<Long> cl = clusterByPlayer.computeIfAbsent(uid, k -> new ArrayDeque<>());
        win.addLast(now);
        cl.addLast(now);
        while (!win.isEmpty() && win.peekFirst() < winCut) win.pollFirst();
        while (!cl.isEmpty() && cl.peekFirst() < clusterCut) cl.pollFirst();

        int winCount = win.size();
        int clCount = cl.size();

        boolean windowExceeded = winCount > BarixServerConfig.NUKER_MAX_BLOCKS_IN_WINDOW.get();
        boolean clusterExceeded = clCount > BarixServerConfig.NUKER_MAX_BLOCKS_IN_CLUSTER.get();
        boolean suspicious = (!reachOk) || windowExceeded || clusterExceeded;

        if (suspicious) {
            String reason = !reachOk ? Lang.tr("barix.nuker.reason.reach", BarixServerConfig.CORE_LOCALE.get(), String.format(Locale.ROOT, "%.2f", dist), String.format(Locale.ROOT, "%.2f", maxReach))
                    : (windowExceeded ? Lang.tr("barix.nuker.reason.window", BarixServerConfig.CORE_LOCALE.get(), winCount, BarixServerConfig.NUKER_MAX_BLOCKS_IN_WINDOW.get(), winSec)
                    : Lang.tr("barix.nuker.reason.cluster", BarixServerConfig.CORE_LOCALE.get(), clCount, BarixServerConfig.NUKER_MAX_BLOCKS_IN_CLUSTER.get(), clusterMs));

            long last = lastAlert.getOrDefault(uid, 0L);
            if (now - last >= BarixServerConfig.NUKER_COOLDOWN_SECONDS.get() * 1000L) {
                lastAlert.put(uid, now);
                String action = Optional.ofNullable(BarixServerConfig.NUKER_ACTION_ON_DETECT.get()).orElse("alert").trim().toLowerCase(Locale.ROOT);
                String title = Lang.tr("barix.nuker.suspect.title", BarixServerConfig.CORE_LOCALE.get(), p.getGameProfile().getName());
                String desc = Lang.tr("barix.nuker.suspect.desc", BarixServerConfig.CORE_LOCALE.get(), reason, winCount, clCount, bp.toShortString());
                switch (action) {
                    case "log" -> BarixConstants.log.warn("§cNuker", desc);
                    case "alert" -> DiscordAlerts.alert("nuker_" + uid, title, desc, 0xCC0000);
                    case "kick" -> {
                        DiscordAlerts.alert("nuker_" + uid, title, desc, 0xCC0000);
                        if (p instanceof ServerPlayer sp) {
                            String msg = Optional.ofNullable(BarixServerConfig.NUKER_KICK_MESSAGE.get()).orElse("Comportement de cassage massif suspect (nuker).");
                            try { sp.connection.disconnect(Component.literal(msg)); } catch (Throwable t) { BarixConstants.log.error("§cNuker", Lang.tr("barix.kick.error", BarixServerConfig.CORE_LOCALE.get(), t.getMessage()), t); }
                        }
                    }
                    default -> BarixConstants.log.warn("§cNuker", Lang.tr("barix.action.unknown", BarixServerConfig.CORE_LOCALE.get(), action));
                }
            }
        }
    }
}
