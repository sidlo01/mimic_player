package org.sidlo01.mimic_player.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.sidlo01.mimic_player.server.ServerSkinStorage;

import java.util.function.Supplier;

/**
 * Client -> Server: "Send me manifest (file -> sha256) for <world>/skins/"
 */
public final class C2SRequestSkinManifestPacket {

    public static void encode(C2SRequestSkinManifestPacket msg, FriendlyByteBuf buf) {
        // no fields
    }

    public static C2SRequestSkinManifestPacket decode(FriendlyByteBuf buf) {
        return new C2SRequestSkinManifestPacket();
    }

    public static void handle(C2SRequestSkinManifestPacket msg, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ServerPlayer sender = ctx.getSender();
        if (sender == null) return;

        ctx.enqueueWork(() -> ServerSkinStorage.sendManifestTo(sender));
        ctx.setPacketHandled(true);
    }
}
