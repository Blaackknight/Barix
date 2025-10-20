package fr.bck.barix.command;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import fr.bck.barix.BarixConstants;
import fr.bck.barix.alert.DiscordAlerts;
import fr.bck.barix.api.BarixAPI;
import fr.bck.barix.api.IBarixLagTracker;
import fr.bck.barix.audit.AuditJsonLog;
import fr.bck.barix.audit.AuditQuery;
import fr.bck.barix.audit.AuditRotator;
import fr.bck.barix.config.BarixServerConfig;
import fr.bck.barix.lang.Lang;
import fr.bck.barix.lang.LangKey;
import fr.bck.barix.region.RegionCommands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

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
                .then(cmdConfig())
                // Ajout TopLuck
                .then(cmdTopLuck())
                // Ajout modules
                .then(cmdModules())
                // Ajout anticheat
                .then(cmdAntiCheat())
                // Ajout régions
                .then(RegionCommands.command());

        LiteralCommandNode<CommandSourceStack> root = d.register(bx);

        d.register(Commands.literal("barix").requires(src -> src.hasPermission(3)).redirect(root));
    }

    // Helpers i18n
    private static void sendMsg(CommandSourceStack src, String key, boolean broadcast, Object... args) {
        ServerPlayer p = src.getPlayer();
        if (p != null) {
            src.sendSuccess(() -> Lang.c(key, p, args), broadcast);
        } else {
            String code = BarixServerConfig.CORE_LOCALE.get();
            src.sendSuccess(() -> Component.literal(Lang.tr(key, code, args)), broadcast);
        }
    }

    private static void sendFail(CommandSourceStack src, String key, Object... args) {
        ServerPlayer p = src.getPlayer();
        if (p != null) {
            src.sendFailure(Lang.c(key, p, args));
        } else {
            String code = BarixServerConfig.CORE_LOCALE.get();
            src.sendFailure(Component.literal(Lang.tr(key, code, args)));
        }
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
            sendMsg(ctx.getSource(), "barix.help", false);
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
            sendMsg(ctx.getSource(), "barix.audit.status", false, s, open, close, place, brk, winMs);
            return 1;
        })).then(Commands.literal("on").executes(ctx -> {
            BarixServerConfig.AUDIT_ENABLE_ALL.set(true);
            sendMsg(ctx.getSource(), "barix.audit.enabled", true);
            return 1;
        })).then(Commands.literal("off").executes(ctx -> {
            BarixServerConfig.AUDIT_ENABLE_ALL.set(false);
            sendMsg(ctx.getSource(), "barix.audit.disabled", true);
            return 1;
        })).then(Commands.literal("toggle").executes(ctx -> {
            boolean v = !BarixServerConfig.AUDIT_ENABLE_ALL.get();
            BarixServerConfig.AUDIT_ENABLE_ALL.set(v);
            sendMsg(ctx.getSource(), v ? "barix.audit.enabled" : "barix.audit.disabled", true);
            return 1;
        })).then(Commands.literal("window").then(Commands.argument("ms", IntegerArgumentType.integer(0, 60000)).executes(ctx -> {
            int ms = IntegerArgumentType.getInteger(ctx, "ms");
            BarixServerConfig.AUDIT_CLICK_OPEN_WINDOW_MS.set(ms);
            sendMsg(ctx.getSource(), "barix.audit.window.set", true, ms);
            return 1;
        }))).then(Commands.literal("set").then(Commands.literal("open").then(Commands.literal("on").executes(c -> setFlag(c.getSource(), "open", true))).then(Commands.literal("off").executes(c -> setFlag(c.getSource(), "open", false)))).then(Commands.literal("close").then(Commands.literal("on").executes(c -> setFlag(c.getSource(), "close", true))).then(Commands.literal("off").executes(c -> setFlag(c.getSource(), "close", false)))).then(Commands.literal("place").then(Commands.literal("on").executes(c -> setFlag(c.getSource(), "place", true))).then(Commands.literal("off").executes(c -> setFlag(c.getSource(), "place", false)))).then(Commands.literal("break").then(Commands.literal("on").executes(c -> setFlag(c.getSource(), "break", true))).then(Commands.literal("off").executes(c -> setFlag(c.getSource(), "break", false))))).then(Commands.literal("query").then(Commands.literal("uuid").then(Commands.argument("u", UuidArgument.uuid()).executes(c -> queryUuid(c.getSource(), UuidArgument.getUuid(c, "u"), 20)).then(Commands.argument("limit", IntegerArgumentType.integer(1, 500)).executes(c -> queryUuid(c.getSource(), UuidArgument.getUuid(c, "u"), IntegerArgumentType.getInteger(c, "limit")))))).then(Commands.literal("block").then(Commands.argument("id", ResourceLocationArgument.id()).executes(c -> queryBlock(c.getSource(), ResourceLocationArgument.getId(c, "id").toString(), 20)).then(Commands.argument("limit", IntegerArgumentType.integer(1, 500)).executes(c -> queryBlock(c.getSource(), ResourceLocationArgument.getId(c, "id").toString(), IntegerArgumentType.getInteger(c, "limit"))))))).then(Commands.literal("compress-now").executes(ctx -> {
            AuditRotator.compressTodayIfConfigured();
            AuditRotator.compressNow(AuditJsonLog.currentFile());
            sendMsg(ctx.getSource(), "barix.compress.triggered", true);
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
        sendMsg(src, "barix.audit.flag", true, which, val);
        return 1;
    }

    private static int queryUuid(CommandSourceStack src, UUID uuid, int limit) {
        List<JsonObject> list = AuditQuery.find(uuid.toString(), null, null, null, null, limit);
        sendMsg(src, "barix.audit.entries_for", false, list.size(), uuid);
        for (int i = 0; i < Math.min(10, list.size()); i++) {
            // ligne brute
            sendMsg(src, "barix.raw", false, list.get(i).toString());
        }
        return list.size();
    }

    private static int queryBlock(CommandSourceStack src, String blockId, int limit) {
        List<com.google.gson.JsonObject> list = AuditQuery.find(null, blockId, null, null, null, limit);
        sendMsg(src, "barix.audit.entries_for", false, list.size(), blockId);
        for (int i = 0; i < Math.min(10, list.size()); i++) {
            sendMsg(src, "barix.raw", false, list.get(i).toString());
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
            sendMsg(ctx.getSource(), "barix.alert.test", false);
            return 1;
        }));
    }

    // --------------------------------------------------------------
    // /bx lang reload
    // --------------------------------------------------------------
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> cmdLang() {
        return net.minecraft.commands.Commands.literal("lang").then(net.minecraft.commands.Commands.literal("reload").executes(ctx -> {
            fr.bck.barix.lang.Lang.reload();
            sendMsg(ctx.getSource(), "barix.lang.reloaded", true);
            return 1;
        }));
    }

    // --------------------------------------------------------------
    // Suggestion providers pour les configs
    // --------------------------------------------------------------
    private static final com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> SUGGEST_CONFIG_KEYS = (ctx, builder) -> {
        String rem = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (String k : BarixServerConfig.keys().keySet()) {
            if (rem.isEmpty() || k.startsWith(rem)) builder.suggest(k);
        }
        return builder.buildFuture();
    };

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestConfigValues(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        String key;
        try {
            key = ctx.getArgument("key", String.class);
        } catch (IllegalArgumentException ex) {
            // si pas encore fourni
            return builder.buildFuture();
        }
        // Vérifier la présence de la clé dans le registre lisible pour éviter un éventuel null
        if (!BarixServerConfig.keys().containsKey(key)) return builder.buildFuture();
        var cfg = BarixServerConfig.get(key);
        if (cfg instanceof ForgeConfigSpec.BooleanValue) {
            builder.suggest("true");
            builder.suggest("false");
            builder.suggest("on");
            builder.suggest("off");
        } else {
            builder.suggest(BarixServerConfig.describeValue(cfg));
        }
        return builder.buildFuture();
    }

    // --------------------------------------------------------------
    // /bx config ...
    // --------------------------------------------------------------
    private static LiteralArgumentBuilder<CommandSourceStack> cmdConfig() {
        return Commands.literal("config").then(Commands.literal("list").executes(ctx -> {
            var keys = BarixServerConfig.keys();
            sendMsg(ctx.getSource(), "barix.config.keys_header", false, keys.size());
            keys.forEach((k, v) -> sendMsg(ctx.getSource(), "barix.config.key_line", false, k, BarixServerConfig.describeValue(v)));
            return 1;
        }).then(Commands.argument("prefix", StringArgumentType.string()).suggests(SUGGEST_CONFIG_KEYS).executes(ctx -> {
            String prefix = StringArgumentType.getString(ctx, "prefix").toLowerCase();
            var keys = BarixServerConfig.keys();
            int count = 0;
            for (var e : keys.entrySet()) {
                if (e.getKey().startsWith(prefix)) {
                    count++;
                    sendMsg(ctx.getSource(), "barix.config.key_line", false, e.getKey(), BarixServerConfig.describeValue(e.getValue()));
                }
            }
            if (count == 0) sendMsg(ctx.getSource(), "barix.config.no_prefix", false, prefix);
            return 1;
        }))).then(Commands.literal("get").then(Commands.argument("key", StringArgumentType.string()).suggests(SUGGEST_CONFIG_KEYS).executes(ctx -> {
            String key = StringArgumentType.getString(ctx, "key");
            if (!BarixServerConfig.keys().containsKey(key)) {
                sendFail(ctx.getSource(), "barix.config.unknown_key", key);
                return 0;
            }
            var cfg = BarixServerConfig.get(key);
            sendMsg(ctx.getSource(), "barix.config.key_line", false, key, BarixServerConfig.describeValue(cfg));
            return 1;
        }))).then(Commands.literal("set").then(Commands.argument("key", StringArgumentType.string()).suggests(SUGGEST_CONFIG_KEYS).then(Commands.argument("value", StringArgumentType.greedyString()).suggests(BarixCommands::suggestConfigValues).executes(ctx -> {
            String key = StringArgumentType.getString(ctx, "key");
            String value = StringArgumentType.getString(ctx, "value");
            try {
                Object parsed = BarixServerConfig.parseAndSet(key, value);
                // Effets secondaires utiles
                if (key.equalsIgnoreCase("core.locale")) {
                    Lang.initFromConfig();
                }
                if (key.startsWith("logging.")) {
                    DiscordAlerts.initFromConfig();
                }
                sendMsg(ctx.getSource(), "barix.config.updated", true, key, parsed);
                return 1;
            } catch (IllegalArgumentException ex) {
                sendFail(ctx.getSource(), "barix.error", ex.getMessage());
                return 0;
            }
        })))).then(Commands.literal("save").executes(ctx -> {
            boolean ok = BarixServerConfig.trySaveToDisk();
            if (ok) {
                sendMsg(ctx.getSource(), "barix.config.saved_disk", true);
            } else {
                sendMsg(ctx.getSource(), "barix.config.save_failed", true);
            }
            return ok ? 1 : 0;
        })).then(Commands.literal("reload").executes(ctx -> {
            // Best-effort: recharger les sous-systèmes dépendants; la relecture disque complète dépend de Forge
            try {
                // Recharger langues + alertes depuis la config actuelle
                fr.bck.barix.lang.Lang.initFromConfig();
                fr.bck.barix.alert.DiscordAlerts.initFromConfig();
                sendMsg(ctx.getSource(), "barix.config.reloaded_subsystems", true);
                return 1;
            } catch (Exception ex) {
                sendFail(ctx.getSource(), "barix.config.reload_error", ex.getMessage());
                return 0;
            }
        }));
    }

    // --------------------------------------------------------------
    // /bx topluck ...
    // --------------------------------------------------------------
    private static LiteralArgumentBuilder<CommandSourceStack> cmdTopLuck() {
        return Commands.literal("topluck").then(Commands.literal("status").executes(ctx -> {
            String enabled = BarixServerConfig.LUCK_ENABLED.get() ? "enabled" : "disabled";
            sendMsg(ctx.getSource(), "barix.topluck.status", false, enabled, String.format(Locale.ROOT, "%.3f", BarixServerConfig.LUCK_THRESHOLD.get()), BarixServerConfig.LUCK_COOLDOWN_SECONDS.get());
            sendMsg(ctx.getSource(), "barix.topluck.events_line", false, BarixServerConfig.LUCK_EVENT_FISHING.get(), BarixServerConfig.LUCK_EVENT_LOOTING.get(), BarixServerConfig.LUCK_EVENT_MINING.get());
            sendMsg(ctx.getSource(), "barix.topluck.ratio_line", false, BarixServerConfig.LUCK_RATIO_THRESHOLD.get(), BarixServerConfig.LUCK_RATIO_MIN_BASE.get(), BarixServerConfig.LUCK_RATIO_WINDOW_SECONDS.get(), BarixServerConfig.LUCK_RATIO_SPLIT_DIMENSIONS.get());
            return 1;
        })).then(Commands.literal("on").executes(ctx -> {
            BarixServerConfig.LUCK_ENABLED.set(true);
            sendMsg(ctx.getSource(), "barix.topluck.on", true);
            return 1;
        })).then(Commands.literal("off").executes(ctx -> {
            BarixServerConfig.LUCK_ENABLED.set(false);
            sendMsg(ctx.getSource(), "barix.topluck.off", true);
            return 1;
        })).then(Commands.literal("toggle").executes(ctx -> {
            boolean v = !BarixServerConfig.LUCK_ENABLED.get();
            BarixServerConfig.LUCK_ENABLED.set(v);
            sendMsg(ctx.getSource(), v ? "barix.topluck.on" : "barix.topluck.off", true);
            return 1;
        })).then(Commands.literal("reset-cooldowns").executes(ctx -> {
            fr.bck.barix.topluck.TopLuck.resetCooldowns();
            sendMsg(ctx.getSource(), "barix.topluck.cooldowns_reset", true);
            return 1;
        })).then(Commands.literal("cooldown").then(Commands.argument("seconds", IntegerArgumentType.integer(0, 3600)).executes(ctx -> {
            int v = IntegerArgumentType.getInteger(ctx, "seconds");
            BarixServerConfig.LUCK_COOLDOWN_SECONDS.set(v);
            sendMsg(ctx.getSource(), "barix.topluck.cooldown_set", true, v);
            return 1;
        }))).then(Commands.literal("threshold").then(Commands.argument("value", DoubleArgumentType.doubleArg(-10.0, 100.0)).executes(ctx -> {
            double v = DoubleArgumentType.getDouble(ctx, "value");
            BarixServerConfig.LUCK_THRESHOLD.set(v);
            sendMsg(ctx.getSource(), "barix.topluck.threshold_set", true, String.format(Locale.ROOT, "%.3f", v));
            return 1;
        }))).then(Commands.literal("ratio").then(Commands.literal("threshold").then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0, 1.0)).executes(ctx -> {
            double v = DoubleArgumentType.getDouble(ctx, "value");
            BarixServerConfig.LUCK_RATIO_THRESHOLD.set(v);
            sendMsg(ctx.getSource(), "barix.topluck.ratio.threshold_set", true, v);
            return 1;
        }))).then(Commands.literal("minbase").then(Commands.argument("value", IntegerArgumentType.integer(1, 100000)).executes(ctx -> {
            int v = IntegerArgumentType.getInteger(ctx, "value");
            BarixServerConfig.LUCK_RATIO_MIN_BASE.set(v);
            sendMsg(ctx.getSource(), "barix.topluck.ratio.min_base_set", true, v);
            return 1;
        }))).then(Commands.literal("window").then(Commands.argument("seconds", IntegerArgumentType.integer(10, 86400)).executes(ctx -> {
            int v = IntegerArgumentType.getInteger(ctx, "seconds");
            BarixServerConfig.LUCK_RATIO_WINDOW_SECONDS.set(v);
            sendMsg(ctx.getSource(), "barix.topluck.ratio.window_set", true, v);
            return 1;
        }))).then(Commands.literal("splitdims").then(Commands.literal("on").executes(ctx -> setTopLuckToggle(ctx.getSource(), "splitdims", true))).then(Commands.literal("off").executes(ctx -> setTopLuckToggle(ctx.getSource(), "splitdims", false))).then(Commands.literal("toggle").executes(ctx -> setTopLuckToggle(ctx.getSource(), "splitdims", null))))).then(Commands.literal("events").then(Commands.literal("list").executes(ctx -> {
            sendMsg(ctx.getSource(), "barix.topluck.events_line", false, BarixServerConfig.LUCK_EVENT_FISHING.get(), BarixServerConfig.LUCK_EVENT_LOOTING.get(), BarixServerConfig.LUCK_EVENT_MINING.get());
            sendMsg(ctx.getSource(), "barix.topluck.opts_line", false, BarixServerConfig.LUCK_LOG_CONSOLE.get(), BarixServerConfig.LUCK_BROADCAST.get(), BarixServerConfig.LUCK_NOTIFY_ADMINS_ONLY.get());
            return 1;
        })).then(Commands.literal("enable").then(Commands.argument("name", StringArgumentType.word()).suggests(SUGGEST_TOPLUCK_TOGGLES).executes(ctx -> setTopLuckToggle(ctx.getSource(), StringArgumentType.getString(ctx, "name"), true)))).then(Commands.literal("disable").then(Commands.argument("name", StringArgumentType.word()).suggests(SUGGEST_TOPLUCK_TOGGLES).executes(ctx -> setTopLuckToggle(ctx.getSource(), StringArgumentType.getString(ctx, "name"), false)))));
    }

    private static final com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> SUGGEST_TOPLUCK_TOGGLES = (ctx, builder) -> {
        String rem = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (String s : new String[]{"fishing", "looting", "mining", "broadcast", "notify", "console", "splitdims"}) {
            if (rem.isEmpty() || s.startsWith(rem)) builder.suggest(s);
        }
        return builder.buildFuture();
    };

    private static int setTopLuckToggle(CommandSourceStack src, String name, Boolean value) {
        String n = name.toLowerCase(Locale.ROOT);
        // si value==null => on veut toggler, sinon on applique la valeur fournie
        boolean v = (value != null) ? value : switch (n) {
            case "fishing" -> !BarixServerConfig.LUCK_EVENT_FISHING.get();
            case "looting" -> !BarixServerConfig.LUCK_EVENT_LOOTING.get();
            case "mining" -> !BarixServerConfig.LUCK_EVENT_MINING.get();
            case "broadcast" -> !BarixServerConfig.LUCK_BROADCAST.get();
            case "notify" -> !BarixServerConfig.LUCK_NOTIFY_ADMINS_ONLY.get();
            case "console" -> !BarixServerConfig.LUCK_LOG_CONSOLE.get();
            case "splitdims" -> !BarixServerConfig.LUCK_RATIO_SPLIT_DIMENSIONS.get();
            default -> {
                sendFail(src, "barix.unknown", name);
                yield false;
            }
        };

        switch (n) {
            case "fishing" -> BarixServerConfig.LUCK_EVENT_FISHING.set(v);
            case "looting" -> BarixServerConfig.LUCK_EVENT_LOOTING.set(v);
            case "mining" -> BarixServerConfig.LUCK_EVENT_MINING.set(v);
            case "broadcast" -> BarixServerConfig.LUCK_BROADCAST.set(v);
            case "notify" -> BarixServerConfig.LUCK_NOTIFY_ADMINS_ONLY.set(v);
            case "console" -> BarixServerConfig.LUCK_LOG_CONSOLE.set(v);
            case "splitdims" -> BarixServerConfig.LUCK_RATIO_SPLIT_DIMENSIONS.set(v);
            default -> {
                sendFail(src, "barix.unknown", name);
                return 0;
            }
        }
        sendMsg(src, "barix.topluck.toggle_set", true, n, v);
        return 1;
    }

    // --------------------------------------------------------------
    // /bx lagscan <seconds>
    // --------------------------------------------------------------
    private static LiteralArgumentBuilder<CommandSourceStack> cmdLagScan() {
        return Commands.literal("lagscan").then(Commands.argument("seconds", IntegerArgumentType.integer(1, 600)).executes(ctx -> {
            int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
            CommandSourceStack src = ctx.getSource();

            if (scanActive) {
                sendFail(src, "barix.lagscan.already_running");
                return 0;
            }

            scanActive = true;

            IBarixLagTracker tracker = BarixAPI.getLagTracker();
            tracker.reset();

            sendMsg(src, "barix.lagscan.start", true, seconds);

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

            sendMsg(src, "barix.lagscan.title", false);
            results.entrySet().stream().sorted(Map.Entry.<ResourceLocation, Double>comparingByValue().reversed()).limit(10).forEach(e -> {
                String id = String.valueOf(e.getKey());
                String ms = String.format(Locale.ROOT, "%.3f", e.getValue());
                sendMsg(src, "barix.lagscan.line", false, id, ms);
            });
            sendMsg(src, "barix.lagscan.end", false);
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
                BarixConstants.log.error("§4BarixLag", Lang.tr("barix.lag.read.error", BarixServerConfig.CORE_LOCALE.get()), ex);
            }
        }

        return results;
    }

    // --------------------------------------------------------------
    // /bx modules reload|restart|list|status
    // --------------------------------------------------------------
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> cmdModules() {
        return net.minecraft.commands.Commands.literal("modules")
                .then(net.minecraft.commands.Commands.literal("reload").executes(ctx -> {
                    fr.bck.barix.BarixMod.reloadModulesFromConfig();
                    sendMsg(ctx.getSource(), "barix.modules.reloaded", true);
                    return 1;
                }))
                .then(net.minecraft.commands.Commands.literal("restart").executes(ctx -> {
                    fr.bck.barix.BarixMod.restartModules();
                    sendMsg(ctx.getSource(), "barix.modules.restarted", true);
                    return 1;
                }))
                .then(net.minecraft.commands.Commands.literal("list").executes(ctx -> {
                    java.util.List<String> ids = fr.bck.barix.BarixMod.getLoadedModuleIds();
                    sendMsg(ctx.getSource(), "barix.modules.list.header", false, ids.size());
                    for (String id : ids) {
                        sendMsg(ctx.getSource(), "barix.modules.list.item", false, id);
                    }
                    return 1;
                }))
                .then(net.minecraft.commands.Commands.literal("status").executes(ctx -> {
                    boolean started = fr.bck.barix.BarixMod.isModulesStarted();
                    java.util.List<String> ids = fr.bck.barix.BarixMod.getLoadedModuleIds();
                    sendMsg(ctx.getSource(), "barix.modules.status_line", false, started);
                    sendMsg(ctx.getSource(), "barix.modules.list.header", false, ids.size());
                    for (String id : ids) {
                        sendMsg(ctx.getSource(), "barix.modules.list.item", false, id);
                    }
                    return 1;
                }));
    }

    // --------------------------------------------------------------
    // /bx anticheat on|off|toggle|status
    // --------------------------------------------------------------
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> cmdAntiCheat() {
        return net.minecraft.commands.Commands.literal("anticheat")
                .then(net.minecraft.commands.Commands.literal("status").executes(ctx -> {
                    boolean enabled = fr.bck.barix.config.BarixServerConfig.MODULES_ANTICHEAT_ENABLED.get();
                    sendMsg(ctx.getSource(), enabled ? "barix.anticheat.status.enabled" : "barix.anticheat.status.disabled", false);
                    return 1;
                }))
                .then(net.minecraft.commands.Commands.literal("on").executes(ctx -> {
                    fr.bck.barix.config.BarixServerConfig.MODULES_ANTICHEAT_ENABLED.set(true);
                    fr.bck.barix.BarixMod.reloadModulesFromConfig();
                    sendMsg(ctx.getSource(), "barix.anticheat.enabled", true);
                    return 1;
                }))
                .then(net.minecraft.commands.Commands.literal("off").executes(ctx -> {
                    fr.bck.barix.config.BarixServerConfig.MODULES_ANTICHEAT_ENABLED.set(false);
                    fr.bck.barix.BarixMod.reloadModulesFromConfig();
                    sendMsg(ctx.getSource(), "barix.anticheat.disabled", true);
                    return 1;
                }))
                .then(net.minecraft.commands.Commands.literal("toggle").executes(ctx -> {
                    boolean v = !fr.bck.barix.config.BarixServerConfig.MODULES_ANTICHEAT_ENABLED.get();
                    fr.bck.barix.config.BarixServerConfig.MODULES_ANTICHEAT_ENABLED.set(v);
                    fr.bck.barix.BarixMod.reloadModulesFromConfig();
                    sendMsg(ctx.getSource(), v ? "barix.anticheat.enabled" : "barix.anticheat.disabled", true);
                    return 1;
                }));
    }
}
