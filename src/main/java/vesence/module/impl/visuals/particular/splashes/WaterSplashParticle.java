package vesence.module.impl.visuals.particular.splashes;

import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.tags.FluidTags;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.BiomeColors;
import org.joml.Quaternionf;

import java.awt.Color;

public class WaterSplashParticle extends SingleQuadParticle {
    protected final SpriteSet provider;
    protected final float width;
    protected final float height;

    protected WaterSplashParticle(ClientLevel level, double x, double y, double z, float width, float height, SpriteSet provider) {
        super(level, x, y, z, provider.get(level.getRandom()));
        this.provider = provider;
        this.width = width;
        this.height = height;
        this.lifetime = 18;
        this.gravity = 0.0F;
        this.setSprite(provider.get(this.age, this.lifetime));

        Color color = new Color(BiomeColors.getAverageWaterColor(level, BlockPos.containing(x, y, z)));
        this.rCol = color.getRed() / 255.0F;
        this.gCol = color.getGreen() / 255.0F;
        this.bCol = color.getBlue() / 255.0F;
    }

    @Override
    public SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
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

    /**
     * Рендер объёмного "венца" воды из 4 вертикальных граней (как цилиндр в оригинале),
     * вместо одиночного биллборда. Каждая грань — квадрат submittable.render с фиксированным
     * мировым поворотом (две грани в плоскости XY, две повёрнуты на 90 по Y).
     */
    @Override
    public void extract(QuadParticleRenderState submittable, Camera camera, float tickProgress) {
        Vec3 cam = camera.position();
        float progress = Mth.lerp(tickProgress, this.age - 1.0F, this.age) / this.lifetime;
        float w = this.width * (0.8F + 0.2F * progress);
        if (w < 0.05F) {
            w = 0.05F;
        }

        float cx = (float) (Mth.lerp(tickProgress, this.xo, this.x) - cam.x);
        float cy = (float) (Mth.lerp(tickProgress, this.yo, this.y) - cam.y);
        float cz = (float) (Mth.lerp(tickProgress, this.zo, this.z) - cam.z);
        // Центрируем грани на поверхности воды: видимая (надводная) часть венца
        // получается низкой и широкой, как в оригинале (submittable рисует только
        // квадратные грани, поэтому короткие широкие стены иначе не сделать).
        float wallCenterY = cy;

        int color = ARGB.colorFromFloat(this.alpha, this.rCol, this.gCol, this.bCol);
        int light = this.getLightCoords(tickProgress);
        float minU = this.getU0();
        float maxU = this.getU1();
        float minV = this.getV0();
        float maxV = this.getV1();
        SingleQuadParticle.Layer type = this.getLayer();

        Quaternionf faceZ = new Quaternionf();
        Quaternionf faceX = new Quaternionf().rotationY((float) (Math.PI / 2.0));

        submittable.add(type, cx, wallCenterY, cz + w, faceZ.x, faceZ.y, faceZ.z, faceZ.w, w, minU, maxU, minV, maxV, color, light);
        submittable.add(type, cx, wallCenterY, cz - w, faceZ.x, faceZ.y, faceZ.z, faceZ.w, w, minU, maxU, minV, maxV, color, light);
        submittable.add(type, cx + w, wallCenterY, cz, faceX.x, faceX.y, faceX.z, faceX.w, w, minU, maxU, minV, maxV, color, light);
        submittable.add(type, cx - w, wallCenterY, cz, faceX.x, faceX.y, faceX.z, faceX.w, w, minU, maxU, minV, maxV, color, light);
    }

    public static class Factory implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet provider;

        public Factory(SpriteSet provider) {
            this.provider = provider;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd, RandomSource random) {
            return new WaterSplashParticle(level, x, y, z, (float) xd, (float) yd, this.provider);
        }
    }
}
