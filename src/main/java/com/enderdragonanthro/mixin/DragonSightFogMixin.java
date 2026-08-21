package com.enderdragonanthro.mixin;

import com.enderdragonanthro.client.DragonSightClient;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Water stops being murky while the sight is open. Lava does not.
 *
 * Vanilla pulls the fog planes in hard underwater — a few blocks, less again
 * with the biome's water colour on top — and that is a fine rule for a person
 * holding their breath. It is the wrong rule for something whose whole ability
 * is seeing further than it should.
 *
 * Lava is left exactly as it is, and not as an oversight. Water fog is
 * atmosphere, the murk of looking through a lot of it; lava fog is the fluid
 * being opaque. Seeing through water is a sharper eye. Seeing through lava
 * would be a different claim entirely, and a silly one.
 *
 * Written at RETURN rather than by cancelling: vanilla has already chosen its
 * shape and its colour by then, and only the two distances are overwritten, so
 * everything else about the underwater view survives untouched.
 */
@Mixin(FogRenderer.class)
public abstract class DragonSightFogMixin {
    /** Where the fog starts once the murk is gone: behind the camera. */
    private static final float START = -8.0F;
    /**
     * ...and where it ends: well past anything that will be drawn.
     *
     * Ending it exactly at the far plane still leaves a gradient that reaches
     * full strength at the edge of view, which underwater is the difference
     * between clear and slightly green. Pushing it out four times removes the
     * gradient entirely rather than merely stretching it.
     */
    private static final float BEYOND = 4.0F;

    @Inject(method = "setupFog", at = @At("RETURN"))
    private static void enderdragonanthro$clearWater(Camera camera, FogRenderer.FogMode mode,
                                                     float farPlane, boolean nearFog,
                                                     float partialTick, CallbackInfo ci) {
        if (!DragonSightClient.isOpen()
                || camera.getFluidInCamera() != FogType.WATER) {
            return;
        }
        RenderSystem.setShaderFogStart(START);
        RenderSystem.setShaderFogEnd(farPlane * BEYOND);
    }
}
