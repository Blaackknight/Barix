package fr.bck.barix;

import fr.bck.barix.alert.DiscordAlerts;
import fr.bck.barix.command.BarixCommands;
import fr.bck.barix.config.BarixServerConfig;
import fr.bck.barix.events.BarixAuditEvents;
import fr.bck.barix.events.BarixAuditEventsExtra;
import fr.bck.barix.events.ServerLifecycleHooks;
import fr.bck.barix.lang.Lang;
import fr.bck.barix.logging.BarixColoredConsole;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkConstants;

@Mod(BarixConstants.MODID)
public class BarixMod {
    public BarixMod(FMLJavaModLoadingContext context) {
        IEventBus bus = context.getModEventBus();

        context.registerConfig(ModConfig.Type.SERVER, BarixServerConfig.SPEC);

        BarixColoredConsole.install();

        MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);
        MinecraftForge.EVENT_BUS.addListener(this::registerCommands);
        MinecraftForge.EVENT_BUS.register(new BarixAuditEvents());
        MinecraftForge.EVENT_BUS.register(new BarixAuditEventsExtra());
        MinecraftForge.EVENT_BUS.register(new ServerLifecycleHooks());

        // Server only mod, ignore client version
        context.registerExtensionPoint(IExtensionPoint.DisplayTest.class, () -> new IExtensionPoint.DisplayTest(() -> NetworkConstants.IGNORESERVERONLY, (remote, isServer) -> true));

        BarixConstants.log.info("§aInitialized §eX)");
    }

    private void onServerStarting(ServerStartingEvent e) {
        // Init langues serveur
        Lang.initFromConfig();
        // Init alertes (Discord + in-game)
        DiscordAlerts.initFromConfig();
        DiscordAlerts.alert(
                "startup",
                "Serveur Barix démarré",
                e.getServer().isDedicatedServer() ? "Mode: dedicated" : "Mode: integrated (singleplayer)",
                0x00CC66
        );
        String mode = e.getServer().isDedicatedServer() ? "§bdedicated" : "§3integrated(singleplayer)";
        BarixConstants.log.info(null, "§9Barix §aactive §7on logical server: " + mode);
    }

    private void registerCommands(final RegisterCommandsEvent e) {
        BarixCommands.register(e);
    }
}