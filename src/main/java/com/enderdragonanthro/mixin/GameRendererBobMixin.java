package com.enderdragonanthro.mixin;

import com.enderdragonanthro.client.DragonHud;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Paces the walk bob to the dragon's stride rather than the player's.
 *
 * Vanilla drives the bob off {@code walkDist} — ground covered, in blocks — so
 * the cadence is distance-based, not time-based: one full cycle roughly every
 * 3.3 blocks whatever your speed. That is right for a human, whose legs are a
 * fixed length. Give the same legs a 3x stride and they simply cycle three
 * times as fast, which is what made the hand look like it was sprinting on the
 * spot.
 *
 * A real stride grows with the leg, so the cadence is speed over stride length,
 * and stride length goes with height. Dividing the phase by the scale gives
 * 3 / 4.44 -- about two thirds of a human's cadence at three times the ground
 * speed. Slow and heavy, which is the point of being eight blocks tall.
 *
 * The amplitude is left alone: vanilla caps it at 0.1 and scaling that as well
 * would heave the camera around by nearly half a block a step.
 *
 * This is the camera bob AND the hand bob — {@code renderItemInHand} runs the
 * same method over the hand's own pose stack, so one place covers both.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererBobMixin {
    @Inject(method = "bobView(Lcom/mojang/blaze3d/vertex/PoseStack;F)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void enderdragonanthro$strideBob(PoseStack poseStack, float partialTick,
                                             CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (!(client.getCameraEntity() instanceof Player player)
                || !DragonHud.isDragonForm(player)) {
            return;                                   // human: vanilla bob, untouched
        }
        float stride = Math.max(1.0F, player.getScale());
        float step = player.walkDist - player.walkDistO;
        float phase = -(player.walkDist + step * partialTick) / stride;
        float amount = Mth.lerp(partialTick, player.oBob, player.bob);

        // Vanilla's own bob, verbatim, on the lengthened phase.
        poseStack.translate(Mth.sin(phase * (float) Math.PI) * amount * 0.5F,
                -Math.abs(Mth.cos(phase * (float) Math.PI) * amount), 0.0F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(
                Mth.sin(phase * (float) Math.PI) * amount * 3.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(
                Math.abs(Mth.cos(phase * (float) Math.PI - 0.2F) * amount) * 5.0F));
        ci.cancel();
    }
}
