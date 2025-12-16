package org.sidlo01.mimic_player.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.sidlo01.mimic_player.server.ServerSkinStorage;

import java.util.function.Supplier;

/**
 * Client -> Server: "Send me your server skin manifest (filenames + hashes)"
 */
public final class C2SRequestSkinManifestPacket {

    public static void encode(C2SRequestSkinManifestPacket msg, FriendlyByteBuf buf) {
        // no payload
    }

    public static C2SRequestSkinManifestPacket decode(FriendlyByteBuf buf) {
        return new C2SRequestSkinManifestPacket();
    }

    public static void handle(C2SRequestSkinManifestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;

            // Build and send manifest
            ServerSkinStorage.sendManifestTo(sender);
        });
        ctx.get().setPacketHandled(true);
    }
}
