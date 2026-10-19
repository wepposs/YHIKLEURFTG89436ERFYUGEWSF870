package vesence.module.impl.visuals.particular;

import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.random.Random;

public class CaveDustParticle extends BillboardParticle {
    private float acceleration;

    protected CaveDustParticle(ClientWorld world, double x, double y, double z, SpriteProvider provider) {
        super(world, x, y, z, provider.getSprite(world.getRandom()));
        float[] color = ParticularEffects.getCaveDustColor();
        this.setSprite(provider.getSprite(this.random));
        this.setColor(color[0], color[1], color[2]);
        this.scale *= 0.75F;
        this.maxAge = ParticularEffects.getCaveDustBaseMaxAge() + this.random.nextInt(40);
        this.velocityX = (this.random.nextDouble() - 0.5D) * 0.01D;
        this.velocityY = this.random.nextDouble() * 0.01D;
        this.velocityZ = (this.random.nextDouble() - 0.5D) * 0.01D;
        this.acceleration = (this.random.nextFloat() - 0.5F) * ParticularEffects.getCaveDustMaxAcceleration();
    }

    @Override
    public void tick() {
        this.lastX = this.x;
        this.lastY = this.y;
        this.lastZ = this.z;
        if (this.age++ >= this.maxAge) {
            this.markDead();
            return;
        }

        if (this.random.nextInt(Math.max(1, ParticularEffects.getCaveDustAccelChangeChance())) == 0) {
            this.acceleration = (this.random.nextFloat() - 0.5F) * ParticularEffects.getCaveDustMaxAcceleration();
        }

        this.velocityY += this.acceleration;
        this.move(this.velocityX, this.velocityY, this.velocityZ);
        this.velocityX *= 0.99D;
        this.velocityY *= 0.99D;
        this.velocityZ *= 0.99D;

        int fade = ParticularEffects.getCaveDustFadeDuration();
        if (this.age <= fade) {
            this.alpha = this.age / (float) fade;
        } else if (this.age > this.maxAge - fade) {
            this.alpha = (this.maxAge - this.age) / (float) fade;
        } else {
            this.alpha = 1.0F;
        }
    }

    @Override
    public RenderType getRenderType() {
        return RenderType.PARTICLE_ATLAS_TRANSLUCENT;
    }

    public static class Factory implements ParticleFactory<SimpleParticleType> {
        private final SpriteProvider provider;

        public Factory(SpriteProvider provider) {
            this.provider = provider;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientWorld world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, Random random) {
            return new CaveDustParticle(world, x, y, z, this.provider);
        }
    }
}
