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
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import vesence.event.render.EventRender3D;
import vesence.module.impl.visuals.Attributes;
import vesence.utils.friends.FriendStorage;

/**
 * Кристалл — вращающийся зелёный кристалл с glow над ником друга.
 *
 * <p>Порт косметики «Кристалл друга» из клиента RelevantPremium. Кристалл
 * рисуется над nametag'ом каждого игрока из списка друзей (см. {@link FriendStorage}),
 * чуть выше ванильного/клиентского ника. Адаптирован под render-API MC 1.21.11
 * (RenderPipeline + RenderLayer).
 */
public final class AttributesCrystal {

    private static final Identifier BLOOM = Identifier.of("vesence", "textures/targetesp/bloom.png");

    /** Тело кристалла — цветные треугольники без depth-test (видно сквозь стены). */
    private static final RenderPipeline CRYSTAL_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(new Snippet[]{RenderPipelines.POSITION_COLOR_SNIPPET})
                    .withLocation(Identifier.of("vesence", "pipeline/world/attributes_crystal"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.TRIANGLES)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .build());

    private static final RenderLayer CRYSTAL_LAYER = RenderLayer.of(
            "vesence_attributes_crystal",
            RenderSetup.builder(CRYSTAL_PIPELINE).expectedBufferSize(4096).translucent().build());

    /** Glow вокруг кристалла — аддитивные биллборды без depth-test. */
    private static final RenderPipeline GLOW_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(new Snippet[]{RenderPipelines.POSITION_TEX_COLOR_SNIPPET})
                    .withLocation(Identifier.of("vesence", "pipeline/world/attributes_crystal_glow"))
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build());

    private static final RenderLayer GLOW_LAYER = RenderLayer.of(
            "vesence_attributes_crystal_glow",
            RenderSetup.builder(GLOW_PIPELINE).expectedBufferSize(4096).translucent().texture("Sampler0", BLOOM).build());

    // Зелёный цвет (как в оригинале).
    private static final int GREEN_COLOR = 0xFF00FF66;
    private static final int GREEN_GLOW = 0x7000FF66;
    private static final int GREEN_GLOW_OUTER = 0x3800FF66;

    private static final float CRYSTAL_WIDTH = 0.12F;
    private static final float CRYSTAL_HEIGHT = 0.25F;
    /** Зазор от верха головы до нижнего кончика кристалла. */
    private static final float CLEARANCE = 1.10F;
    private static final float GLOW_INNER_SIZE = 0.45F;
    private static final float GLOW_OUTER_SIZE = 0.7F;

    private final Attributes parent;
    private final MinecraftClient mc = MinecraftClient.getInstance();

    private float rotationAngle = 0.0F;

    public AttributesCrystal(Attributes parent) {
        this.parent = parent;
    }

    public void deactivate() {
        this.rotationAngle = 0.0F;
    }

    public void onRender3D(EventRender3D event) {
        if (mc.player == null || mc.world == null) return;

        float partialTicks = event.getTickDelta();
        Vec3d cameraPos = event.getCameraPos();
        MatrixStack matrices = event.getMatrixStack();

        this.rotationAngle += 1.2F;

        float camYaw = mc.gameRenderer.getCamera().getYaw();
        float camPitch = mc.gameRenderer.getCamera().getPitch();

        // Считаем размещение кристалла для каждого друга заранее, затем рисуем
        // послойно (тело → glow). Один RenderLayer должен быть записан целиком
        // до переключения на следующий — иначе "Not building!".
        java.util.List<Placement> placements = new java.util.ArrayList<>();
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            if (!player.isAlive() || player.isInvisible()) continue;
            if (!FriendStorage.isFriend(player.getName().getString())) continue;
            if (!parent.shouldRenderFor(player, cameraPos)) continue;
            placements.add(computePlacement(player, partialTicks, cameraPos));
        }
        if (placements.isEmpty()) return;

        Immediate immediate = VertexConsumerProvider.immediate(parent.allocator());

        // ─── Тело кристалла ───────────────────────────────────────────────
        VertexConsumer crystalBuf = immediate.getBuffer(CRYSTAL_LAYER);
        for (Placement p : placements) {
            matrices.push();
            matrices.translate(p.worldX - cameraPos.x, p.worldY - cameraPos.y, p.worldZ - cameraPos.z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(this.rotationAngle));
            Matrix4f matrix = matrices.peek().getPositionMatrix();
            renderCrystalShape(crystalBuf, matrix, p.crystalW, p.crystalH, GREEN_COLOR);
            matrices.pop();
        }

        // ─── Glow ─────────────────────────────────────────────────────────
        VertexConsumer glowBuf = immediate.getBuffer(GLOW_LAYER);
        for (Placement p : placements) {
            drawGlowBillboard(glowBuf, matrices, cameraPos, p.worldX, p.worldY, p.worldZ, p.glowOuter, camYaw, camPitch, GREEN_GLOW_OUTER);
            drawGlowBillboard(glowBuf, matrices, cameraPos, p.worldX, p.worldY, p.worldZ, p.glowInner, camYaw, camPitch, GREEN_GLOW);
        }

        immediate.draw();
    }

    private Placement computePlacement(PlayerEntity player, float partialTicks, Vec3d cameraPos) {
        Vec3d pos = player.getLerpedPos(partialTicks);

        // Лёгкий рост кристалла на дистанции — чтобы над ником друга он читался и издалека.
        double anchorY = pos.y + player.getHeight() + 0.6;
        double dist = cameraPos.distanceTo(new Vec3d(pos.x, anchorY, pos.z));
        if (dist < 1.0) dist = 1.0;
        float scale = (float) Math.pow(dist / 3.0, 0.7);
        if (scale < 1.0F) scale = 1.0F;
        if (scale > 4.0F) scale = 4.0F;

        Placement p = new Placement();
        p.crystalW = CRYSTAL_WIDTH * scale;
        p.crystalH = CRYSTAL_HEIGHT * scale;
        p.glowInner = GLOW_INNER_SIZE * scale;
        p.glowOuter = GLOW_OUTER_SIZE * scale;

        // Нижний кончик кристалла над ником (CLEARANCE выше макушки) + покачивание.
        float bottomTipOffset = CLEARANCE + MathHelper.sin((player.age + partialTicks) * 0.08F) * 0.04F;
        p.worldX = pos.x;
        p.worldY = pos.y + player.getHeight() + bottomTipOffset + p.crystalH;
        p.worldZ = pos.z;
        return p;
    }

    private static final class Placement {
        double worldX, worldY, worldZ;
        float crystalW, crystalH, glowInner, glowOuter;
    }

    private void renderCrystalShape(VertexConsumer buffer, Matrix4f matrix, float w, float h, int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;

        // Верхняя пирамида (4 грани).
        colorVertex(buffer, matrix, 0.0F, h, 0.0F, r, g, b, a);
        colorVertex(buffer, matrix, w, 0.0F, 0.0F, r, g, b, a);
        colorVertex(buffer, matrix, 0.0F, 0.0F, w, r, g, b, a);

        colorVertex(buffer, matrix, 0.0F, h, 0.0F, r, g, b, a);
        colorVertex(buffer, matrix, 0.0F, 0.0F, w, r, g, b, a);
        colorVertex(buffer, matrix, -w, 0.0F, 0.0F, r, g, b, a);

        colorVertex(buffer, matrix, 0.0F, h, 0.0F, r, g, b, a);
        colorVertex(buffer, matrix, -w, 0.0F, 0.0F, r, g, b, a);
        colorVertex(buffer, matrix, 0.0F, 0.0F, -w, r, g, b, a);

        colorVertex(buffer, matrix, 0.0F, h, 0.0F, r, g, b, a);
        colorVertex(buffer, matrix, 0.0F, 0.0F, -w, r, g, b, a);
        colorVertex(buffer, matrix, w, 0.0F, 0.0F, r, g, b, a);

        // Нижняя пирамида (4 грани).
        colorVertex(buffer, matrix, 0.0F, -h, 0.0F, r, g, b, a);
        colorVertex(buffer, matrix, 0.0F, 0.0F, w, r, g, b, a);
        colorVertex(buffer, matrix, w, 0.0F, 0.0F, r, g, b, a);

        colorVertex(buffer, matrix, 0.0F, -h, 0.0F, r, g, b, a);
        colorVertex(buffer, matrix, -w, 0.0F, 0.0F, r, g, b, a);
        colorVertex(buffer, matrix, 0.0F, 0.0F, w, r, g, b, a);

        colorVertex(buffer, matrix, 0.0F, -h, 0.0F, r, g, b, a);
        colorVertex(buffer, matrix, 0.0F, 0.0F, -w, r, g, b, a);
        colorVertex(buffer, matrix, -w, 0.0F, 0.0F, r, g, b, a);

        colorVertex(buffer, matrix, 0.0F, -h, 0.0F, r, g, b, a);
        colorVertex(buffer, matrix, w, 0.0F, 0.0F, r, g, b, a);
        colorVertex(buffer, matrix, 0.0F, 0.0F, -w, r, g, b, a);
    }

    private void drawGlowBillboard(VertexConsumer buffer, MatrixStack matrices, Vec3d cameraPos,
                                   double worldX, double worldY, double worldZ,
                                   float size, float camYaw, float camPitch, int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;
        if (a <= 0) return;
        float half = size * 0.5F;

        matrices.push();
        matrices.translate(worldX - cameraPos.x, worldY - cameraPos.y, worldZ - cameraPos.z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camYaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camPitch));
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        texVertex(buffer, matrix, -half, -half, 0f, 1f, r, g, b, a);
        texVertex(buffer, matrix, -half,  half, 0f, 0f, r, g, b, a);
        texVertex(buffer, matrix,  half,  half, 1f, 0f, r, g, b, a);
        texVertex(buffer, matrix,  half, -half, 1f, 1f, r, g, b, a);
        matrices.pop();
    }

    private static void colorVertex(VertexConsumer buffer, Matrix4f matrix, float x, float y, float z,
                                    int r, int g, int b, int a) {
        buffer.vertex(matrix, x, y, z).color(r, g, b, a);
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
