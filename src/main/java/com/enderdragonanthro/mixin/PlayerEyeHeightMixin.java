package com.enderdragonanthro.mixin;

import com.enderdragonanthro.config.DragonConfig;
import com.enderdragonanthro.transform.DragonFormManager;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Moves the eye — the red plane in F3+B — off vanilla's fixed 0.9 of the hitbox.
 *
 * That 0.9 is baked into Player.STANDING_DIMENSIONS as 1.62 against 1.8, and
 * EntityDimensions.scale() multiplies height and eyeHeight by the same factor,
 * so the ratio survives any amount of scaling. Nothing about it knows where the
 * model's eyes are painted, which is why moving the head in Blockbench never
 * moved the camera.
 *
 * Expressed against the dimensions' own height rather than an absolute, so it
 * lands correctly whether this fires before or after the scale is applied —
 * and applying it twice, if the call chain overrides itself, is a no-op.
 */
@Mixin(Player.class)
public abstract class PlayerEyeHeightMixin {
    @Inject(method = "getDimensions", at = @At("RETURN"), cancellable = true, require = 0)
    private void enderdragonanthro$eyeHeight(Pose pose,
                                             CallbackInfoReturnable<EntityDimensions> cir) {
        Player self = (Player) (Object) this;
        if (!DragonFormManager.isDragon(self)) {
            return;                                // human form is vanilla, untouched
        }
        double ratio = DragonConfig.eyeHeightRatio();
        if (Math.abs(ratio - 0.9) < 1.0e-4) {
            return;                                // configured to vanilla anyway
        }
        EntityDimensions dims = cir.getReturnValue();
        cir.setReturnValue(dims.withEyeHeight((float) (dims.height() * ratio)));
    }
}
