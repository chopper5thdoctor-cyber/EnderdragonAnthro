package com.enderdragonanthro.mixin;

import com.enderdragonanthro.EnderdragonAnthro;
import com.enderdragonanthro.ability.HomingCrystals;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Paints the homing crystal orange so it reads apart from a live end crystal.
 *
 * Cosmetic only, so this injection is optional: if the mapping ever shifts the
 * crystal simply renders as normal rather than the game refusing to start.
 */
@Mixin(EndCrystalRenderer.class)
public abstract class EndCrystalTextureMixin {
    private static final ResourceLocation HOMING =
            EnderdragonAnthro.id("textures/entity/homing_crystal.png");

    @Inject(method = "getTextureLocation(Lnet/minecraft/world/entity/boss/enderdragon/EndCrystal;)"
            + "Lnet/minecraft/resources/ResourceLocation;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void enderdragonanthro$orange(EndCrystal crystal,
                                          CallbackInfoReturnable<ResourceLocation> cir) {
        if (HomingCrystals.isHoming(crystal)) {
            cir.setReturnValue(HOMING);
        }
    }
}
