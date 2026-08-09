package com.enderdragonanthro.transform;

/**
 * Duck interface implemented onto {@link net.minecraft.world.entity.player.Player}
 * by {@code PlayerTransformDataMixin}. Holds the persistent "is a dragon" flag.
 */
public interface TransformAccess {
    boolean enderdragonanthro$isDragon();

    void enderdragonanthro$setDragon(boolean dragon);
}
