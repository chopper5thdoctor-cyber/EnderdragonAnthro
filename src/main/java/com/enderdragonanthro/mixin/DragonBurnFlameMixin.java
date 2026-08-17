package com.enderdragonanthro.mixin;

import com.enderdragonanthro.EnderdragonAnthro;
import com.enderdragonanthro.client.DragonBurnClient;
import com.enderdragonanthro.client.DragonBurnMarker;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Purple flames on anything burning with ours.
 *
 * Both of the sprite lookups in renderFlame go through here — vanilla draws the
 * near layer from FIRE_0 and the far one from FIRE_1 — so the swap has to key
 * off which Material it was handed rather than picking one texture for both,
 * or the two layers would come out the same and the flames would flatten.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class DragonBurnFlameMixin {
    private static final Material FIRE_0 = new Material(
            InventoryMenu.BLOCK_ATLAS, EnderdragonAnthro.id("block/dragon_fire_0"));
    private static final Material FIRE_1 = new Material(
            InventoryMenu.BLOCK_ATLAS, EnderdragonAnthro.id("block/dragon_fire_1"));

    @Inject(method = "renderFlame", at = @At("HEAD"))
    private void enderdragonanthro$noteEntity(PoseStack poseStack, MultiBufferSource buffers,
                                              Entity entity, Quaternionf rotation, CallbackInfo ci) {
        DragonBurnMarker.current = entity;
    }

    @Inject(method = "renderFlame", at = @At("RETURN"))
    private void enderdragonanthro$clearEntity(PoseStack poseStack, MultiBufferSource buffers,
                                               Entity entity, Quaternionf rotation, CallbackInfo ci) {
        DragonBurnMarker.current = null;
    }

    @Redirect(method = "renderFlame",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/client/resources/model/Material;sprite()"
                                + "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;"))
    private TextureAtlasSprite enderdragonanthro$purpleFlames(Material material) {
        // The entity being drawn is not handed to this redirect, so the flag is
        // read from the one the dispatcher is mid-way through rendering.
        if (DragonBurnMarker.current != null && DragonBurnClient.isBurning(DragonBurnMarker.current)) {
            return (material == net.minecraft.client.resources.model.ModelBakery.FIRE_0
                    ? FIRE_0 : FIRE_1).sprite();
        }
        return material.sprite();
    }
}
