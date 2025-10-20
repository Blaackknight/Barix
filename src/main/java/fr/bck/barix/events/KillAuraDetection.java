package fr.bck.barix.events;

import fr.bck.barix.BarixConstants;
import fr.bck.barix.alert.DiscordAlerts;
import fr.bck.barix.config.BarixServerConfig;
import fr.bck.barix.lang.Lang;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class KillAuraDetection {

    private static final class Hit {
        long ts;
        int targetId;
        Hit(long ts, int targetId) { this.ts = ts; this.targetId = targetId; }
    }

    // Infractions par joueur (timestamps ms)
    private final Map<UUID, Deque<Long>> infractions = new ConcurrentHashMap<>();
    // Cooldown alertes
    private final Map<UUID, Long> lastAlert = new ConcurrentHashMap<>();
    // Historique d'attaques: timestamps + targetId
    private final Map<UUID, Deque<Hit>> hitsByPlayer = new ConcurrentHashMap<>();

    // Cache de whitelist dynamique
    private volatile String lastWhitelistRaw = null;
    private volatile Set<String> whitelistNames = Set.of();

    public KillAuraDetection() {
        // Ne pas lire la config ici; chargement paresseux via isWhitelisted()
    }

    private boolean isWhitelisted(ServerPlayer sp) {
        String raw = BarixServerConfig.KA_WHITELIST_PLAYERS.get();
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
        try { if (sp.hasPermissions(BarixServerConfig.KA_IGNORE_PERMISSION_LEVEL.get())) return true; } catch (Throwable ignored) {}
        return isWhitelisted(sp);
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        if (!BarixServerConfig.KA_ENABLED.get()) return;
        Player p = event.getEntity();
        if (p == null) return;
        if (ignore(p)) return;

        Entity target = event.getTarget();
        if (!(target instanceof LivingEntity victim)) return;

        UUID uid = p.getUUID();
        long now = System.currentTimeMillis();

        // Enregistrer le hit
        Deque<Hit> hits = hitsByPlayer.computeIfAbsent(uid, k -> new ArrayDeque<>());
        hits.addLast(new Hit(now, target.getId()));
        // Purge selon fenêtre max pertinente
        int cpsWindow = BarixServerConfig.KA_CPS_WINDOW_MS.get();
        int switchMs = BarixServerConfig.KA_SWITCH_WINDOW_MS.get();
        long cut = now - Math.max(cpsWindow, switchMs);
        while (!hits.isEmpty() && hits.peekFirst().ts < cut) hits.pollFirst();

        // 1) CPS
        long cpsCut = now - cpsWindow;
        int cps = 0;
        for (Hit h : hits) if (h.ts >= cpsCut) cps++;
        boolean cpsExceeded = cps > BarixServerConfig.KA_MAX_CPS.get();

        // 2) FOV check
        boolean fovExceeded = false;
        try {
            Vec3 eye = p.getEyePosition(1.0f);
            Vec3 look = p.getLookAngle().normalize();
            Vec3 toTarget = new Vec3(victim.getX() - eye.x, (victim.getEyeY()) - eye.y, victim.getZ() - eye.z).normalize();
            double dot = look.dot(toTarget);
            dot = Math.max(-1.0, Math.min(1.0, dot));
            double angleDeg = Math.toDegrees(Math.acos(dot));
            double maxFov = BarixServerConfig.KA_MAX_FOV_DEGREES.get();
            fovExceeded = angleDeg > maxFov;
        } catch (Throwable ignored) {}

        // 3) Line-of-sight (à travers les murs)
        boolean losViolated = false;
        if (Boolean.TRUE.equals(BarixServerConfig.KA_REQUIRE_LINE_OF_SIGHT.get())) {
            try {
                Vec3 eye = p.getEyePosition(1.0f);
                Vec3 targetEye = new Vec3(victim.getX(), victim.getEyeY(), victim.getZ());
                var level = p.level();
                var ctx = new ClipContext(eye, targetEye, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p);
                HitResult result = level.clip(ctx);
                if (result.getType() == HitResult.Type.BLOCK) {
                    double distToHit = result.getLocation().distanceTo(eye);
                    double distToTarget = targetEye.distanceTo(eye);
                    losViolated = distToHit + 0.05 < distToTarget;
                }
            } catch (Throwable ignored) {}
        }

        // 4) Target switching: unique cibles sur la fenêtre switchMs
        long switchCut = now - switchMs;
        Set<Integer> uniq = new LinkedHashSet<>();
        for (Iterator<Hit> it = hits.descendingIterator(); it.hasNext();) {
            Hit h = it.next();
            if (h.ts < switchCut) break;
            uniq.add(h.targetId);
            if (uniq.size() > BarixServerConfig.KA_MAX_TARGETS_IN_SWITCH_WINDOW.get()) break;
        }
        int uniqueCount = uniq.size();
        boolean tooManySwitch = uniqueCount > BarixServerConfig.KA_MAX_TARGETS_IN_SWITCH_WINDOW.get();

        // Décider si suspect
        boolean suspicious = cpsExceeded || fovExceeded || losViolated || tooManySwitch;
        if (suspicious) {
            Deque<Long> inf = infractions.computeIfAbsent(uid, k -> new ArrayDeque<>());
            inf.addLast(now);
            pruneInfractions(uid);

            String reason;
            if (cpsExceeded) {
                reason = Lang.tr("barix.killaura.reason.cps", BarixServerConfig.CORE_LOCALE.get(), cps, BarixServerConfig.KA_MAX_CPS.get(), cpsWindow);
            } else if (fovExceeded) {
                reason = Lang.tr("barix.killaura.reason.fov", BarixServerConfig.CORE_LOCALE.get(), BarixServerConfig.KA_MAX_FOV_DEGREES.get());
            } else if (losViolated) {
                reason = Lang.tr("barix.killaura.reason.los", BarixServerConfig.CORE_LOCALE.get());
            } else {
                reason = Lang.tr("barix.killaura.reason.switch", BarixServerConfig.CORE_LOCALE.get(), uniqueCount, BarixServerConfig.KA_MAX_TARGETS_IN_SWITCH_WINDOW.get(), switchMs);
            }

            int count = inf.size();
            BarixConstants.log.warn("§cKillAura", Lang.tr("barix.killaura.suspect.line", BarixServerConfig.CORE_LOCALE.get(), p.getName().getString(), reason, count));

            if (count >= BarixServerConfig.KA_INFRACTIONS_THRESHOLD.get()) {
                long last = lastAlert.getOrDefault(uid, 0L);
                if (now - last >= BarixServerConfig.KA_COOLDOWN_SECONDS.get() * 1000L) {
                    lastAlert.put(uid, now);
                    String action = Optional.ofNullable(BarixServerConfig.KA_ACTION_ON_DETECT.get()).orElse("alert").trim().toLowerCase(Locale.ROOT);
                    String title = Lang.tr("barix.killaura.suspect.title", BarixServerConfig.CORE_LOCALE.get(), p.getName().getString());
                    String desc = Lang.tr("barix.killaura.suspect.desc", BarixServerConfig.CORE_LOCALE.get(), reason, count);
                    switch (action) {
                        case "log" -> BarixConstants.log.warn("§cKillAura", desc);
                        case "alert" -> DiscordAlerts.alert("killaura_" + uid, title, desc, 0xFF5500);
                        case "kick" -> {
                            DiscordAlerts.alert("killaura_" + uid, title, desc, 0xFF5500);
                            if (p instanceof ServerPlayer sp) {
                                String msg = Optional.ofNullable(BarixServerConfig.KA_KICK_MESSAGE.get()).orElse("Comportement d'attaque suspect (KillAura).");
                                try { sp.connection.disconnect(Component.literal(msg)); } catch (Throwable t) { BarixConstants.log.error("§cKillAura", Lang.tr("barix.kick.error", BarixServerConfig.CORE_LOCALE.get(), t.getMessage()), t); }
                            }
                        }
                        default -> BarixConstants.log.warn("§cKillAura", Lang.tr("barix.action.unknown", BarixServerConfig.CORE_LOCALE.get(), action));
                    }
                }
            }
        } else {
            pruneInfractions(uid);
        }
    }

    private void pruneInfractions(UUID uuid) {
        Deque<Long> inf = infractions.get(uuid);
        if (inf == null) return;
        long cut = System.currentTimeMillis() - BarixServerConfig.KA_HIST_WINDOW_SECONDS.get() * 1000L;
        while (!inf.isEmpty() && inf.peekFirst() < cut) inf.pollFirst();
        if (inf.isEmpty()) infractions.remove(uuid);
    }
}
