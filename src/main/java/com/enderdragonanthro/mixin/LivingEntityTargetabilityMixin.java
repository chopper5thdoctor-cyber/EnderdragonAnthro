package com.enderdragonanthro.mixin;

import com.enderdragonanthro.transform.DragonFormManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Canon: no mob is hostile to the Ender Dragon — until it is given a reason.
 *
 * canBeSeenAsEnemy() is asked of the TARGET and knows nothing about who is
 * asking, so it can only answer for everyone at once. That is what made the
 * world placid: nothing could ever be angry at a dragon, however hard it was
 * hit. The blanket answer stays, because it is the right default and it covers
 * the brain mobs whose sensors never go near canAttack.
 *
 * The exception is granted one mob at a time in canAttack, which does know both
 * sides. A mob that has been hit gets to answer yes without the blanket rule
 * ever being consulted.
 *
 * Every targeting path —
 * goal-based mobs, brain mobs (warden, piglins), enderman stare anger —
 * funnels through canBeSeenAsEnemy(), so answering false here makes all
 * mob AI ignore a transformed player, the same way it ignores creative
 * players. Declared on LivingEntity (Player does not override it).
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityTargetabilityMixin {
    @Inject(method = "canBeSeenAsEnemy", at = @At("HEAD"), cancellable = true)
    private void enderdragonanthro$dragonIsNotPrey(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player player && DragonFormManager.isDragon(player)) {
            cir.setReturnValue(false);
        }
    }

    /**
     * ...unless this particular one has been hit by it.
     *
     * Returning true here short-circuits the blanket rule rather than arguing
     * with it: canAttack is what the goals actually ask, and it is answered
     * before canBeSeenAsEnemy is reached.
     */
    @Inject(method = "canAttack(Lnet/minecraft/world/entity/LivingEntity;)Z",
            at = @At("HEAD"), cancellable = true)
    private void enderdragonanthro$grudge(LivingEntity target,
                                          CallbackInfoReturnable<Boolean> cir) {
        if (target instanceof Player player && DragonFormManager.isDragon(player)
                && com.enderdragonanthro.ability.DragonPresence.provoked(
                        (LivingEntity) (Object) this, player)) {
            cir.setReturnValue(true);
        }
    }
}
