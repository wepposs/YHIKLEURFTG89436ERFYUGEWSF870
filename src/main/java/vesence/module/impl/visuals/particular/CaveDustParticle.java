package vesence.module.impl.visuals.particular;

import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

public class CaveDustParticle extends SingleQuadParticle {
    private float acceleration;

    protected CaveDustParticle(ClientLevel level, double x, double y, double z, SpriteSet provider) {
        super(level, x, y, z, provider.get(level.getRandom()));
        float[] color = ParticularEffects.getCaveDustColor();
        this.setSprite(provider.get(this.random));
        this.setColor(color[0], color[1], color[2]);
        this.quadSize *= 0.75F;
        this.lifetime = ParticularEffects.getCaveDustBaseMaxAge() + this.random.nextInt(40);
        this.xd = (this.random.nextDouble() - 0.5D) * 0.01D;
        this.yd = this.random.nextDouble() * 0.01D;
        this.zd = (this.random.nextDouble() - 0.5D) * 0.01D;
        this.acceleration = (this.random.nextFloat() - 0.5F) * ParticularEffects.getCaveDustMaxAcceleration();
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }

        if (this.random.nextInt(Math.max(1, ParticularEffects.getCaveDustAccelChangeChance())) == 0) {
            this.acceleration = (this.random.nextFloat() - 0.5F) * ParticularEffects.getCaveDustMaxAcceleration();
        }

        this.yd += this.acceleration;
        this.move(this.xd, this.yd, this.zd);
        this.xd *= 0.99D;
        this.yd *= 0.99D;
        this.zd *= 0.99D;

        int fade = ParticularEffects.getCaveDustFadeDuration();
        if (this.age <= fade) {
            this.alpha = this.age / (float) fade;
        } else if (this.age > this.lifetime - fade) {
            this.alpha = (this.lifetime - this.age) / (float) fade;
        } else {
            this.alpha = 1.0F;
        }
    }

    @Override
    public SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    public static class Factory implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet provider;

        public Factory(SpriteSet provider) {
            this.provider = provider;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd, RandomSource random) {
            return new CaveDustParticle(level, x, y, z, this.provider);
        }
    }
}
