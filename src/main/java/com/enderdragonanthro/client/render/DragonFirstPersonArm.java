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

        poseStack.pushPose();
        // The vanilla arm hangs from a shoulder at the origin with its cubes
        // running +Y away from it; the dragon's arm is built the same way, just
        // very much bigger, so the whole job is the scale.
        poseStack.scale(DragonFormModel.ARM_SCALE,
                DragonFormModel.ARM_SCALE,
                DragonFormModel.ARM_SCALE);
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
