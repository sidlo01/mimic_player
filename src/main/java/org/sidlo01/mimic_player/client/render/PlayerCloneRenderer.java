package org.sidlo01.mimic_player.client.render;

import org.sidlo01.mimic_player.entity.PlayerCloneEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders the clone using the vanilla player model.
 *
 * This is the key part that makes the entity "look like a player".
 */
public class PlayerCloneRenderer extends HumanoidMobRenderer<PlayerCloneEntity, PlayerModel<PlayerCloneEntity>> {

    public PlayerCloneRenderer(EntityRendererProvider.Context ctx) {
        // NOTE: We always use the classic (Steve) model layer here for simplicity.
        // If you want correct slim-arm rendering (Alex), you can create a second model based on ModelLayers.PLAYER_SLIM
        // and swap based on the profile skin model.
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);

        // Optional: render held item in hand if you later add equipment to the clone.
        this.addLayer(new ItemInHandLayer<>(this, ctx.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(PlayerCloneEntity entity) {
        return PlayerCloneSkins.getSkinTexture(entity.getGameProfile());
    }
}
