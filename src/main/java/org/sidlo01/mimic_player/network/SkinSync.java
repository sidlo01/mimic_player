package org.sidlo01.mimic_player.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.sidlo01.mimic_player.Mimic_player;
import org.sidlo01.mimic_player.network.packet.C2SRequestSkinsPacket;
import org.sidlo01.mimic_player.network.packet.C2SRequestSkinManifestPacket;
import org.sidlo01.mimic_player.network.packet.S2CSkinFilePacket;
import org.sidlo01.mimic_player.network.packet.S2CSkinManifestPacket;

/**
 * SimpleChannel used to sync server-provided skins to clients.
 */
public final class SkinSync {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(Mimic_player.MODID, "skin_sync"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    private static int id = 0;

    /** Call once during common setup. */
    public static void register() {
        CHANNEL.registerMessage(id++,
                C2SRequestSkinManifestPacket.class,
                C2SRequestSkinManifestPacket::encode,
                C2SRequestSkinManifestPacket::decode,
                C2SRequestSkinManifestPacket::handle
        );

        CHANNEL.registerMessage(id++,
                S2CSkinManifestPacket.class,
                S2CSkinManifestPacket::encode,
                S2CSkinManifestPacket::decode,
                S2CSkinManifestPacket::handle
        );

        CHANNEL.registerMessage(id++,
                C2SRequestSkinsPacket.class,
                C2SRequestSkinsPacket::encode,
                C2SRequestSkinsPacket::decode,
                C2SRequestSkinsPacket::handle
        );

        CHANNEL.registerMessage(id++,
                S2CSkinFilePacket.class,
                S2CSkinFilePacket::encode,
                S2CSkinFilePacket::decode,
                S2CSkinFilePacket::handle
        );
    }

    private SkinSync() {}
}
