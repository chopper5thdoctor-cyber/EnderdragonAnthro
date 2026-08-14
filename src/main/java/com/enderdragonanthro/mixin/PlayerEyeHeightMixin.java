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
 *
 * The hook is getDefaultDimensions, not getDimensions. Player does not declare
 * getDimensions at all: LivingEntity does, and it is final. getDefaultDimensions
 * is what Player overrides, and LivingEntity scales its result afterwards — which
 * is harmless here, because scale() multiplies height and eyeHeight by the same
 * factor and this sets a ratio between them.
 *
 * This targeted getDimensions for several builds. Nothing said so: mixin only
 * warns when a target cannot be remapped, the build stays green, and require = 0
 * turned the miss into silence. It is 1 now, so a mapping change breaks the build
 * rather than the camera.
 */
@Mixin(Player.class)
public abstract class PlayerEyeHeightMixin {
    @Inject(method = "getDefaultDimensions", at = @At("RETURN"), cancellable = true)
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
