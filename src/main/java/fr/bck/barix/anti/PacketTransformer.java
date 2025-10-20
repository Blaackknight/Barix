package fr.bck.barix.anti;

import fr.bck.barix.BarixConstants;
import fr.bck.barix.config.BarixServerConfig;
import fr.bck.barix.lang.Lang;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.server.level.ServerPlayer;

public final class PacketTransformer extends ChannelOutboundHandlerAdapter {
    private final ServerPlayer sp;

    public PacketTransformer(ServerPlayer sp) {
        this.sp = sp;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise p) {
        if (!fr.bck.barix.config.BarixServerConfig.CORE_ENABLED.get() || !fr.bck.barix.config.BarixServerConfig.ANTIXRAY_ENABLE.get()) {
            ctx.write(msg, p);
            return;
        }
        try {
            if (msg instanceof net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket pkt) {
                // Écrire le chunk d'abord, puis appliquer le masquage quand l'envoi est complété
                ctx.write(msg, p).addListener(future -> {
                    try {
                        AntiXrayManager.onChunkSent(sp, pkt);
                    } catch (Throwable t) {
                        BarixConstants.log.error("§4AntiXray§6/§7Transform", Lang.tr("barix.antixray.transform.listener_error", BarixServerConfig.CORE_LOCALE.get(), t.toString()));
                    }
                });
                return;
            }
            if (msg instanceof net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket pkt) {
                if (Boolean.TRUE.equals(BarixServerConfig.ANTIXRAY_MASK_SECTION_UPDATES.get())) {
                    AntiXrayManager.onSectionUpdateSent(sp, pkt);
                }
                ctx.write(msg, p);
                return;
            }
        } catch (Throwable t) {
            BarixConstants.log.error("§4AntiXray§6/§7Transform", Lang.tr("barix.antixray.transform.error", BarixServerConfig.CORE_LOCALE.get(), t.toString()));
        }
        ctx.write(msg, p);
    }
}