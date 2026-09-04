package com.enderdragonanthro.mixin;

import com.enderdragonanthro.client.DragonCameraView;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Camera view does not get paused out of shot.
 *
 * ## What was happening
 *
 * {@code GameRenderer.render} opens the pause menu half a second after the
 * window loses focus:
 *
 * <pre>
 *   if (!minecraft.isWindowActive() &amp;&amp; options.pauseOnLostFocus &amp;&amp; …) {
 *       if (Util.getMillis() - this.lastActiveTime &gt; 500L) {
 *           this.minecraft.pauseGame(false);
 *       }
 *   }
 * </pre>
 *
 * Which is exactly what clicking on the other client does. {@link
 * DragonCameraView} was closing the screen again on the next tick, and that is
 * a fight rather than a fix: the check runs every FRAME, {@code lastActiveTime}
 * stays stale for as long as the window is unfocused, so the menu came straight
 * back. The visible result was a pause screen that would not go away, on the
 * one window whose whole job is to have nothing on it.
 *
 * ## Why here rather than the option
 *
 * Setting {@code options.pauseOnLostFocus = false} would also work and needs no
 * mixin — that field is what the branch above reads. It is a persisted user
 * setting, though, and a crash while camera view was open would leave it
 * written to options.txt turned off, with nothing to say why. Cancelling the
 * call touches nothing the player owns and ends when camera view does.
 *
 * ## The other caller
 *
 * {@code KeyboardHandler} calls {@code pauseGame} too, for the Escape key.
 * Cancelling that one is correct as well and not a side effect worth avoiding:
 * inside camera view Escape means "leave camera view", which is what
 * {@link DragonCameraView#tick} does with it.
 */
@Mixin(Minecraft.class)
public class CameraViewPauseMixin {
    @Inject(method = "pauseGame", at = @At("HEAD"), cancellable = true)
    private void enderdragonanthro$stayInShot(boolean pauseOnly, CallbackInfo callback) {
        if (DragonCameraView.active()) {
            callback.cancel();
        }
    }
}
