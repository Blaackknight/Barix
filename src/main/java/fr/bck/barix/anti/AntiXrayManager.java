package fr.bck.barix.anti;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class AntiXrayManager {
    private static final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<ChunkMask> MASKS = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();

    public static Object maskChunkPacket(ServerPlayer sp, ClientboundLevelChunkWithLightPacket pkt) {
        // 1) Récupérer le chunk serveur
        var lvl = sp.serverLevel();
        var pos = new net.minecraft.world.level.ChunkPos(pkt.getX(), pkt.getZ());
        var chunk = lvl.getChunk(pos.x, pos.z);
        // 2) Construire des sections copiées avec états masqués selon BitSet + règles
        var copy = copyAndMaskSections(sp, chunk);
        // 3) Recréer le paquet (en 1.20.1: ClientboundLevelChunkWithLightPacket(LevelChunk, LightEngine))
        //    -> on doit refléter/encoder les données de sections. Simplifié ici:
        return PacketRebuilder.rebuildChunkPacket(copy, lvl);
    }

    public static Object maskSectionUpdate(ServerPlayer sp, ClientboundSectionBlocksUpdatePacket pkt) {
        // Lire positions, remplacer états cachés par "stone" si hors révélation
        return PacketRebuilder.rebuildSectionUpdateMasked(sp, pkt);
    }

    static boolean shouldHide(ServerPlayer sp, BlockPos pos, BlockState state, Level lvl) {
        if (!state.is(fr.bck.barix.tags.BarixBlockTags.ANTIXRAY_ORES)) return false;
        if (!fr.bck.barix.config.BarixServerConfig.ANTIXRAY_HIDE_SURFACE.get() && touchesAir(lvl, pos)) return false;
        if (sp.blockPosition().closerThan(pos, fr.bck.barix.config.BarixServerConfig.ANTIXRAY_REVEAL_RADIUS.get()))
            return false;
        return true;
    }

    // Vérifie si le bloc touche l’air (6 directions)
    private static boolean touchesAir(Level lvl, BlockPos pos) {
        // positions adjacentes
        var up = pos.above();
        var down = pos.below();
        var north = pos.north();
        var south = pos.south();
        var east = pos.east();
        var west = pos.west();

        return lvl.isEmptyBlock(up) || lvl.isEmptyBlock(down) || lvl.isEmptyBlock(north) || lvl.isEmptyBlock(south) || lvl.isEmptyBlock(east) || lvl.isEmptyBlock(west);
    }

    // Stub minimal pour éviter l’erreur de symbole manquant.
    // TODO: implémenter le masquage réel des sections si nécessaire.
    private static net.minecraft.world.level.chunk.LevelChunk copyAndMaskSections(ServerPlayer sp, net.minecraft.world.level.chunk.LevelChunk chunk) {
        return chunk;
    }
}