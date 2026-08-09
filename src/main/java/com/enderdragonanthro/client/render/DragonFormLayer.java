package com.enderdragonanthro.client.render;

import com.enderdragonanthro.EnderdragonAnthro;
import com.enderdragonanthro.client.DragonHud;
import com.enderdragonanthro.client.model.DragonFormModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders the anthro dragon over the player while transformed. The suit's
 * cubes are inflated past the skin so the player underneath disappears
 * inside the dragon. Skipped entirely in human form.
 */
public class DragonFormLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation TEXTURE =
            EnderdragonAnthro.id("textures/entity/dragon_form.png");

    private final DragonFormModel model;

    public DragonFormLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
                           EntityModelSet models) {
        super(parent);
        this.model = new DragonFormModel(models.bakeLayer(DragonFormModel.LAYER));
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int light,
                       AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!DragonHud.isDragonForm(player)) {
            return;
        }
        this.model.copyPose(getParentModel());
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        this.model.render(poseStack, buffer, light);
    }
}
