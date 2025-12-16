package org.sidlo01.mimic_player.registry;

import org.sidlo01.mimic_player.Mimic_player;
import org.sidlo01.mimic_player.entity.PlayerCloneEntity;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Registers attributes for our living entity.
 *
 * Without this, the game will crash when the entity is spawned.
 */
@Mod.EventBusSubscriber(modid = Mimic_player.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModEntityAttributes {

    @SubscribeEvent
    public static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(ModEntities.PLAYER_CLONE.get(), PlayerCloneEntity.createAttributes().build());
    }

    private ModEntityAttributes() {}
}
