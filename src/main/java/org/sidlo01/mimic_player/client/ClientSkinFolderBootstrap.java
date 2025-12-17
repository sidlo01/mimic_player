package org.sidlo01.mimic_player.client;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.sidlo01.mimic_player.Mimic_player;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

/**
 * Copies bundled default skins from assets into .minecraft/skins on first run.
 *
 * Put PNGs here (lowercase filenames only!):
 *   src/main/resources/assets/mimic_player/default_skins/<file>.png
 *
 * Copies only missing files - never overwrites user's existing files.
 */
@Mod.EventBusSubscriber(modid = Mimic_player.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSkinFolderBootstrap {

    // IMPORTANT: must be lowercase, both here and in the jar resources.
    private static final List<String> DEFAULT_SKINS = List.of(
            "steve.png",
            "alex.png",
            "verso.png"
            // add yours: "npc_1.png", "npc_2.png", ...
    );

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(ClientSkinFolderBootstrap::ensureDefaultSkinsPresent);
    }

    private static void ensureDefaultSkinsPresent() {
        File gameDir = Minecraft.getInstance().gameDirectory;
        File skinsDir = new File(gameDir, "skins");
        if (!skinsDir.exists() && !skinsDir.mkdirs()) return;

        ResourceManager rm = Minecraft.getInstance().getResourceManager();

        for (String file : DEFAULT_SKINS) {
            copyBundledIfMissing(rm, skinsDir, file);
        }
    }

    private static void copyBundledIfMissing(ResourceManager rm, File skinsDir, String fileName) {
        File outFile = new File(skinsDir, fileName);
        if (outFile.exists()) return;

        ResourceLocation src = ResourceLocation.fromNamespaceAndPath(Mimic_player.MODID, "default_skins/" + fileName);

        try {
            Optional<net.minecraft.server.packs.resources.Resource> opt = rm.getResource(src);
            if (opt.isEmpty()) return;

            try (InputStream in = opt.get().open();
                 FileOutputStream out = new FileOutputStream(outFile)) {
                in.transferTo(out);
            }
        } catch (Throwable ignored) {}
    }

    private ClientSkinFolderBootstrap() {}
}
