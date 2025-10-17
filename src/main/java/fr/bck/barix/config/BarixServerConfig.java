package fr.bck.barix.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class BarixServerConfig {

    public static final ForgeConfigSpec SPEC;

    // --- CORE ---
    public static final ForgeConfigSpec.ConfigValue<String> CORE_LOCALE;
    public static final ForgeConfigSpec.BooleanValue CORE_DEBUG;
    public static final ForgeConfigSpec.BooleanValue CORE_ENABLED;

    // --- AUDIT ---
    public static final ForgeConfigSpec.BooleanValue AUDIT_ENABLE_ALL;
    public static final ForgeConfigSpec.IntValue AUDIT_CLICK_OPEN_WINDOW_MS;
    public static final ForgeConfigSpec.BooleanValue AUDIT_LOG_OPEN;
    public static final ForgeConfigSpec.BooleanValue AUDIT_LOG_CLOSE;
    public static final ForgeConfigSpec.BooleanValue AUDIT_LOG_PLACE;
    public static final ForgeConfigSpec.BooleanValue AUDIT_LOG_BREAK;
    public static final ForgeConfigSpec.BooleanValue AUDIT_LOG_LOGIN;
    public static final ForgeConfigSpec.BooleanValue AUDIT_LOG_LOGOUT;
    public static final ForgeConfigSpec.BooleanValue AUDIT_LOG_RESPAWN;
    public static final ForgeConfigSpec.BooleanValue AUDIT_LOG_DIM_CHANGE;
    public static final ForgeConfigSpec.BooleanValue AUDIT_LOG_PICKUP;
    public static final ForgeConfigSpec.BooleanValue AUDIT_LOG_DROP;
    public static final ForgeConfigSpec.BooleanValue AUDIT_LOG_CRAFT;
    public static final ForgeConfigSpec.BooleanValue AUDIT_LOG_SMELT;
    public static final ForgeConfigSpec.BooleanValue AUDIT_LOG_EXPLOSION;
    public static final ForgeConfigSpec.BooleanValue AUDIT_LOG_BLOCK_INTERACT;
    public static final ForgeConfigSpec.BooleanValue AUDIT_LOG_ANVIL;
    public static final ForgeConfigSpec.BooleanValue AUDIT_LOG_CHAT;

    // --- REGIONS ---
    public static final ForgeConfigSpec.BooleanValue REGIONS_ENABLED;
    public static final ForgeConfigSpec.BooleanValue REGIONS_PARTICLES;
    public static final ForgeConfigSpec.IntValue REGIONS_PREVIEW_RADIUS;

    // --- PERMS ---
    public static final ForgeConfigSpec.IntValue PERMS_DEFAULT_LEVEL; // 0..4
    public static final ForgeConfigSpec.BooleanValue PERMS_OP_IS_ADMIN;

    // --- LOGGING ---
    public static final ForgeConfigSpec.BooleanValue LOG_JSONL;
    public static final ForgeConfigSpec.BooleanValue LOG_ROLL_DAILY;
    public static final ForgeConfigSpec.LongValue LOG_MAX_BYTES; // rotation si >=
    public static final ForgeConfigSpec.BooleanValue LOG_COMPRESS_ON_STOP;
    public static final ForgeConfigSpec.ConfigValue<String> LOG_DISCORD_WEBHOOK; // vide = off
    // Nouveaux réglages d'alertes/Discord
    public static final ForgeConfigSpec.ConfigValue<String> LOG_DISCORD_USERNAME;
    public static final ForgeConfigSpec.IntValue LOG_ALERT_COOLDOWN_SECONDS;
    public static final ForgeConfigSpec.BooleanValue LOG_ALERT_ADMINS_ONLY;

    // --- ANTI XRAY ---
    public static final ForgeConfigSpec.BooleanValue ANTIXRAY_ENABLE;
    public static final ForgeConfigSpec.BooleanValue ANTIXRAY_HIDE_SURFACE;
    public static final ForgeConfigSpec.IntValue ANTIXRAY_REVEAL_RADIUS;

    public static final ForgeConfigSpec.DoubleValue LAG_MONITOR_THRESHOLD_MS;

    // Registre lisible: "section.key" -> valeur config
    private static final Map<String, ForgeConfigSpec.ConfigValue<?>> KEY_REGISTRY = new LinkedHashMap<>();

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.push("core");
        CORE_ENABLED = b.define("enabled", true);
        CORE_DEBUG = b.define("debug", false);
        CORE_LOCALE = b.define("locale", "fr_fr");
        b.pop();

        b.push("audit");
        AUDIT_ENABLE_ALL = b.comment("Active/désactive tout l’audit.").define("enable_all", true);
        AUDIT_LOG_OPEN = b.define("log_open", true);
        AUDIT_LOG_CLOSE = b.define("log_close", true);
        AUDIT_LOG_PLACE = b.define("log_place", true);
        AUDIT_LOG_BREAK = b.define("log_break", true);
        AUDIT_CLICK_OPEN_WINDOW_MS = b.comment("Fenêtre clic→open (ms).").defineInRange("click_open_window_ms", 3000, 0, 60000);
        AUDIT_LOG_LOGIN = b.define("log_login", true);
        AUDIT_LOG_LOGOUT = b.define("log_logout", true);
        AUDIT_LOG_RESPAWN = b.define("log_respawn", true);
        AUDIT_LOG_DIM_CHANGE = b.define("log_dim_change", true);
        AUDIT_LOG_PICKUP = b.define("log_pickup", true);
        AUDIT_LOG_DROP = b.define("log_drop", true);
        AUDIT_LOG_CRAFT = b.define("log_craft", true);
        AUDIT_LOG_SMELT = b.define("log_smelt", true);
        AUDIT_LOG_EXPLOSION = b.define("log_explosion", true);
        AUDIT_LOG_BLOCK_INTERACT = b.define("log_block_interact", true);
        AUDIT_LOG_ANVIL = b.define("log_anvil", true);
        AUDIT_LOG_CHAT = b.define("log_chat", false);
        b.pop();

        b.push("regions");
        REGIONS_ENABLED = b.define("enabled", true);
        REGIONS_PARTICLES = b.define("selection_particles", true);
        REGIONS_PREVIEW_RADIUS = b.defineInRange("preview_radius", 16, 4, 128);
        b.pop();

        b.push("permissions");
        PERMS_DEFAULT_LEVEL = b.comment("Niveau requis par défaut pour /bx (0..4).").defineInRange("default_level", 3, 0, 4);
        PERMS_OP_IS_ADMIN = b.define("op_is_admin", true);
        b.pop();

        b.push("logging");
        LOG_JSONL = b.define("jsonl", true);
        LOG_ROLL_DAILY = b.define("roll_daily", true);
        LOG_DISCORD_WEBHOOK = b.define("discord_webhook", "");
        LOG_DISCORD_USERNAME = b.comment("Nom d'affichage pour les webhooks Discord.").define("discord_username", "Barix");
        LOG_ALERT_COOLDOWN_SECONDS = b.comment("Anti-spam: délai minimal entre 2 alertes identiques.").defineInRange("alert_cooldown_seconds", 30, 0, 3600);
        LOG_ALERT_ADMINS_ONLY = b.comment("Si vrai, les alertes in-game sont envoyées seulement aux administrateurs (permission >=3).").define("alert_ingame_admins_only", true);
        LOG_MAX_BYTES = b.defineInRange("max_bytes", 10_000_000L, 100_000L, 2_000_000_000L);
        LOG_COMPRESS_ON_STOP = b.define("compress_on_stop", true);
        b.pop();

        b.push("antixray");
        ANTIXRAY_ENABLE = b.define("enable", true);
        ANTIXRAY_HIDE_SURFACE = b.comment("Masquer les minerais exposés à l’air (false = ne pas masquer s’ils touchent l’air).").define("hide_surface", true);
        ANTIXRAY_REVEAL_RADIUS = b.comment("Rayon de révélation autour du joueur (blocs).").defineInRange("reveal_radius", 8, 1, 64);
        b.pop();

        b.push("lag_monitor");
        LAG_MONITOR_THRESHOLD_MS = b.comment("Seuil de lag pour alerte (ms).").defineInRange("lag_threshold_ms", 100.0, 10.0, 9999.0);
        b.pop();

        SPEC = b.build();

        // Remplir le registre lisible
        put("core.enabled", CORE_ENABLED);
        put("core.debug", CORE_DEBUG);
        put("core.locale", CORE_LOCALE);

        put("audit.enable_all", AUDIT_ENABLE_ALL);
        put("audit.log_open", AUDIT_LOG_OPEN);
        put("audit.log_close", AUDIT_LOG_CLOSE);
        put("audit.log_place", AUDIT_LOG_PLACE);
        put("audit.log_break", AUDIT_LOG_BREAK);
        put("audit.click_open_window_ms", AUDIT_CLICK_OPEN_WINDOW_MS);
        put("audit.log_login", AUDIT_LOG_LOGIN);
        put("audit.log_logout", AUDIT_LOG_LOGOUT);
        put("audit.log_respawn", AUDIT_LOG_RESPAWN);
        put("audit.log_dim_change", AUDIT_LOG_DIM_CHANGE);
        put("audit.log_pickup", AUDIT_LOG_PICKUP);
        put("audit.log_drop", AUDIT_LOG_DROP);
        put("audit.log_craft", AUDIT_LOG_CRAFT);
        put("audit.log_smelt", AUDIT_LOG_SMELT);
        put("audit.log_explosion", AUDIT_LOG_EXPLOSION);
        put("audit.log_block_interact", AUDIT_LOG_BLOCK_INTERACT);
        put("audit.log_anvil", AUDIT_LOG_ANVIL);
        put("audit.log_chat", AUDIT_LOG_CHAT);

        put("regions.enabled", REGIONS_ENABLED);
        put("regions.selection_particles", REGIONS_PARTICLES);
        put("regions.preview_radius", REGIONS_PREVIEW_RADIUS);

        put("permissions.default_level", PERMS_DEFAULT_LEVEL);
        put("permissions.op_is_admin", PERMS_OP_IS_ADMIN);

        put("logging.jsonl", LOG_JSONL);
        put("logging.roll_daily", LOG_ROLL_DAILY);
        put("logging.discord_webhook", LOG_DISCORD_WEBHOOK);
        put("logging.discord_username", LOG_DISCORD_USERNAME);
        put("logging.alert_cooldown_seconds", LOG_ALERT_COOLDOWN_SECONDS);
        put("logging.alert_ingame_admins_only", LOG_ALERT_ADMINS_ONLY);
        put("logging.max_bytes", LOG_MAX_BYTES);
        put("logging.compress_on_stop", LOG_COMPRESS_ON_STOP);

        put("antixray.enable", ANTIXRAY_ENABLE);
        put("antixray.hide_surface", ANTIXRAY_HIDE_SURFACE);
        put("antixray.reveal_radius", ANTIXRAY_REVEAL_RADIUS);

        put("lag_monitor.lag_threshold_ms", LAG_MONITOR_THRESHOLD_MS);
    }

    private static void put(String key, ForgeConfigSpec.ConfigValue<?> v) {
        KEY_REGISTRY.put(key, v);
    }

    public static Map<String, ForgeConfigSpec.ConfigValue<?>> allKeys() {
        return Collections.unmodifiableMap(KEY_REGISTRY);
    }

    public static ForgeConfigSpec.ConfigValue<?> get(String path) {
        if (path == null) return null;
        String p = path.toLowerCase(Locale.ROOT).trim();
        return KEY_REGISTRY.get(p);
    }

    public static String describeValue(ForgeConfigSpec.ConfigValue<?> cfg) {
        Object v = cfg != null ? cfg.get() : null;
        return Objects.toString(v);
    }

    public static Object parseAndSet(String path, String value) throws IllegalArgumentException {
        ForgeConfigSpec.ConfigValue<?> cfg = get(path);
        if (cfg == null) throw new IllegalArgumentException("Clé inconnue: " + path);
        Object parsed;
        if (cfg instanceof ForgeConfigSpec.BooleanValue) {
            parsed = parseBool(value);
        } else if (cfg instanceof ForgeConfigSpec.IntValue) {
            parsed = parseInt(value);
        } else if (cfg instanceof ForgeConfigSpec.LongValue) {
            parsed = parseLong(value);
        } else if (cfg instanceof ForgeConfigSpec.DoubleValue) {
            parsed = parseDouble(value);
        } else {
            // String / autres
            parsed = value;
        }
        @SuppressWarnings("unchecked") ForgeConfigSpec.ConfigValue<Object> cv = (ForgeConfigSpec.ConfigValue<Object>) cfg;
        cv.set(parsed);
        return parsed;
    }

    private static boolean parseBool(String v) {
        String s = v.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "true", "1", "yes", "on", "y" -> true;
            case "false", "0", "no", "off", "n" -> false;
            default -> throw new IllegalArgumentException("Valeur booléenne invalide: " + v);
        };
    }

    private static int parseInt(String v) {
        return Integer.decode(v.replace("_", ""));
    }

    private static long parseLong(String v) {
        return Long.decode(v.replace("_", ""));
    }

    private static double parseDouble(String v) {
        return Double.parseDouble(v.replace("_", ""));
    }

    // Best-effort pour demander à Forge de sauvegarder la config serveur
    public static boolean trySaveToDisk() {
        try {
            Class<?> trackerCls = Class.forName("net.minecraftforge.fml.config.ConfigTracker");
            Object tracker = trackerCls.getField("INSTANCE").get(null);
            // saveConfigs(ModConfig.Type)
            Class<?> typeCls = Class.forName("net.minecraftforge.fml.config.ModConfig$Type");
            Object serverType = Enum.valueOf((Class<Enum>) typeCls, "SERVER");
            for (var m : trackerCls.getMethods()) {
                if (m.getName().equals("saveConfigs") && m.getParameterCount() == 1) {
                    m.invoke(tracker, serverType);
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private BarixServerConfig() {
    }
}