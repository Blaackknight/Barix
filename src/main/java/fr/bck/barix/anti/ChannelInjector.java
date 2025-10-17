package fr.bck.barix.anti;

import net.minecraft.server.level.ServerPlayer;

public final class ChannelInjector {
    public static void inject(ServerPlayer sp) {
        var ch = sp.connection.connection.channel();
        if (ch.pipeline().get("barix-axray") != null) return;
        ch.pipeline().addBefore("packet_handler", "barix-axray", new PacketTransformer(sp));
    }

    public static void remove(ServerPlayer sp) {
        var ch = sp.connection.connection.channel();
        if (ch.pipeline().get("barix-axray") != null) ch.pipeline().remove("barix-axray");
    }
}