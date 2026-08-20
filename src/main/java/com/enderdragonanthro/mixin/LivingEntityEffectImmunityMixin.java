package com.enderdragonanthro.mixin;

import com.enderdragonanthro.transform.DragonFormManager;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Canon: the dragon is immune to all status effects.
 *
 * With one exception, and it is the reason Dragon Sight had a tint but no dark
 * vision: the sight grants night vision, addEffect asks canBeAffected first,
 * and this said no. The sight is the dragon's own, not something drunk out of a
 * bottle, so it is let through -- and only while the sight is actually open, so
 * a thrown potion still does nothing.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityEffectImmunityMixin {
    @Inject(method = "canBeAffected", at = @At("HEAD"), cancellable = true)
    private void enderdragonanthro$effectImmunity(MobEffectInstance effect,
                                                  CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof Player player) || !DragonFormManager.isDragon(player)) {
            return;
        }
        if (effect.getEffect().is(net.minecraft.world.effect.MobEffects.NIGHT_VISION)
                && player instanceof net.minecraft.server.level.ServerPlayer server
                && com.enderdragonanthro.ability.DragonSight.isOpen(server)) {
            return;
        }
        cir.setReturnValue(false);
    }
}
