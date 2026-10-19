package vesence.module.impl.visuals.particular.splashes;

import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.math.random.Random;

public class WaterSplashFoamParticle extends WaterSplashParticle {
    protected WaterSplashFoamParticle(ClientLevel world, double x, double y, double z, float width, float height, SpriteSet provider) {
        super(world, x, y, z, width, height, provider);
        this.red = 1.0F;
        this.green = 1.0F;
        this.blue = 1.0F;
    }

    public static class Factory implements ParticleFactory<SimpleParticleType> {
        private final SpriteSet provider;

        public Factory(SpriteSet provider) {
            this.provider = provider;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, Random random) {
            return new WaterSplashFoamParticle(world, x, y, z, (float) velocityX, (float) velocityY, this.provider);
        }
    }
}
