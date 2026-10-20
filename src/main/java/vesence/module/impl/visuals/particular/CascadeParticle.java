package vesence.module.impl.visuals.particular;

import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;

public class CascadeParticle extends SingleQuadParticle {
    private final SpriteSet provider;

    protected CascadeParticle(ClientLevel level, double x, double y, double z, SpriteSet provider) {
        super(level, x, y, z, provider.get(level.getRandom()));
        this.provider = provider;
        this.lifetime = 9;
        this.quadSize = 1.0F;
        this.gravity = 0.4F;
        this.xd = this.random.nextDouble() * 0.25D - 0.125D;
        this.zd = this.random.nextDouble() * 0.25D - 0.125D;
        this.setSprite(provider.get(this.age, this.lifetime));
        this.removeIfInsideSolidBlock();
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.isAlive()) {
            return;
        }
        this.removeIfInsideSolidBlock();
        if (this.isAlive()) {
            this.setSprite(this.provider.get(this.age, this.lifetime));
        }
    }

    private void removeIfInsideSolidBlock() {
        BlockPos pos = BlockPos.containing(this.x, this.y, this.z);
        if (this.level.getBlockState(pos).canOcclude()) {
            this.alpha = 0.0F;
            this.remove();
        }
    }

    @Override
    public SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.OPAQUE;
    }

    public static class Factory implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet provider;

        public Factory(SpriteSet provider) {
            this.provider = provider;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd, RandomSource random) {
            return new CascadeParticle(level, x, y, z, this.provider);
        }
    }
}
