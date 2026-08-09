package com.enderdragonanthro.client.render;

import com.enderdragonanthro.EnderdragonAnthro;
import com.enderdragonanthro.client.DragonHud;
import com.enderdragonanthro.client.model.CanonDragonHeadModel;
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
 * Renders the anthro dragon over the (hidden) player. The body uses the
 * mod's palette texture; the head is the canon dragon head rendered with
 * the VANILLA dragon textures at runtime — including the same emissive
 * eyes pass the real Ender Dragon uses, so eyes and mouth glow in the
 * dark. Nothing from the vanilla assets is redistributed.
 */
public class DragonFormLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation TEXTURE =
            EnderdragonAnthro.id("textures/entity/dragon_form.png");
    private static final ResourceLocation DRAGON_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/enderdragon/dragon.png");
    private static final ResourceLocation DRAGON_EYES_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/enderdragon/dragon_eyes.png");

    /** 16 canon head units = 1 block = 4 anthro units at the 4.44x render scale. */
    private static final float HEAD_SCALE = 0.25F;

    private final DragonFormModel model;
    private final CanonDragonHeadModel canonHead;

    public DragonFormLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
                           EntityModelSet models) {
        super(parent);
        this.model = new DragonFormModel(models.bakeLayer(DragonFormModel.LAYER));
        this.canonHead = new CanonDragonHeadModel(models.bakeLayer(CanonDragonHeadModel.LAYER));
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int light,
                       AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!DragonHud.isDragonForm(player)) {
            return;
        }
        // whole model is authored at 4x and drawn at 1/4 for head-grade
        // texel density; the canon head's native units ARE the 4x space
        poseStack.pushPose();
        poseStack.scale(HEAD_SCALE, HEAD_SCALE, HEAD_SCALE);
        this.model.copyPose(getParentModel());
        VertexConsumer bodyBuffer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        this.model.render(poseStack, bodyBuffer, light);

        poseStack.pushPose();
        this.model.applyHeadTransform(poseStack);
        poseStack.translate(0.0F, -1.0F, 0.0F);
        this.canonHead.render(poseStack,
                buffers.getBuffer(RenderType.entityCutoutNoCull(DRAGON_TEXTURE)), light);
        this.canonHead.render(poseStack,
                buffers.getBuffer(RenderType.eyes(DRAGON_EYES_TEXTURE)), light);
        poseStack.popPose();
        poseStack.popPose();
    }
}
