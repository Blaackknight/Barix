package fr.bck.barix.events;

import fr.bck.barix.BarixConstants;
import fr.bck.barix.anti.AntiXrayManager;
import fr.bck.barix.anti.ChannelInjector;
import fr.bck.barix.config.BarixServerConfig;
import fr.bck.barix.lang.Lang;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.player.PlayerEvent;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AntiXrayLoginEvents {
    private static boolean dbg() {
        return Boolean.TRUE.equals(BarixServerConfig.ANTIXRAY_DEBUG.get()) || Boolean.TRUE.equals(BarixServerConfig.CORE_DEBUG.get());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        try {
            var entity = e.getEntity();
            if (!(entity instanceof ServerPlayer sp)) {
                if (dbg()) BarixConstants.log.debug("§1AntiXray§6/§dInject", Lang.tr("barix.antixray.inject.ignored_login_non_server", BarixServerConfig.CORE_LOCALE.get(), entity.getClass().getName()));
                return;
            }
            if (sp.level().isClientSide()) {
                if (dbg()) BarixConstants.log.debug("§1AntiXray§6/§dInject", Lang.tr("barix.antixray.inject.ignored_login_client", BarixServerConfig.CORE_LOCALE.get(), sp.getGameProfile().getName()));
                return;
            }
            // Compat/config: court-circuiter l'injection si AntiXray doit être désactivé
            if (!AntiXrayManager.shouldInject()) {
                if (dbg()) BarixConstants.log.debug("§1AntiXray§6/§dInject", "Skip injection: AntiXray disabled by compat/config");
                return;
            }
            ChannelInjector.inject(sp);
            BarixConstants.log.info("§1AntiXray§6/§dInject", Lang.tr("barix.antixray.inject.channel_injected", BarixServerConfig.CORE_LOCALE.get(), sp.getGameProfile().getName()));
        } catch (Throwable t) {
            BarixConstants.log.error("§1AntiXray§6/§dInject", Lang.tr("barix.antixray.inject.error_on_login", BarixServerConfig.CORE_LOCALE.get(), t.toString()));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        try {
            var entity = e.getEntity();
            if (!(entity instanceof ServerPlayer sp)) {
                if (dbg()) BarixConstants.log.debug("§1AntiXray§6/§dInject", Lang.tr("barix.antixray.inject.ignored_logout_non_server", BarixServerConfig.CORE_LOCALE.get(), entity.getClass().getName()));
                return;
            }
            if (sp.level().isClientSide()) {
                if (dbg()) BarixConstants.log.debug("§1AntiXray§6/§dInject", Lang.tr("barix.antixray.inject.ignored_logout_client", BarixServerConfig.CORE_LOCALE.get(), sp.getGameProfile().getName()));
                return;
            }
            ChannelInjector.remove(sp);
            BarixConstants.log.info("§1AntiXray§6/§dInject", Lang.tr("barix.antixray.inject.channel_removed", BarixServerConfig.CORE_LOCALE.get(), sp.getGameProfile().getName()));
        } catch (Throwable t) {
            BarixConstants.log.error("§1AntiXray§6/§dInject", Lang.tr("barix.antixray.inject.error_on_logout", BarixServerConfig.CORE_LOCALE.get(), t.toString()));
        }
    }
}