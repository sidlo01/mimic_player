package org.sidlo01.mimic_player;

import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import org.sidlo01.mimic_player.config.PlayerNPCConfig;
import org.sidlo01.mimic_player.registry.ModEntities;
import org.sidlo01.mimic_player.server.ModCommands;
import org.sidlo01.mimic_player.network.SkinSync;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Main mod entry point.
 *
 * Forge loads this class because mods.toml lists the same mod id.
 *
 * Docs reference for @Mod and mods.toml entrypoint:
 * https://docs.minecraftforge.net/en/1.20.1/gettingstarted/modfiles/
 */
@Mod(Mimic_player.MODID)
public class Mimic_player {
    public static final String MODID = "mimic_player";

    public Mimic_player() {
        // Mod Event Bus: used for registry events (DeferredRegister) and mod lifecycle events.
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register our entity type(s)
        ModEntities.register(modBus);

        // Register config (common = loads on both client + server and is synced to server configs folder).
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, PlayerNPCConfig.SPEC);

        // Register Forge (game) event handlers.
        MinecraftForge.EVENT_BUS.register(ModCommands.class);

        SkinSync.register();
        modBus.addListener((FMLCommonSetupEvent e) -> e.enqueueWork(SkinSync::register));
    }

}
