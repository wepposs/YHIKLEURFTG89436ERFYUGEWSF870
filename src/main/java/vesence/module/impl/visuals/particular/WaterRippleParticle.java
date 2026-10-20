package vesence.module.impl.visuals.particular;

import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

public class WaterRippleParticle extends SingleQuadParticle {
    private final SpriteSet provider;

    protected WaterRippleParticle(ClientLevel level, double x, double y, double z, SpriteSet provider) {
        super(level, x, y, z, provider.get(level.getRandom()));
        this.provider = provider;
        this.lifetime = 7;
        this.alpha = 0.2F;
        this.quadSize = 0.25F;
        this.setSprite(provider.get(this.age, this.lifetime));
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
        } else {
            this.setSprite(this.provider.get(this.age, this.lifetime));
        }
    }

    @Override
    public SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    @Override
    public FacingCameraMode getFacingCameraMode() {
        // Плоский горизонтальный квад на поверхности воды (как в оригинале),
        // а не биллборд лицом к камере.
        return (quaternion, camera, tickProgress) -> quaternion.rotationX((float) (Math.PI / 2.0));
    }

    public static class Factory implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet provider;

        public Factory(SpriteSet provider) {
            this.provider = provider;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd, RandomSource random) {
            return new WaterRippleParticle(level, x, y, z, this.provider);
        }
    }
}
