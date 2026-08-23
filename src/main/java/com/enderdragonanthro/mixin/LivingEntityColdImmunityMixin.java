package com.enderdragonanthro.mixin;

import com.enderdragonanthro.transform.DragonFormManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The other half of being made of fire.
 *
 * A thing that shrugs off lava has no business shivering in powder snow, and
 * the frost vignette creeping in over a dragon's own violet was the tell that
 * something was wrong with the fiction.
 *
 * Targeted at LivingEntity rather than Entity, and that is not a detail.
 * canFreeze is declared on Entity and OVERRIDDEN on LivingEntity, so the one
 * that actually runs for a player is LivingEntity's — injecting into the
 * declaration would be shadowed by the override and never execute, which is the
 * same trap fireImmune avoids by going the other way (Entity declares it and
 * nothing overrides it).
 *
 * Both doors are shut. canFreeze stops the freeze ticks accumulating at all, so
 * the meter never fills, the vignette never draws and the damage never fires;
 * the damage guard is for cold arriving by some other road — a command, another
 * mod — where nothing consulted canFreeze on the way in.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityColdImmunityMixin {
    private boolean enderdragonanthro$isDragon() {
        return (Object) this instanceof Player player && DragonFormManager.isDragon(player);
    }

    @Inject(method = "canFreeze", at = @At("HEAD"), cancellable = true)
    private void enderdragonanthro$neverFreezes(CallbackInfoReturnable<Boolean> cir) {
        if (enderdragonanthro$isDragon()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void enderdragonanthro$noColdDamage(DamageSource source, float amount,
                                                CallbackInfoReturnable<Boolean> cir) {
        if (source.is(DamageTypes.FREEZE) && enderdragonanthro$isDragon()) {
            cir.setReturnValue(false);
        }
    }
}
