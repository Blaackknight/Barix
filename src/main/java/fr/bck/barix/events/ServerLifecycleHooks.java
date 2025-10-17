package fr.bck.barix.events;

import fr.bck.barix.audit.AuditRotator;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ServerLifecycleHooks {
    @SubscribeEvent
    public void onStop(ServerStoppingEvent e) {
        AuditRotator.compressTodayIfConfigured();
    }
}