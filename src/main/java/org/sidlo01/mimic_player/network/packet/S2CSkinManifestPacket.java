package org.sidlo01.mimic_player.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.sidlo01.mimic_player.client.ClientServerSkinCache;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Server -> Client: manifest of available server skins: filename -> sha256.
 */
public final class S2CSkinManifestPacket {
    public final Map<String, String> fileToSha256;

    public S2CSkinManifestPacket(Map<String, String> fileToSha256) {
        this.fileToSha256 = fileToSha256;
    }

    public static void encode(S2CSkinManifestPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.fileToSha256.size());
        for (var e : msg.fileToSha256.entrySet()) {
            buf.writeUtf(e.getKey());
            buf.writeUtf(e.getValue());
        }
    }

    public static S2CSkinManifestPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < n; i++) {
            String file = buf.readUtf();
            String hash = buf.readUtf();
            map.put(file, hash);
        }
        return new S2CSkinManifestPacket(map);
    }

    public static void handle(S2CSkinManifestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientServerSkinCache.onManifest(msg.fileToSha256));
        ctx.get().setPacketHandled(true);
    }
}
