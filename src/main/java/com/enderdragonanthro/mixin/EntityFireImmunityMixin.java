package com.enderdragonanthro.mixin;

import com.enderdragonanthro.transform.DragonFormManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Canon: the dragon is immune to fire and lava. fireImmune() is declared
 * on Entity (Player never overrides it), so the mixin must target Entity —
 * injecting into a method the target class doesn't declare fails at load.
 */
@Mixin(Entity.class)
public abstract class EntityFireImmunityMixin {
    @Inject(method = "fireImmune", at = @At("HEAD"), cancellable = true)
    private void enderdragonanthro$fireImmunity(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player player && DragonFormManager.isDragon(player)) {
            cir.setReturnValue(true);
        }
    }
}
