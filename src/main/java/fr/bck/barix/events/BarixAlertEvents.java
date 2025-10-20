package fr.bck.barix.events;

import fr.bck.barix.BarixConstants;
import fr.bck.barix.alert.DiscordAlerts;
import fr.bck.barix.config.BarixServerConfig;
import fr.bck.barix.lang.Lang;
import fr.bck.barix.lang.LangKey;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.nio.file.Path;

public class BarixAlertEvents {

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent e) {
        // Init alertes (Discord + in-game)
        DiscordAlerts.initFromConfig();
        String worldName = resolveWorldName(e.getServer());

        String serverLabel = e.getServer().getServerModName() + ":" + worldName;
        DiscordAlerts.alert("startup", Lang.tr(LangKey.BARIX_DISCORD_ALERT_EVENT_SERVER_STARTING, "starting"), Lang.tr(LangKey.BARIX_DISCORD_ALERT_EVENT_SERVER_STARTING_DESCRIPTION, "starting:description", worldName, (e.getServer().isDedicatedServer() ? Lang.tr(LangKey.BARIX_DISCORD_ALERT_EVENT_SERVER_STARTING_DESCRIPTION_DEDICATED, "starting:description:dedicated") : Lang.tr(LangKey.BARIX_DISCORD_ALERT_EVENT_SERVER_STARTING_DESCRIPTION_INTEGRATED, "starting:description:integrated")), e.getServer().getServerVersion(), e.getServer().getPlayerCount(), e.getServer().getMaxPlayers()), 0x00CC66);
        String mode = e.getServer().isDedicatedServer() ? "§bdedicated" : "§3integrated(singleplayer)";
        BarixConstants.log.info(null, Lang.tr("barix.server.active_line", BarixServerConfig.CORE_LOCALE.get(), mode));
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent e) {
        String worldName = resolveWorldName(e.getServer());

        String serverLabel = e.getServer().getServerModName() + ":" + worldName;
        DiscordAlerts.alert("stopping", Lang.tr(LangKey.BARIX_DISCORD_ALERT_EVENT_SERVER_STOPPING, "stopping"), Lang.tr(LangKey.BARIX_DISCORD_ALERT_EVENT_SERVER_STOPPING_DESCRIPTION, "stopping", worldName), 0xFF3333);
    }

    private static String resolveWorldName(net.minecraft.server.MinecraftServer server) {
        // 1) Préférer le nom d'affichage (inclut les parenthèses et caractères spéciaux)
        try {
            String name = server.getWorldData().getLevelName();
            if (!name.isBlank()) return name;
        } catch (Throwable ignored) {
        }
        // 2) Fallback: essayer le dossier monde
        try {
            Path root = server.getWorldPath(LevelResource.ROOT);
            Path last = root.toAbsolutePath().normalize().getFileName();
            if (last != null) {
                String s = last.toString();
                if (!s.isBlank() && !".".equals(s)) return s;
            }
        } catch (Throwable ignored) {
        }
        return "unknown";
    }
}
