package com.enderdragonanthro.client.render;

import com.enderdragonanthro.EnderdragonAnthro;
import com.enderdragonanthro.client.DragonHud;
import com.enderdragonanthro.client.model.DragonFormModel;
import com.enderdragonanthro.config.DragonConfig;
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
    // ...and the part's own pivot, PartPose.offset(-5, 2, 0), which those box
    // figures are measured against.
    //
    // This is easy to leave out and was. ModelPart.render applies the pivot
    // itself, so vanilla's arm lands at pivot + box while renderArm zeroes the
    // pivot and this placed the dragon's arm at box alone -- adrift by exactly
    // (-5, 2, 0). Not a subtle 2 units, either: the hand renderer rotates 200
    // degrees about X before drawing, which turns model-space down into roughly
    // screen-up, so the missing +2 read as the arm sitting too LOW on screen.
    private static final float VANILLA_PIVOT_X = -5.0F;
    private static final float VANILLA_PIVOT_Y = 2.0F;

    /**
     * How much bigger than the human arm the dragon's hand reads, on top of the
     * fit below.
     *
     * The fit answers "the same size vanilla's arm was", which is correct and
     * not what a dragon's arm should feel like — a hand this heavy filling the
     * same corner a human wrist did reads as a small hand far away. This is the
     * one number that dials it, and it is deliberately not derived from
     * anything: the rig cannot tell you how present a hand should feel.
     *
     * It grows about the shoulder rather than about the hand, because the y fit
     * pins ARM_MIN_Y to the shoulder line whatever the scale is — so the arm
     * stays rooted where it leaves the bottom of the screen and reaches further
     * in, instead of drifting off its own joint.
     */
    private static final float BULK = 1.0F;

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
        // Follows the body's setting: at authored proportions the hand is the
        // same 18% larger the rest of the dragon is.
        float scale = DragonFormModel.ARM_SCALE * BULK
                * (DragonConfig.trueProportions()
                   ? DragonFormModel.TRUE_SCALE / DragonFormModel.RENDER_SCALE : 1.0F);
        float centreX = (DragonFormModel.ARM_MIN_X + DragonFormModel.ARM_MAX_X) / 2.0F;
        float centreZ = (DragonFormModel.ARM_MIN_Z + DragonFormModel.ARM_MAX_Z) / 2.0F;
        float side = right ? 1.0F : -1.0F;      // the left arm is its mirror

        poseStack.pushPose();
        // Model units over sixteen: ModelPart does that division itself, one
        // level further down, so the offset has to be in blocks by the time it
        // reaches the stack.
        // Pivot included, so the arm ends up in exactly the volume vanilla's
        // occupies: x -8..-4, y 0..12, mirrored for the left.
        //
        // Note the PLUS on centreX. ARM_MIN_X/MAX_X are measured off the rig's
        // left arm, whose cubes run +x; the right arm's are its mirror and run
        // -x, so the arm actually being drawn has centre -centreX and the two
        // negations cancel. Getting this wrong is invisible until the hand
        // carries art with a direction to it.
        poseStack.translate(
                side * (VANILLA_PIVOT_X + VANILLA_CENTRE_X + scale * centreX) / 16.0F,
                (VANILLA_PIVOT_Y + VANILLA_SHOULDER_Y - scale * DragonFormModel.ARM_MIN_Y) / 16.0F,
                -scale * centreZ / 16.0F);
        poseStack.scale(scale, scale, scale);
        // The rig names its arms as the game does — copyPose has always bound
        // rig left_arm to vanilla leftArm, and the geometry agrees, since that
        // group sits at x +20 where vanilla's leftArm pivot is +5. So the hand
        // clones the arm of the SAME side, and the comment that used to sit
        // here saying otherwise had first person wearing the other hand.
        //
        // It went unnoticed because the fists' mirror flag was on the wrong
        // twin, which flipped the art back and made a left hand read as a
        // right one. Fixing that flag is what finally showed this.
        model.renderArm(right, poseStack,
                buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light);
        model.renderArm(right, poseStack, buffers.getBuffer(RenderType.eyes(EYES)), light);
        poseStack.popPose();
        return true;
    }

    /** Model layers are rebuilt on a resource reload; drop the stale bake. */
    public static void invalidate() {
        model = null;
    }
}
