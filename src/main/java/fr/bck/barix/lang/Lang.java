package fr.bck.barix.lang;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import fr.bck.barix.config.BarixServerConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Lang {
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {
    }.getType();
    private static final Map<String, Map<String, String>> BUNDLES = new ConcurrentHashMap<>();
    private static volatile String defaultLang = "en_us";

    private Lang() {
    }

    public static void initFromConfig() {
        defaultLang = sanitize(BarixServerConfig.CORE_LOCALE.get());
        reload();
    }

    public static void reload() {
        BUNDLES.clear();
        // Charger tous les fichiers du dossier config/barix/lang/*.json
        Path dir = FMLPaths.CONFIGDIR.get().resolve("barix").resolve("lang");
        try {
            if (Files.isDirectory(dir)) {
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.json")) {
                    for (Path p : ds) {
                        String code = fileCode(p.getFileName().toString());
                        Map<String, String> map = readJsonFile(p);
                        if (!map.isEmpty()) BUNDLES.put(code, map);
                    }
                }
            }
        } catch (IOException ignored) {
        }

        // Délaisser sur défauts embarqués (JAR) pour en_us + fr_fr + defaultLang
        loadJarDefault("en_us");
        loadJarDefault("fr_fr");
        if (!"en_us".equals(defaultLang) && !"fr_fr".equals(defaultLang)) {
            loadJarDefault(defaultLang);
        }
    }

    public static String tr(String key, String code, Object... args) {
        String lang = sanitize(code);
        String raw = lookup(key, lang);
        if (raw == null) raw = lookup(key, defaultLang);
        if (raw == null) raw = lookup(key, "en_us");
        if (raw == null) raw = key;

        try {
            MessageFormat mf = new MessageFormat(raw, localeOf(lang));
            return mf.format(args == null ? new Object[0] : args);
        } catch (Exception e) {
            // En cas de problème de format, retourner brut
            return raw;
        }
    }

    // --- Surcharges type-safe (autocomplétion via enum généré) ---
    public static String tr(LangKey key, String code, Object... args) {
        return tr(key.id, code, args);
    }

    public static Component c(String key, ServerPlayer player, Object... args) {
        String lang = playerLang(player);
        return Component.literal(tr(key, lang, args));
    }

    public static Component c(LangKey key, ServerPlayer player, Object... args) {
        return c(key.id, player, args);
    }

    public static String playerLang(ServerPlayer p) {
        try {
            Object conn = p.connection;
            // Essayer une méthode connection.clientInformation()
            try {
                Method m = conn.getClass().getMethod("clientInformation");
                Object ci = m.invoke(conn);
                String l = readLanguage(ci);
                if (l != null) return sanitize(l);
            } catch (NoSuchMethodException ignored) {
                // ignore
            }
            // Essayer un champ connection.clientInformation
            try {
                var f = conn.getClass().getDeclaredField("clientInformation");
                f.setAccessible(true);
                Object ci = f.get(conn);
                String l = readLanguage(ci);
                if (l != null) return sanitize(l);
            } catch (NoSuchFieldException ignored) {
                // ignore
            }
        } catch (Throwable ignored) {
        }
        return defaultLang;
    }

    private static String readLanguage(Object clientInfo) {
        if (clientInfo == null) return null;
        try {
            var m = clientInfo.getClass().getMethod("language");
            Object v = m.invoke(clientInfo);
            return v != null ? v.toString() : null;
        } catch (NoSuchMethodException e) {
            try {
                var f = clientInfo.getClass().getDeclaredField("language");
                f.setAccessible(true);
                Object v = f.get(clientInfo);
                return v != null ? v.toString() : null;
            } catch (Throwable t) {
                return null;
            }
        } catch (Throwable t) {
            return null;
        }
    }

    private static String lookup(String key, String code) {
        if (code == null) return null;
        Map<String, String> map = BUNDLES.get(code);
        return map != null ? map.get(key) : null;
    }

    private static void loadJarDefault(String code) {
        if (BUNDLES.containsKey(code)) return; // déjà fourni par config
        String path = "/lang/server/" + code + ".json";
        try (var in = Lang.class.getResourceAsStream(path)) {
            if (in == null) return;
            try (var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                Map<String, String> map = GSON.fromJson(reader, MAP_TYPE);
                if (map != null && !map.isEmpty()) {
                    // Ne pas écraser ce qui vient de config
                    BUNDLES.putIfAbsent(code, map);
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static Map<String, String> readJsonFile(Path p) {
        try (var reader = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            Map<String, String> map = GSON.fromJson(reader, MAP_TYPE);
            return map != null ? map : Map.of();
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static String fileCode(String filename) {
        // ex: en_us.json -> en_us
        int i = filename.lastIndexOf('.');
        return sanitize(i > 0 ? filename.substring(0, i) : filename);
    }

    private static String sanitize(String code) {
        if (code == null) return "en_us";
        return code.trim().toLowerCase(Locale.ROOT);
    }

    private static Locale localeOf(String code) {
        try {
            String[] p = code.split("[_\\-]");
            return p.length >= 2 ? new Locale(p[0], p[1]) : new Locale(p[0]);
        } catch (Exception e) {
            return Locale.ENGLISH;
        }
    }
}