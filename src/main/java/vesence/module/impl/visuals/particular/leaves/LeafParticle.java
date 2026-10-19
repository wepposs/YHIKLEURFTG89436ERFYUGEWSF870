package vesence.module.impl.visuals.particular.leaves;

import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import vesence.module.impl.visuals.particular.ParticularEffects;
import vesence.module.impl.visuals.particular.ParticularParticles;

public class LeafParticle extends BillboardParticle {
    protected static final int FADE_IN_DURATION = 10;
    protected static final int FADE_OUT_DURATION = 100;

    protected float rotateFactor;
    protected float gravityFactor = 0.075F;
    protected final boolean flippedSprite;
    protected final int flippedDirection;
    protected boolean expiring;

    protected LeafParticle(ClientWorld world, double x, double y, double z, double red, double green, double blue, SpriteProvider provider) {
        super(world, x, y, z, provider.getSprite(world.getRandom()));
        this.setSprite(provider.getSprite(this.random));
        this.collidesWithWorld = true;
        this.gravityStrength = 0.0F;
        this.maxAge = 200;
        this.velocityX = 0.0D;
        this.velocityY = 0.0D;
        this.velocityZ = 0.0D;
        this.alpha = 0.0F;
        this.red = (float) red;
        this.green = (float) green;
        this.blue = (float) blue;
        this.rotateFactor = 6.0F + this.random.nextFloat() * 3.0F;
        this.flippedSprite = this.random.nextBoolean();
        this.flippedDirection = this.random.nextBoolean() ? 1 : -1;
        this.scale = 7.0F / 32.0F;
    }

    protected float getAngle() {
        int time = this.age - FADE_IN_DURATION;
        float amplitude = Math.max(0.0F, Math.min(30.0F, time)) / 30.0F;
        return (float) Math.sin(time / this.rotateFactor) * amplitude * 0.5F * this.flippedDirection;
    }

    @Override
    public void tick() {
        this.lastX = this.x;
        this.lastY = this.y;
        this.lastZ = this.z;
        this.lastZRotation = this.zRotation;
        if (this.age++ >= this.maxAge) {
            this.markDead();
            return;
        }

        if (this.age <= FADE_IN_DURATION) {
            this.velocityY = 0.0D;
        } else if (!this.expiring && (this.onGround || Math.abs(this.velocityY) < 1.0E-5D)) {
            this.expiring = true;
            this.age = this.maxAge - FADE_OUT_DURATION;
            this.y += 0.01D;
            if (ParticularEffects.isLeafLayFlatEnabled()) {
                if (ParticularEffects.isLeafRightAngleSnapEnabled()) {
                    this.zRotation = (float) (this.random.nextInt(4) / 2.0D * Math.PI);
                } else {
                    this.zRotation = this.random.nextFloat() * (float) (Math.PI * 2.0D);
                }
                this.lastZRotation = this.zRotation;
            }
        }

        this.move(this.velocityX, this.velocityY, this.velocityZ);

        BlockPos pos = BlockPos.ofFloored(this.x, this.y, this.z);
        FluidState fluidState = this.world.getFluidState(pos);
        if (fluidState.isIn(FluidTags.WATER)) {
            if (this.gravityStrength > 0.0F) {
                this.y = pos.getY() + fluidState.getHeight(this.world, pos);
                if (ParticularEffects.isLeafSpawnRippleEnabled()) {
                    this.world.addParticleClient(ParticularParticles.WATER_RIPPLE, this.x, this.y, this.z, 0.0D, 0.0D, 0.0D);
                }
            }

            this.velocityY = 0.0D;
            this.gravityStrength = 0.0F;
        } else if (this.age >= FADE_IN_DURATION) {
            this.gravityStrength = this.gravityFactor;
            this.velocityY -= 0.003D * this.gravityStrength;
            if (!this.onGround && !this.expiring) {
                this.zRotation = this.getAngle();
            }
        }

        if (this.age <= FADE_IN_DURATION) {
            this.alpha = (float) this.age / FADE_IN_DURATION;
        } else if (this.age > this.maxAge - FADE_OUT_DURATION) {
            this.alpha = (float) (this.maxAge - this.age) / FADE_OUT_DURATION;
        } else {
            this.alpha = 1.0F;
        }
    }

    @Override
    public RenderType getRenderType() {
        return RenderType.PARTICLE_ATLAS_TRANSLUCENT;
    }

    @Override
    public Rotator getRotator() {
        // Осевший лист лежит плоско на поверхности (как в оригинале); падающий —
        // биллборд лицом к камере с покачиванием (zRotation).
        if (this.expiring && ParticularEffects.isLeafLayFlatEnabled()) {
            return (quaternion, camera, tickProgress) -> quaternion.rotationX((float) (Math.PI / 2.0));
        }
        return Rotator.ALL_AXIS;
    }

    public static class Factory implements ParticleFactory<SimpleParticleType> {
        private final SpriteProvider provider;

        public Factory(SpriteProvider provider) {
            this.provider = provider;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientWorld world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, Random random) {
            return new LeafParticle(world, x, y, z, velocityX, velocityY, velocityZ, this.provider);
        }
    }
}
