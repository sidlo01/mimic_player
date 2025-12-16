package org.sidlo01.mimic_player.client;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.sidlo01.mimic_player.Mimic_player;

import javax.swing.text.html.Option;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

/**
 * Client-only bootstrap:
 * - If <gameDir>/skins does not exist (first run), create it
 * - Extract bundled default skins from the mod jar into that folder
 *
 * This ensures every client who installs the mod gets the same initial skins,
 * while still allowing them to edit/replace PNGs later.
 */
@Mod.EventBusSubscriber(modid = Mimic_player.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSkinFolderBootstrap {

    /**
     * List of default skin PNG filenames that you ship in:
     *   assets/<modid>/default_skins/<filename>
     *
     * Add/remove names to match the files you provide.
     */
    private static final List<String> DEFAULT_SKINS = List.of(
            "Steve.png",
            "Alex.png",
            "NPC_1.png",
            "NPC_2.png"
    );

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // Run after client is ready enough (safe to touch Minecraft instance + resource manager).
        event.enqueueWork(ClientSkinFolderBootstrap::ensureSkinFolderAndDefaults);
    }

    private static void ensureSkinFolderAndDefaults() {
        // <.minecraft> or custom instance directory
        File gameDir = Minecraft.getInstance().gameDirectory;

        // Your override folder used by PlayerCloneSkins.java
        File skinsDir = new File(gameDir, "skins");

        // Requirement: only do this on first run when folder doesn't exist.
        if (skinsDir.exists()) return;

        if (!skinsDir.mkdirs()) {
            // If we can’t create it, just stop (don’t crash).
            return;
        }

        ResourceManager rm = Minecraft.getInstance().getResourceManager();

        // Copy each bundled PNG into <gameDir>/skins/
        for (String fileName : DEFAULT_SKINS) {
            copyBundledSkin(rm, skinsDir, fileName);
        }
    }

    private static void copyBundledSkin(ResourceManager rm, File skinsDir, String fileName) {
        // Source inside jar: assets/mimic_player/default_skins/<fileName>
        ResourceLocation src = ResourceLocation.fromNamespaceAndPath(Mimic_player.MODID, "default_skins/" + fileName);

        // Destination on disk: <gameDir>/skins/<fileName>
        File outFile = new File(skinsDir, fileName);

        // Don’t overwrite if the file already exists (useful if user customized)
        if (outFile.exists()) return;

        try  {
            var resOpt = rm.getResource(src);
            if (resOpt.isEmpty()) return; // file not found in jar (maybe you forgot to add it)

            try (InputStream in = resOpt.get().open();
                 FileOutputStream out = new FileOutputStream(outFile)) {
                in.transferTo(out);
            }
        } catch (Throwable ignored) {
            // Don't crash the game if one file fails.
        }
    }

    private ClientSkinFolderBootstrap() {}
}
