package fr.bck.barix.events;

import fr.bck.barix.BarixConstants;
import fr.bck.barix.alert.DiscordAlerts;
import fr.bck.barix.config.BarixServerConfig;
import fr.bck.barix.lang.Lang;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ReachDetection {

    // Map joueur -> liste de timestamps (ms) d'infractions récentes
    private final Map<UUID, List<Long>> infractions = new ConcurrentHashMap<>();
    // cooldown per player to avoid spam d'alerte
    private final Map<UUID, Long> lastAlert = new ConcurrentHashMap<>();

    // Cache de whitelist dynamique
    private volatile String lastWhitelistRaw = null;
    private volatile Set<String> whitelistNames = Set.of();

    public ReachDetection() {
        // Ne plus lire la config ici (server config peut changer). On remplira à la volée.
    }

    private boolean isWhitelisted(ServerPlayer p) {
        String raw = BarixServerConfig.REACH_WHITELIST_PLAYERS.get();
        if (raw == null) raw = "";
        // Mettre à jour le cache si la valeur a changé
        if (!raw.equals(lastWhitelistRaw)) {
            Set<String> parsed = ConcurrentHashMap.newKeySet();
            for (String s : raw.split(",")) {
                String t = s.trim().toLowerCase(Locale.ROOT);
                if (!t.isEmpty()) parsed.add(t);
            }
            whitelistNames = parsed;
            lastWhitelistRaw = raw;
        }
        return whitelistNames.contains(p.getName().getString().toLowerCase(Locale.ROOT));
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        if (!BarixServerConfig.REACH_ENABLED.get()) return;
        if (event.getEntity() instanceof ServerPlayer attacker) {
            Entity target = event.getTarget();
            if (!(target instanceof LivingEntity)) return; // ignore non-living

            // Ignore if attacker has high permission level
            try {
                if (attacker.hasPermissions(BarixServerConfig.REACH_IGNORE_PERMISSION_LEVEL.get())) return;
            } catch (Throwable ignored) {
            }

            // Whitelist dynamique
            if (isWhitelisted(attacker)) return;

            // Mesure précise: distance de l'œil du joueur à la hitbox (AABB) de la cible
            Vec3 eye = attacker.getEyePosition(1.0f);
            AABB box = target.getBoundingBox();
            double dist;
            try {
                // Méthode moderne: distanceToSqr(Vec3)
                dist = Math.sqrt(box.distanceToSqr(eye));
            } catch (Throwable t) {
                // Fallback: approx centre de la hitbox
                Vec3 center = new Vec3((box.minX + box.maxX) * 0.5, (box.minY + box.maxY) * 0.5, (box.minZ + box.maxZ) * 0.5);
                dist = eye.distanceTo(center);
            }

            double maxLegal = BarixServerConfig.REACH_MAX_LEGAL_DISTANCE.get();
            double buffer = BarixServerConfig.REACH_BUFFER.get();

            if (dist > (maxLegal + buffer)) {
                recordInfraction(attacker.getUUID());
                int count = countRecentInfractions(attacker.getUUID());
                BarixConstants.log.warn("Reach", Lang.tr("barix.anti.reach.suspect", BarixServerConfig.CORE_LOCALE.get(), attacker.getName().getString(), String.format(Locale.ROOT, "%.2f", dist), count));

                if (count >= BarixServerConfig.REACH_INFRACTIONS_THRESHOLD.get()) {
                    long now = System.currentTimeMillis();
                    Long last = lastAlert.get(attacker.getUUID());
                    if (last == null || (now - last) > (BarixServerConfig.REACH_COOLDOWN_SECONDS.get() * 1000L)) {
                        lastAlert.put(attacker.getUUID(), now);
                        // Alert discord + in-game
                        String title = "Suspected Reach: " + attacker.getName().getString();
                        String desc = String.format(Locale.ROOT, "Distance %.2fm (max %.2fm + buffer %.2fm) — infractions %d", dist, maxLegal, buffer, count);
                        DiscordAlerts.alert("reach_" + attacker.getUUID(), title, desc, 0xFFAA00);
                    }
                }
            } else {
                // Optionnel: nettoyer les anciens éléments
                pruneInfractions(attacker.getUUID());
            }
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent e) {
        if (!BarixServerConfig.REACH_BLOCK_CHECK_ENABLED.get()) return;
        Player p0 = e.getPlayer();
        if (!(p0 instanceof ServerPlayer p)) return;
        try {
            if (p.hasPermissions(BarixServerConfig.REACH_IGNORE_PERMISSION_LEVEL.get())) return;
        } catch (Throwable ignored) {
        }
        if (BarixServerConfig.REACH_BLOCK_IGNORE_CREATIVE.get() && p.isCreative()) return;
        if (isWhitelisted(p)) return;

        BlockPos bp = e.getPos();
        Vec3 eye = p.getEyePosition(1.0f);
        double bx = bp.getX() + 0.5;
        double by = bp.getY() + 0.5;
        double bz = bp.getZ() + 0.5;
        double dist = eye.distanceTo(new Vec3(bx, by, bz));

        double maxLegal = BarixServerConfig.REACH_BLOCK_MAX_DISTANCE.get();
        double buffer = BarixServerConfig.REACH_BLOCK_BUFFER.get();

        if (dist > (maxLegal + buffer)) {
            UUID uid = p.getUUID();
            recordInfraction(uid);
            int count = countRecentInfractions(uid);
            BarixConstants.log.warn("Reach", Lang.tr("barix.anti.reach.suspect", BarixServerConfig.CORE_LOCALE.get(), p.getName().getString(), String.format(Locale.ROOT, "%.2f", dist), count));

            if (count >= BarixServerConfig.REACH_INFRACTIONS_THRESHOLD.get()) {
                long now = System.currentTimeMillis();
                Long last = lastAlert.get(uid);
                if (last == null || (now - last) > (BarixServerConfig.REACH_COOLDOWN_SECONDS.get() * 1000L)) {
                    lastAlert.put(uid, now);
                    String title = "Suspected Block Reach: " + p.getName().getString();
                    String desc = String.format(Locale.ROOT, "Block (%d,%d,%d) — Dist %.2fm (max %.2fm + buffer %.2fm) — infractions %d", bp.getX(), bp.getY(), bp.getZ(), dist, maxLegal, buffer, count);
                    DiscordAlerts.alert("reach_block_" + uid, title, desc, 0xFFAA00);
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
        pruneInfractions(uuid);
    }

    private void pruneInfractions(UUID uuid) {
        List<Long> list = infractions.get(uuid);
        if (list == null) return;
        long cutoff = System.currentTimeMillis() - BarixServerConfig.REACH_HIST_WINDOW_SECONDS.get() * 1000L;
        synchronized (list) {
            list.removeIf(t -> t < cutoff);
            if (list.isEmpty()) infractions.remove(uuid);
        }
    }

    private int countRecentInfractions(UUID uuid) {
        List<Long> list = infractions.get(uuid);
        if (list == null) return 0;
        pruneInfractions(uuid);
        return infractions.getOrDefault(uuid, Collections.emptyList()).size();
    }
}
