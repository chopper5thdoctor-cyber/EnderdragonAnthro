package com.enderdragonanthro.mixin;

import com.enderdragonanthro.ability.DragonMinions;
import net.minecraft.world.entity.monster.EnderMan;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A shade holds its ground.
 *
 * Vanilla endermen blink about constantly — idly, when struck, when rained on
 * — which is fine for a wild mob and useless in a retinue you are trying to
 * keep with you. Every one of those paths runs through {@code teleport()}, so
 * refusing it there covers all of them at once.
 *
 * The court's own hops do not come through here: those call
 * {@code Entity.teleportTo} directly, so a Collect order still ranges as far
 * as it likes.
 */
@Mixin(EnderMan.class)
public abstract class EndermanTeleportMixin {
    @Inject(method = "teleport()Z", at = @At("HEAD"), cancellable = true)
    private void enderdragonanthro$stayPut(CallbackInfoReturnable<Boolean> cir) {
        if (DragonMinions.isShade((EnderMan) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
