package vesence.module.impl.visuals.particular.leaves;

import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.math.random.Random;

public class SpinningLeafParticle extends LeafParticle {
    protected float angleFactor;

    protected SpinningLeafParticle(ClientLevel world, double x, double y, double z, double red, double green, double blue, SpriteSet provider) {
        super(world, x, y, z, red, green, blue, provider);
        this.angleFactor = (float) (Math.random() * Math.PI * 2.0D);
        this.zRotation = this.getAngle();
        this.lastZRotation = this.zRotation;
    }

    @Override
    protected float getAngle() {
        int offsetAge = Math.max(0, this.tickCount - FADE_IN_DURATION);
        return (this.angleFactor + offsetAge / (this.rotateFactor + (this.maxAge - offsetAge) / 100.0F) / 2.0F) * this.flippedDirection;
    }

    public static class Factory implements ParticleFactory<SimpleParticleType> {
        private final SpriteSet provider;

        public Factory(SpriteSet provider) {
            this.provider = provider;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, Random random) {
            return new SpinningLeafParticle(world, x, y, z, velocityX, velocityY, velocityZ, this.provider);
        }
    }
}
