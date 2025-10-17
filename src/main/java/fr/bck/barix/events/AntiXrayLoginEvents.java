package fr.bck.barix.events;

import fr.bck.barix.anti.ChannelInjector;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public final class AntiXrayLoginEvents {
    @SubscribeEvent
    public static void onLogin(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e) {
        var srv = (net.minecraft.server.level.ServerPlayer) e.getEntity();
        ChannelInjector.inject(srv);
    }

    @SubscribeEvent
    public static void onLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) {
        var srv = (net.minecraft.server.level.ServerPlayer) e.getEntity();
        ChannelInjector.remove(srv);
    }
}