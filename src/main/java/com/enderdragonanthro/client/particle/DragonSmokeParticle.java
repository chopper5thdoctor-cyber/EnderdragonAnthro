package com.enderdragonanthro.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.LargeSmokeParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Vanilla's fire smoke, with the grey taken off it. Nothing else changed.
 *
 * This extended SmokeParticle, and that was the whole problem with the column:
 * a burning block does not spawn ParticleTypes.SMOKE. BaseFireBlock.animateTick
 * spawns LARGE_SMOKE, which is the same particle at a quadSizeMultiplier of
 * 2.5. Matching the small one made a smoke two and a half times too short in
 * every dimension, and the rise that comes with it looked like nothing because
 * the thing rising was the size of a spark.
 *
 * Extending the large one instead means the gravity, the friction, the drift,
 * the lifetime and the way it walks its frames by age are all vanilla's own
 * numbers, byte for byte — so the column climbs exactly as far as the orange
 * one climbing beside it. The only difference left is which sprites it uses.
 *
 * The colour still has to be undone. SmokeParticle multiplies its sprites by
 * 0.1 grey because Mojang's eight frames are white masks and the colour is
 * applied at draw time; run purple through that and it comes out near black.
 * White gives our frames back, and they carry their own colour.
 */
public class DragonSmokeParticle extends LargeSmokeParticle {
    protected DragonSmokeParticle(ClientLevel level, double x, double y, double z,
                                  double xSpeed, double ySpeed, double zSpeed,
                                  SpriteSet sprites) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
        setColor(1.0F, 1.0F, 1.0F);
    }

    @Override
    public ParticleRenderType getRenderType() {
        // Translucent rather than opaque: these frames have a soft edge, and
        // the cutout type vanilla's white masks use would harden it to a
        // staircase.
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            return new DragonSmokeParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
        }
    }
}
