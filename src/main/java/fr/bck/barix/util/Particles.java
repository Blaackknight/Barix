package fr.bck.barix.util;

import fr.bck.barix.config.BarixServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class Particles {
    private Particles() {}

    public static void showPos(ServerPlayer p, BlockPos pos) {
        if (!Boolean.TRUE.equals(BarixServerConfig.REGIONS_PARTICLES.get())) return;
        ServerLevel lvl = p.serverLevel();
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.1;
        double z = pos.getZ() + 0.5;
        // petite croix
        emit(lvl, p, x, y, z, 6, 0.0, 0.0, 0.0, 0.0);
        emit(lvl, p, x + 0.5, y + 0.01, z, 1, 0, 0, 0, 0);
        emit(lvl, p, x - 0.5, y + 0.01, z, 1, 0, 0, 0, 0);
        emit(lvl, p, x, y + 0.01, z + 0.5, 1, 0, 0, 0, 0);
        emit(lvl, p, x, y + 0.01, z - 0.5, 1, 0, 0, 0, 0);
    }

    public static void showBox(ServerPlayer p, BlockPos a, BlockPos b) {
        if (!Boolean.TRUE.equals(BarixServerConfig.REGIONS_PARTICLES.get())) return;
        ServerLevel lvl = p.serverLevel();
        int minX = Math.min(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ());
        int maxX = Math.max(a.getX(), b.getX());
        int maxY = Math.max(a.getY(), b.getY());
        int maxZ = Math.max(a.getZ(), b.getZ());
        // Parcourt les 12 arêtes
        for (int x = minX; x <= maxX; x++) {
            edge(lvl, p, x + 0.5, minY + 0.01, minZ + 0.5);
            edge(lvl, p, x + 0.5, minY + 0.01, maxZ + 0.5);
            edge(lvl, p, x + 0.5, maxY + 0.01, minZ + 0.5);
            edge(lvl, p, x + 0.5, maxY + 0.01, maxZ + 0.5);
        }
        for (int z = minZ; z <= maxZ; z++) {
            edge(lvl, p, minX + 0.5, minY + 0.01, z + 0.5);
            edge(lvl, p, maxX + 0.5, minY + 0.01, z + 0.5);
            edge(lvl, p, minX + 0.5, maxY + 0.01, z + 0.5);
            edge(lvl, p, maxX + 0.5, maxY + 0.01, z + 0.5);
        }
        for (int y = minY; y <= maxY; y++) {
            edge(lvl, p, minX + 0.5, y + 0.01, minZ + 0.5);
            edge(lvl, p, minX + 0.5, y + 0.01, maxZ + 0.5);
            edge(lvl, p, maxX + 0.5, y + 0.01, minZ + 0.5);
            edge(lvl, p, maxX + 0.5, y + 0.01, maxZ + 0.5);
        }
    }

    private static void edge(ServerLevel lvl, ServerPlayer p, double x, double y, double z) {
        emit(lvl, p, x, y, z, 1, 0, 0, 0, 0);
    }

    private static void emit(ServerLevel lvl, ServerPlayer p, double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
        lvl.sendParticles(p, ParticleTypes.HAPPY_VILLAGER, false, x, y, z, count, dx, dy, dz, speed);
    }
}

