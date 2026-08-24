package com.enderdragonanthro.client.render;

import com.enderdragonanthro.EnderdragonAnthro;
import com.enderdragonanthro.client.DragonHud;
import com.enderdragonanthro.client.DragonTail;
import com.enderdragonanthro.client.DragonWings;
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
        // One beat per Boost. copyPose has already put the body back to the
        // player's pose, so this has to come after it or the wings are reset
        // out from under themselves every frame.
        float beat = DragonWings.phase(player, partialTick);
        if (beat >= 0.0F) {
            this.model.flap(beat);
        }
        // And the tail, which is not tied to the beat at all -- it is the air
        // pushing a long heavy thing around, so it runs the whole time you are
        // gliding rather than when you tap. The weight is what fades it in and
        // out; see DragonTail for why a looping clip needs one.
        this.model.wag(DragonTail.phase(player, partialTick),
                DragonTail.weight(player, partialTick));

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
