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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The canon dragon takes (damage / 4) + 1 on every non-head hit. A player
 * has one hitbox, so M1 applies the body formula to everything; the
 * headshot-zone exception is a later milestone (DESIGN.md complication #3).
 *
 * The same hook is where the court learns who is worth killing.
 */
@Mixin(Player.class)
public abstract class PlayerDamageResilienceMixin {
    /**
     * A dragon does not bruise on scenery.
     *
     * Vanilla's elytra branch charges you for hitting a wall: LivingEntity
     * .travel reads horizontalCollision and bills flyIntoWall by the speed you
     * lost. That is a rule for a human strapped to a pair of wings, and it made
     * flying fast into anything a self-inflicted wound. What happens instead is
     * DragonFlight's business — with Explosive Intent armed, the wall loses.
     */
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void enderdragonanthro$shrugOffWalls(DamageSource source, float amount,
                                                 CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player) (Object) this;
        boolean scenery = source.is(net.minecraft.world.damagesource.DamageTypes.FLY_INTO_WALL)
                // Being inside a block is the intended state of something
                // tunnelling, and the bore cannot always finish before the
                // move: turn hard enough at ninety blocks a second and you
                // arrive somewhere the sweep did not predict. Suffocating for
                // it turns a near miss into a death.
                || (source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL)
                        && self.isFallFlying());
        if (scenery && DragonFormManager.isDragon(self)) {
            // hurt(), not actuallyHurt(). Cancelling the latter stops the
            // damage and nothing else: the red flash, the grunt and the camera
            // kick are all set up in hurt() before it ever gets that far, which
            // is why flying into a cliff still felt like flying into a cliff.
            cir.setReturnValue(false);
        }
    }

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
