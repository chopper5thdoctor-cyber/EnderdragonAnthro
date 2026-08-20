package com.enderdragonanthro.mixin;

import com.enderdragonanthro.client.render.DragonSightRender;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The violet wash, laid over the world and under the HUD.
 *
 * HudRenderCallback would have been the obvious place and is the wrong one: it
 * fires at the END of Gui.render, so a full-screen fill from there goes over
 * the hotbar, the hearts and the hunger — a tint you cannot read your own
 * health through is not a sight, it is a blindfold. The head of Gui.render is
 * after the world and before any of that.
 */
@Mixin(Gui.class)
public abstract class DragonSightTintMixin {
    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void enderdragonanthro$sightTint(GuiGraphics graphics, DeltaTracker delta,
                                             CallbackInfo ci) {
        DragonSightRender.tint(graphics);
    }
}
