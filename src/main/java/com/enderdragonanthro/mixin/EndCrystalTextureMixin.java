package com.enderdragonanthro.mixin;

import com.enderdragonanthro.ability.HomingCrystals;
import com.enderdragonanthro.client.render.HomingCrystalRender;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Paints the homing crystal orange so it reads apart from a live end crystal.
 *
 * It has to be the buffer that is swapped, not the texture: the renderer holds
 * one {@code static final RenderType} built at class-load and never asks
 * {@code getTextureLocation} a thing, so overriding that method does nothing at
 * all. Only the first {@code getBuffer} call in {@code render} is the crystal
 * itself — the beam to the dragon is drawn from a separate method.
 *
 * Cosmetic only, so this stays optional: if the mapping ever shifts, the
 * anchor renders as an ordinary crystal instead of the game refusing to start.
 */
@Mixin(EndCrystalRenderer.class)
public abstract class EndCrystalTextureMixin {
    @Redirect(
            method = "render(Lnet/minecraft/world/entity/boss/enderdragon/EndCrystal;FF"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", ordinal = 0,
                    target = "Lnet/minecraft/client/renderer/MultiBufferSource;"
                            + "getBuffer(Lnet/minecraft/client/renderer/RenderType;)"
                            + "Lcom/mojang/blaze3d/vertex/VertexConsumer;"),
            require = 0)
    private VertexConsumer enderdragonanthro$orange(MultiBufferSource buffers, RenderType original,
                                                    EndCrystal crystal, float entityYaw,
                                                    float partialTick, PoseStack poseStack,
                                                    MultiBufferSource source, int light) {
        return buffers.getBuffer(HomingCrystals.isHoming(crystal)
                ? HomingCrystalRender.type()
                : original);
    }
}
