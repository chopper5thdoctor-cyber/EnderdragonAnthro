package com.enderdragonanthro.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * The heart that rises off a happy enderman: swells as it appears, drifts up,
 * and fades. Same motion as the vanilla heart, drawn from the mod's own sheet.
 */
public class PurpleHeartParticle extends TextureSheetParticle {
    protected PurpleHeartParticle(ClientLevel level, double x, double y, double z,
                                  double xd, double yd, double zd) {
        super(level, x, y, z, 0.0, 0.0, 0.0);
        this.xd = xd * 0.01 + this.xd;
        this.yd = yd * 0.01 + this.yd + 0.1;
        this.zd = zd * 0.01 + this.zd;
        this.quadSize *= 1.6F;
        this.lifetime = 22;
        this.hasPhysics = false;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** Pops to full size over the first few ticks rather than appearing whole. */
    @Override
    public float getQuadSize(float partialTick) {
        return this.quadSize * Mth.clamp(
                ((float) this.age + partialTick) / (float) this.lifetime * 24.0F, 0.0F, 1.0F);
    }

    @Override
    public void tick() {
        super.tick();
        this.xd *= 0.86;
        this.yd *= 0.86;
        this.zd *= 0.86;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xd, double yd, double zd) {
            PurpleHeartParticle particle = new PurpleHeartParticle(level, x, y, z, xd, yd, zd);
            particle.pickSprite(this.sprites);
            return particle;
        }
    }
}
