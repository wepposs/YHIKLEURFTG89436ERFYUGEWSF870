package vesence.module.impl.visuals.particular;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.tags.FluidTags;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;

public class EnderBubbleParticle extends SingleQuadParticle {
    protected EnderBubbleParticle(ClientLevel level, double x, double y, double z, double motionX, double motionY, double motionZ, SpriteSet provider) {
        super(level, x, y, z, provider.get(level.getRandom()));
        this.setSprite(provider.get(this.random));
        this.setSize(0.02F, 0.02F);
        this.quadSize *= this.random.nextFloat() * 0.6F + 0.2F;
        this.xd = motionX * 0.2D + (Math.random() * 2.0D - 1.0D) * 0.02D;
        this.yd = motionY * 0.2D + (Math.random() * 2.0D - 1.0D) * 0.02D;
        this.zd = motionZ * 0.2D + (Math.random() * 2.0D - 1.0D) * 0.02D;
        this.lifetime = (int) (40.0D / (Math.random() * 0.8D + 0.2D));
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.yd += 0.005D;
        boolean expired = false;
        if (this.lifetime-- <= 0) {
            expired = true;
            this.remove();
        } else {
            this.move(this.xd, this.yd, this.zd);
            this.xd *= 0.85D;
            this.yd *= 0.85D;
            this.zd *= 0.85D;
            if (!this.level.getFluidState(BlockPos.containing(this.x, this.y, this.z)).is(FluidTags.WATER)) {
                expired = true;
                this.remove();
            }
        }

        if (expired && ParticularEffects.isEnabled()) {
            Particle bubble = Minecraft.getInstance().particleEngine.createParticle(ParticularParticles.ENDER_BUBBLE_POP, this.x, this.y, this.z, 0.0D, 0.0D, 0.0D);
            if (bubble != null) {
                bubble.scale(this.quadSize * 20.0F);
            }
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
            return new EnderBubbleParticle(level, x, y, z, xd, yd, zd, this.provider);
        }
    }
}
