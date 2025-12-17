package org.sidlo01.mimic_player.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.sidlo01.mimic_player.client.ClientServerSkinCache;

import java.util.function.Supplier;

/**
 * Server -> Client: one PNG file
 */
public final class S2CSkinFilePacket {

    public final String fileName; // example: "notch.png" or "Notch.png"
    public final byte[] bytes;

    public S2CSkinFilePacket(String fileName, byte[] bytes) {
        this.fileName = fileName;
        this.bytes = bytes;
    }

    public static void encode(S2CSkinFilePacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.fileName);
        buf.writeVarInt(msg.bytes.length);
        buf.writeByteArray(msg.bytes);
    }

    public static S2CSkinFilePacket decode(FriendlyByteBuf buf) {
        String f = buf.readUtf();
        int len = buf.readVarInt();
        byte[] bytes = buf.readByteArray(len);
        return new S2CSkinFilePacket(f, bytes);
    }

    public static void handle(S2CSkinFilePacket msg, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> ClientServerSkinCache.onFile(msg.fileName, msg.bytes));
        ctx.setPacketHandled(true);
    }
}
