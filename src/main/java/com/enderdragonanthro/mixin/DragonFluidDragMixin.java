package com.enderdragonanthro.mixin;

import com.enderdragonanthro.EnderdragonAnthro;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Water and lava do not get to stop something moving that fast.
 *
 * LivingEntity.travel picks one of three branches in order — swimming, lava,
 * then elytra — and the first two are both guarded by isAffectedByFluids. So
 * clipping a lake at ninety blocks a second dropped the whole flight into swim
 * physics for a tick and killed it. Answering no here lets travel fall through
 * to the branch it should have been in, and costs nothing else: the method is
 * consulted for exactly this choice.
 *
 * The test is speed rather than the Explosive Intent toggle, because that is
 * what was asked for and it happens to be self-limiting. Unarmed flight settles
 * near 2.7 a tick and a vanilla elytra dive approaches 3.92 without reaching
 * it, since falling is its asymptote — so "faster than falling" is a line only
 * an armed dragon crosses, and it needs no flag to say so.
 *
 * Both sides run travel, so both must agree or the client and server will
 * disagree about where you are. The dragon is recognised by the scale modifier,
 * which is a synced attribute, rather than by the server-side form flag.
 */
@Mixin(LivingEntity.class)
public abstract class DragonFluidDragMixin {
    /** Terminal velocity: 0.08 a tick against 0.98 drag settles here. */
    private static final double FREE_FALL = 3.92;

    @Inject(method = "isAffectedByFluids", at = @At("HEAD"), cancellable = true)
    private void enderdragonanthro$outrunFluids(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof Player player) || !player.isFallFlying()) {
            return;
        }
        AttributeInstance scale = player.getAttribute(Attributes.SCALE);
        if (scale == null || scale.getModifier(EnderdragonAnthro.id("dragon_scale")) == null) {
            return;
        }
        if (player.getDeltaMovement().length() > FREE_FALL) {
            cir.setReturnValue(false);
        }
    }
}
