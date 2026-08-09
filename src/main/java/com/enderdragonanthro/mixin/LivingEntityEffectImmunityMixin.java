package com.enderdragonanthro.mixin;

import com.enderdragonanthro.transform.DragonFormManager;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Canon: the dragon is immune to all status effects. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityEffectImmunityMixin {
    @Inject(method = "canBeAffected", at = @At("HEAD"), cancellable = true)
    private void enderdragonanthro$effectImmunity(MobEffectInstance effect,
                                                  CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof Player player && DragonFormManager.isDragon(player)) {
            cir.setReturnValue(false);
        }
    }
}
