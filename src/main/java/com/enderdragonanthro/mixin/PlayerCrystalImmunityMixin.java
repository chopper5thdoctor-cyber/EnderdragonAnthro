package com.enderdragonanthro.mixin;

import com.enderdragonanthro.transform.DragonFormManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * End crystals cannot hurt the dragon.
 *
 * The real dragon circles at speed and is rarely beside its own crystals; an
 * anthro walks among them, so the same blast that is a non-issue for the boss
 * would be a constant self-inflicted wound. Immunity keeps Crystal Link usable.
 * Summoned shades get no such protection — they are standing too close.
 */
@Mixin(Player.class)
public abstract class PlayerCrystalImmunityMixin {
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void enderdragonanthro$crystalProof(DamageSource source, float amount,
                                                CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player) (Object) this;
        if (!DragonFormManager.isDragon(self)) {
            return;
        }
        if (source.getDirectEntity() instanceof EndCrystal
                || source.getEntity() instanceof EndCrystal) {
            cir.setReturnValue(false);
        }
    }
}
