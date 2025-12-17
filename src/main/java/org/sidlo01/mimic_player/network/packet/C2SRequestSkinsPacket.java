package org.sidlo01.mimic_player.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.sidlo01.mimic_player.server.ServerSkinStorage;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Client -> Server: request concrete PNG files listed in manifest.
 */
public final class C2SRequestSkinsPacket {

    public final List<String> files;

    public C2SRequestSkinsPacket(List<String> files) {
        this.files = files;
    }

    public static void encode(C2SRequestSkinsPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.files.size());
        for (String f : msg.files) buf.writeUtf(f);
    }

    public static C2SRequestSkinsPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<String> files = new ArrayList<>(n);
        for (int i = 0; i < n; i++) files.add(buf.readUtf());
        return new C2SRequestSkinsPacket(files);
    }

    public static void handle(C2SRequestSkinsPacket msg, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ServerPlayer sender = ctx.getSender();
        if (sender == null) return;

        ctx.enqueueWork(() -> ServerSkinStorage.sendFilesTo(sender, msg.files));
        ctx.setPacketHandled(true);
    }
}
