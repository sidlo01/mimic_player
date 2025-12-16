package org.sidlo01.mimic_player.registry;

import org.sidlo01.mimic_player.Mimic_player;
import org.sidlo01.mimic_player.entity.PlayerCloneEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * DeferredRegister-based registry holder for our EntityTypes.
 *
 * DeferredRegister is the recommended registration approach for modern Forge.
 */
public final class ModEntities {
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Mimic_player.MODID);

    /**
     * A "player-like" living entity rendered with the player model + a player's skin.
     */
    public static final RegistryObject<EntityType<PlayerCloneEntity>> PLAYER_CLONE =
            ENTITY_TYPES.register("player_clone", () ->
                    EntityType.Builder
                            .of(PlayerCloneEntity::new, MobCategory.MISC)
                            // Player-sized hitbox.
                            .sized(0.6f, 1.8f)
                            // Update interval similar to mobs.
                            .clientTrackingRange(8)
                            .build("player_clone")
            );

    public static void register(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
    }

    private ModEntities() {}
}
