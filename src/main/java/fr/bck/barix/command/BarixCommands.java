package fr.bck.barix.command;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import fr.bck.barix.BarixConstants;
import fr.bck.barix.api.BarixAPI;
import fr.bck.barix.api.IBarixLagTracker;
import fr.bck.barix.audit.AuditQuery;
import fr.bck.barix.audit.AuditRotator;
import fr.bck.barix.config.BarixServerConfig;
import fr.bck.barix.lang.Lang;
import fr.bck.barix.lang.LangKey;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Racine /bx avec alias /barix.
 * Sous-commandes à ajouter ici ou via classes dédiées.
 */
public final class BarixCommands {
    private BarixCommands() {
    }

    private static boolean scanActive = false;

    public static void register(RegisterCommandsEvent e) {
        var d = e.getDispatcher();

        LiteralArgumentBuilder<CommandSourceStack> bx = Commands.literal("bx").requires(src -> src.hasPermission(3))
                .then(cmdPing())
                .then(cmdHelp())
                .then(cmdAudit())
                .then(cmdLagScan())
                .then(cmdAlert())
                .then(cmdLang())
                .then(cmdConfig());

        LiteralCommandNode<CommandSourceStack> root = d.register(bx);

        d.register(Commands.literal("barix").requires(src -> src.hasPermission(3)).redirect(root));
    }

    // /bx ping
    private static LiteralArgumentBuilder<CommandSourceStack> cmdPing() {
        return Commands.literal("ping").executes(ctx -> {
            CommandSourceStack src = ctx.getSource();
            ServerPlayer p = src.getPlayer();
            if (p != null) {
                src.sendSuccess(() -> Lang.c(LangKey.BARIX_PING_OK, p), false);
            } else {
                // Console / non-joueur : utilise la langue serveur
                String code = BarixServerConfig.CORE_LOCALE.get();
                src.sendSuccess(() -> Component.literal(Lang.tr(LangKey.BARIX_PING_OK, code)), false);
            }
            return 1;
        });
    }

    // /bx help
    private static LiteralArgumentBuilder<CommandSourceStack> cmdHelp() {
        return Commands.literal("help").executes(ctx -> {
            ctx.getSource().sendSuccess(() -> Component.literal("""
                    /bx ping | /bx audit status|on|off|toggle | /bx audit window <ms> |
                    /bx audit set <open|close|place|break> <on|off> |
                    /bx audit query uuid <uuid> [limit] |
                    /bx audit query block <mod:id> [limit] |
                    /bx audit compress-now |
                    /bx lagscan <seconds> |
                    /bx alert test [message] |
                    /bx lang reload |
                    /bx config list [prefix] |
                    /bx config get <key> |
                    /bx config set <key> <value> |
                    /bx config save | /bx config reload
                    """), false);
            return 1;
        });
    }

    // /bx audit ...
    private static LiteralArgumentBuilder<CommandSourceStack> cmdAudit() {
        return Commands.literal("audit").then(Commands.literal("status").executes(ctx -> {
            var s = BarixServerConfig.AUDIT_ENABLE_ALL.get();
            var open = BarixServerConfig.AUDIT_LOG_OPEN.get();
            var close = BarixServerConfig.AUDIT_LOG_CLOSE.get();
            var place = BarixServerConfig.AUDIT_LOG_PLACE.get();
            var brk = BarixServerConfig.AUDIT_LOG_BREAK.get();
            int winMs = BarixServerConfig.AUDIT_CLICK_OPEN_WINDOW_MS.get();
            ctx.getSource().sendSuccess(() -> Component.literal("audit=" + s + " {open=" + open + ", close=" + close + ", place=" + place + ", break=" + brk + "} windowMs=" + winMs), false);
            return 1;
        })).then(Commands.literal("on").executes(ctx -> {
            BarixServerConfig.AUDIT_ENABLE_ALL.set(true);
            ctx.getSource().sendSuccess(() -> Component.literal("Barix audit: enabled"), true);
            return 1;
        })).then(Commands.literal("off").executes(ctx -> {
            BarixServerConfig.AUDIT_ENABLE_ALL.set(false);
            ctx.getSource().sendSuccess(() -> Component.literal("Barix audit: disabled"), true);
            return 1;
        })).then(Commands.literal("toggle").executes(ctx -> {
            boolean v = !BarixServerConfig.AUDIT_ENABLE_ALL.get();
            BarixServerConfig.AUDIT_ENABLE_ALL.set(v);
            ctx.getSource().sendSuccess(() -> Component.literal("Barix audit: " + (v ? "enabled" : "disabled")), true);
            return 1;
        })).then(Commands.literal("window").then(Commands.argument("ms", IntegerArgumentType.integer(0, 60000)).executes(ctx -> {
            int ms = IntegerArgumentType.getInteger(ctx, "ms");
            BarixServerConfig.AUDIT_CLICK_OPEN_WINDOW_MS.set(ms);
            ctx.getSource().sendSuccess(() -> Component.literal("Barix audit window set to " + ms + " ms"), true);
            return 1;
        }))).then(Commands.literal("set").then(Commands.literal("open").then(Commands.literal("on").executes(c -> setFlag(c.getSource(), "open", true))).then(Commands.literal("off").executes(c -> setFlag(c.getSource(), "open", false)))).then(Commands.literal("close").then(Commands.literal("on").executes(c -> setFlag(c.getSource(), "close", true))).then(Commands.literal("off").executes(c -> setFlag(c.getSource(), "close", false)))).then(Commands.literal("place").then(Commands.literal("on").executes(c -> setFlag(c.getSource(), "place", true))).then(Commands.literal("off").executes(c -> setFlag(c.getSource(), "place", false)))).then(Commands.literal("break").then(Commands.literal("on").executes(c -> setFlag(c.getSource(), "break", true))).then(Commands.literal("off").executes(c -> setFlag(c.getSource(), "break", false))))).then(Commands.literal("query").then(Commands.literal("uuid").then(Commands.argument("u", UuidArgument.uuid()).executes(c -> queryUuid(c.getSource(), UuidArgument.getUuid(c, "u"), 20)).then(Commands.argument("limit", IntegerArgumentType.integer(1, 500)).executes(c -> queryUuid(c.getSource(), UuidArgument.getUuid(c, "u"), IntegerArgumentType.getInteger(c, "limit")))))).then(Commands.literal("block").then(Commands.argument("id", ResourceLocationArgument.id()).executes(c -> queryBlock(c.getSource(), ResourceLocationArgument.getId(c, "id").toString(), 20)).then(Commands.argument("limit", IntegerArgumentType.integer(1, 500)).executes(c -> queryBlock(c.getSource(), ResourceLocationArgument.getId(c, "id").toString(), IntegerArgumentType.getInteger(c, "limit"))))))).then(Commands.literal("compress-now").executes(ctx -> {
            fr.bck.barix.audit.AuditRotator.compressTodayIfConfigured();
            AuditRotator.compressNow(fr.bck.barix.audit.AuditJsonLog.currentFile());
            ctx.getSource().sendSuccess(() -> Component.literal("Compression déclenchée."), true);
            return 1;
        }));
    }

    private static int setFlag(CommandSourceStack src, String which, boolean val) {
        switch (which) {
            case "open" -> BarixServerConfig.AUDIT_LOG_OPEN.set(val);
            case "close" -> BarixServerConfig.AUDIT_LOG_CLOSE.set(val);
            case "place" -> BarixServerConfig.AUDIT_LOG_PLACE.set(val);
            case "break" -> BarixServerConfig.AUDIT_LOG_BREAK.set(val);
        }
        src.sendSuccess(() -> Component.literal("Barix audit " + which + " = " + val), true);
        return 1;
    }

    private static int queryUuid(CommandSourceStack src, UUID uuid, int limit) {
        List<JsonObject> list = AuditQuery.find(uuid.toString(), null, null, null, null, limit);
        src.sendSuccess(() -> Component.literal("Entries: " + list.size() + " for " + uuid), false);
        for (int i = 0; i < Math.min(10, list.size()); i++) {
            int finalI = i;
            src.sendSuccess(() -> Component.literal(list.get(finalI).toString()), false);
        }
        return list.size();
    }

    private static int queryBlock(CommandSourceStack src, String blockId, int limit) {
        List<com.google.gson.JsonObject> list = AuditQuery.find(null, blockId, null, null, null, limit);
        src.sendSuccess(() -> Component.literal("Entries: " + list.size() + " for " + blockId), false);
        for (int i = 0; i < Math.min(10, list.size()); i++) {
            int finalI = i;
            src.sendSuccess(() -> Component.literal(list.get(finalI).toString()), false);
        }
        return list.size();
    }

    // --------------------------------------------------------------
    // /bx alert test [message]
    // --------------------------------------------------------------
    private static LiteralArgumentBuilder<CommandSourceStack> cmdAlert() {
        return Commands.literal("alert").then(Commands.literal("test").executes(ctx -> {
            fr.bck.barix.alert.DiscordAlerts.alert("manual:test", "Test d'alerte", "Aucune description fournie", 0xF59E0B // orange
            );
            ctx.getSource().sendSuccess(() -> Component.literal("Alerte de test envoyée."), false);
            return 1;
        }));
    }

    // --------------------------------------------------------------
    // /bx lang reload
    // --------------------------------------------------------------
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> cmdLang() {
        return net.minecraft.commands.Commands.literal("lang")
                .then(net.minecraft.commands.Commands.literal("reload").executes(ctx -> {
                    fr.bck.barix.lang.Lang.reload();
                    ctx.getSource().sendSuccess(() -> Component.literal("Langues rechargées."), true);
                    return 1;
                }));
    }

    // --------------------------------------------------------------
    // /bx config ...
    // --------------------------------------------------------------
    private static LiteralArgumentBuilder<CommandSourceStack> cmdConfig() {
        return Commands.literal("config")
                .then(Commands.literal("list").executes(ctx -> {
                    var keys = BarixServerConfig.allKeys();
                    ctx.getSource().sendSuccess(() -> Component.literal("Clés de configuration (" + keys.size() + ") :"), false);
                    keys.forEach((k, v) -> ctx.getSource().sendSuccess(() -> Component.literal(" - " + k + " = " + BarixServerConfig.describeValue(v)), false));
                    return 1;
                }).then(Commands.argument("prefix", StringArgumentType.string()).executes(ctx -> {
                    String prefix = StringArgumentType.getString(ctx, "prefix").toLowerCase();
                    var keys = BarixServerConfig.allKeys();
                    int count = 0;
                    for (var e : keys.entrySet()) {
                        if (e.getKey().startsWith(prefix)) {
                            count++;
                            String line = " - " + e.getKey() + " = " + BarixServerConfig.describeValue(e.getValue());
                            ctx.getSource().sendSuccess(() -> Component.literal(line), false);
                        }
                    }
                    if (count == 0)
                        ctx.getSource().sendSuccess(() -> Component.literal("Aucune clé ne commence par: " + prefix), false);
                    return 1;
                })))
                .then(Commands.literal("get").then(Commands.argument("key", StringArgumentType.string()).executes(ctx -> {
                    String key = StringArgumentType.getString(ctx, "key");
                    var cfg = BarixServerConfig.get(key);
                    if (cfg == null) {
                        ctx.getSource().sendFailure(Component.literal("Clé inconnue: " + key));
                        return 0;
                    }
                    ctx.getSource().sendSuccess(() -> Component.literal(key + " = " + BarixServerConfig.describeValue(cfg)), false);
                    return 1;
                })))
                .then(Commands.literal("set").then(Commands.argument("key", StringArgumentType.string()).then(Commands.argument("value", StringArgumentType.greedyString()).executes(ctx -> {
                    String key = StringArgumentType.getString(ctx, "key");
                    String value = StringArgumentType.getString(ctx, "value");
                    try {
                        Object parsed = BarixServerConfig.parseAndSet(key, value);
                        // Effets secondaires utiles
                        if (key.equalsIgnoreCase("core.locale")) {
                            fr.bck.barix.lang.Lang.initFromConfig();
                        }
                        if (key.startsWith("logging.")) {
                            fr.bck.barix.alert.DiscordAlerts.initFromConfig();
                        }
                        ctx.getSource().sendSuccess(() -> Component.literal("Config mise à jour: " + key + " = " + parsed), true);
                        return 1;
                    } catch (IllegalArgumentException ex) {
                        ctx.getSource().sendFailure(Component.literal("Erreur: " + ex.getMessage()));
                        return 0;
                    }
                }))))
                .then(Commands.literal("save").executes(ctx -> {
                    boolean ok = BarixServerConfig.trySaveToDisk();
                    ctx.getSource().sendSuccess(() -> Component.literal(ok ? "Configuration enregistrée sur disque." : "Impossible d’enregistrer via API Forge; les valeurs restent actives en mémoire."), true);
                    return ok ? 1 : 0;
                }))
                .then(Commands.literal("reload").executes(ctx -> {
                    // Best-effort: recharger les sous-systèmes dépendants; la relecture disque complète dépend de Forge
                    try {
                        // Recharger langues + alertes depuis la config actuelle
                        fr.bck.barix.lang.Lang.initFromConfig();
                        fr.bck.barix.alert.DiscordAlerts.initFromConfig();
                        ctx.getSource().sendSuccess(() -> Component.literal("Sous-systèmes rechargés (lang/alertes)."), true);
                        return 1;
                    } catch (Exception ex) {
                        ctx.getSource().sendFailure(Component.literal("Erreur au rechargement: " + ex.getMessage()));
                        return 0;
                    }
                }));
    }

    // --------------------------------------------------------------
    // /bx lagscan <seconds>
    // --------------------------------------------------------------
    private static LiteralArgumentBuilder<CommandSourceStack> cmdLagScan() {
        return Commands.literal("lagscan").then(Commands.argument("seconds", IntegerArgumentType.integer(1, 600)).executes(ctx -> {
            int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
            CommandSourceStack src = ctx.getSource();

            if (scanActive) {
                src.sendFailure(Component.literal("Un scan est déjà en cours."));
                return 0;
            }

            scanActive = true;

            IBarixLagTracker tracker = BarixAPI.getLagTracker();
            tracker.reset();

            src.sendSuccess(() -> Component.literal("Analyse du lag lancée pour " + seconds + " secondes..."), true);

            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            server.execute(() -> scheduleLagReport(src, seconds));

            return 1;
        }));
    }

    private static void scheduleLagReport(CommandSourceStack src, int seconds) {
        new Thread(() -> {
            try {
                Thread.sleep(seconds * 1000L);
            } catch (InterruptedException ignored) {
            }
            scanActive = false;

            IBarixLagTracker tracker = BarixAPI.getLagTracker();
            Map<ResourceLocation, Double> results = collectLagStats(tracker);

            src.sendSuccess(() -> Component.literal("§e=== Résultats du LagScan ==="), false);
            results.entrySet().stream().sorted(Map.Entry.<ResourceLocation, Double>comparingByValue().reversed()).limit(10).forEach(e -> src.sendSuccess(() -> Component.literal("§7" + e.getKey() + " §f→ §c" + String.format("%.3f", e.getValue()) + " ms"), false));
            src.sendSuccess(() -> Component.literal("§eFin de l’analyse."), false);
        }, "Barix-LagScanThread").start();
    }

    private static Map<ResourceLocation, Double> collectLagStats(IBarixLagTracker tracker) {
        Map<ResourceLocation, Double> results = new HashMap<>();

        // Première tentative : accès via l'API (préféré)
        try {
            Map<ResourceLocation, ?> map = tracker.getStatsMap();
            if (map != null) {
                for (var entry : map.entrySet()) {
                    ResourceLocation id = entry.getKey();
                    Object stat = entry.getValue();
                    long total = 0;
                    int count = 0;
                    try {
                        var totalField = stat.getClass().getDeclaredField("totalNanos");
                        totalField.setAccessible(true);
                        total = totalField.getLong(stat);
                    } catch (NoSuchFieldException ignored) {
                    }
                    try {
                        var countField = stat.getClass().getDeclaredField("count");
                        countField.setAccessible(true);
                        count = countField.getInt(stat);
                    } catch (NoSuchFieldException ignored) {
                    }

                    if (count > 0) results.put(id, (total / (double) count) / 1_000_000.0);
                }
                return results;
            }
        } catch (Exception ignored) {
            // si l'implémentation n'expose pas la méthode proprement, on continue vers le fallback reflectif
        }

        // Fallback : comportement ancien (réflexion sur fr.bck.barix.server.LagMonitor)
        if (tracker instanceof fr.bck.barix.server.LagMonitor monitor) {
            try {
                var field = monitor.getClass().getDeclaredField("stats");
                field.setAccessible(true);
                @SuppressWarnings("unchecked") Map<ResourceLocation, ?> map = (Map<ResourceLocation, ?>) field.get(monitor);
                for (var entry : map.entrySet()) {
                    ResourceLocation id = entry.getKey();
                    Object stat = entry.getValue();
                    var totalField = stat.getClass().getDeclaredField("totalNanos");
                    var countField = stat.getClass().getDeclaredField("count");
                    totalField.setAccessible(true);
                    countField.setAccessible(true);
                    long total = totalField.getLong(stat);
                    int count = countField.getInt(stat);
                    if (count > 0) results.put(id, (total / (double) count) / 1_000_000.0);
                }
            } catch (Exception ex) {
                BarixConstants.log.error("§4BarixLag", "Error reading lag stats", ex);
            }
        }

        return results;
    }
}
