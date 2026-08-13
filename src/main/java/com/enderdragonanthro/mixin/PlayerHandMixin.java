package com.enderdragonanthro.mixin;

import com.enderdragonanthro.client.render.DragonFirstPersonArm;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Swaps the first-person hand for the dragon's while transformed.
 *
 * These two methods are the whole first-person arm: the hand renderer does all
 * the placing and swinging and then calls one of them to actually draw
 * something. Taking them over means the bob, the swing and the item hold all
 * still come from vanilla — only the limb changes.
 *
 * Optional: if it misses, you get human arms in first person, which is what
 * you had before and nothing else breaks.
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerHandMixin {
    @Inject(method = "renderRightHand", at = @At("HEAD"), cancellable = true, require = 0)
    private void enderdragonanthro$rightClaw(PoseStack poseStack, MultiBufferSource buffers,
                                             int light, AbstractClientPlayer player,
                                             CallbackInfo ci) {
        if (DragonFirstPersonArm.render(poseStack, buffers, light, player, true)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderLeftHand", at = @At("HEAD"), cancellable = true, require = 0)
    private void enderdragonanthro$leftClaw(PoseStack poseStack, MultiBufferSource buffers,
                                            int light, AbstractClientPlayer player,
                                            CallbackInfo ci) {
        if (DragonFirstPersonArm.render(poseStack, buffers, light, player, false)) {
            ci.cancel();
        }
    }
}
