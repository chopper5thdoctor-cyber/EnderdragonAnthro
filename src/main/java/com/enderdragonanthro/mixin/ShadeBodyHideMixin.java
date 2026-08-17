package com.enderdragonanthro.mixin;

import com.enderdragonanthro.ability.ShadeIdentity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.EnderMan;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Takes the enderman out from under a shade.
 *
 * Same trick PlayerBodyHideMixin plays on the transformed player: null from
 * getRenderType skips the base model entirely while leaving every feature
 * layer running, so ShadeLayer still draws and the carried block a shade is
 * hauling home still shows. Only the vanilla body goes.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class ShadeBodyHideMixin {
    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void enderdragonanthro$hideShadeBody(LivingEntity entity, boolean bodyVisible,
                                                 boolean translucent, boolean glowing,
                                                 CallbackInfoReturnable<RenderType> cir) {
        if (entity instanceof EnderMan enderman && ShadeIdentity.slotOf(enderman) >= 0) {
            cir.setReturnValue(null);
        }
    }
}
