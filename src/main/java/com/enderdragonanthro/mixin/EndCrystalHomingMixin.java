package com.enderdragonanthro.mixin;

import com.enderdragonanthro.ability.HomingCrystals;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A homing crystal shatters instead of detonating. */
@Mixin(EndCrystal.class)
public abstract class EndCrystalHomingMixin {
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void enderdragonanthro$shatterQuietly(DamageSource source, float amount,
                                                  CallbackInfoReturnable<Boolean> cir) {
        EndCrystal self = (EndCrystal) (Object) this;
        if (!HomingCrystals.isHoming(self)) {
            return;
        }
        if (self.level() instanceof ServerLevel level && !self.isRemoved()) {
            HomingCrystals.shatter(level, self);
        }
        cir.setReturnValue(true);
    }
}
