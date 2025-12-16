package org.sidlo01.mimic_player.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.sidlo01.mimic_player.client.ClientServerSkinCache;

import java.util.function.Supplier;

/**
 * Server -> Client: one skin file (png bytes).
 */
public final class S2CSkinFilePacket {
    public final String fileName;
    public final byte[] bytes;

    public S2CSkinFilePacket(String fileName, byte[] bytes) {
        this.fileName = fileName;
        this.bytes = bytes;
    }

    public static void encode(S2CSkinFilePacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.fileName);
        buf.writeByteArray(msg.bytes); // varint length + bytes
    }

    public static S2CSkinFilePacket decode(FriendlyByteBuf buf) {
        String f = buf.readUtf();
        byte[] b = buf.readByteArray();
        return new S2CSkinFilePacket(f, b);
    }

    public static void handle(S2CSkinFilePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientServerSkinCache.onFile(msg.fileName, msg.bytes));
        ctx.get().setPacketHandled(true);
    }
}
