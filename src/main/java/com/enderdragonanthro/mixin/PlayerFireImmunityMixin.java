package com.enderdragonanthro.mixin;

import com.enderdragonanthro.transform.DragonFormManager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Canon: the dragon is immune to fire and lava. */
@Mixin(Player.class)
public abstract class PlayerFireImmunityMixin {
    @Inject(method = "fireImmune", at = @At("HEAD"), cancellable = true)
    private void enderdragonanthro$fireImmunity(CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player) (Object) this;
        if (DragonFormManager.isDragon(self)) {
            cir.setReturnValue(true);
        }
    }
}
