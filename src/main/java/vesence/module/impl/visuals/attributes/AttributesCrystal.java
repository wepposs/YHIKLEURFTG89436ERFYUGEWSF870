package vesence.module.impl.visuals.attributes;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline.Snippet;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import com.mojang.math.Axis;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import vesence.event.render.EventRender3D;
import vesence.module.impl.visuals.Attributes;
import vesence.utils.friends.FriendStorage;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import vesence.module.impl.visuals.Immediate;
import net.minecraft.client.renderer.BindGroupLayouts;

/**
 * Кристалл — вращающийся зелёный кристалл с glow над ником друга.
 *
 * <p>Порт косметики «Кристалл друга» из клиента RelevantPremium. Кристалл
 * рисуется над nametag'ом каждого игрока из списка друзей (см. {@link FriendStorage}),
 * чуть выше ванильного/клиентского ника. Адаптирован под render-API MC 1.21.11
 * (RenderPipeline + RenderType).
 */
public final class AttributesCrystal {

    private static final Identifier BLOOM = Identifier.fromNamespaceAndPath("vesence", "textures/targetesp/bloom.png");

    /** Тело кристалла — цветные треугольники без depth-test (видно сквозь стены). */
    private static final RenderPipeline CRYSTAL_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(new Snippet[]{RenderPipelines.MATRICES_FOG_SNIPPET})
                    .withLocation(Identifier.fromNamespaceAndPath("vesence", "pipeline/world/attributes_crystal"))
                    .withVertexShader("core/position_color")
                    .withFragmentShader("core/position_color")
                    .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)                    .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                    .withCull(false)
                    .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .build());

    private static final RenderType CRYSTAL_LAYER = RenderType.create(
            "vesence_attributes_crystal",
            RenderSetup.builder(CRYSTAL_PIPELINE).createRenderSetup());

    /** Glow вокруг кристалла — аддитивные биллборды без depth-test. */
    private static final RenderPipeline GLOW_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(new Snippet[]{RenderPipelines.MATRICES_FOG_SNIPPET})
                    .withLocation(Identifier.fromNamespaceAndPath("vesence", "pipeline/world/attributes_crystal_glow"))
                    .withVertexShader("core/position_tex_color")
                    .withFragmentShader("core/position_tex_color")
                    .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                    .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)                    .withPrimitiveTopology(PrimitiveTopology.QUADS)
                    .withCull(false)
                    .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                    .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
                    .build());

    private static final RenderType GLOW_LAYER = RenderType.create(
            "vesence_attributes_crystal_glow",
            RenderSetup.builder(GLOW_PIPELINE).withTexture("Sampler0", BLOOM).createRenderSetup());

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
    private final Minecraft mc = Minecraft.getInstance();

    private float rotationAngle = 0.0F;

    public AttributesCrystal(Attributes parent) {
        this.parent = parent;
    }

    public void deactivate() {
        this.rotationAngle = 0.0F;
    }

    public void onRender3D(EventRender3D event) {
        if (mc.player == null || mc.level == null) return;

        float partialTicks = event.getTickDelta();
        Vec3 cameraPos = event.getCameraPos();
        PoseStack matrices = event.getMatrixStack();

        this.rotationAngle += 1.2F;

        float camYaw = mc.gameRenderer.mainCamera().yRot();
        float camPitch = mc.gameRenderer.mainCamera().xRot();

        // Считаем размещение кристалла для каждого друга заранее, затем рисуем
        // послойно (тело → glow). Один RenderType должен быть записан целиком
        // до переключения на следующий — иначе "Not building!".
        java.util.List<Placement> placements = new java.util.ArrayList<>();
        for (Player player : mc.level.players()) {
            if (player == mc.player) continue;
            if (!player.isAlive() || player.isInvisible()) continue;
            if (!FriendStorage.isFriend(player.getName().getString())) continue;
            if (!parent.shouldRenderFor(player, cameraPos)) continue;
            placements.add(computePlacement(player, partialTicks, cameraPos));
        }
        if (placements.isEmpty()) return;

        Immediate immediate = new Immediate();

        // ─── Тело кристалла ───────────────────────────────────────────────
        VertexConsumer crystalBuf = immediate.getBuffer(CRYSTAL_LAYER);
        for (Placement p : placements) {
            matrices.pushPose();
            matrices.translate(p.worldX - cameraPos.x, p.worldY - cameraPos.y, p.worldZ - cameraPos.z);
            matrices.rotate(Axis.YP.rotationDegrees(this.rotationAngle));
            Matrix4f matrix = matrices.last().pose();
            renderCrystalShape(crystalBuf, matrix, p.crystalW, p.crystalH, GREEN_COLOR);
            matrices.popPose();
        }

        // ─── Glow ─────────────────────────────────────────────────────────
        VertexConsumer glowBuf = immediate.getBuffer(GLOW_LAYER);
        for (Placement p : placements) {
            drawGlowBillboard(glowBuf, matrices, cameraPos, p.worldX, p.worldY, p.worldZ, p.glowOuter, camYaw, camPitch, GREEN_GLOW_OUTER);
            drawGlowBillboard(glowBuf, matrices, cameraPos, p.worldX, p.worldY, p.worldZ, p.glowInner, camYaw, camPitch, GREEN_GLOW);
        }

        immediate.draw();
    }

    private Placement computePlacement(Player player, float partialTicks, Vec3 cameraPos) {
        Vec3 pos = player.getPosition(partialTicks);

        // Лёгкий рост кристалла на дистанции — чтобы над ником друга он читался и издалека.
        double anchorY = pos.y + player.getBbHeight() + 0.6;
        double dist = cameraPos.distanceTo(new Vec3(pos.x, anchorY, pos.z));
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
        float bottomTipOffset = CLEARANCE + Mth.sin((player.tickCount + partialTicks) * 0.08F) * 0.04F;
        p.worldX = pos.x;
        p.worldY = pos.y + player.getBbHeight() + bottomTipOffset + p.crystalH;
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

    private void drawGlowBillboard(VertexConsumer buffer, PoseStack matrices, Vec3 cameraPos,
                                   double worldX, double worldY, double worldZ,
                                   float size, float camYaw, float camPitch, int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;
        if (a <= 0) return;
        float half = size * 0.5F;

        matrices.pushPose();
        matrices.translate(worldX - cameraPos.x, worldY - cameraPos.y, worldZ - cameraPos.z);
        matrices.rotate(Axis.YP.rotationDegrees(-camYaw));
        matrices.rotate(Axis.XP.rotationDegrees(camPitch));
        Matrix4f matrix = matrices.last().pose();
        texVertex(buffer, matrix, -half, -half, 0f, 1f, r, g, b, a);
        texVertex(buffer, matrix, -half,  half, 0f, 0f, r, g, b, a);
        texVertex(buffer, matrix,  half,  half, 1f, 0f, r, g, b, a);
        texVertex(buffer, matrix,  half, -half, 1f, 1f, r, g, b, a);
        matrices.popPose();
    }

    private static void colorVertex(VertexConsumer buffer, Matrix4f matrix, float x, float y, float z,
                                    int r, int g, int b, int a) {
        buffer.addVertex(matrix, x, y, z).setColor(r, g, b, a);
    }

    private static void texVertex(VertexConsumer buffer, Matrix4f matrix, float x, float y,
                                  float u, float v, int r, int g, int b, int a) {
        buffer.addVertex(matrix, x, y, 0f)
                .setUv(u, v)
                .setColor(r, g, b, a)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880)
                .setNormal(0f, 0f, 1f);
    }
}
