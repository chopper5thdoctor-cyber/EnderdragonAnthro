package com.enderdragonanthro.client;

import com.enderdragonanthro.mixin.EntityRenderDispatcherInvoker;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The shadow you cast under yourself in first person.
 *
 * Vanilla gives nobody one. LevelRenderer skips the camera entity outright
 * unless the camera is detached, and the shadow is drawn inside
 * EntityRenderDispatcher.render, so in first person your entity is never
 * rendered and neither is anything belonging to it. For a human that is a
 * detail nobody could see — the ground under your feet is barely in frame, and
 * a shadow a block across would sit behind your own hand.
 *
 * At eight blocks tall it is not a detail. Look down as a dragon and you are
 * looking at several blocks of ground that should have a dragon on them, and
 * the shadows of everything else nearby are right there for comparison. So one
 * is drawn, on the same terms vanilla would have used: the same private
 * renderShadow, the same radius its renderer would report, the same fade — and
 * the same entityShadows setting, which is respected rather than worked around.
 */
public final class DragonShadow {
    /** PlayerRenderer's own shadowRadius, which scale is applied to. */
    private static final float BASE_RADIUS = 0.5F;
    /** PlayerRenderer's shadowStrength. */
    private static final float STRENGTH = 1.0F;
    /** Vanilla's fade budget: sixteen blocks, squared. */
    private static final double RANGE_SQR = 256.0;

    private DragonShadow() {
    }

    public static void render(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        Camera camera = context.camera();
        if (player == null || camera == null || camera.isDetached()) {
            return;                          // detached: vanilla is already drawing it
        }
        if (client.getCameraEntity() != player || player.isInvisible()
                || !client.options.entityShadows().get()
                || !DragonHud.isDragonForm(player)) {
            return;
        }

        float partialTick = context.tickCounter().getGameTimeDeltaPartialTick(false);
        double x = Mth.lerp(partialTick, player.xOld, player.getX());
        double y = Mth.lerp(partialTick, player.yOld, player.getY());
        double z = Mth.lerp(partialTick, player.zOld, player.getZ());

        // The dispatcher's own arithmetic, including the correction for a
        // camera that is pushed back by the entity's own scale.
        float scale = Math.max(1.0F, player.getScale());
        double distance = camera.getPosition().distanceToSqr(x, y, z) / (scale * scale);
        float weight = (float) ((1.0 - distance / RANGE_SQR) * STRENGTH);
        if (weight <= 0.0F) {
            return;
        }

        // renderShadow emits its vertices relative to the entity's own lerped
        // position, so the pose has to be standing there, camera-relative --
        // which is exactly where EntityRenderDispatcher.render leaves it.
        Vec3 eye = camera.getPosition();
        PoseStack poseStack = context.matrixStack();
        poseStack.pushPose();
        poseStack.translate(x - eye.x, y - eye.y, z - eye.z);
        EntityRenderDispatcherInvoker.enderdragonanthro$renderShadow(
                poseStack, context.consumers(), player, weight, partialTick,
                player.level(), BASE_RADIUS * scale);
        poseStack.popPose();
    }
}
