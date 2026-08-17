package com.enderdragonanthro.mixin;

import com.enderdragonanthro.EnderdragonAnthro;
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
 */
@Mixin(Gui.class)
public abstract class DragonHeartsMixin {
    private static final String VANILLA = "hud/heart/";

    @Redirect(method = "renderHeart",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite"
                                + "(Lnet/minecraft/resources/ResourceLocation;IIII)V"))
    private void enderdragonanthro$purpleHearts(GuiGraphics graphics, ResourceLocation sprite,
                                                int x, int y, int width, int height) {
        graphics.blitSprite(enderdragonanthro$swap(sprite), x, y, width, height);
    }

    private static ResourceLocation enderdragonanthro$swap(ResourceLocation sprite) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || !DragonHud.isDragonForm(client.player)) {
            return sprite;
        }
        String path = sprite.getPath();
        if (!sprite.getNamespace().equals("minecraft") || !path.startsWith(VANILLA)) {
            return sprite;
        }
        // Only the shapes this mod actually draws. Poisoned, withered, frozen
        // and absorbing keep vanilla's, because a dragon being poisoned should
        // still read as poisoned rather than as one more shade of purple.
        String name = path.substring(VANILLA.length());
        return switch (name) {
            case "container", "container_blinking", "full", "full_blinking",
                 "half", "half_blinking" ->
                    EnderdragonAnthro.id(VANILLA + name);
            default -> sprite;
        };
    }
}
