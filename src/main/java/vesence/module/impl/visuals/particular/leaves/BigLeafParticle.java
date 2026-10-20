package vesence.module.impl.visuals.particular.leaves;

import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

public class BigLeafParticle extends LeafParticle {
    protected BigLeafParticle(ClientLevel level, double x, double y, double z, double rCol, double gCol, double bCol, SpriteSet provider) {
        super(level, x, y, z, rCol, gCol, bCol, provider);
        this.rotateFactor = 9.0F + this.random.nextFloat() * 3.0F;
        this.quadSize = 9.0F / 32.0F;
    }

    public static class Factory implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet provider;

        public Factory(SpriteSet provider) {
            this.provider = provider;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd, RandomSource random) {
            return new BigLeafParticle(level, x, y, z, xd, yd, zd, this.provider);
        }
    }
}
