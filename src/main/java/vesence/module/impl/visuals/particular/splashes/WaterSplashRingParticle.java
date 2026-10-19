package vesence.module.impl.visuals.particular.splashes;

import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.tags.FluidTags;
import net.minecraft.core.BlockPos;
import net.minecraft.util.math.random.Random;

public class WaterSplashRingParticle extends BillboardParticle {
    private final SpriteSet provider;
    private final float width;

    protected WaterSplashRingParticle(ClientLevel world, double x, double y, double z, float width, SpriteSet provider) {
        super(world, x, y, z, provider.getSprite(world.getRandom()));
        this.provider = provider;
        this.width = width;
        this.maxAge = 18;
        this.gravityStrength = 0.0F;
        this.scale = Math.max(0.1F, width);
        this.setSprite(provider.getSprite(this.tickCount, this.maxAge));
    }

    @Override
    public RenderType getRenderType() {
        return RenderType.PARTICLE_ATLAS_TRANSLUCENT;
    }

    @Override
    public Rotator getRotator() {
        return (quaternion, camera, tickProgress) -> quaternion.rotationX((float) (Math.PI / 2.0));
    }

    @Override
    public void tick() {
        this.lastX = this.x;
        this.lastY = this.y;
        this.lastZ = this.z;
        if (this.tickCount++ >= this.maxAge) {
            this.markDead();
            return;
        }
        this.setSprite(this.provider.getSprite(this.tickCount, this.maxAge));
        if (!this.world.getFluidState(BlockPos.ofFloored(this.x, this.y, this.z)).isIn(FluidTags.WATER)) {
            this.markDead();
        }
    }

    public static class Factory implements ParticleFactory<SimpleParticleType> {
        private final SpriteSet provider;

        public Factory(SpriteSet provider) {
            this.provider = provider;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, Random random) {
            return new WaterSplashRingParticle(world, x, y, z, (float) velocityX, this.provider);
        }
    }
}
