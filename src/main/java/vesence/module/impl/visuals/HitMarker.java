package vesence.module.impl.visuals;

import java.util.ArrayList;
import java.util.Optional;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.resources.Identifier;
import com.mojang.math.Axis;
import net.minecraft.world.phys.Vec3;
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
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import net.minecraft.client.renderer.BindGroupLayouts;

/**
 * HitMarker — порт из RelevantPremiumpp4.
 * Крестик-маркер (cross.png) появляется в точке удара, с анимацией
 * появления/показа/исчезновения и опциональным свечением.
 */
@IModule(name = "HitMarker", description = "Показывает маркер при ударе", category = Category.VISUALS, bind = -1)
@Environment(EnvType.CLIENT)
public class HitMarker extends Module {

    private static final Identifier CROSS_TEXTURE = Identifier.fromNamespaceAndPath("vesence", "textures/cross/cross.png");

    public final SliderSetting size = new SliderSetting("Размер", 0.5, 0.1, 2.0, 0.05);
    public final SliderSetting fadeInTime = new SliderSetting("Время появления", 100.0, 50.0, 500.0, 10.0);
    public final SliderSetting displayTime = new SliderSetting("Время показа", 300.0, 100.0, 1000.0, 50.0);
    public final SliderSetting fadeOutTime = new SliderSetting("Время исчезновения", 200.0, 50.0, 500.0, 10.0);
    public final BooleanSetting glow = new BooleanSetting("Свечение", true);
    public final BooleanSetting scale = new BooleanSetting("Анимация масштаба", true);

    private final ArrayList<HitMarkerData> hitMarkers = new ArrayList<>();
    private ByteBufferBuilder markerAllocator = null;

    // Additive (glow) и обычный translucent пайплайны
    private static final RenderPipeline MARKER_GLOW_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(new RenderPipeline.Snippet[]{RenderPipelines.MATRICES_FOG_SNIPPET})
                    .withLocation(Identifier.fromNamespaceAndPath("vesence", "pipeline/world/hitmarker_glow"))
                    .withVertexShader("core/position_tex_color")
                    .withFragmentShader("core/position_tex_color")
                    .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                    .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)                    .withPrimitiveTopology(PrimitiveTopology.QUADS)
                    .withCull(false)
                    .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                    .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
                    .build());
    private static final RenderPipeline MARKER_NORMAL_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(new RenderPipeline.Snippet[]{RenderPipelines.MATRICES_FOG_SNIPPET})
                    .withLocation(Identifier.fromNamespaceAndPath("vesence", "pipeline/world/hitmarker_normal"))
                    .withVertexShader("core/position_tex_color")
                    .withFragmentShader("core/position_tex_color")
                    .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                    .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)                    .withPrimitiveTopology(PrimitiveTopology.QUADS)
                    .withCull(false)
                    .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .build());
    private static final RenderType MARKER_GLOW_LAYER = RenderType.create(
            "vesence_hitmarker_glow",
            RenderSetup.builder(MARKER_GLOW_PIPELINE)
                    .withTexture("Sampler0", CROSS_TEXTURE).createRenderSetup());
    private static final RenderType MARKER_NORMAL_LAYER = RenderType.create(
            "vesence_hitmarker_normal",
            RenderSetup.builder(MARKER_NORMAL_PIPELINE)
                    .withTexture("Sampler0", CROSS_TEXTURE).createRenderSetup());

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
        if (mc.player == null || mc.level == null) return;
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

    private Vec3 resolveHitPosition(Entity attacker, Entity target) {
        Vec3 fallback = new Vec3(target.getX(), target.getY() + target.getBbHeight() / 2.0, target.getZ());
        if (attacker == null) return fallback;
        Vec3 eyePos = attacker.getEyePosition(1.0F);
        Vec3 lookVec = attacker.getViewVector(1.0F);
        Vec3 targetCenter = target.getBoundingBox().getCenter();
        double distance = Math.max(eyePos.distanceTo(targetCenter) + 1.0, 6.0);
        Vec3 reachPos = eyePos.add(lookVec.scale(distance));
        Optional<Vec3> hitPos = target.getBoundingBox().clip(eyePos, reachPos);
        return hitPos.isPresent() ? hitPos.get() : eyePos.add(lookVec.scale(eyePos.distanceTo(targetCenter)));
    }

    @EventInit
    public void onRender3D(EventRender3D e) {
        if (mc.player == null || mc.level == null) return;

        synchronized (this.hitMarkers) {
            this.hitMarkers.removeIf(HitMarkerData::isDead);
        }
        if (this.hitMarkers.isEmpty()) return;

        PoseStack matrices = e.getMatrixStack();
        Vec3 camera = mc.gameRenderer.mainCamera().position();

        ArrayList<HitMarkerData> renderList;
        synchronized (this.hitMarkers) {
            renderList = new ArrayList<>(this.hitMarkers);
        }

        int color = ColorUtil.fade();
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        RenderType layer = this.glow.get() ? MARKER_GLOW_LAYER : MARKER_NORMAL_LAYER;

        ByteBufferBuilder alloc = markerAllocator;
        if (alloc == null) {
            alloc = new ByteBufferBuilder(65536);
            markerAllocator = alloc;
        }
        Immediate immediate = new Immediate();
        try {
            VertexConsumer buffer = immediate.getBuffer(layer);
            for (HitMarkerData marker : renderList) {
                float alpha = marker.getAlpha();
                if (alpha <= 0.0F) continue;

                double x = marker.position.x - camera.x;
                double y = marker.position.y - camera.y;
                double z = marker.position.z - camera.z;
                matrices.pushPose();
                matrices.translate((float) x, (float) y, (float) z);
                matrices.rotate(Axis.YP.rotationDegrees(-mc.gameRenderer.mainCamera().yRot()));
                matrices.rotate(Axis.XP.rotationDegrees(mc.gameRenderer.mainCamera().xRot()));

                float currentSize = (float) (double) this.size.get();
                if (this.scale.get()) {
                    currentSize *= marker.getScaleMultiplier();
                }

                Matrix4f matrix = matrices.last().pose();
                float half = currentSize / 2.0F;
                int alphaInt = (int) (alpha * 255.0F);
                buffer.addVertex(matrix, -half, -half, 0.0F).setUv(0.0F, 1.0F).setColor(r, g, b, alphaInt);
                buffer.addVertex(matrix, -half, half, 0.0F).setUv(0.0F, 0.0F).setColor(r, g, b, alphaInt);
                buffer.addVertex(matrix, half, half, 0.0F).setUv(1.0F, 0.0F).setColor(r, g, b, alphaInt);
                buffer.addVertex(matrix, half, -half, 0.0F).setUv(1.0F, 1.0F).setColor(r, g, b, alphaInt);
                matrices.popPose();
            }
            immediate.draw();
        } finally {
            // reused across frames
        }
    }

    static class HitMarkerData {
        Vec3 position;
        long birthTime;
        long fadeInTime;
        long displayTime;
        long fadeOutTime;

        HitMarkerData(Vec3 position, long birthTime, long fadeInTime, long displayTime, long fadeOutTime) {
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
