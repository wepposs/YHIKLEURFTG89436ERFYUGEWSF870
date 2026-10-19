package vesence.module.impl.visuals;

import java.util.ArrayList;
import java.util.Optional;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import vesence.event.EventInit;
import vesence.event.player.AttackEvent;
import vesence.event.render.EventRender3D;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.utils.render.ColorUtil;

/**
 * HitMarker — порт из RelevantPremiumpp4.
 * Крестик-маркер (cross.png) появляется в точке удара, с анимацией
 * появления/показа/исчезновения и опциональным свечением.
 */
@IModule(name = "HitMarker", description = "Показывает маркер при ударе", category = Category.VISUALS, bind = -1)
@Environment(EnvType.CLIENT)
public class HitMarker extends Module {

    private static final Identifier CROSS_TEXTURE = Identifier.of("vesence", "textures/cross/cross.png");

    public final SliderSetting size = new SliderSetting("Размер", 0.5, 0.1, 2.0, 0.05);
    public final SliderSetting fadeInTime = new SliderSetting("Время появления", 100.0, 50.0, 500.0, 10.0);
    public final SliderSetting displayTime = new SliderSetting("Время показа", 300.0, 100.0, 1000.0, 50.0);
    public final SliderSetting fadeOutTime = new SliderSetting("Время исчезновения", 200.0, 50.0, 500.0, 10.0);
    public final BooleanSetting glow = new BooleanSetting("Свечение", true);
    public final BooleanSetting scale = new BooleanSetting("Анимация масштаба", true);

    private final ArrayList<HitMarkerData> hitMarkers = new ArrayList<>();
    private BufferAllocator markerAllocator = null;

    // Additive (glow) и обычный translucent пайплайны
    private static final RenderPipeline MARKER_GLOW_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(new RenderPipeline.Snippet[]{RenderPipelines.POSITION_TEX_COLOR_SNIPPET})
                    .withLocation(Identifier.of("vesence", "pipeline/world/hitmarker_glow"))
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build());
    private static final RenderPipeline MARKER_NORMAL_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(new RenderPipeline.Snippet[]{RenderPipelines.POSITION_TEX_COLOR_SNIPPET})
                    .withLocation(Identifier.of("vesence", "pipeline/world/hitmarker_normal"))
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .build());
    private static final RenderLayer MARKER_GLOW_LAYER = RenderLayer.of(
            "vesence_hitmarker_glow",
            RenderSetup.builder(MARKER_GLOW_PIPELINE).expectedBufferSize(4096).translucent()
                    .texture("Sampler0", CROSS_TEXTURE).build());
    private static final RenderLayer MARKER_NORMAL_LAYER = RenderLayer.of(
            "vesence_hitmarker_normal",
            RenderSetup.builder(MARKER_NORMAL_PIPELINE).expectedBufferSize(4096).translucent()
                    .texture("Sampler0", CROSS_TEXTURE).build());

    public HitMarker() {
        this.addSettings(new Setting[]{this.size, this.fadeInTime, this.displayTime, this.fadeOutTime, this.glow, this.scale});
    }

    @Override
    public void onDisable() {
        synchronized (this.hitMarkers) {
            this.hitMarkers.clear();
        }
        if (markerAllocator != null) { markerAllocator.close(); markerAllocator = null; }
        super.onDisable();
    }

    @EventInit
    public void onAttack(AttackEvent event) {
        if (mc.player == null || mc.world == null) return;
        Entity target = event.getTarget();
        if (target == null) return;
        synchronized (this.hitMarkers) {
            this.hitMarkers.add(new HitMarkerData(
                    resolveHitPosition(mc.player, target),
                    System.currentTimeMillis(),
                    (long) (double) this.fadeInTime.get(),
                    (long) (double) this.displayTime.get(),
                    (long) (double) this.fadeOutTime.get()));
        }
    }

    private Vec3d resolveHitPosition(Entity attacker, Entity target) {
        Vec3d fallback = new Vec3d(target.getX(), target.getY() + target.getHeight() / 2.0, target.getZ());
        if (attacker == null) return fallback;
        Vec3d eyePos = attacker.getCameraPosVec(1.0F);
        Vec3d lookVec = attacker.getRotationVec(1.0F);
        Vec3d targetCenter = target.getBoundingBox().getCenter();
        double distance = Math.max(eyePos.distanceTo(targetCenter) + 1.0, 6.0);
        Vec3d reachPos = eyePos.add(lookVec.multiply(distance));
        Optional<Vec3d> hitPos = target.getBoundingBox().raycast(eyePos, reachPos);
        return hitPos.isPresent() ? hitPos.get() : eyePos.add(lookVec.multiply(eyePos.distanceTo(targetCenter)));
    }

    @EventInit
    public void onRender3D(EventRender3D e) {
        if (mc.player == null || mc.world == null) return;

        synchronized (this.hitMarkers) {
            this.hitMarkers.removeIf(HitMarkerData::isDead);
        }
        if (this.hitMarkers.isEmpty()) return;

        MatrixStack matrices = e.getMatrixStack();
        Vec3d camera = mc.gameRenderer.getCamera().getCameraPos();

        ArrayList<HitMarkerData> renderList;
        synchronized (this.hitMarkers) {
            renderList = new ArrayList<>(this.hitMarkers);
        }

        int color = ColorUtil.fade();
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        RenderLayer layer = this.glow.get() ? MARKER_GLOW_LAYER : MARKER_NORMAL_LAYER;

        BufferAllocator alloc = markerAllocator;
        if (alloc == null) {
            alloc = new BufferAllocator(65536);
            markerAllocator = alloc;
        }
        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(alloc);
        try {
            VertexConsumer buffer = immediate.getBuffer(layer);
            for (HitMarkerData marker : renderList) {
                float alpha = marker.getAlpha();
                if (alpha <= 0.0F) continue;

                double x = marker.position.x - camera.x;
                double y = marker.position.y - camera.y;
                double z = marker.position.z - camera.z;
                matrices.push();
                matrices.translate((float) x, (float) y, (float) z);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-mc.gameRenderer.getCamera().getYaw()));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(mc.gameRenderer.getCamera().getPitch()));

                float currentSize = (float) (double) this.size.get();
                if (this.scale.get()) {
                    currentSize *= marker.getScaleMultiplier();
                }

                Matrix4f matrix = matrices.peek().getPositionMatrix();
                float half = currentSize / 2.0F;
                int alphaInt = (int) (alpha * 255.0F);
                buffer.vertex(matrix, -half, -half, 0.0F).texture(0.0F, 1.0F).color(r, g, b, alphaInt);
                buffer.vertex(matrix, -half, half, 0.0F).texture(0.0F, 0.0F).color(r, g, b, alphaInt);
                buffer.vertex(matrix, half, half, 0.0F).texture(1.0F, 0.0F).color(r, g, b, alphaInt);
                buffer.vertex(matrix, half, -half, 0.0F).texture(1.0F, 1.0F).color(r, g, b, alphaInt);
                matrices.pop();
            }
            immediate.draw();
        } finally {
            // reused across frames
        }
    }

    static class HitMarkerData {
        Vec3d position;
        long birthTime;
        long fadeInTime;
        long displayTime;
        long fadeOutTime;

        HitMarkerData(Vec3d position, long birthTime, long fadeInTime, long displayTime, long fadeOutTime) {
            this.position = position;
            this.birthTime = birthTime;
            this.fadeInTime = fadeInTime;
            this.displayTime = displayTime;
            this.fadeOutTime = fadeOutTime;
        }

        boolean isDead() {
            return System.currentTimeMillis() - this.birthTime >= this.fadeInTime + this.displayTime + this.fadeOutTime;
        }

        float getAlpha() {
            long elapsed = System.currentTimeMillis() - this.birthTime;
            if (elapsed < this.fadeInTime) {
                float progress = (float) elapsed / (float) this.fadeInTime;
                return easeOutCubic(progress);
            } else if (elapsed < this.fadeInTime + this.displayTime) {
                return 1.0F;
            } else {
                long fadeOutElapsed = elapsed - this.fadeInTime - this.displayTime;
                float progress = Math.min(1.0F, (float) fadeOutElapsed / (float) this.fadeOutTime);
                return 1.0F - easeInCubic(progress);
            }
        }

        float getScaleMultiplier() {
            long elapsed = System.currentTimeMillis() - this.birthTime;
            if (elapsed < this.fadeInTime) {
                float progress = (float) elapsed / (float) this.fadeInTime;
                return 0.5F + 0.5F * easeOutBack(progress);
            } else if (elapsed < this.fadeInTime + this.displayTime) {
                return 1.0F;
            } else {
                long fadeOutElapsed = elapsed - this.fadeInTime - this.displayTime;
                float progress = Math.min(1.0F, (float) fadeOutElapsed / (float) this.fadeOutTime);
                return 1.0F - 0.3F * easeInCubic(progress);
            }
        }

        private float easeOutCubic(float x) {
            return 1.0F - (float) Math.pow(1.0 - x, 3.0);
        }

        private float easeInCubic(float x) {
            return x * x * x;
        }

        private float easeOutBack(float x) {
            float c1 = 1.70158F;
            float c3 = c1 + 1.0F;
            return 1.0F + c3 * (float) Math.pow(x - 1.0, 3.0) + c1 * (float) Math.pow(x - 1.0, 2.0);
        }
    }
}
