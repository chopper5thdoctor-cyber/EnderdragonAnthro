package com.enderdragonanthro.mixin;

import com.enderdragonanthro.ability.DragonMinions;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A shade cannot be hurt by the dragon it serves.
 *
 * Each ability that hits an area already skipped the court — Buffet, Charge,
 * the breath jet and the fire stream all carry the same filter, because a
 * stream that cooked the retinue would make Defend a liability. That covered
 * the abilities and nothing else, and the gaps were the ones that actually got
 * shades killed: a stray left click while turning, the lingering cloud a
 * Fireball leaves, the blast off a crater punch.
 *
 * Filtering at the point of damage rather than at each source closes all of
 * them at once, including the ones that do not exist yet. It also catches the
 * indirect cases the per-ability filters could not see — an area effect cloud
 * hurts through MobEffects.HARM, so what arrives here is a magic damage source
 * whose owner is the dragon, and only the source knows that.
 *
 * Strictly one-way. A shade can still be killed by anything else in the world,
 * and the dragon can still be hurt by a shade that has been turned — this says
 * only that your own court is not something you can hit.
 */
@Mixin(LivingEntity.class)
public abstract class ShadeLoyaltyMixin {
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void enderdragonanthro$loyalty(DamageSource source, float amount,
                                           CallbackInfoReturnable<Boolean> cir) {
        // getEntity, not getDirectEntity: the thing that arrives is often a
        // cloud, a fireball or an explosion, and the owner is who we mean.
        if (source.getEntity() instanceof Player dragon
                && DragonMinions.isOwnedBy(dragon, (LivingEntity) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
