package fr.bck.barix.api;

import fr.bck.barix.edicts.EdictService;
import fr.bck.barix.edicts.Edicts;
import fr.bck.barix.server.LagMonitor;
import fr.bck.barix.topluck.TopLuck;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class BarixAPI {
    private static final LagMonitor LAG_MONITOR = new LagMonitor();

    public static IBarixLagTracker getLagTracker() {
        return LAG_MONITOR;
    }

    /**
     * API TopLuck: handler fonctionnel.
     */
    public interface IBarixTopLuck {
        boolean handle(String playerName, double luck, String category);
    }

    /**
     * Récupère le handler TopLuck (impl. interne).
     */
    public static IBarixTopLuck getTopLuck() {
        return TopLuck::handle;
    }

    /**
     * Méthode utilitaire: envoie un évènement TopLuck.
     */
    public static boolean submitTopLuck(String playerName, double luck, String category) {
        return TopLuck.handle(playerName, luck, category);
    }

    /**
     * Méthode utilitaire: catégorie par défaut.
     */
    public static boolean submitTopLuck(String playerName, double luck) {
        return TopLuck.handle(playerName, luck);
    }

    // Entrée API globale "exotique" pour les autorisations
    public static EdictService getEdicts() {
        return Edicts.service();
    }

    // Alias de compatibilité si besoin
    public static EdictService getPermissions() {
        return getEdicts();
    }

    // ===================== API Modules publiques =====================

    /**
     * Enregistre un module tiers. Si les modules Barix sont déjà démarrés, ils seront redémarrés
     * pour prendre en compte l’ordre des dépendances. Retourne false si l'id est déjà enregistré.
     */
    public static boolean registerModule(IBarixModule module) {
        return fr.bck.barix.BarixMod.registerExternalModule(new ApiModuleAdapter(module));
    }

    /**
     * Désenregistre un module tiers par son id. Retourne false si absent.
     */
    public static boolean unregisterModule(String id) {
        return fr.bck.barix.BarixMod.unregisterExternalModule(id);
    }

    /**
     * Liste les ids de tous les modules actuellement chargés (natif + tiers).
     */
    public static List<String> listLoadedModuleIds() {
        return fr.bck.barix.BarixMod.getLoadedModuleIds();
    }

    /**
     * Liste les ids des modules tiers enregistrés (qu’ils soient démarrés ou non suivant l’état global).
     */
    public static List<String> listExternalModuleIds() {
        return fr.bck.barix.BarixMod.listExternalModuleIds();
    }

    /**
     * Indique si la pile de modules est démarrée.
     */
    public static boolean areModulesStarted() {
        return fr.bck.barix.BarixMod.isModulesStarted();
    }

    /**
     * Redémarre les modules existants (sans relire la configuration Barix).
     */
    public static void restartModules() {
        fr.bck.barix.BarixMod.restartModules();
    }

    /**
     * Recharge la configuration Barix, reconstruit la pile de modules (natifs + tiers), et démarre.
     */
    public static void reloadModules() {
        fr.bck.barix.BarixMod.reloadModulesFromConfig();
    }

    /**
     * Adaptateur privé reliant l’interface publique IBarixModule à l’impl. interne ModuleManager.
     */
    private static final class ApiModuleAdapter implements fr.bck.barix.modules.Module {
        private final IBarixModule delegate;

        private ApiModuleAdapter(IBarixModule delegate) {
            if (delegate == null) throw new IllegalArgumentException("module null");
            this.delegate = delegate;
        }

        @Override
        public String id() {
            return delegate.id();
        }

        @Override
        public List<String> dependsOn() {
            try {
                List<String> d = delegate.dependsOn();
                return d != null ? d : List.of();
            } catch (Throwable t) {
                return List.of();
            }
        }

        @Override
        public void start() {
            delegate.start();
        }

        @Override
        public void stop() {
            delegate.stop();
        }
    }

    // ===================== Compatibilité Anti-Xray =====================

    private static final CopyOnWriteArrayList<IAntiXrayCompat> ANTI_XRAY_COMPATS = new CopyOnWriteArrayList<>();

    /**
     * Enregistre un provider de compatibilité Anti-Xray (id unique recommandé).
     * Peut être utilisé pour désactiver Barix Anti-Xray ou étendre son comportement.
     */
    public static boolean registerAntiXrayCompat(IAntiXrayCompat provider) {
        if (provider == null) return false;
        // éviter doublons naïfs par id()
        for (IAntiXrayCompat p : ANTI_XRAY_COMPATS) {
            if (safeEq(p.id(), provider.id())) return false;
        }
        return ANTI_XRAY_COMPATS.addIfAbsent(provider);
    }

    /**
     * Désenregistre un provider par id.
     */
    public static boolean unregisterAntiXrayCompat(String id) {
        if (id == null || id.isBlank()) return false;
        boolean removed = false;
        for (IAntiXrayCompat p : ANTI_XRAY_COMPATS) {
            if (safeEq(p.id(), id)) {
                ANTI_XRAY_COMPATS.remove(p);
                removed = true;
            }
        }
        return removed;
    }

    /**
     * Snapshot non modifiable des providers enregistrés.
     */
    public static java.util.List<IAntiXrayCompat> getAntiXrayCompatProviders() {
        return java.util.List.copyOf(ANTI_XRAY_COMPATS);
    }

    private static boolean safeEq(String a, String b) {
        return (a == null && b == null) || (a != null && a.equals(b));
    }
}