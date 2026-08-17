package com.enderdragonanthro.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Keep a big creature's shadow from falling off the end of its own range.
 *
 * Two vanilla rules that have never had to meet. The third-person camera sits
 * back {@code entity.getScale() * 4} blocks — Camera.setup scales the zoom, so
 * an eight-block dragon is framed from about eighteen blocks rather than four.
 * Entity shadows, meanwhile, fade on a fixed budget:
 *
 * <pre>strength = (1 - camera.distanceToSqr(entity) / 256) * shadowStrength</pre>
 *
 * 256 is sixteen squared, and below zero the shadow is skipped outright. So the
 * shadow of anything scaled past about four is not faint in third person, it is
 * absent — the camera has been pushed further away than the shadow is allowed
 * to be seen from, by the entity's own size. A human at four blocks gets 0.94
 * and a dragon at eighteen gets a negative number.
 *
 * Nobody at Mojang would have hit this: nothing vanilla is both large and
 * something you look at from behind.
 *
 * Dividing the squared distance by the squared scale makes the budget grow with
 * the creature instead of staying fixed, so a dragon framed the way the camera
 * frames it reads 0.94 as well — the same shadow a human gets, at the same
 * point on the fade. Entities at scale 1 divide by 1 and are untouched.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class DragonShadowMixin {
    @Redirect(
            method = "render(Lnet/minecraft/world/entity/Entity;DDDFF"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;"
                            + "distanceToSqr(DDD)D"),
            require = 0)
    private double enderdragonanthro$shadowReach(EntityRenderDispatcher dispatcher,
                                                 double x, double y, double z,
                                                 Entity entity, double renderX, double renderY,
                                                 double renderZ, float rotationYaw,
                                                 float partialTick, PoseStack poseStack,
                                                 MultiBufferSource buffers, int light) {
        double distance = dispatcher.distanceToSqr(x, y, z);
        if (!(entity instanceof LivingEntity living)) {
            return distance;
        }
        float scale = living.getScale();
        return scale > 1.0F ? distance / (scale * scale) : distance;
    }
}
