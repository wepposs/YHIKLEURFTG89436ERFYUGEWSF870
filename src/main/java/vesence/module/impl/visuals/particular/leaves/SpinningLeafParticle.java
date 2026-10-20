package vesence.module.impl.visuals.particular.leaves;

import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

public class SpinningLeafParticle extends LeafParticle {
    protected float angleFactor;

    protected SpinningLeafParticle(ClientLevel level, double x, double y, double z, double rCol, double gCol, double bCol, SpriteSet provider) {
        super(level, x, y, z, rCol, gCol, bCol, provider);
        this.angleFactor = (float) (Math.random() * Math.PI * 2.0D);
        this.roll = this.getAngle();
        this.oRoll = this.roll;
    }

    @Override
    protected float getAngle() {
        int offsetAge = Math.max(0, this.age - FADE_IN_DURATION);
        return (this.angleFactor + offsetAge / (this.rotateFactor + (this.lifetime - offsetAge) / 100.0F) / 2.0F) * this.flippedDirection;
    }

    public static class Factory implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet provider;

        public Factory(SpriteSet provider) {
            this.provider = provider;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd, RandomSource random) {
            return new SpinningLeafParticle(level, x, y, z, xd, yd, zd, this.provider);
        }
    }
}
