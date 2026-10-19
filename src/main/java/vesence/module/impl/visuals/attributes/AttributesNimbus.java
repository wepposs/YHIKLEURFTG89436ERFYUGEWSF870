package vesence.module.impl.visuals.attributes;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.render.MultiBufferSource;
import net.minecraft.client.render.MultiBufferSource.Immediate;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import vesence.event.render.EventRender3D;
import vesence.module.impl.visuals.Attributes;
import vesence.utils.friends.FriendStorage;
import vesence.utils.render.ColorUtil;

/**
 * Нимб — «огненное» кольцо частиц над головой игрока: тематический glow-ring,
 * белая горячая сердцевина и тёмные «угольки».
 *
 * <p>Порт косметики «Нимб» из клиента RelevantPremium ({@code Cosmetics.renderNimbus}).
 * Оригинал рисовал нимб только над своей головой через immediate-mode Tessellator
 * с ручным additive-blend. Здесь тот же эффект перенесён на render-API MC 1.21.11
 * (RenderPipeline + RenderType): glow/core — аддитивный слой, угольки — обычный
 * translucent-слой. Рисуется над своей головой и над головами друзей.
 */
public final class AttributesNimbus {

    private static final ResourceLocation BLOOM = ResourceLocation.of("vesence", "textures/targetesp/bloom.png");

    /** Glow + сердцевина — аддитивные биллборды (LIGHTNING) без depth-test. */
    private static final RenderPipeline GLOW_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(new Snippet[]{RenderPipelines.POSITION_TEX_COLOR_SNIPPET})
                    .withLocation(ResourceLocation.of("vesence", "pipeline/world/attributes_nimbus_glow"))
                    .withVertexFormat(DefaultVertexFormat.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build());

    private static final RenderType GLOW_LAYER = RenderType.of(
            "vesence_attributes_nimbus_glow",
            RenderSetup.builder(GLOW_PIPELINE).expectedBufferSize(65536).translucent().texture("Sampler0", BLOOM).build());

    /** Тёмные угольки — обычный translucent (иначе чёрный в additive невидим). */
    private static final RenderPipeline DARK_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(new Snippet[]{RenderPipelines.POSITION_TEX_COLOR_SNIPPET})
                    .withLocation(ResourceLocation.of("vesence", "pipeline/world/attributes_nimbus_dark"))
                    .withVertexFormat(DefaultVertexFormat.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .build());

    private static final RenderType DARK_LAYER = RenderType.of(
            "vesence_attributes_nimbus_dark",
            RenderSetup.builder(DARK_PIPELINE).expectedBufferSize(16384).translucent().texture("Sampler0", BLOOM).build());

    private static final int NIMBUS_PARTICLES_PER_RING = 96;
    private static final int NIMBUS_RING_LAYERS = 2;
    private static final float NIMBUS_LAYER_SPREAD = 0.03F;
    private static final float NIMBUS_FIRE_RADIUS = 0.4F;
    private static final float NIMBUS_RADIUS_JITTER = 0.08F;
    private static final float NIMBUS_CORE_SIZE = 0.26F;
    private static final float NIMBUS_GLOW_SIZE = 0.6F;
    private static final float NIMBUS_TURBULENCE = 0.05F;
    private static final double NIMBUS_ROTATION_SPEED = 0.0009;
    private static final int NIMBUS_DARK_PARTICLES = 64;
    private static final float NIMBUS_DARK_SIZE = 0.14F;
    /** Высота нижней плоскости кольца над макушкой. */
    private static final double NIMBUS_HEAD_CLEARANCE = 0.25;

    private final Attributes parent;
    private final Minecraft mc = Minecraft.getInstance();

    public AttributesNimbus(Attributes parent) {
        this.parent = parent;
    }

    public void deactivate() {}

    public void onRender3D(EventRender3D event) {
        if (mc.player == null || mc.level == null) return;

        float tickDelta = event.getTickDelta();
        Vec3 cameraPos = event.getCameraPos();
        PoseStack matrices = event.getMatrixStack();

        float camYaw = mc.gameRenderer.getCamera().getYRot();
        float camPitch = mc.gameRenderer.getCamera().getXRot();

        int theme = ColorUtil.theme(255);
        int themeR = ColorUtil.red(theme);
        int themeG = ColorUtil.green(theme);
        int themeB = ColorUtil.blue(theme);

        long nowMs = System.currentTimeMillis();
        double phase = nowMs * NIMBUS_ROTATION_SPEED;

        java.util.List<Player> targets = new java.util.ArrayList<>();
        for (Player player : mc.level.getPlayers()) {
            if (shouldRender(player) && parent.shouldRenderFor(player, cameraPos)) targets.add(player);
        }
        if (targets.isEmpty()) return;

        Immediate immediate = MultiBufferSource.immediate(parent.allocator());

        // Слой glow/core (аддитивный) — пишем полностью до переключения слоя.
        VertexConsumer glowBuf = immediate.getBuffer(GLOW_LAYER);
        for (Player player : targets) {
            renderNimbusGlow(player, tickDelta, cameraPos, matrices, glowBuf,
                    camYaw, camPitch, phase, themeR, themeG, themeB);
        }

        // Слой тёмных угольков (translucent).
        VertexConsumer darkBuf = immediate.getBuffer(DARK_LAYER);
        for (Player player : targets) {
            renderNimbusDark(player, tickDelta, cameraPos, matrices, darkBuf, camYaw, camPitch, phase);
        }

        immediate.draw();
    }

    private boolean shouldRender(Player player) {
        if (mc.player == null) return false;
        if (!player.isAlive() || player.isInvisible()) return false;
        if (player == mc.player) {
            return !mc.options.getPerspective().isFirstPerson();
        }
        return FriendStorage.isFriend(player.getName().getString());
    }

    private void renderNimbusGlow(Player player, float tickDelta, Vec3 camera, PoseStack matrices,
                                  VertexConsumer glowBuf, float camYaw, float camPitch,
                                  double phase, int themeR, int themeG, int themeB) {
        Vec3 pos = player.getLerpedPos(tickDelta);
        double cx = pos.x;
        double cy = pos.y + player.getHeight() + NIMBUS_HEAD_CLEARANCE;
        double cz = pos.z;

        // ── Проход 1: внешний glow (тематический цвет). ──
        for (int layer = 0; layer < NIMBUS_RING_LAYERS; layer++) {
            float layerT = (NIMBUS_RING_LAYERS == 1) ? 0.5F : (float) layer / (NIMBUS_RING_LAYERS - 1);
            float yOffset = (layerT - 0.5F) * 2.0F * NIMBUS_LAYER_SPREAD;
            float layerFalloff = Math.max(0.25F, 1.0F - Math.abs(layerT - 0.5F) * 1.5F);
            double layerPhaseShift = layer * 0.7;

            for (int i = 0; i < NIMBUS_PARTICLES_PER_RING; i++) {
                float baseAngle = (float) (Math.PI * 2.0 * i / NIMBUS_PARTICLES_PER_RING);
                float turbulence = (float) (Math.sin(phase * 1.3 + i * 0.7 + layerPhaseShift) * NIMBUS_TURBULENCE);
                double angle = phase + baseAngle + turbulence;
                float radiusJitter = (float) Math.sin(i * 12.9898F + layer * 78.233F) * NIMBUS_RADIUS_JITTER;
                float radius = NIMBUS_FIRE_RADIUS + radiusJitter;
                double offsetX = Math.cos(angle) * radius;
                double offsetZ = Math.sin(angle) * radius;
                float pulse = 0.85F + 0.15F * (float) Math.sin(phase * 2.5 + i * 1.7);
                float size = NIMBUS_GLOW_SIZE * pulse * layerFalloff;
                int alpha = (int) (110 * layerFalloff);
                drawBillboard(glowBuf, matrices, camera,
                        cx + offsetX, cy + yOffset, cz + offsetZ,
                        size, camYaw, camPitch, themeR, themeG, themeB, alpha);
            }
        }

        // ── Проход 2: горячая сердцевина (осветлённый цвет темы). ──
        int coreR = Math.min(255, themeR + 90);
        int coreG = Math.min(255, themeG + 90);
        int coreB = Math.min(255, themeB + 90);
        for (int i = 0; i < NIMBUS_PARTICLES_PER_RING; i++) {
            float baseAngle = (float) (Math.PI * 2.0 * i / NIMBUS_PARTICLES_PER_RING);
            float turbulence = (float) (Math.sin(phase * 1.6 + i * 0.9) * NIMBUS_TURBULENCE);
            double angle = -phase + baseAngle + turbulence;
            float radiusJitter = (float) Math.cos(i * 7.123F) * (NIMBUS_RADIUS_JITTER * 0.6F);
            float radius = NIMBUS_FIRE_RADIUS + radiusJitter;
            double offsetX = Math.cos(angle) * radius;
            double offsetZ = Math.sin(angle) * radius;
            float pulse = 0.7F + 0.3F * (float) Math.sin(phase * 3.2 + i * 2.1);
            float size = NIMBUS_CORE_SIZE * pulse;
            int alpha = (int) (220 * pulse);
            drawBillboard(glowBuf, matrices, camera,
                    cx + offsetX, cy, cz + offsetZ,
                    size, camYaw, camPitch, coreR, coreG, coreB, alpha);
        }

    }

    private void renderNimbusDark(Player player, float tickDelta, Vec3 camera, PoseStack matrices,
                                  VertexConsumer darkBuf, float camYaw, float camPitch, double phase) {
        Vec3 pos = player.getLerpedPos(tickDelta);
        double cx = pos.x;
        double cy = pos.y + player.getHeight() + NIMBUS_HEAD_CLEARANCE;
        double cz = pos.z;

        // ── Проход 3: тёмные угольки (обычный alpha-бленд). ──
        for (int i = 0; i < NIMBUS_DARK_PARTICLES; i++) {
            float baseAngle = (float) (Math.PI * 2.0 * i / NIMBUS_DARK_PARTICLES);
            float turbulence = (float) (Math.sin(phase * 0.8 + i * 1.3) * NIMBUS_TURBULENCE * 0.5);
            double angle = phase * 0.4 + baseAngle + turbulence;
            float radiusJitter = (float) Math.sin(i * 5.731F) * (NIMBUS_RADIUS_JITTER * 0.5F);
            float radius = NIMBUS_FIRE_RADIUS + radiusJitter;
            double offsetX = Math.cos(angle) * radius;
            double offsetZ = Math.sin(angle) * radius;
            float pulse = 0.7F + 0.3F * (float) Math.sin(phase * 2.1 + i * 1.9);
            float size = NIMBUS_DARK_SIZE * pulse;
            int alpha = (int) (180 * (0.5F + 0.5F * (float) Math.sin(phase * 1.7 + i * 2.3)));
            drawBillboard(darkBuf, matrices, camera,
                    cx + offsetX, cy, cz + offsetZ,
                    size, camYaw, camPitch, 0, 0, 0, alpha);
        }
    }

    private static void drawBillboard(VertexConsumer buffer, PoseStack matrices, Vec3 cameraPos,
                                      double worldX, double worldY, double worldZ,
                                      float size, float camYaw, float camPitch,
                                      int r, int g, int b, int a) {
        if (a <= 0 || size <= 0f) return;
        float half = size * 0.5F;
        matrices.pushPose();
        matrices.translate(worldX - cameraPos.x, worldY - cameraPos.y, worldZ - cameraPos.z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camYaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camPitch));
        Matrix4f matrix = matrices.last().getPositionMatrix();
        texVertex(buffer, matrix, -half, -half, 0f, 1f, r, g, b, a);
        texVertex(buffer, matrix, -half,  half, 0f, 0f, r, g, b, a);
        texVertex(buffer, matrix,  half,  half, 1f, 0f, r, g, b, a);
        texVertex(buffer, matrix,  half, -half, 1f, 1f, r, g, b, a);
        matrices.popPose();
    }

    private static void texVertex(VertexConsumer buffer, Matrix4f matrix, float x, float y,
                                  float u, float v, int r, int g, int b, int a) {
        buffer.vertex(matrix, x, y, 0f)
                .texture(u, v)
                .color(r, g, b, a)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(15728880)
                .normal(0f, 0f, 1f);
    }
}
