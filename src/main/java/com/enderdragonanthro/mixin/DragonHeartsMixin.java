package com.enderdragonanthro.mixin;

import com.enderdragonanthro.client.DragonHearts;
import com.enderdragonanthro.client.DragonHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Purple hearts while transformed.
 *
 * A tint will not do this. GuiGraphics.setColor MULTIPLIES, so it can only ever
 * darken vanilla's red hearts — there is no factor that puts blue into a pixel
 * that has none. The only way to a purple heart is a purple heart, so the mod
 * ships its own and swaps them in here.
 *
 * The swap is on the blit rather than on HeartType.getSprite, which would be
 * the more direct target: HeartType is package-private, so a redirect handler
 * cannot name it in its signature. Going through the sprite id instead costs
 * nothing and buys something — the last path segment is matched, so every
 * variant vanilla asks for is handled by name, and any this mod does not ship
 * falls through to vanilla's rather than vanishing.
 *
 * Which name maps to which sprite lives in {@link DragonHearts} rather than
 * here, because a mixin is dissolved into its target at load time and nothing
 * outside the running game can call into one. This class keeps only the part
 * that genuinely needs the game: whether the player looking at this HUD is
 * currently a dragon.
 */
@Mixin(Gui.class)
public abstract class DragonHeartsMixin {
    @Redirect(method = "renderHeart",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite"
                                + "(Lnet/minecraft/resources/ResourceLocation;IIII)V"))
    private void enderdragonanthro$purpleHearts(GuiGraphics graphics, ResourceLocation sprite,
                                                int x, int y, int width, int height) {
        Minecraft client = Minecraft.getInstance();
        ResourceLocation ours = client.player != null && DragonHud.isDragonForm(client.player)
                ? DragonHearts.swap(sprite) : null;
        graphics.blitSprite(ours != null ? ours : sprite, x, y, width, height);
    }
}
