package fr.bck.barix.util;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Petit planificateur de tâches serveur basé sur les ticks.
 * Permet d'exécuter des Runnable après N ticks (phase END du tick serveur).
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ServerScheduler {
    private ServerScheduler() {}

    private static final Object LOCK = new Object();
    private static final List<Task> TASKS = new ArrayList<>();

    private static final class Task {
        final int dueTick;
        final Runnable r;
        Task(int dueTick, Runnable r) { this.dueTick = dueTick; this.r = r; }
    }

    /**
     * Planifie l'exécution d'une tâche après un certain nombre de ticks serveur.
     * @param level Ignoré pour l'instant (le planning est global serveur), conservé pour compat.
     * @param ticks Délai en ticks (>=0)
     * @param r     Tâche à exécuter sur le thread serveur
     */
    public static void runLater(net.minecraft.server.level.ServerLevel level, int ticks, Runnable r) {
        int now = ServerLifecycleHooks.getCurrentServer().getTickCount();
        int due = now + Math.max(0, ticks);
        synchronized (LOCK) {
            TASKS.add(new Task(due, r));
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        int now = ServerLifecycleHooks.getCurrentServer().getTickCount();
        List<Runnable> toRun = new ArrayList<>();
        synchronized (LOCK) {
            Iterator<Task> it = TASKS.iterator();
            while (it.hasNext()) {
                Task t = it.next();
                if (t.dueTick <= now) {
                    toRun.add(t.r);
                    it.remove();
                }
            }
        }
        for (Runnable r : toRun) {
            try { r.run(); } catch (Throwable ignored) {}
        }
    }
}

