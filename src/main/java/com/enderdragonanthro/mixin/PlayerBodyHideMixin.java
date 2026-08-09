package com.enderdragonanthro.mixin;

import com.enderdragonanthro.client.DragonHud;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hides the vanilla player body while transformed, the same way
 * invisibility does — the base model is skipped but render layers
 * (including DragonFormLayer, which draws the anthro dragon) still run.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class PlayerBodyHideMixin {
    @Inject(method = "isBodyVisible", at = @At("HEAD"), cancellable = true)
    private void enderdragonanthro$hideHumanBody(LivingEntity entity,
                                                 CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof Player player && DragonHud.isDragonForm(player)) {
            cir.setReturnValue(false);
        }
    }
}
