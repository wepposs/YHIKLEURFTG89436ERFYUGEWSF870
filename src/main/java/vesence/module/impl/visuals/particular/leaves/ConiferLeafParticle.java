package vesence.module.impl.visuals.particular.leaves;

import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.math.random.Random;

public class ConiferLeafParticle extends LeafParticle {
    protected ConiferLeafParticle(ClientLevel world, double x, double y, double z, double red, double green, double blue, SpriteSet provider) {
        super(world, x, y, z, red, green, blue, provider);
        this.gravityFactor = 0.3F;
    }

    public static class Factory implements ParticleFactory<SimpleParticleType> {
        private final SpriteSet provider;

        public Factory(SpriteSet provider) {
            this.provider = provider;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, Random random) {
            return new ConiferLeafParticle(world, x, y, z, velocityX, velocityY, velocityZ, this.provider);
        }
    }
}
