package vesence.module.impl.visuals.particular;

import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.math.random.Random;

public class WaterRippleParticle extends BillboardParticle {
    private final SpriteSet provider;

    protected WaterRippleParticle(ClientLevel world, double x, double y, double z, SpriteSet provider) {
        super(world, x, y, z, provider.getSprite(world.getRandom()));
        this.provider = provider;
        this.maxAge = 7;
        this.alpha = 0.2F;
        this.scale = 0.25F;
        this.setSprite(provider.getSprite(this.tickCount, this.maxAge));
    }

    @Override
    public void tick() {
        this.lastX = this.x;
        this.lastY = this.y;
        this.lastZ = this.z;
        if (this.tickCount++ >= this.maxAge) {
            this.markDead();
        } else {
            this.setSprite(this.provider.getSprite(this.tickCount, this.maxAge));
        }
    }

    @Override
    public RenderType getRenderType() {
        return RenderType.PARTICLE_ATLAS_TRANSLUCENT;
    }

    @Override
    public Rotator getRotator() {
        // Плоский горизонтальный квад на поверхности воды (как в оригинале),
        // а не биллборд лицом к камере.
        return (quaternion, camera, tickProgress) -> quaternion.rotationX((float) (Math.PI / 2.0));
    }

    public static class Factory implements ParticleFactory<SimpleParticleType> {
        private final SpriteSet provider;

        public Factory(SpriteSet provider) {
            this.provider = provider;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, Random random) {
            return new WaterRippleParticle(world, x, y, z, this.provider);
        }
    }
}
