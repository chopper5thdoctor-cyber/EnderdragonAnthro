package com.enderdragonanthro.mixin;

import com.enderdragonanthro.ability.DragonMinions;
import com.enderdragonanthro.transform.DragonFormManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The canon dragon takes (damage / 4) + 1 on every non-head hit. A player
 * has one hitbox, so M1 applies the body formula to everything; the
 * headshot-zone exception is a later milestone (DESIGN.md complication #3).
 *
 * The same hook is where the court learns who is worth killing.
 */
@Mixin(Player.class)
public abstract class PlayerDamageResilienceMixin {
    @ModifyVariable(method = "actuallyHurt", at = @At("HEAD"), argsOnly = true)
    private float enderdragonanthro$bodyResilience(float amount, DamageSource source) {
        Player self = (Player) (Object) this;
        if (amount > 0.0F && DragonFormManager.isDragon(self)) {
            return amount / 4.0F + 1.0F;
        }
        return amount;
    }

    /** Whoever keeps hitting you gets the whole court's attention. */
    @Inject(method = "actuallyHurt", at = @At("HEAD"))
    private void enderdragonanthro$callTheCourt(DamageSource source, float amount, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (amount > 0.0F && !self.level().isClientSide && DragonFormManager.isDragon(self)) {
            DragonMinions.retaliate(self, source.getEntity());
        }
    }
}
