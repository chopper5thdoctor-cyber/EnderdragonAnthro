package com.enderdragonanthro.client.render;

import com.enderdragonanthro.EnderdragonAnthro;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * The orange sheet the homing crystal is drawn with.
 *
 * Built lazily and held here rather than in the mixin: a static initialiser
 * inside a mixin ends up merged into the vanilla renderer's own class setup,
 * which is a poor place to be touching render state.
 */
public final class HomingCrystalRender {
    private static final ResourceLocation TEXTURE =
            EnderdragonAnthro.id("textures/entity/homing_crystal.png");

    private static RenderType type;

    private HomingCrystalRender() {
    }

    public static RenderType type() {
        if (type == null) {
            type = RenderType.entityCutoutNoCull(TEXTURE);
        }
        return type;
    }
}
