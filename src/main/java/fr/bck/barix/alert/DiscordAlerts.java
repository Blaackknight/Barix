package fr.bck.barix.alert;

import fr.bck.barix.BarixConstants;
import fr.bck.barix.config.BarixServerConfig;
import fr.bck.barix.infra.DiscordWebhook;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DiscordAlerts {
    private static volatile DiscordWebhook webhook;
    private static final Map<String, Long> lastByKey = new ConcurrentHashMap<>();
    private static volatile int cooldownSec = 30;
    private static volatile boolean adminsOnly = true;

    private DiscordAlerts() {
    }

    public static void initFromConfig() {
        String url = BarixServerConfig.LOG_DISCORD_WEBHOOK.get();
        String username = BarixServerConfig.LOG_DISCORD_USERNAME.get();
        cooldownSec = BarixServerConfig.LOG_ALERT_COOLDOWN_SECONDS.get();
        adminsOnly = BarixServerConfig.LOG_ALERT_ADMINS_ONLY.get();

        if (url == null || url.isBlank()) {
            webhook = null;
            BarixConstants.log.info("§bAlerts", "§7Discord Alerts: §cdeactivated (missing webhook)");
        } else {
            webhook = new DiscordWebhook(url, username);
            BarixConstants.log.info("§bAlerts", "§7Discord Alerts: §aactivated");
        }
    }

    public static void alert(String key, String title, String description, int color) {
        if (!passCooldown(key)) return;
        // Discord embed
        DiscordWebhook w = webhook;
        if (w != null) w.sendEmbed(title, description, color);
        // In-game
        broadcastIngame("§6[§9Barix§6] §e" + title + "§7 — §f" + description);
    }

    public static void alertText(String key, String content) {
        if (!passCooldown(key)) return;
        // Discord texte
        DiscordWebhook w = webhook;
        if (w != null) w.sendContent(content);
        // In-game
        broadcastIngame("§6[§9Barix§6] §f" + content);
    }

    private static boolean passCooldown(String key) {
        long now = System.currentTimeMillis();
        Long last = lastByKey.get(key);
        if (last != null && (now - last) < cooldownSec * 1000L) return false;
        lastByKey.put(key, now);
        return true;
    }

    public static void broadcastIngame(String message) {
        try {
            MinecraftServer srv = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            if (srv == null) return;
            srv.execute(() -> {
                try {
                    if (adminsOnly) {
                        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
                            if (p.hasPermissions(3)) {
                                p.sendSystemMessage(Component.literal(message));
                            }
                        }
                    } else {
                        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
                            p.sendSystemMessage(Component.literal(message));
                        }
                    }
                } catch (Throwable t) {
                    BarixConstants.log.error("§bAlerts", "§eIn-game §7sending §cerror (thread)", t);
                }
            });
            // Log console aussi
            BarixConstants.log.info("§bAlerts", message);
        } catch (Throwable t) {
            BarixConstants.log.error("§bAlerts", "§eIn-game §7broadcast §cerror", t);
        }
    }
}
