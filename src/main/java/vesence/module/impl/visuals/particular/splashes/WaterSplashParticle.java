package vesence.module.impl.visuals.particular.splashes;

import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.BillboardParticleSubmittable;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.tags.FluidTags;
import net.minecraft.core.BlockPos;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.Mth;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.BiomeColors;
import org.joml.Quaternionf;

import java.awt.Color;

public class WaterSplashParticle extends BillboardParticle {
    protected final SpriteSet provider;
    protected final float width;
    protected final float height;

    protected WaterSplashParticle(ClientLevel world, double x, double y, double z, float width, float height, SpriteSet provider) {
        super(world, x, y, z, provider.getSprite(world.getRandom()));
        this.provider = provider;
        this.width = width;
        this.height = height;
        this.maxAge = 18;
        this.gravityStrength = 0.0F;
        this.setSprite(provider.getSprite(this.tickCount, this.maxAge));

        Color color = new Color(BiomeColors.getWaterColor(world, BlockPos.ofFloored(x, y, z)));
        this.red = color.getRed() / 255.0F;
        this.green = color.getGreen() / 255.0F;
        this.blue = color.getBlue() / 255.0F;
    }

    @Override
    public RenderType getRenderType() {
        return RenderType.PARTICLE_ATLAS_TRANSLUCENT;
    }

    @Override
    public void tick() {
        this.lastX = this.x;
        this.lastY = this.y;
        this.lastZ = this.z;
        if (this.tickCount++ >= this.maxAge) {
            this.markDead();
            return;
        }
        this.setSprite(this.provider.getSprite(this.tickCount, this.maxAge));
        if (!this.world.getFluidState(BlockPos.ofFloored(this.x, this.y, this.z)).isIn(FluidTags.WATER)) {
            this.markDead();
        }
    }

    /**
     * Рендер объёмного "венца" воды из 4 вертикальных граней (как цилиндр в оригинале),
     * вместо одиночного биллборда. Каждая грань — квадрат submittable.render с фиксированным
     * мировым поворотом (две грани в плоскости XY, две повёрнуты на 90 по Y).
     */
    @Override
    public void render(BillboardParticleSubmittable submittable, Camera camera, float tickProgress) {
        Vec3 cam = camera.getCameraPos();
        float progress = Mth.lerp(tickProgress, this.tickCount - 1.0F, this.tickCount) / this.maxAge;
        float w = this.width * (0.8F + 0.2F * progress);
        if (w < 0.05F) {
            w = 0.05F;
        }

        float cx = (float) (Mth.lerp(tickProgress, this.lastX, this.x) - cam.getX());
        float cy = (float) (Mth.lerp(tickProgress, this.lastY, this.y) - cam.getY());
        float cz = (float) (Mth.lerp(tickProgress, this.lastZ, this.z) - cam.getZ());
        // Центрируем грани на поверхности воды: видимая (надводная) часть венца
        // получается низкой и широкой, как в оригинале (submittable рисует только
        // квадратные грани, поэтому короткие широкие стены иначе не сделать).
        float wallCenterY = cy;

        int color = ColorHelper.fromFloats(this.alpha, this.red, this.green, this.blue);
        int light = this.getBrightness(tickProgress);
        float minU = this.getMinU();
        float maxU = this.getMaxU();
        float minV = this.getMinV();
        float maxV = this.getMaxV();
        RenderType type = this.getRenderType();

        Quaternionf faceZ = new Quaternionf();
        Quaternionf faceX = new Quaternionf().rotationY((float) (Math.PI / 2.0));

        submittable.render(type, cx, wallCenterY, cz + w, faceZ.x, faceZ.y, faceZ.z, faceZ.w, w, minU, maxU, minV, maxV, color, light);
        submittable.render(type, cx, wallCenterY, cz - w, faceZ.x, faceZ.y, faceZ.z, faceZ.w, w, minU, maxU, minV, maxV, color, light);
        submittable.render(type, cx + w, wallCenterY, cz, faceX.x, faceX.y, faceX.z, faceX.w, w, minU, maxU, minV, maxV, color, light);
        submittable.render(type, cx - w, wallCenterY, cz, faceX.x, faceX.y, faceX.z, faceX.w, w, minU, maxU, minV, maxV, color, light);
    }

    public static class Factory implements ParticleFactory<SimpleParticleType> {
        private final SpriteSet provider;

        public Factory(SpriteSet provider) {
            this.provider = provider;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, Random random) {
            return new WaterSplashParticle(world, x, y, z, (float) velocityX, (float) velocityY, this.provider);
        }
    }
}
