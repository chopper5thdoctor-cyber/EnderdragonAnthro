package com.enderdragonanthro.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Reach vanilla's own shadow, rather than drawing a second one beside it.
 *
 * renderShadow is private and static, and everything interesting about a
 * shadow is in there: the radial falloff across the sprite, the per-block
 * brightness lookup, the refusal to fall on anything that is not a full
 * collision box. Reimplementing that would mean a shadow that almost matches
 * every other shadow in the game, which is worse than none.
 */
@Mixin(EntityRenderDispatcher.class)
public interface EntityRenderDispatcherInvoker {
    @Invoker("renderShadow")
    static void enderdragonanthro$renderShadow(PoseStack poseStack, MultiBufferSource buffers,
                                               Entity entity, float weight, float partialTick,
                                               LevelReader level, float radius) {
        throw new AssertionError();
    }
}
