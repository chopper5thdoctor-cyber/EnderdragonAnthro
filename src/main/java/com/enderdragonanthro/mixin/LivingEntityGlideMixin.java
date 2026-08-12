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
        } else {
            player.stopFallFlying();
            DragonFlight.clear(player);
        }
        ci.cancel();
    }
}
