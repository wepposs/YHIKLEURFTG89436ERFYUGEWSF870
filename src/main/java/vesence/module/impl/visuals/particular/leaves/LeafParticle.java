package vesence.module.impl.visuals.particular.leaves;

import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.tags.FluidTags;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import vesence.module.impl.visuals.particular.ParticularEffects;
import vesence.module.impl.visuals.particular.ParticularParticles;

public class LeafParticle extends SingleQuadParticle {
    protected static final int FADE_IN_DURATION = 10;
    protected static final int FADE_OUT_DURATION = 100;

    protected float rotateFactor;
    protected float gravityFactor = 0.075F;
    protected final boolean flippedSprite;
    protected final int flippedDirection;
    protected boolean expiring;

    protected LeafParticle(ClientLevel level, double x, double y, double z, double rCol, double gCol, double bCol, SpriteSet provider) {
        super(level, x, y, z, provider.get(level.getRandom()));
        this.setSprite(provider.get(this.random));
        this.hasPhysics = true;
        this.gravity = 0.0F;
        this.lifetime = 200;
        this.xd = 0.0D;
        this.yd = 0.0D;
        this.zd = 0.0D;
        this.alpha = 0.0F;
        this.rCol = (float) rCol;
        this.gCol = (float) gCol;
        this.bCol = (float) bCol;
        this.rotateFactor = 6.0F + this.random.nextFloat() * 3.0F;
        this.flippedSprite = this.random.nextBoolean();
        this.flippedDirection = this.random.nextBoolean() ? 1 : -1;
        this.quadSize = 7.0F / 32.0F;
    }

    protected float getAngle() {
        int time = this.age - FADE_IN_DURATION;
        float amplitude = Math.max(0.0F, Math.min(30.0F, time)) / 30.0F;
        return (float) Math.sin(time / this.rotateFactor) * amplitude * 0.5F * this.flippedDirection;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.oRoll = this.roll;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }

        if (this.age <= FADE_IN_DURATION) {
            this.yd = 0.0D;
        } else if (!this.expiring && (this.onGround || Math.abs(this.yd) < 1.0E-5D)) {
            this.expiring = true;
            this.age = this.lifetime - FADE_OUT_DURATION;
            this.y += 0.01D;
            if (ParticularEffects.isLeafLayFlatEnabled()) {
                if (ParticularEffects.isLeafRightAngleSnapEnabled()) {
                    this.roll = (float) (this.random.nextInt(4) / 2.0D * Math.PI);
                } else {
                    this.roll = this.random.nextFloat() * (float) (Math.PI * 2.0D);
                }
                this.oRoll = this.roll;
            }
        }

        this.move(this.xd, this.yd, this.zd);

        BlockPos pos = BlockPos.containing(this.x, this.y, this.z);
        FluidState fluidState = this.level.getFluidState(pos);
        if (fluidState.is(FluidTags.WATER)) {
            if (this.gravity > 0.0F) {
                this.y = pos.getY() + fluidState.getHeight(this.level, pos);
                if (ParticularEffects.isLeafSpawnRippleEnabled()) {
                    this.level.addParticle(ParticularParticles.WATER_RIPPLE, this.x, this.y, this.z, 0.0D, 0.0D, 0.0D);
                }
            }

            this.yd = 0.0D;
            this.gravity = 0.0F;
        } else if (this.age >= FADE_IN_DURATION) {
            this.gravity = this.gravityFactor;
            this.yd -= 0.003D * this.gravity;
            if (!this.onGround && !this.expiring) {
                this.roll = this.getAngle();
            }
        }

        if (this.age <= FADE_IN_DURATION) {
            this.alpha = (float) this.age / FADE_IN_DURATION;
        } else if (this.age > this.lifetime - FADE_OUT_DURATION) {
            this.alpha = (float) (this.lifetime - this.age) / FADE_OUT_DURATION;
        } else {
            this.alpha = 1.0F;
        }
    }

    @Override
    public SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    @Override
    public FacingCameraMode getFacingCameraMode() {
        // Осевший лист лежит плоско на поверхности (как в оригинале); падающий —
        // биллборд лицом к камере с покачиванием (roll).
        if (this.expiring && ParticularEffects.isLeafLayFlatEnabled()) {
            return (quaternion, camera, tickProgress) -> quaternion.rotationX((float) (Math.PI / 2.0));
        }
        return FacingCameraMode.LOOKAT_XYZ;
    }

    public static class Factory implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet provider;

        public Factory(SpriteSet provider) {
            this.provider = provider;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd, RandomSource random) {
            return new LeafParticle(level, x, y, z, xd, yd, zd, this.provider);
        }
    }
}
