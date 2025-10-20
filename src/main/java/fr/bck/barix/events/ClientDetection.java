package fr.bck.barix.events;

import fr.bck.barix.BarixConstants;
import fr.bck.barix.alert.DiscordAlerts;
import fr.bck.barix.config.BarixServerConfig;
import fr.bck.barix.lang.Lang;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ClientDetection {

    private static final class Recheck { int ticksLeft; int attemptsLeft; Recheck(int t, int a){ this.ticksLeft=t; this.attemptsLeft=a; }}
    private static final Map<UUID, Recheck> PENDING_RECHECK = new ConcurrentHashMap<>();

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!Boolean.TRUE.equals(BarixServerConfig.CLIENT_DETECT_ENABLED.get())) return;
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;

        // Collecter infos client (premier passage)
        int protocol = getProtocolVersion(sp);
        String brand = getClientBrand(sp);
        Map<String, String> mods = getClientMods(sp);

        if (Boolean.TRUE.equals(BarixServerConfig.CLIENT_LOG_ON_LOGIN.get())) {
            BarixConstants.log.info("§dClient", Lang.tr("barix.client.login.info", BarixServerConfig.CORE_LOCALE.get(), sp.getGameProfile().getName(), brand, protocol, mods.keySet()));
        }

        // Appliquer deny rules immédiatement si possible
        String denyIdsCsv = Optional.ofNullable(BarixServerConfig.CLIENT_DENY_MOD_IDS.get()).orElse("");
        String denyNamesCsv = Optional.ofNullable(BarixServerConfig.CLIENT_DENY_MOD_NAMES.get()).orElse("");
        Set<String> denyIds = csvToSet(denyIdsCsv);
        Set<String> denyNames = csvToSet(denyNamesCsv);

        String matched = findDeniedMod(mods, denyIds, denyNames);
        if (matched != null) {
            applyDenyAction(sp, matched, brand, protocol);
            return; // déjà traité
        }

        // Si infos incomplètes, planifier des re-vérifications (jusqu'à 5 essais, toutes les 20 ticks)
        boolean incomplete = brand.isBlank() || "unknown".equalsIgnoreCase(brand) || protocol < 0 || mods.isEmpty();
        if (incomplete) {
            PENDING_RECHECK.put(sp.getUUID(), new Recheck(20, 5));
            debugDump(sp, brand, protocol, mods);
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (PENDING_RECHECK.isEmpty()) return;
        var it = PENDING_RECHECK.entrySet().iterator();
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        while (it.hasNext()) {
            var entry = it.next();
            Recheck r = entry.getValue();
            if (--r.ticksLeft > 0) continue;
            // temps écoulé pour cette tentative
            UUID uuid = entry.getKey();
            ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
            if (sp == null || !sp.connection.isAcceptingMessages()) { it.remove(); continue; }

            int p2 = getProtocolVersion(sp);
            String b2 = getClientBrand(sp);
            Map<String, String> m2 = getClientMods(sp);

            boolean improved = false;
            if ((b2 != null && !b2.isBlank() && !"unknown".equalsIgnoreCase(b2))) improved = true;
            if (p2 >= 0) improved = true;
            if (!m2.isEmpty()) improved = true;
            if (improved && Boolean.TRUE.equals(BarixServerConfig.CLIENT_LOG_ON_LOGIN.get())) {
                BarixConstants.log.info("§dClient", Lang.tr("barix.client.login.info", BarixServerConfig.CORE_LOCALE.get(), sp.getGameProfile().getName(), b2, p2, m2.keySet()));
            }

            // Appliquer deny rules
            String denyIdsCsv = Optional.ofNullable(BarixServerConfig.CLIENT_DENY_MOD_IDS.get()).orElse("");
            String denyNamesCsv = Optional.ofNullable(BarixServerConfig.CLIENT_DENY_MOD_NAMES.get()).orElse("");
            Set<String> denyIds = csvToSet(denyIdsCsv);
            Set<String> denyNames = csvToSet(denyNamesCsv);
            String m = findDeniedMod(m2, denyIds, denyNames);
            if (m != null) { it.remove(); applyDenyAction(sp, m, b2, p2); continue; }

            // Si toujours incomplet et encore des tentatives: reprogrammer, sinon retirer
            boolean stillIncomplete = (b2 == null || b2.isBlank() || "unknown".equalsIgnoreCase(b2)) || p2 < 0 || m2.isEmpty();
            if (stillIncomplete && --r.attemptsLeft > 0) {
                r.ticksLeft = 20; // prochaine tentative dans 20 ticks
                debugDump(sp, b2, p2, m2);
            } else {
                if (stillIncomplete) debugDump(sp, b2, p2, m2); // dernier dump si échec final
                it.remove();
            }
        }
    }

    private static void applyDenyAction(ServerPlayer sp, String matched, String brand, int protocol) {
        String action = Optional.ofNullable(BarixServerConfig.CLIENT_ACTION_ON_DENY.get()).orElse("kick").trim().toLowerCase(Locale.ROOT);
        String title = Lang.tr("barix.client.deny.title", BarixServerConfig.CORE_LOCALE.get(), sp.getGameProfile().getName());
        String desc = Lang.tr("barix.client.deny.desc", BarixServerConfig.CORE_LOCALE.get(), matched, brand, protocol);
        switch (action) {
            case "log" -> BarixConstants.log.warn("§dClient", desc);
            case "alert" -> DiscordAlerts.alert("client_deny_" + sp.getUUID(), title, desc, 0xDD4444);
            case "kick" -> {
                DiscordAlerts.alert("client_deny_" + sp.getUUID(), title, desc, 0xDD4444);
                String msg = Optional.ofNullable(BarixServerConfig.CLIENT_KICK_MESSAGE.get()).orElse("Client non autorisé: {mod} interdit.");
                msg = msg.replace("{mod}", matched);
                try {
                    sp.connection.disconnect(Component.literal(msg));
                } catch (Throwable t) {
                    BarixConstants.log.error("§dClient", Lang.tr("barix.kick.error", BarixServerConfig.CORE_LOCALE.get(), t.getMessage()), t);
                }
            }
            default -> BarixConstants.log.warn("§dClient", Lang.tr("barix.action.unknown", BarixServerConfig.CORE_LOCALE.get(), action));
        }
    }

    private static Set<String> csvToSet(String csv) {
        if (csv == null || csv.isBlank()) return Collections.emptySet();
        String[] parts = csv.split(",");
        Set<String> r = new HashSet<>();
        for (String p : parts) {
            String s = p.trim().toLowerCase(Locale.ROOT);
            if (!s.isEmpty()) r.add(s);
        }
        return r;
    }

    private static String findDeniedMod(Map<String, String> mods, Set<String> denyIds, Set<String> denyNames) {
        // check ids exacts
        for (String id : mods.keySet()) {
            String lid = id.toLowerCase(Locale.ROOT);
            if (denyIds.contains(lid)) return id;
        }
        // check noms partiels si dispo
        for (Map.Entry<String, String> e : mods.entrySet()) {
            String name = Optional.ofNullable(e.getValue()).orElse("").toLowerCase(Locale.ROOT);
            for (String n : denyNames) {
                if (!n.isEmpty() && name.contains(n)) return e.getKey();
            }
        }
        return null;
    }

    private static Object getNetworkConnection(Object listener) {
        if (listener == null) return null;
        // Essayer champ 'connection'
        try {
            Field f = listener.getClass().getDeclaredField("connection");
            f.setAccessible(true);
            Object v = f.get(listener);
            if (v != null) return v;
        } catch (Throwable ignored) {}
        // Essayer méthode getConnection()
        try {
            Method m = listener.getClass().getMethod("getConnection");
            return m.invoke(listener);
        } catch (Throwable ignored) {}
        return null;
    }

    private static int getProtocolVersion(ServerPlayer sp) {
        try {
            Object conn = getNetworkConnection(sp.connection);
            if (conn == null) return -1;
            // Essais directs
            for (String mname : new String[]{"getProtocolVersion", "protocolVersion"}) {
                try {
                    Method m = conn.getClass().getMethod(mname);
                    Object v = m.invoke(conn);
                    if (v instanceof Integer i) return i;
                } catch (Throwable ignored) {}
            }
            // Méthodes int sans args contenant 'protocol'
            for (Method m : conn.getClass().getMethods()) {
                if (m.getParameterCount() == 0 && (m.getReturnType() == int.class || m.getReturnType() == Integer.class)) {
                    String n = m.getName().toLowerCase(Locale.ROOT);
                    if (n.contains("protocol")) {
                        try { Object v = m.invoke(conn); if (v instanceof Integer i) return i; } catch (Throwable ignored) {}
                    }
                }
            }
            // getProtocol() -> objet, essayer id()/getId()/ordinal()
            try {
                Method gp = conn.getClass().getMethod("getProtocol");
                Object proto = gp.invoke(conn);
                if (proto != null) {
                    for (String idm : new String[]{"id", "getId", "protocolId"}) {
                        try { Method im = proto.getClass().getMethod(idm); Object v = im.invoke(proto); if (v instanceof Integer i) return i; } catch (Throwable ignored) {}
                    }
                    try { Method ord = proto.getClass().getMethod("ordinal"); Object v = ord.invoke(proto); if (v instanceof Integer i) return i; } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
            // Champ 'protocolVersion'
            for (Field f : conn.getClass().getDeclaredFields()) {
                try {
                    if (f.getName().toLowerCase(Locale.ROOT).contains("protocol") && (f.getType() == int.class || f.getType() == Integer.class)) {
                        f.setAccessible(true);
                        Object v = f.get(conn);
                        if (v instanceof Integer i) return i;
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return -1;
    }

    private static String getClientBrand(ServerPlayer sp) {
        // 1) Essais directs via méthodes/champs ServerPlayer
        try {
            for (String name : new String[]{"getClientBrandName", "getClientBrand"}) {
                try {
                    Method m = sp.getClass().getMethod(name);
                    Object v = m.invoke(sp);
                    if (v != null) {
                        String s = String.valueOf(v).trim();
                        if (!s.isEmpty()) return s;
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        try {
            for (String f : new String[]{"clientBrandName", "clientBrand"}) {
                try {
                    Field fld = sp.getClass().getDeclaredField(f);
                    fld.setAccessible(true);
                    Object v = fld.get(sp);
                    if (v != null) {
                        String s = String.valueOf(v).trim();
                        if (!s.isEmpty()) return s;
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        // 2) Essais via sp.connection (ServerGamePacketListenerImpl)
        try {
            Object listener = sp.connection;
            for (String name : new String[]{"getClientBrand", "getBrand", "getClientBrandName"}) {
                try { Method m = listener.getClass().getMethod(name); Object v = m.invoke(listener); if (v != null) { String s = String.valueOf(v).trim(); if (!s.isEmpty()) return s; } } catch (Throwable ignored) {}
            }
            for (String f : new String[]{"clientBrand", "clientBrandName"}) {
                try { Field fld = listener.getClass().getDeclaredField(f); fld.setAccessible(true); Object v = fld.get(listener); if (v != null) { String s = String.valueOf(v).trim(); if (!s.isEmpty()) return s; } } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        // 3) Fallback via Forge NetworkHooks ConnectionData
        try {
            Object data = getForgeConnectionData(sp);
            if (data != null) {
                Object v = tryInvokeNoArg(data, "getBrand", "getClientBrand", "brand");
                if (v != null) {
                    String s = String.valueOf(v).trim();
                    if (!s.isEmpty()) return s;
                }
                Object brands = tryInvokeNoArg(data, "getBrands", "brands");
                if (brands instanceof Collection<?>) {
                    String s = ((Collection<?>) brands).stream().map(String::valueOf).collect(Collectors.joining(","));
                    if (!s.isEmpty()) return s;
                }
            }
        } catch (Throwable ignored) {}
        return "unknown";
    }

    private static Map<String, String> getClientMods(ServerPlayer sp) {
        // Best-effort via NetworkHooks.getConnectionData(sp)
        try {
            Object data = getForgeConnectionData(sp);
            if (data != null) {
                for (String mname : new String[]{"getModList", "modList", "getMods", "mods"}) {
                    try {
                        Method m = data.getClass().getMethod(mname);
                        Object r = m.invoke(data);
                        Map<String, String> parsed = parseModsResult(r);
                        if (!parsed.isEmpty()) return parsed;
                    } catch (Throwable ignored) {}
                }
                for (String mname : new String[]{"toMap", "asMap"}) {
                    try {
                        Method m = data.getClass().getMethod(mname);
                        Object r = m.invoke(data);
                        Map<String, String> parsed = parseModsResult(r);
                        if (!parsed.isEmpty()) return parsed;
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
        return Collections.emptyMap();
    }

    // ---- Helpers réflexion ----
    private static Object getForgeConnectionData(ServerPlayer sp) {
        try {
            Class<?> hooks = Class.forName("net.minecraftforge.network.NetworkHooks");
            Method m = null;
            for (Method mm : hooks.getDeclaredMethods()) {
                if (mm.getName().equals("getConnectionData") && mm.getParameterCount() == 1) {
                    m = mm;
                    break;
                }
            }
            if (m != null) {
                return m.invoke(null, sp);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static Object tryInvokeNoArg(Object obj, String... methodNames) {
        for (String n : methodNames) {
            try {
                Method m = obj.getClass().getMethod(n);
                return m.invoke(obj);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Map<String, String> parseModsResult(Object r) {
        Map<String, String> res = new LinkedHashMap<>();
        if (r == null) return res;
        try {
            if (r instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    String id = String.valueOf(e.getKey());
                    String ver = e.getValue() == null ? null : String.valueOf(e.getValue());
                    res.put(id, ver);
                }
                return res;
            }
            if (r instanceof Optional<?> opt) {
                if (opt.isPresent()) return parseModsResult(opt.get());
                return res;
            }
            if (r.getClass().isArray()) {
                int len = java.lang.reflect.Array.getLength(r);
                for (int i = 0; i < len; i++) {
                    Object it = java.lang.reflect.Array.get(r, i);
                    addModFromInfo(res, it);
                }
                return res;
            }
            if (r instanceof Iterable<?> it) {
                for (Object o : it) addModFromInfo(res, o);
                return res;
            }
            // Dernier recours: unique objet ModInfo-like
            addModFromInfo(res, r);
        } catch (Throwable ignored) {}
        return res;
    }

    private static void addModFromInfo(Map<String, String> res, Object info) {
        if (info == null) return;
        try {
            String id = invokeStringFirst(info, "getModId", "getModID", "getModid", "getId", "id", "modId", "modid");
            String ver = invokeStringFirst(info, "getVersion", "getDisplayVersion", "version");
            if (id != null && !id.isBlank()) res.put(id, ver);
        } catch (Throwable ignored) {}
    }

    private static String invokeStringFirst(Object obj, String... methodNames) {
        for (String n : methodNames) {
            try {
                Method m = obj.getClass().getMethod(n);
                Object v = m.invoke(obj);
                if (v != null) {
                    String s = String.valueOf(v).trim();
                    if (!s.isEmpty()) return s;
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static void debugDump(ServerPlayer sp, String brand, int protocol, Map<String, String> mods) {
        if (!Boolean.TRUE.equals(BarixServerConfig.CORE_DEBUG.get())) return;
        try {
            Object listener = sp.connection;
            Object conn = getNetworkConnection(listener);
            Object data = getForgeConnectionData(sp);
            BarixConstants.log.debug("§dClient/DBG", Lang.tr("barix.client.debug.summary", BarixServerConfig.CORE_LOCALE.get(),
                    sp.getGameProfile().getName(), brand, protocol, mods.keySet(),
                    (listener != null ? listener.getClass().getName() : "null"),
                    (conn != null ? conn.getClass().getName() : "null"),
                    (data != null ? data.getClass().getName() : "null")));
            // Lister méthodes pertinentes
            if (conn != null) {
                List<String> protoMethods = new ArrayList<>();
                for (Method m : conn.getClass().getMethods()) {
                    if (m.getParameterCount() == 0) {
                        String n = m.getName();
                        if (n.toLowerCase(Locale.ROOT).contains("protocol")) protoMethods.add(n + ":" + m.getReturnType().getSimpleName());
                    }
                }
                BarixConstants.log.debug("§dClient/DBG", Lang.tr("barix.client.debug.conn_protocol_methods", BarixServerConfig.CORE_LOCALE.get(), protoMethods));
            }
            if (listener != null) {
                List<String> brandMethods = new ArrayList<>();
                for (String n : new String[]{"getClientBrand", "getBrand", "getClientBrandName"}) {
                    try { Method m = listener.getClass().getMethod(n); brandMethods.add(n + ":" + m.getReturnType().getSimpleName()); } catch (Throwable ignored) {}
                }
                BarixConstants.log.debug("§dClient/DBG", Lang.tr("barix.client.debug.listener_brand_methods", BarixServerConfig.CORE_LOCALE.get(), brandMethods));
            }
            if (data != null) {
                List<String> dataMethods = new ArrayList<>();
                for (String n : new String[]{"getModList", "getMods", "toMap", "asMap", "getBrand", "getBrands"}) {
                    try { Method m = data.getClass().getMethod(n); dataMethods.add(n + ":" + m.getReturnType().getSimpleName()); } catch (Throwable ignored) {}
                }
                BarixConstants.log.debug("§dClient/DBG", Lang.tr("barix.client.debug.connection_data_methods", BarixServerConfig.CORE_LOCALE.get(), dataMethods));
            }
        } catch (Throwable t) {
            BarixConstants.log.debug("§dClient/DBG", Lang.tr("barix.client.debug.error", BarixServerConfig.CORE_LOCALE.get(), t.getMessage()));
        }
    }
}
