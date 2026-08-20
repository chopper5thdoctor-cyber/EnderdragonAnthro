package com.enderdragonanthro.mixin;

import com.enderdragonanthro.client.DragonSightClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The override that was actually deciding it.
 *
 * DragonSightHealthMixin answers shouldShowName on EntityRenderer, which is the
 * class that calls it — but the call is virtual, and LivingEntityRenderer
 * overrides the method:
 *
 * <pre>
 * super.shouldShowName(entity) && (entity.shouldShowName()
 *         || entity.hasCustomName() && entity == dispatcher.crosshairPickEntity)
 * </pre>
 *
 * So every mob went to the override, and the override ANDs the base answer with
 * a second condition that a pig never satisfies. Answering the base method true
 * changed nothing at all; it was being multiplied by false a line later.
 *
 * Answered here instead, at the front of the override, which is the method
 * every living thing's renderer actually runs — players included, since
 * PlayerRenderer extends this too.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class DragonSightNameMixin {
    /** How far off a mob is still worth reading. Vanilla's own cut is 64. */
    private static final double RANGE = 48.0;

    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;)Z",
            at = @At("HEAD"), cancellable = true)
    private void enderdragonanthro$nameEverything(LivingEntity entity,
                                                  CallbackInfoReturnable<Boolean> cir) {
        Minecraft client = Minecraft.getInstance();
        if (DragonSightClient.isOpen()
                && client.player != null
                && entity != client.player
                && entity.distanceToSqr(client.player) <= RANGE * RANGE) {
            cir.setReturnValue(true);
        }
    }
}
