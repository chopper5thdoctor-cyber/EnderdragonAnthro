package com.enderdragonanthro.mixin;

import com.enderdragonanthro.transform.DragonFormManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Canon: no mob is hostile to the Ender Dragon. Every targeting path —
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
}
