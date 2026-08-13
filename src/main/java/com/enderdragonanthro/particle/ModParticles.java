package com.enderdragonanthro.particle;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

import static com.enderdragonanthro.EnderdragonAnthro.id;

/**
 * Affection reads magenta here, not red — endermen are magenta-eyed, and a
 * vanilla heart particle over a shade looks like a tamed wolf rather than
 * something that answers to the Ender Dragon.
 */
public final class ModParticles {
    public static final SimpleParticleType PURPLE_HEART =
            Registry.register(BuiltInRegistries.PARTICLE_TYPE, id("purple_heart"),
                    FabricParticleTypes.simple());

    private ModParticles() {
    }

    /** Touching the class runs its registration; called from the initialiser. */
    public static void init() {
    }
}
