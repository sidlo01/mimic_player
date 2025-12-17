package org.sidlo01.mimic_player.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.sidlo01.mimic_player.client.ClientServerSkinCache;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Server -> Client: manifest of <world>/skins
 */
public final class S2CSkinManifestPacket {

    public final Map<String, String> manifest;

    public S2CSkinManifestPacket(Map<String, String> manifest) {
        this.manifest = manifest;
    }

    public static void encode(S2CSkinManifestPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.manifest.size());
        for (var e : msg.manifest.entrySet()) {
            buf.writeUtf(e.getKey());
            buf.writeUtf(e.getValue());
        }
    }

    public static S2CSkinManifestPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < n; i++) {
            map.put(buf.readUtf(), buf.readUtf());
        }
        return new S2CSkinManifestPacket(map);
    }

    public static void handle(S2CSkinManifestPacket msg, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> ClientServerSkinCache.onManifest(msg.manifest));
        ctx.setPacketHandled(true);
    }
}
