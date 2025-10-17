package fr.bck.barix.api;

import fr.bck.barix.edicts.EdictService;
import fr.bck.barix.edicts.Edicts;
import fr.bck.barix.server.LagMonitor;

public class BarixAPI {
    private static final LagMonitor LAG_MONITOR = new LagMonitor();

    public static IBarixLagTracker getLagTracker() {
        return LAG_MONITOR;
    }

    // Entrée API globale "exotique" pour les autorisations
    public static EdictService getEdicts() {
        return Edicts.service();
    }

    // Alias de compatibilité si besoin
    public static EdictService getPermissions() {
        return getEdicts();
    }
}