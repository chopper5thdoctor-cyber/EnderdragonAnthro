package com.enderdragonanthro.mixin;

import com.enderdragonanthro.client.DragonSightClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Health over every living thing, in the one frame where that is simple.
 *
 * This has now been tried twice in the wrong place. A world pass drew nothing.
 * A feature layer drew it under the mob's feet, and once that was corrected it
 * turned edge-on and vanished depending on which way the mob happened to be
 * facing — because a layer does not run in world space. By the time layers are
 * called, LivingEntityRenderer has applied the entity's body yaw, then
 * scale(-1, -1, 1), then translate(0, -1.501, 0). Billboarding inside that
 * composes the camera's rotation with the mob's own, so the text swings to
 * face the camera only when the mob is already pointing north.
 *
 * EntityRenderer.render is where vanilla draws name tags, and it is the frame
 * that makes them work: camera-relative, world-aligned, standing at the
 * entity's feet with none of the model's transforms applied. Same place, same
 * recipe, one line higher.
 */
@Mixin(EntityRenderer.class)
public abstract class DragonSightHealthMixin {
    /** How far off a mob is still worth reading. */
    private static final double RANGE = 48.0;
    /** Bigger than a name tag's 0.025, because it is read across a field. */
    private static final float SCALE = 0.055F;
    /** Clear of the name tag, which sits at height + 0.5. */
    private static final float HEADROOM = 1.1F;

    @Inject(method = "render", at = @At("TAIL"))
    private void enderdragonanthro$health(Entity entity, float entityYaw, float partialTick,
                                          PoseStack poseStack, MultiBufferSource buffers,
                                          int light, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (!DragonSightClient.isOpen() || client.player == null
                || !(entity instanceof LivingEntity living) || entity == client.player) {
            return;
        }
        if (entity.distanceToSqr(client.player) > RANGE * RANGE) {
            return;
        }

        float health = living.getHealth();
        float max = living.getMaxHealth();
        String text = Math.round(health) + "/" + Math.round(max);
        // Green through red by what is left, so a glance reads as a threat
        // assessment rather than as a wall of numbers.
        float share = max > 0.0F ? Math.min(1.0F, Math.max(0.0F, health / max)) : 0.0F;
        int colour = 0xFF000000
                | (Math.round((1.0F - share) * 255) << 16)
                | (Math.round(share * 255) << 8);

        Font font = client.font;
        poseStack.pushPose();
        poseStack.translate(0.0F, living.getBbHeight() + HEADROOM, 0.0F);
        poseStack.mulPose(client.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-SCALE, -SCALE, SCALE);
        Matrix4f pose = poseStack.last().pose();
        font.drawInBatch(text, -font.width(text) / 2.0F, 0.0F, colour, false, pose,
                buffers, Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }
}
