package fr.bck.barix;

import fr.bck.barix.command.BarixCommands;
import fr.bck.barix.config.BarixServerConfig;
import fr.bck.barix.events.BarixAlertEvents;
import fr.bck.barix.events.BarixAuditEvents;
import fr.bck.barix.events.BarixAuditEventsExtra;
import fr.bck.barix.events.RegionSelectionWand;
import fr.bck.barix.events.ServerLifecycleHooks;
import fr.bck.barix.lang.Lang;
import fr.bck.barix.logging.BarixColoredConsole;
import fr.bck.barix.modules.AntiCheatModule;
import fr.bck.barix.modules.Module;
import fr.bck.barix.modules.ModuleManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkConstants;

import java.util.*;

@Mod(BarixConstants.MODID)
public class BarixMod {
    private static final ModuleManager MODULES = new ModuleManager();
    // Registre des modules externes (ajoutés via l'API publique)
    private static final Map<String, Module> EXTERNAL_MODULES = new LinkedHashMap<>();

    public BarixMod(FMLJavaModLoadingContext context) {
        // obtenir le bus de mod si nécessaire (inutile ici mais conservé pour compatibilité future)
        context.getModEventBus();

        context.registerConfig(ModConfig.Type.SERVER, BarixServerConfig.SPEC);

        BarixColoredConsole.install();

        MinecraftForge.EVENT_BUS.addListener(this::registerCommands);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);
        MinecraftForge.EVENT_BUS.register(new BarixAuditEvents());
        MinecraftForge.EVENT_BUS.register(new BarixAuditEventsExtra());
        MinecraftForge.EVENT_BUS.register(new ServerLifecycleHooks());
        MinecraftForge.EVENT_BUS.register(new BarixAlertEvents());
        // Baguette de sélection des régions
        MinecraftForge.EVENT_BUS.register(new RegionSelectionWand());
        // Les détecteurs anti-cheat sont désormais gérés par AntiCheatModule via ModuleManager

        // Server only mod, ignore client version
        context.registerExtensionPoint(IExtensionPoint.DisplayTest.class, () -> new IExtensionPoint.DisplayTest(() -> NetworkConstants.IGNORESERVERONLY, (remote, isServer) -> true));

        // Logs d’initialisation (locale nulle pour éviter l’accès config avant chargement)
        BarixConstants.log.info(null, Lang.tr("barix.mod.initialized", null));
        BarixConstants.log.info(null, Lang.tr("barix.mod.banner", null, BarixConstants.MOD_NAME, BarixConstants.MOD_VERSION, BarixConstants.AUTHOR));
        BarixConstants.log.info(null, Lang.tr("barix.mod.env", null, BarixConstants.MINECRAFT_VERSION, BarixConstants.FORGE_VERSION, BarixConstants.MAPPINGS));
        BarixConstants.log.info(null, Lang.tr("barix.mod.build", null, BarixConstants.BUILD_TIME, BarixConstants.GIT_BRANCH, BarixConstants.GIT_COMMIT));
    }

    private void onServerStarting(final ServerStartingEvent e) {
        // Init langues serveur
        Lang.initFromConfig();
        // Reconfigurer les modules selon la config serveur
        reloadModulesFromConfig();
    }

    private void registerCommands(final RegisterCommandsEvent e) {
        BarixCommands.register(e);
    }

    public static void stopModules() {
        MODULES.stopAll();
    }

    public static synchronized void reloadModulesFromConfig() {
        // Arrêter
        MODULES.stopAll();
        // Réinitialiser la liste
        MODULES.clear();
        // Ajouter modules natifs selon config
        if (Boolean.TRUE.equals(BarixServerConfig.MODULES_ANTICHEAT_ENABLED.get())) {
            MODULES.add(new AntiCheatModule());
        }
        // Ajouter modules externes enregistrés via API
        for (Module m : EXTERNAL_MODULES.values()) {
            try {
                MODULES.add(m);
            } catch (IllegalArgumentException dup) {
                BarixConstants.log.warn("Modules", "Duplicate external module id ignored: {0}", m.id());
            }
        }
        // Démarrer
        try {
            MODULES.startAll();
        } catch (Throwable t) {
            BarixConstants.log.error("Modules", "Failed to start modules: {0}", String.valueOf(t.getMessage()));
        }
    }

    public static synchronized void restartModules() {
        MODULES.stopAll();
        try {
            MODULES.startAll();
        } catch (Throwable t) {
            BarixConstants.log.error("Modules", "Failed to restart modules: {0}", String.valueOf(t.getMessage()));
        }
    }

    public static synchronized java.util.List<String> getLoadedModuleIds() {
        return MODULES.listIds();
    }

    public static synchronized boolean isModulesStarted() {
        return MODULES.isStarted();
    }

    // ===================== API interne pour BarixAPI =====================

    public static synchronized boolean registerExternalModule(Module module) {
        Objects.requireNonNull(module, "module");
        String id = module.id();
        if (id == null || id.isBlank()) throw new IllegalArgumentException("module.id() vide");
        if (EXTERNAL_MODULES.containsKey(id)) return false;
        EXTERNAL_MODULES.put(id, module);
        BarixConstants.log.info("Modules/" + id, "External module registered: {0}", id);
        // Si déjà démarré, redémarrer proprement pour appliquer le tri des dépendances
        if (MODULES.isStarted()) {
            BarixConstants.log.info("Modules/" + id, "Restarting modules to apply new external module {0}", id);
            reloadModulesFromConfig();
        }
        return true;
    }

    public static synchronized boolean unregisterExternalModule(String id) {
        if (id == null || id.isBlank()) return false;
        Module removed = EXTERNAL_MODULES.remove(id);
        if (removed == null) return false;
        BarixConstants.log.info("Modules/" + id, "External module unregistered: {0}", id);
        if (MODULES.isStarted()) {
            BarixConstants.log.info("Modules/" + id, "Restarting modules to remove external module {0}", id);
            reloadModulesFromConfig();
        }
        return true;
    }

    public static synchronized java.util.List<String> listExternalModuleIds() {
        return new ArrayList<>(EXTERNAL_MODULES.keySet());
    }
}