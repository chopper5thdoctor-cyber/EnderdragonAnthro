package com.enderdragonanthro.mixin;

import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.UUID;

/**
 * How many boss bars are on screen, so something can be put under them.
 *
 * There is no public way to ask. The overlay exposes shouldPlayMusic and
 * shouldDarkenScreen and nothing about its contents, and the count is what
 * decides the height: vanilla starts at y=12 and steps 19 down per bar, so
 * anything that wants to sit below the stack has to know how tall the stack is.
 */
@Mixin(BossHealthOverlay.class)
public interface BossOverlayAccessor {
    @Accessor("events")
    Map<UUID, LerpingBossEvent> enderdragonanthro$events();
}
