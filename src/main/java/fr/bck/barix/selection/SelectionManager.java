package fr.bck.barix.selection;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SelectionManager {
    public static class Sel {
        public BlockPos pos1;
        public BlockPos pos2;
    }

    private static final Map<UUID, Sel> MAP = new HashMap<>();

    private static Sel sel(ServerPlayer p) { return MAP.computeIfAbsent(p.getUUID(), k -> new Sel()); }

    public static void clear(ServerPlayer p) { MAP.remove(p.getUUID()); }

    public static void setPos1(ServerPlayer p, BlockPos pos) { sel(p).pos1 = pos; }
    public static void setPos2(ServerPlayer p, BlockPos pos) { sel(p).pos2 = pos; }
    public static BlockPos getPos1(ServerPlayer p) { Sel s = MAP.get(p.getUUID()); return s != null ? s.pos1 : null; }
    public static BlockPos getPos2(ServerPlayer p) { Sel s = MAP.get(p.getUUID()); return s != null ? s.pos2 : null; }

    public static BlockPos raytraceBlock(ServerPlayer p, double distance) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        Vec3 to = eye.add(look.x * distance, look.y * distance, look.z * distance);
        HitResult hr = p.level().clip(new net.minecraft.world.level.ClipContext(eye, to, net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.ANY, p));
        if (hr != null && hr.getType() == HitResult.Type.BLOCK) {
            return new BlockPos(((net.minecraft.world.phys.BlockHitResult) hr).getBlockPos());
        }
        return null;
    }
}
