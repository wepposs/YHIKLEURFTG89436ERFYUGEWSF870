package vesence.module.impl.visuals.particular;

import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.client.renderer.BiomeColors;

import java.awt.Color;
import com.mojang.math.Axis;

public class WaterfallSprayParticle extends SingleQuadParticle {
    protected WaterfallSprayParticle(ClientLevel level, double x, double y, double z, double motionX, double motionY, double motionZ, SpriteSet provider) {
        super(level, x, y, z, provider.get(level.getRandom()));
        this.setSprite(provider.get(this.random));
        this.gravity = 0.04F;
        this.xd = this.xd * 0.3D + motionX;
        this.yd = this.yd * 0.3D + motionY;
        this.zd = this.zd * 0.3D + motionZ;
        this.xd += (this.random.nextDouble() - 0.5D) * 0.01D;
        this.yd += this.random.nextDouble() * 0.01D;
        this.zd += (this.random.nextDouble() - 0.5D) * 0.01D;
        this.lifetime = (int) (8.0D / (Math.random() * 0.8D + 0.2D));

        this.xd += motionX;
        this.yd *= 0.75D;
        this.zd += motionZ;

        Color color = new Color(BiomeColors.getAverageWaterColor(level, BlockPos.containing(x, y, z)));
        this.rCol = color.getRed() / 255.0F;
        this.gCol = color.getGreen() / 255.0F;
        this.bCol = color.getBlue() / 255.0F;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.lifetime-- <= 0) {
            this.remove();
        } else {
            this.yd -= this.gravity;
            this.move(this.xd, this.yd, this.zd);
            this.xd *= 0.98D;
            this.yd *= 0.98D;
            this.zd *= 0.98D;
            if (this.onGround) {
                if (Math.random() < 0.5D) {
                    this.remove();
                }

                this.xd *= 0.7D;
                this.zd *= 0.7D;
            }

            BlockPos blockPos = BlockPos.containing(this.x, this.y, this.z);
            double collisionHeight = this.level.getBlockState(blockPos).getCollisionShape(this.level, blockPos).max(Direction.Axis.Y);
            FluidState fluidState = this.level.getFluidState(blockPos);
            if (fluidState.isSource()) {
                collisionHeight = Math.max(collisionHeight, fluidState.getHeight(this.level, blockPos));
            }

            if (collisionHeight > 0.0D && this.y < blockPos.getY() + collisionHeight) {
                this.remove();
            }
        }
    }

    @Override
    public SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.OPAQUE;
    }

    public static class Factory implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet provider;

        public Factory(SpriteSet provider) {
            this.provider = provider;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd, RandomSource random) {
            return new WaterfallSprayParticle(level, x, y, z, xd, yd, zd, this.provider);
        }
    }
}
