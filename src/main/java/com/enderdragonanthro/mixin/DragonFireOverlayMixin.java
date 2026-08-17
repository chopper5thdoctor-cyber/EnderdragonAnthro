package com.enderdragonanthro.mixin;

import com.enderdragonanthro.EnderdragonAnthro;
import com.enderdragonanthro.block.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.AABB;
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
    /**
     * Its own sheet, not the block's.
     *
     * The two want different things from the same drawing. The block sits on
     * the ground, so its flames have to start at the very bottom row of the
     * frame or the fire visibly floats. The overlay is pressed against your
     * face, where the bottom of the sprite is off the bottom of the screen
     * anyway and what matters is how far up the view the flames climb.
     *
     * Sharing one sheet meant every pixel spent on one cost the other, which
     * is how the block ended up hovering. This one is drawn two rows higher
     * and referenced by nothing else; the block keeps the grounded pair. It is
     * on the atlas because vanilla's blocks.json stitches the whole block
     * texture directory of every namespace, so no model has to point at it.
     */
    private static final Material DRAGON_FIRE = new Material(
            InventoryMenu.BLOCK_ATLAS, EnderdragonAnthro.id("block/dragon_fire_overlay"));

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

    /**
     * Whether any part of the player is inside dragonfire.
     *
     * The whole bounding box, not two named blocks. Feet-and-eyes missed the
     * common case outright: a dragon is eight blocks tall, so its feet block
     * and its eye block are nowhere near each other and the fire it is standing
     * in is neither of them. Walking the box is a handful of lookups and cannot
     * be wrong about which blocks the player occupies.
     */
    private static boolean enderdragonanthro$standingInIt(Player player) {
        AABB box = player.getBoundingBox().inflate(0.001);
        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            if (player.level().getBlockState(pos).is(ModBlocks.DRAGON_FIRE)) {
                return true;
            }
        }
        return false;
    }
}
