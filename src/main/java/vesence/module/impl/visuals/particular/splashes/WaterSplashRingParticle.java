package vesence.module.impl.visuals.particular.splashes;

import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.tags.FluidTags;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;

public class WaterSplashRingParticle extends SingleQuadParticle {
    private final SpriteSet provider;
    private final float width;

    protected WaterSplashRingParticle(ClientLevel level, double x, double y, double z, float width, SpriteSet provider) {
        super(level, x, y, z, provider.get(level.getRandom()));
        this.provider = provider;
        this.width = width;
        this.lifetime = 18;
        this.gravity = 0.0F;
        this.quadSize = Math.max(0.1F, width);
        this.setSprite(provider.get(this.age, this.lifetime));
    }

    @Override
    public SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    @Override
    public FacingCameraMode getFacingCameraMode() {
        return (quaternion, camera, tickProgress) -> quaternion.rotationX((float) (Math.PI / 2.0));
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
        this.setSprite(this.provider.get(this.age, this.lifetime));
        if (!this.level.getFluidState(BlockPos.containing(this.x, this.y, this.z)).is(FluidTags.WATER)) {
            this.remove();
        }
    }

    public static class Factory implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet provider;

        public Factory(SpriteSet provider) {
            this.provider = provider;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd, RandomSource random) {
            return new WaterSplashRingParticle(level, x, y, z, (float) xd, this.provider);
        }
    }
}
