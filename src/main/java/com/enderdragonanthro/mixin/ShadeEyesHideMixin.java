package com.enderdragonanthro.mixin;

import com.enderdragonanthro.ability.ShadeIdentity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.EnderMan;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * And the enderman's eyes with it.
 *
 * getRenderType only skips the base model; the eyes are a feature layer of
 * their own and would otherwise hang in the air where the hidden head used to
 * be. EyesLayer is the shared class — spiders and blazes come through here too
 * — so the guard is on the entity, not on the layer.
 */
@Mixin(EyesLayer.class)
public abstract class ShadeEyesHideMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void enderdragonanthro$hideShadeEyes(PoseStack poseStack, MultiBufferSource buffers,
                                                 int light, Entity entity, float limbSwing,
                                                 float limbSwingAmount, float partialTick,
                                                 float ageInTicks, float netHeadYaw,
                                                 float headPitch, CallbackInfo ci) {
        if (entity instanceof EnderMan enderman && ShadeIdentity.slotOf(enderman) >= 0) {
            ci.cancel();
        }
    }
}
