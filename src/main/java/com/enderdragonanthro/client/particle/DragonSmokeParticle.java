package com.enderdragonanthro.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SmokeParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Vanilla's smoke, with the tint taken off it.
 *
 * SmokeParticle passes {@code 0.1f, 0.1f, 0.1f} down to BaseAshSmokeParticle,
 * because Mojang's eight frames are pure white masks and the grey is applied at
 * draw time rather than painted. Reusing the provider directly on coloured
 * sprites therefore multiplies them by a tenth, and a purple through that comes
 * out very near black — which is what shipped, and why this class exists.
 *
 * Setting the colour to white gives the sprites back. Everything else about the
 * particle is vanilla's: the rise, the drift, the shrink, the lifetime, the way
 * it walks its frames by age. Only the colour was ever in the way, and our
 * frames can carry it better than a flat multiply can — smoke off a flame is
 * brighter at its heart than at its edge.
 */
public class DragonSmokeParticle extends SmokeParticle {
    protected DragonSmokeParticle(ClientLevel level, double x, double y, double z,
                                  double xSpeed, double ySpeed, double zSpeed,
                                  SpriteSet sprites) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed, 1.0F, sprites);
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
