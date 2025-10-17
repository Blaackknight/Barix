package fr.bck.barix.anti;

import io.netty.channel.ChannelOutboundHandlerAdapter;
import net.minecraft.server.level.ServerPlayer;

public final class PacketTransformer extends ChannelOutboundHandlerAdapter {
    private final ServerPlayer sp;

    public PacketTransformer(ServerPlayer sp) {
        this.sp = sp;
    }

    @Override
    public void write(io.netty.channel.ChannelHandlerContext ctx, Object msg, io.netty.channel.ChannelPromise p) throws Exception {
        if (!fr.bck.barix.config.BarixServerConfig.CORE_ENABLED.get() || !fr.bck.barix.config.BarixServerConfig.ANTIXRAY_ENABLE.get()) {
            ctx.write(msg, p);
            return;
        }
        if (msg instanceof net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket pkt) {
            var masked = AntiXrayManager.maskChunkPacket(sp, pkt);
            ctx.write(masked, p);
            return;
        }
        if (msg instanceof net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket pkt) {
            var masked = AntiXrayManager.maskSectionUpdate(sp, pkt);
            ctx.write(masked, p);
            return;
        }
        ctx.write(msg, p);
    }
}