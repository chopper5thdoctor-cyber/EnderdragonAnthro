package com.enderdragonanthro.client.render;

import com.enderdragonanthro.EnderdragonAnthro;
import com.enderdragonanthro.client.DragonHud;
import com.enderdragonanthro.client.model.DragonFormModel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * The hand you see in first person, while transformed.
 *
 * The hand renderer has already placed and swung the arm by the time it asks
 * the player renderer to draw one, so all this has to do is put the dragon's
 * arm where the human one would have gone: shoulder on the origin, scaled so
 * it keeps the same screen footprint, and the fingers pointing the same way.
 */
public final class DragonFirstPersonArm {
    private static final ResourceLocation TEXTURE =
            EnderdragonAnthro.id("textures/entity/dragon_form.png");
    private static final ResourceLocation EYES =
            EnderdragonAnthro.id("textures/entity/dragon_form_eyes.png");

    // The vanilla arm this replaces: addBox(-3, -2, -2, 4, 12, 4) on the right
    // arm part. Its centre line, and where its shoulder end sits.
    private static final float VANILLA_CENTRE_X = -1.0F;
    private static final float VANILLA_SHOULDER_Y = -2.0F;

    private static DragonFormModel model;

    private DragonFirstPersonArm() {
    }

    /** Draws the dragon's arm and reports whether it took over from vanilla. */
    public static boolean render(PoseStack poseStack, MultiBufferSource buffers, int light,
                                 AbstractClientPlayer player, boolean right) {
        if (!DragonHud.isDragonForm(player)) {
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        if (model == null) {
            model = new DragonFormModel(client.getEntityModels().bakeLayer(DragonFormModel.LAYER));
        }

        // Scaling alone was not enough. The dragon's arm cubes sit seventeen
        // units off their own pivot, so dropping the part at the origin put the
        // wrong end of it on screen — it read as a slab of texture rather than
        // an arm. Fit the arm's real box onto the box vanilla's arm occupied
        // instead: centred on the same line, shoulder in the same place, and
        // the same twelve units long. Everything comes off the rig, so
        // reshaping the arm in Blockbench moves the hand with it.
        float scale = DragonFormModel.ARM_SCALE;
        float centreX = (DragonFormModel.ARM_MIN_X + DragonFormModel.ARM_MAX_X) / 2.0F;
        float centreZ = (DragonFormModel.ARM_MIN_Z + DragonFormModel.ARM_MAX_Z) / 2.0F;
        float side = right ? 1.0F : -1.0F;      // the left arm is its mirror

        poseStack.pushPose();
        // Model units over sixteen: ModelPart does that division itself, one
        // level further down, so the offset has to be in blocks by the time it
        // reaches the stack.
        poseStack.translate(
                side * (VANILLA_CENTRE_X - scale * centreX) / 16.0F,
                (VANILLA_SHOULDER_Y - scale * DragonFormModel.ARM_MIN_Y) / 16.0F,
                -scale * centreZ / 16.0F);
        poseStack.scale(scale, scale, scale);
        // The rig names its arms from the model's own facing, which is the
        // mirror of the game's — so the part to clone for the player's right
        // hand is the rig's LEFT arm, and vice versa.
        boolean rigArm = !right;
        model.renderArm(rigArm, poseStack,
                buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light);
        model.renderArm(rigArm, poseStack, buffers.getBuffer(RenderType.eyes(EYES)), light);
        poseStack.popPose();
        return true;
    }

    /** Model layers are rebuilt on a resource reload; drop the stale bake. */
    public static void invalidate() {
        model = null;
    }
}
