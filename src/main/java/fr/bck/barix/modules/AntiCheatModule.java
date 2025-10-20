package fr.bck.barix.modules;

import fr.bck.barix.events.*;
import net.minecraftforge.common.MinecraftForge;

public final class AntiCheatModule implements Module {
    private final Object[] listeners = new Object[] {
            new ReachDetection(),
            new ClientDetection(),
            new InstantBreakDetection(),
            new KillAuraDetection(),
            new SpeedDetection(),
            new FlyDetection()
    };

    @Override
    public String id() { return "anticheat"; }

    @Override
    public void start() {
        for (Object l : listeners) {
            MinecraftForge.EVENT_BUS.register(l);
        }
    }

    @Override
    public void stop() {
        for (Object l : listeners) {
            try { MinecraftForge.EVENT_BUS.unregister(l); } catch (Throwable ignored) {}
        }
    }
}

