package vesence.module.impl.visuals.particular.splashes;

import net.minecraft.client.particle.NoRenderParticle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import vesence.module.impl.visuals.particular.ParticularParticles;

public class WaterSplashEmitterParticle extends NoRenderParticle {
    private final float speed;
    private final float width;
    private final float height;

    protected WaterSplashEmitterParticle(ClientWorld world, double x, double y, double z, float width, float speed) {
        super(world, x, y, z);
        this.gravityStrength = 0.0F;
        this.maxAge = 24;
        this.speed = Math.min(2.0F, speed);
        this.width = width;
        this.height = this.speed / 2.0F + width / 3.0F;

        WaterCrownRenderer.spawn(this.world, x, y, z, width, this.height, false);
        WaterCrownRenderer.spawn(this.world, x, y, z, width, this.height, true);
        this.world.addParticleClient(ParticularParticles.WATER_SPLASH_RING, true, false, x, y, z, width, 0.0D, 0.0D);

        if (this.speed > 0.5F) {
            this.splash(width, (1.5F / 8.0F + this.speed / 8.0F) + width / 6.0F, 0.15F);
        } else {
            this.markDead();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.age == 8) {
            WaterCrownRenderer.spawn(this.world, this.x, this.y, this.z, this.width * 0.66F, this.height * 2.0F, false);
            WaterCrownRenderer.spawn(this.world, this.x, this.y, this.z, this.width * 0.66F, this.height * 2.0F, true);
            this.world.addParticleClient(ParticularParticles.WATER_SPLASH_RING, true, false, this.x, this.y, this.z, this.width * 0.66F, 0.0D, 0.0D);
            this.splash(this.width * 0.66F, (3.0F / 8.0F + this.speed / 8.0F) + this.width / 6.0F, 0.05F);
        }

        if (!this.world.getFluidState(BlockPos.ofFloored(this.x, this.y, this.z)).isIn(FluidTags.WATER)) {
            this.markDead();
        }
    }

    private void splash(float width, float speed, float spread) {
        for (int i = 0; i < width * 20.0F; ++i) {
            double xVel = (this.random.nextDouble() - this.random.nextDouble()) * spread;
            double yVel = speed * (1.0D + (this.random.nextDouble() - this.random.nextDouble()) * 0.25D);
            double zVel = (this.random.nextDouble() - this.random.nextDouble()) * spread;
            this.world.addParticleClient(ParticleTypes.FALLING_WATER, this.x + xVel / spread * width, this.y + 1.0D / 16.0D, this.z + zVel / spread * width, xVel, yVel, zVel);
        }
    }

    public static class Factory implements ParticleFactory<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientWorld world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, Random random) {
            return new WaterSplashEmitterParticle(world, x, y, z, (float) velocityX, (float) velocityY);
        }
    }
}
