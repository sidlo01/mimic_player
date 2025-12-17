package org.sidlo01.mimic_player.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.sidlo01.mimic_player.Mimic_player;
import org.sidlo01.mimic_player.network.packet.*;

public final class SkinSync {

    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(Mimic_player.MODID, "skin_sync"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    private static int id = 0;

    public static void init() {
        CHANNEL.messageBuilder(C2SRequestSkinManifestPacket.class, id++)
                .encoder(C2SRequestSkinManifestPacket::encode)
                .decoder(C2SRequestSkinManifestPacket::decode)
                .consumerMainThread(C2SRequestSkinManifestPacket::handle)
                .add();

        CHANNEL.messageBuilder(S2CSkinManifestPacket.class, id++)
                .encoder(S2CSkinManifestPacket::encode)
                .decoder(S2CSkinManifestPacket::decode)
                .consumerMainThread(S2CSkinManifestPacket::handle)
                .add();

        CHANNEL.messageBuilder(C2SRequestSkinsPacket.class, id++)
                .encoder(C2SRequestSkinsPacket::encode)
                .decoder(C2SRequestSkinsPacket::decode)
                .consumerMainThread(C2SRequestSkinsPacket::handle)
                .add();

        CHANNEL.messageBuilder(S2CSkinFilePacket.class, id++)
                .encoder(S2CSkinFilePacket::encode)
                .decoder(S2CSkinFilePacket::decode)
                .consumerMainThread(S2CSkinFilePacket::handle)
                .add();
    }

    private SkinSync() {}
}
