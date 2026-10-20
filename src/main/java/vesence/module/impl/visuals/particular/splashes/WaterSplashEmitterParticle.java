package vesence.module.impl.visuals.particular.splashes;

import net.minecraft.client.particle.NoRenderParticle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.tags.FluidTags;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import vesence.module.impl.visuals.particular.ParticularParticles;

public class WaterSplashEmitterParticle extends NoRenderParticle {
    private final float speed;
    private final float width;
    private final float height;

    protected WaterSplashEmitterParticle(ClientLevel level, double x, double y, double z, float width, float speed) {
        super(level, x, y, z);
        this.gravity = 0.0F;
        this.lifetime = 24;
        this.speed = Math.min(2.0F, speed);
        this.width = width;
        this.height = this.speed / 2.0F + width / 3.0F;

        WaterCrownRenderer.spawn(this.level, x, y, z, width, this.height, false);
        WaterCrownRenderer.spawn(this.level, x, y, z, width, this.height, true);
        this.level.addParticle(ParticularParticles.WATER_SPLASH_RING, true, false, x, y, z, width, 0.0D, 0.0D);

        if (this.speed > 0.5F) {
            this.splash(width, (1.5F / 8.0F + this.speed / 8.0F) + width / 6.0F, 0.15F);
        } else {
            this.remove();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.age == 8) {
            WaterCrownRenderer.spawn(this.level, this.x, this.y, this.z, this.width * 0.66F, this.height * 2.0F, false);
            WaterCrownRenderer.spawn(this.level, this.x, this.y, this.z, this.width * 0.66F, this.height * 2.0F, true);
            this.level.addParticle(ParticularParticles.WATER_SPLASH_RING, true, false, this.x, this.y, this.z, this.width * 0.66F, 0.0D, 0.0D);
            this.splash(this.width * 0.66F, (3.0F / 8.0F + this.speed / 8.0F) + this.width / 6.0F, 0.05F);
        }

        if (!this.level.getFluidState(BlockPos.containing(this.x, this.y, this.z)).is(FluidTags.WATER)) {
            this.remove();
        }
    }

    private void splash(float width, float speed, float spread) {
        for (int i = 0; i < width * 20.0F; ++i) {
            double xVel = (this.random.nextDouble() - this.random.nextDouble()) * spread;
            double yVel = speed * (1.0D + (this.random.nextDouble() - this.random.nextDouble()) * 0.25D);
            double zVel = (this.random.nextDouble() - this.random.nextDouble()) * spread;
            this.level.addParticle(ParticleTypes.FALLING_WATER, this.x + xVel / spread * width, this.y + 1.0D / 16.0D, this.z + zVel / spread * width, xVel, yVel, zVel);
        }
    }

    public static class Factory implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd, RandomSource random) {
            return new WaterSplashEmitterParticle(level, x, y, z, (float) xd, (float) yd);
        }
    }
}
