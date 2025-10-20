package fr.bck.barix.example;

import fr.bck.barix.BarixConstants;
import fr.bck.barix.api.IBarixModule;

import java.util.List;

/**
 * ExampleExternalModule
 *
 * This class shows how a third-party module can integrate with Barix.
 * It is NOT auto-registered; see the registration snippet below.
 *
 * Registration snippet (e.g., during your mod initialization):
 *
 *   fr.bck.barix.api.BarixAPI.registerModule(new fr.bck.barix.example.ExampleExternalModule());
 *
 * You can unregister later if needed:
 *
 *   fr.bck.barix.api.BarixAPI.unregisterModule("example");
 */
public final class ExampleExternalModule implements IBarixModule {
    @Override
    public String id() {
        return "example";
    }

    @Override
    public List<String> dependsOn() {
        // Example: start after Barix built-in 'anticheat' module
        return List.of("anticheat");
    }

    @Override
    public void start() {
        BarixConstants.log.info("Modules/" + id(), "ExampleExternalModule started");
        // Subscribe Forge events, start services, etc.
    }

    @Override
    public void stop() {
        BarixConstants.log.info("Modules/" + id(), "ExampleExternalModule stopped");
        // Unsubscribe, cleanup
    }
}

