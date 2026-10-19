package vesence.module.impl.visuals.attributes;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import vesence.event.render.EventRender3D;
import vesence.module.impl.visuals.Attributes;

/**
 * Хвост — облако мягких glow-биллбордов из {@code bloom.png}. Привязан к телу
 * игрока через {@code bodyYaw}, следует за позой (стоя/sneaking) и движением.
 *
 * <p>Порт косметики «Лисий хвост» из клиента RelevantPremium, адаптированный
 * под render-API MC 1.21.11 (RenderPipeline + RenderLayer + VertexConsumer,
 * как в TargetESP). Мировые координаты каждой точки хвоста считаются через
 * свежую {@link Matrix4f}, построенную с нуля от позиции игрока.
 */
public final class AttributesTail {

    static final Identifier BLOOM = Identifier.of("vesence", "textures/targetesp/bloom.png");

    /** Аддитивный (LIGHTNING) текстурный пайплайн с depth-test, как у bloom в TargetESP. */
    static final RenderPipeline BLOOM_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(new Snippet[]{RenderPipelines.POSITION_TEX_COLOR_SNIPPET})
                    .withLocation(Identifier.of("vesence", "pipeline/world/attributes_bloom"))
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build());

    static final RenderLayer BLOOM_LAYER = RenderLayer.of(
            "vesence_attributes_bloom",
            RenderSetup.builder(BLOOM_PIPELINE)
                    .expectedBufferSize(262144)
                    .translucent()
                    .texture("Sampler0", BLOOM)
                    .build());

    private static final int CURVE_POINTS = 18;
    private static final float TAIL_LENGTH = 0.95f;
    /** Размер glow в начале хвоста (у поясницы — компактный). */
    private static final float SIZE_BASE = 0.30f;
    /** Размер glow в средней части хвоста (плавный набор). */
    private static final float SIZE_MID = 0.42f;
    /** Размер glow на самом конце (пушистая кисть). */
    private static final float SIZE_TIP = 0.55f;
    private static final int BASE_ALPHA = 60;

    private static final float WAVE_SPEED = 6.5f;
    private static final float WAVE_AMPL = 0.16f;
    private static final float MOTION_LERP = 0.10f;

    private final Attributes parent;
    private final MinecraftClient mc = MinecraftClient.getInstance();

    private final java.util.Map<java.util.UUID, Float> motionState = new java.util.WeakHashMap<>();
    private BufferAllocator allocator;

    public AttributesTail(Attributes parent) {
        this.parent = parent;
    }

    public void deactivate() {
        this.motionState.clear();
    }

    public void onRender3D(EventRender3D event) {
        if (mc.player == null || mc.world == null) return;
        float tickDelta = event.getTickDelta();
        Vec3d cameraPos = event.getCameraPos();
        MatrixStack matrices = event.getMatrixStack();

        if (this.allocator == null) this.allocator = new BufferAllocator(262144);
        Immediate immediate = VertexConsumerProvider.immediate(this.allocator);
        VertexConsumer buffer = immediate.getBuffer(BLOOM_LAYER);

        boolean any = false;
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (!shouldRenderForPlayer(player)) continue;
            if (player == mc.player && mc.options.getPerspective().isFirstPerson()) continue;
            renderTail(player, tickDelta, matrices, cameraPos, buffer);
            any = true;
        }

        if (any) immediate.draw();
    }

    private boolean shouldRenderForPlayer(PlayerEntity player) {
        if (mc.player == null) return false;
        return player == mc.player;
    }

    private void renderTail(PlayerEntity player, float tickDelta, MatrixStack matrices, Vec3d cameraPos,
                            VertexConsumer buffer) {
        if (!player.isAlive() || player.isInvisible()) return;
        EntityPose pose = player.getPose();
        if (pose == EntityPose.SWIMMING || player.isInSwimmingPose() || player.isGliding()) return;

        // Интерполированная позиция игрока в мире.
        Vec3d lerpPos = player.getLerpedPos(tickDelta);
        double playerX = lerpPos.x;
        double playerY = lerpPos.y;
        double playerZ = lerpPos.z;
        float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, player.lastBodyYaw, player.bodyYaw);

        int theme = parent.resolveThemeColor();
        int rT = (theme >> 16) & 0xFF;
        int gT = (theme >>  8) & 0xFF;
        int bT =  theme        & 0xFF;
        int rW = Math.min(255, rT + 100);
        int gW = Math.min(255, gT + 100);
        int bW = Math.min(255, bT + 100);

        float baseY = player.isSneaking() ? 0.55f : 0.95f;
        // С конвенцией rotateY(180 - bodyYaw) локальный +Z = «за спиной» игрока.
        float baseZ = 0.08f;
        float poseScale = player.isSneaking() ? 0.85f : 1.0f;

        // Скорость движения для управления волной.
        java.util.UUID uuid = player.getUuid();
        double dx = player.getX() - player.lastRenderX;
        double dz = player.getZ() - player.lastRenderZ;
        double horizontalSpeed = Math.sqrt(dx * dx + dz * dz);
        float motionTarget = (float) Math.min(1.0, horizontalSpeed / 0.18);
        float motion = motionState.getOrDefault(uuid, 0.0f);
        motion = MathHelper.lerp(MOTION_LERP, motion, motionTarget);
        motionState.put(uuid, motion);

        float t = (System.currentTimeMillis() % 100000L) / 1000.0f;

        // ─── Строим localToWorld matrix с нуля (без зависимости от стека) ──
        // rotateY(180 - bodyYaw) — локальный +Z это «за спиной» игрока. После
        // rotateX(positive) локальный +Z спускается вниз (свисание хвоста).
        // При ходьбе хвост заметно поднимается вверх: в покое он свисает вниз
        // (~35°), при полном движении уходит выше горизонтали (~-25°).
        float baseDownPitch = 35.0f - 60.0f * motion;
        Matrix4f m = new Matrix4f();
        m.translate((float) playerX, (float) playerY, (float) playerZ);
        m.rotateY((float) Math.toRadians(180.0f - bodyYaw));
        m.translate(0.0f, baseY, baseZ);
        // Положительный pitch = хвост свисает вниз (локальный +Z → -Y).
        m.rotateX((float) Math.toRadians(baseDownPitch));

        float camYaw = mc.gameRenderer.getCamera().getYaw();
        float camPitch = mc.gameRenderer.getCamera().getPitch();

        Vector4f localPoint = new Vector4f();

        for (int i = 0; i < CURVE_POINTS; i++) {
            float p = i / (float)(CURVE_POINTS - 1);

            // Локальная позиция точки хвоста: +Z это «за спиной». Хвост
            // вытягивается вдоль локального +Z.
            float segZ = p * TAIL_LENGTH * poseScale;
            float wavePhase = t * WAVE_SPEED - p * 2.4f;
            float segX = MathHelper.sin(wavePhase) * WAVE_AMPL * motion * (0.4f + p);
            float segY = MathHelper.sin(wavePhase * 0.55f + p * 1.3f) * 0.04f * (0.3f + p);

            // Преобразуем в мировые координаты через нашу свежую матрицу.
            localPoint.set(segX, segY, segZ, 1.0f);
            localPoint.mul(m);
            double worldX = localPoint.x;
            double worldY = localPoint.y;
            double worldZ = localPoint.z;

            float whiteMix = MathHelper.clamp((p - 0.75f) * 4.0f, 0.0f, 1.0f);
            int r = MathHelper.lerp(whiteMix, rT, rW);
            int g = MathHelper.lerp(whiteMix, gT, gW);
            int b = MathHelper.lerp(whiteMix, bT, bW);

            // Размер: компактный у основания → расширяется к концу.
            float size;
            if (p < 0.6f) {
                size = MathHelper.lerp(p / 0.6f, SIZE_BASE, SIZE_MID);
            } else {
                float tipT = (p - 0.6f) / 0.4f;             // 0..1
                tipT = 1.0f - (1.0f - tipT) * (1.0f - tipT); // easeOutQuad
                size = MathHelper.lerp(tipT, SIZE_MID, SIZE_TIP);
            }
            size *= poseScale;
            int alpha = (int)(BASE_ALPHA * (0.95f + 0.10f * p));

            // 3 наложенных биллборда: гало, средний, плотный центр.
            drawBloomBillboard(buffer, matrices, cameraPos, worldX, worldY, worldZ,
                    size * 1.45f, camYaw, camPitch, r, g, b, alpha);
            drawBloomBillboard(buffer, matrices, cameraPos, worldX, worldY, worldZ,
                    size * 1.00f, camYaw, camPitch, r, g, b, alpha);
            drawBloomBillboard(buffer, matrices, cameraPos, worldX, worldY, worldZ,
                    size * 0.55f, camYaw, camPitch, r, g, b, alpha);
        }
    }

    /**
     * Рисует один billboard-quad повёрнутый к камере. Принимает мировые
     * координаты {@code worldX/Y/Z} (абсолютные) и сам вычитает {@code cameraPos}
     * для получения relative-to-camera координат. Вершины пишутся в общий
     * {@link VertexConsumer} (RenderLayer сам биндит текстуру и blend).
     */
    public static void drawBloomBillboard(VertexConsumer buffer, MatrixStack matrices, Vec3d cameraPos,
                                          double worldX, double worldY, double worldZ,
                                          float size, float camYaw, float camPitch,
                                          int r, int g, int b, int a) {
        if (a <= 0 || size <= 0f) return;
        float half = size * 0.5f;
        matrices.push();
        matrices.translate(worldX - cameraPos.x, worldY - cameraPos.y, worldZ - cameraPos.z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camYaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camPitch));
        Matrix4f mat = matrices.peek().getPositionMatrix();
        texVertex(buffer, mat, -half, -half, 0f, 1f, r, g, b, a);
        texVertex(buffer, mat, -half,  half, 0f, 0f, r, g, b, a);
        texVertex(buffer, mat,  half,  half, 1f, 0f, r, g, b, a);
        texVertex(buffer, mat,  half, -half, 1f, 1f, r, g, b, a);
        matrices.pop();
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
