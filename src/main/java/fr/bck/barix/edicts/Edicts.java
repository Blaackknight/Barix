package fr.bck.barix.edicts;

import fr.bck.barix.BarixConstants;
import fr.bck.barix.edicts.command.EdictsCommand;
import fr.bck.barix.edicts.store.JsonEdictStore;
import fr.bck.barix.lang.Lang;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BarixConstants.MODID)
public final class Edicts {
    private static EdictService SERVICE;

    private Edicts() {
    }

    public static EdictService service() {
        if (SERVICE == null) {
            SERVICE = new InMemoryEdictService(new JsonEdictStore());
        }
        return SERVICE;
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent e) {
        service().reload();
        BarixConstants.log.info("§aEdicts", Lang.tr("barix.edicts.reloaded", null));
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent e) {
        service().save();
        BarixConstants.log.info("§aEdicts", Lang.tr("barix.edicts.saved", null));
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent e) {
        EdictsCommand.register(e.getDispatcher());
    }
}