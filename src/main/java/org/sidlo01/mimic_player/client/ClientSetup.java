package org.sidlo01.mimic_player.client;

import org.sidlo01.mimic_player.Mimic_player;
import org.sidlo01.mimic_player.client.render.PlayerCloneRenderer;
import org.sidlo01.mimic_player.registry.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-only registration (renderer).
 */
@Mod.EventBusSubscriber(modid = Mimic_player.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.PLAYER_CLONE.get(), PlayerCloneRenderer::new);
    }

    private ClientSetup() {}
}
