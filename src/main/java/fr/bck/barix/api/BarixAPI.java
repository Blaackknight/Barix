package fr.bck.barix.api;

import fr.bck.barix.server.LagMonitor;

public class BarixAPI {
    private static final LagMonitor LAG_MONITOR = new LagMonitor();

    public static IBarixLagTracker getLagTracker() {
        return LAG_MONITOR;
    }
}