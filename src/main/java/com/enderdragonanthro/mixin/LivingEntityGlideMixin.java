package com.enderdragonanthro.mixin;

import com.enderdragonanthro.ability.DragonFlight;
import com.enderdragonanthro.transform.DragonFormManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets a transformed dragon glide on its own wings.
 *
 * Vanilla clears the fall-flying flag every tick unless an elytra is worn.
 * Taking over that check — rather than reimplementing flight — means the
 * glide runs through the untouched elytra branch of LivingEntity.travel(),
 * so the physics are the real thing rather than an approximation.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityGlideMixin {
    @Inject(method = "updateFallFlying", at = @At("HEAD"), cancellable = true)
    private void enderdragonanthro$wingGlide(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof ServerPlayer player)
                || !DragonFormManager.isDragon(player)
                || !DragonFlight.wantsGlide(player)) {
            return;
        }
        boolean airborne = !player.onGround()
                && !player.isPassenger()
                && !player.hasEffect(MobEffects.LEVITATION);
        if (airborne) {
            player.startFallFlying();
            // A glide is not a fall, and vanilla only half agrees: travel()
            // resets fallDistance while you are climbing, and lets it run while
            // you are descending. An elytra player gets away with that because
            // they level out before landing. A dragon diving at ninety blocks a
            // second does not -- it arrives with a hundred and forty blocks of
            // fall banked, which is a fatal landing after the thirteen blocks
            // of safe fall are taken off it. Ten hearts and change, exactly as
            // reported, and nothing to do with walls or suffocation.
            //
            // Cleared here rather than caught at causeFallDamage, because this
            // runs inside the entity's own tick, before travel and before the
            // move that lands it. Nothing can arrive with a fall already
            // banked, so no grace window is needed and the ordering cannot
            // catch it out. Step off a cliff WITHOUT gliding and the fall is
            // still a fall.
            player.fallDistance = 0.0F;
        } else {
            player.stopFallFlying();
            DragonFlight.clear(player);
        }
        ci.cancel();
    }
}
