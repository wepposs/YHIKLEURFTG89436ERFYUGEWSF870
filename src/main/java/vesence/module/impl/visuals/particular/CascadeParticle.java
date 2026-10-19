package vesence.module.impl.visuals.particular;

import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

public class CascadeParticle extends BillboardParticle {
    private final SpriteProvider provider;

    protected CascadeParticle(ClientWorld world, double x, double y, double z, SpriteProvider provider) {
        super(world, x, y, z, provider.getSprite(world.getRandom()));
        this.provider = provider;
        this.maxAge = 9;
        this.scale = 1.0F;
        this.gravityStrength = 0.4F;
        this.velocityX = this.random.nextDouble() * 0.25D - 0.125D;
        this.velocityZ = this.random.nextDouble() * 0.25D - 0.125D;
        this.setSprite(provider.getSprite(this.age, this.maxAge));
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
            this.setSprite(this.provider.getSprite(this.age, this.maxAge));
        }
    }

    private void removeIfInsideSolidBlock() {
        BlockPos pos = BlockPos.ofFloored(this.x, this.y, this.z);
        if (this.world.getBlockState(pos).isOpaque()) {
            this.alpha = 0.0F;
            this.markDead();
        }
    }

    @Override
    public RenderType getRenderType() {
        return RenderType.PARTICLE_ATLAS_OPAQUE;
    }

    public static class Factory implements ParticleFactory<SimpleParticleType> {
        private final SpriteProvider provider;

        public Factory(SpriteProvider provider) {
            this.provider = provider;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientWorld world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, Random random) {
            return new CascadeParticle(world, x, y, z, this.provider);
        }
    }
}
