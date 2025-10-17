package fr.bck.barix.server;

import fr.bck.barix.BarixConstants;
import fr.bck.barix.api.BarixAPI;
import fr.bck.barix.api.IBarixLagTracker;
import fr.bck.barix.config.BarixServerConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = BarixConstants.MODID)
public class LagMonitor implements IBarixLagTracker {

    private final Map<ResourceLocation, LagStats> stats = new HashMap<>();
    private static final Map<ServerLevel, Long> TICK_START = new WeakHashMap<>();

    public static class LagStats {
        long totalNanos = 0;
        int count = 0;
    }

    public Map<ResourceLocation, LagStats> getStats() {
        return stats;
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.level.isClientSide()) return;
        if (!BarixServerConfig.LAG_MONITOR_ENABLED.get()) return;
        ServerLevel world = (ServerLevel) event.level;

        if (event.phase == TickEvent.Phase.START) {
            TICK_START.put(world, System.nanoTime());
            return;
        }

        Long start = TICK_START.remove(world);
        if (start == null) return;

        long duration = System.nanoTime() - start;
        ResourceLocation id = world.dimension().location();

        LagMonitor monitor = (LagMonitor) BarixAPI.getLagTracker();
        monitor.record(id, duration, world, "level");

        if (duration > (BarixServerConfig.LAG_MONITOR_THRESHOLD_MS.get() * 1_000_000L)) {
            BarixConstants.log.warn("§dLagMonitor", "§6[§cLEVEL LAG§6] §b" + id + " §7took §d" + (duration / 1_000_000.0) + "§5ms");
        }
    }

    @Override
    public void record(ResourceLocation id, long nanos, ServerLevel world, String type) {
        stats.computeIfAbsent(id, k -> new LagStats());
        LagStats s = stats.get(id);
        s.totalNanos += nanos;
        s.count++;
    }

    @Override
    public double getAverageMs(ResourceLocation id) {
        LagStats s = stats.get(id);
        if (s == null || s.count == 0) return 0.0;
        return (s.totalNanos / (double) s.count) / 1_000_000.0;
    }

    @Override
    public Map<ResourceLocation, ?> getStatsMap() {
        return stats;
    }

    @Override
    public void reset() {
        stats.clear();
    }
}