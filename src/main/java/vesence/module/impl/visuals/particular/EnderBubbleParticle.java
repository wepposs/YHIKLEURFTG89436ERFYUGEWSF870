package vesence.module.impl.visuals.particular;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.tags.FluidTags;
import net.minecraft.core.BlockPos;
import net.minecraft.util.math.random.Random;

public class EnderBubbleParticle extends BillboardParticle {
    protected EnderBubbleParticle(ClientLevel world, double x, double y, double z, double motionX, double motionY, double motionZ, SpriteSet provider) {
        super(world, x, y, z, provider.getSprite(world.getRandom()));
        this.setSprite(provider.getSprite(this.random));
        this.setBoundingBoxSpacing(0.02F, 0.02F);
        this.scale *= this.random.nextFloat() * 0.6F + 0.2F;
        this.velocityX = motionX * 0.2D + (Math.random() * 2.0D - 1.0D) * 0.02D;
        this.velocityY = motionY * 0.2D + (Math.random() * 2.0D - 1.0D) * 0.02D;
        this.velocityZ = motionZ * 0.2D + (Math.random() * 2.0D - 1.0D) * 0.02D;
        this.maxAge = (int) (40.0D / (Math.random() * 0.8D + 0.2D));
    }

    @Override
    public void tick() {
        this.lastX = this.x;
        this.lastY = this.y;
        this.lastZ = this.z;
        this.velocityY += 0.005D;
        boolean expired = false;
        if (this.maxAge-- <= 0) {
            expired = true;
            this.markDead();
        } else {
            this.move(this.velocityX, this.velocityY, this.velocityZ);
            this.velocityX *= 0.85D;
            this.velocityY *= 0.85D;
            this.velocityZ *= 0.85D;
            if (!this.world.getFluidState(BlockPos.ofFloored(this.x, this.y, this.z)).isIn(FluidTags.WATER)) {
                expired = true;
                this.markDead();
            }
        }

        if (expired && ParticularEffects.isEnabled()) {
            Particle bubble = Minecraft.getInstance().particleManager.addParticle(ParticularParticles.ENDER_BUBBLE_POP, this.x, this.y, this.z, 0.0D, 0.0D, 0.0D);
            if (bubble != null) {
                bubble.scale(this.scale * 20.0F);
            }
        }
    }

    @Override
    public RenderType getRenderType() {
        return RenderType.PARTICLE_ATLAS_OPAQUE;
    }

    public static class Factory implements ParticleFactory<SimpleParticleType> {
        private final SpriteSet provider;

        public Factory(SpriteSet provider) {
            this.provider = provider;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, Random random) {
            return new EnderBubbleParticle(world, x, y, z, velocityX, velocityY, velocityZ, this.provider);
        }
    }
}
