package com.enderdragonanthro.mixin;

import com.enderdragonanthro.client.DragonHud;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Removes the vanilla player body entirely while transformed. Hiding via
 * isBodyVisible is not enough: for a body the viewer is allowed to see
 * (their own, most importantly) the renderer falls back to a translucent
 * "ghost" pass. Returning null from getRenderType skips the model draw
 * altogether; render layers — including DragonFormLayer — still run.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class PlayerBodyHideMixin {
    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void enderdragonanthro$hideHumanBody(LivingEntity entity, boolean bodyVisible,
                                                 boolean translucent, boolean glowing,
                                                 CallbackInfoReturnable<RenderType> cir) {
        if (entity instanceof Player player && DragonHud.isDragonForm(player)) {
            cir.setReturnValue(null);
        }
    }
}
