package com.enderdragonanthro.client.render;

import com.enderdragonanthro.EnderdragonAnthro;
import com.enderdragonanthro.client.DragonHud;
import com.enderdragonanthro.config.DragonConfig;
import com.enderdragonanthro.client.model.DragonFormModel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws the anthro dragon over the (hidden) player body. The head is the
 * artist-painted one from the mod's own sheet — the eyes, lips and eyelashes
 * are hand-drawn detail the vanilla dragon texture has no equivalent for — and
 * the magenta eye/mouth pixels get a second fullbright pass so they glow in
 * the dark the way the real dragon's do.
 */
public class DragonFormLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation TEXTURE =
            EnderdragonAnthro.id("textures/entity/dragon_form.png");
    private static final ResourceLocation EYES =
            EnderdragonAnthro.id("textures/entity/dragon_form_eyes.png");

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

        float scale = DragonConfig.trueProportions()
                ? DragonFormModel.TRUE_SCALE       // as drawn; the head overshoots
                : DragonFormModel.RENDER_SCALE;    // trimmed to fill the hitbox

        poseStack.pushPose();
        // The offset is in model units. The stack is in BLOCKS here — ModelPart
        // does the /16 itself, further down — so it has to be converted, or the
        // dragon gets shoved sixteen times too far and ends up buried in the
        // floor. (+Y is down: LivingEntityRenderer already scaled by -1, -1, 1.)
        poseStack.translate(0.0F, DragonFormModel.groundOffset(scale) / 16.0F, 0.0F);
        poseStack.scale(scale, scale, scale);
        this.model.render(poseStack,
                buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light);
        this.model.render(poseStack, buffers.getBuffer(RenderType.eyes(EYES)), light);
        poseStack.popPose();
    }
}
