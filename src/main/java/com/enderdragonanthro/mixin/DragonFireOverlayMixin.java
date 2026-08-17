package com.enderdragonanthro.mixin;

import com.enderdragonanthro.EnderdragonAnthro;
import com.enderdragonanthro.block.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The fire that fills the screen, when the fire is ours.
 *
 * Vanilla's overlay is one sprite for every kind of burning — it asks whether
 * you are on fire, not what lit you — so standing in dragonfire filled the
 * screen with orange flames while purple ones burned at your feet.
 *
 * Which fire lit you is now said out loud, over DragonBurnPayload, so the
 * screen stays purple for the whole burn rather than only while your feet are
 * in the block. Standing in it is still checked as well, because a fire you
 * walked into this tick is purple before the packet lands.
 */
@Mixin(ScreenEffectRenderer.class)
public abstract class DragonFireOverlayMixin {
    private static final Material DRAGON_FIRE = new Material(
            InventoryMenu.BLOCK_ATLAS, EnderdragonAnthro.id("block/dragon_fire_1"));

    @Redirect(method = "renderFire",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/client/resources/model/Material;sprite()"
                                + "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;"))
    private static TextureAtlasSprite enderdragonanthro$purpleOverlay(Material material) {
        Player player = Minecraft.getInstance().player;
        if (player != null && (com.enderdragonanthro.client.DragonBurnClient.isBurning(player)
                || enderdragonanthro$standingInIt(player))) {
            return DRAGON_FIRE.sprite();
        }
        return material.sprite();
    }

    private static boolean enderdragonanthro$standingInIt(Player player) {
        // Feet and eyes both, because a two-block-tall fire is drawn from the
        // one you are looking out of rather than the one you are stood on.
        return player.level().getBlockState(player.blockPosition()).is(ModBlocks.DRAGON_FIRE)
                || player.level().getBlockState(player.blockPosition().above())
                        .is(ModBlocks.DRAGON_FIRE);
    }
}
