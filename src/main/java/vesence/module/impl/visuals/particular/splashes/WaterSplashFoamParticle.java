package vesence.module.impl.visuals.particular.splashes;

import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

public class WaterSplashFoamParticle extends WaterSplashParticle {
    protected WaterSplashFoamParticle(ClientLevel level, double x, double y, double z, float width, float height, SpriteSet provider) {
        super(level, x, y, z, width, height, provider);
        this.rCol = 1.0F;
        this.gCol = 1.0F;
        this.bCol = 1.0F;
    }

    public static class Factory implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet provider;

        public Factory(SpriteSet provider) {
            this.provider = provider;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd, RandomSource random) {
            return new WaterSplashFoamParticle(level, x, y, z, (float) xd, (float) yd, this.provider);
        }
    }
}
