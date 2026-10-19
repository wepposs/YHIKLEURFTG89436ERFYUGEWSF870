package vesence.module.impl.visuals.particular;

import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.client.renderer.BiomeColors;

import java.awt.Color;

public class WaterfallSprayParticle extends BillboardParticle {
    protected WaterfallSprayParticle(ClientLevel world, double x, double y, double z, double motionX, double motionY, double motionZ, SpriteSet provider) {
        super(world, x, y, z, provider.getSprite(world.getRandom()));
        this.setSprite(provider.getSprite(this.random));
        this.gravityStrength = 0.04F;
        this.velocityX = this.velocityX * 0.3D + motionX;
        this.velocityY = this.velocityY * 0.3D + motionY;
        this.velocityZ = this.velocityZ * 0.3D + motionZ;
        this.velocityX += (this.random.nextDouble() - 0.5D) * 0.01D;
        this.velocityY += this.random.nextDouble() * 0.01D;
        this.velocityZ += (this.random.nextDouble() - 0.5D) * 0.01D;
        this.maxAge = (int) (8.0D / (Math.random() * 0.8D + 0.2D));

        this.velocityX += motionX;
        this.velocityY *= 0.75D;
        this.velocityZ += motionZ;

        Color color = new Color(BiomeColors.getWaterColor(world, BlockPos.ofFloored(x, y, z)));
        this.red = color.getRed() / 255.0F;
        this.green = color.getGreen() / 255.0F;
        this.blue = color.getBlue() / 255.0F;
    }

    @Override
    public void tick() {
        this.lastX = this.x;
        this.lastY = this.y;
        this.lastZ = this.z;
        if (this.maxAge-- <= 0) {
            this.markDead();
        } else {
            this.velocityY -= this.gravityStrength;
            this.move(this.velocityX, this.velocityY, this.velocityZ);
            this.velocityX *= 0.98D;
            this.velocityY *= 0.98D;
            this.velocityZ *= 0.98D;
            if (this.onGround) {
                if (Math.random() < 0.5D) {
                    this.markDead();
                }

                this.velocityX *= 0.7D;
                this.velocityZ *= 0.7D;
            }

            BlockPos blockPos = BlockPos.ofFloored(this.x, this.y, this.z);
            double collisionHeight = this.world.getBlockState(blockPos).getCollisionShape(this.world, blockPos).getMax(Direction.Axis.Y);
            FluidState fluidState = this.world.getFluidState(blockPos);
            if (fluidState.isStill()) {
                collisionHeight = Math.max(collisionHeight, fluidState.getHeight(this.world, blockPos));
            }

            if (collisionHeight > 0.0D && this.y < blockPos.getY() + collisionHeight) {
                this.markDead();
            }
        }
    }

    @Override
    public RenderType getRenderType() {
        return RenderType.PARTICLE_ATLAS_OPAQUE;
    }

    public static class Factory implements ParticleFactory<SimpleParticleType> {
        private final SpriteSet provider;

        public Factory(SpriteSet provider) {
            this.provider = provider;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, Random random) {
            return new WaterfallSprayParticle(world, x, y, z, velocityX, velocityY, velocityZ, this.provider);
        }
    }
}
