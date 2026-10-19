package vesence.module.impl.visuals.attributes;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.render.MultiBufferSource;
import net.minecraft.client.render.MultiBufferSource.Immediate;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.entity.EntityPose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import vesence.event.render.EventRender3D;
import vesence.module.impl.visuals.Attributes;
import vesence.utils.render.ColorUtil;

/**
 * Шея — раскрытый «воротник» ящера (gecko frill), прикреплённый к голове
 * игрока. Следует за головой по yaw и pitch, лежит полу-кольцом за затылком.
 *
 * <p>Порт косметики «Шея» из клиента RelevantPremium. Оригинал использовал
 * кастомный шейдер {@code batWings} для мембраны и immediate-mode Tessellator;
 * здесь мембрана и каркас перерисованы через RenderPipeline/RenderType
 * (POSITION_COLOR) в цвете темы клиента, совместимо с MC 1.21.11.
 */
public final class AttributesNeck {

    /** Мембрана воротника — translucent цветные треугольники. */
    private static final RenderPipeline MEMBRANE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(new Snippet[]{RenderPipelines.POSITION_COLOR_SNIPPET})
                    .withLocation(ResourceLocation.of("vesence", "pipeline/world/attributes_neck_membrane"))
                    .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, DrawMode.TRIANGLES)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .build());

    private static final RenderType MEMBRANE_LAYER = RenderType.of(
            "vesence_attributes_neck_membrane",
            RenderSetup.builder(MEMBRANE_PIPELINE).expectedBufferSize(16384).translucent().build());

    /** Каркас (спицы + контур) — цветные линии. */
    private static final RenderPipeline LINES_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(new Snippet[]{RenderPipelines.POSITION_COLOR_SNIPPET})
                    .withLocation(ResourceLocation.of("vesence", "pipeline/world/attributes_neck_lines"))
                    .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, DrawMode.DEBUG_LINES)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .build());

    private static final RenderType LINES_LAYER = RenderType.of(
            "vesence_attributes_neck_lines",
            RenderSetup.builder(LINES_PIPELINE).expectedBufferSize(8192).translucent().build());

    private final Attributes parent;
    private final Minecraft mc = Minecraft.getInstance();

    private ByteBufferBuilder allocator;

    public AttributesNeck(Attributes parent) {
        this.parent = parent;
    }

    public void deactivate() {}

    public void onRender3D(EventRender3D event) {
        if (mc.player == null || mc.level == null) return;
        Player player = mc.player;
        if (player == mc.player && mc.options.getPerspective().isFirstPerson()) return;
        if (!player.isAlive() || player.isInvisible()) return;
        EntityPose pose = player.getPose();
        if (pose == EntityPose.SWIMMING || player.isInSwimmingPose() || player.isGliding()) return;

        float tickDelta = event.getTickDelta();
        Vec3 cameraPos = event.getCameraPos();
        PoseStack matrices = event.getMatrixStack();

        Vec3 pos = player.getLerpedPos(tickDelta);
        double px = pos.x - cameraPos.x;
        double py = pos.y - cameraPos.y;
        double pz = pos.z - cameraPos.z;

        float headYaw = Mth.lerpAngleDegrees(tickDelta, player.lastHeadYaw, player.headYaw);
        float pitch = player.getPitch(tickDelta);
        float headBaseY = player.isSneaking() ? 1.27F : 1.50F;
        float headBaseZ = 0.0F;
        float scale = 1.0F;

        int theme = ColorUtil.theme(255);
        int outlineColor = ColorUtil.replAlpha(ColorUtil.multDark(theme, 0.50F), 235);
        int boneColor = ColorUtil.replAlpha(ColorUtil.interpolate(theme, 0xFFFFFFFF, 0.20), 200);

        matrices.pushPose();
        matrices.translate(px, py, pz);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - headYaw));
        matrices.translate(0.0F, headBaseY, headBaseZ);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-pitch));
        matrices.scale(scale, scale, scale);
        // Наклон всей плоскости веера назад на 35°.
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(35.0F));
        Matrix4f matrix = matrices.last().getPositionMatrix();
        matrices.popPose();

        Vec3[] rightContour = computeGeckoFrillContour(+1.0F, 1.0F);
        Vec3[] leftContour = computeGeckoFrillContour(-1.0F, 1.0F);

        if (this.allocator == null) this.allocator = new ByteBufferBuilder(32768);
        Immediate immediate = MultiBufferSource.immediate(this.allocator);

        // Мембрана (theme-цвет, alpha-градиент от центра к краям).
        VertexConsumer membraneBuf = immediate.getBuffer(MEMBRANE_LAYER);
        drawFrillMembrane(membraneBuf, matrix, rightContour, theme);
        drawFrillMembrane(membraneBuf, matrix, leftContour, theme);

        // Спицы + контур.
        VertexConsumer lineBuf = immediate.getBuffer(LINES_LAYER);
        drawFrillSpines(lineBuf, matrix, rightContour, boneColor);
        drawFrillSpines(lineBuf, matrix, leftContour, boneColor);
        drawFrillOutline(lineBuf, matrix, rightContour, outlineColor);
        drawFrillOutline(lineBuf, matrix, leftContour, outlineColor);

        immediate.draw();
    }

    /** Считает контур половины «воротника». side = +1 правая, -1 левая. */
    private Vec3[] computeGeckoFrillContour(float side, float fold) {
        Vec3 neckBase = new Vec3(side * 0.10, 0.0, 0.0);
        Vec3 neckBottom = new Vec3(side * 0.18, -0.08, 0.0);

        float[] rayAngles = {5.0F, 30.0F, 55.0F, 80.0F, 100.0F};
        float[] rayLengths = {0.50F, 0.85F, 1.05F, 0.90F, 0.55F};

        java.util.List<Vec3> contour = new java.util.ArrayList<>();
        contour.add(neckBase);

        Vec3[] tips = new Vec3[rayAngles.length];
        for (int i = 0; i < rayAngles.length; i++) {
            float angle = rayAngles[i];
            double rad = Math.toRadians(angle);
            float lengthOpen = rayLengths[i];
            float length = lengthOpen * Mth.lerp(fold, 0.55F, 1.0F);
            double rx = side * (float) Math.sin(rad) * length;
            double ry = (float) Math.cos(rad) * length;
            tips[i] = new Vec3(rx, ry, 0.0);
        }

        contour.add(tips[0]);
        for (int i = 0; i < tips.length - 1; i++) {
            appendBatArch(contour, tips[i], tips[i + 1], neckBase, 4, 0.18F * fold);
            contour.add(tips[i + 1]);
        }

        contour.add(neckBottom);
        return contour.toArray(new Vec3[0]);
    }

    /** Triangle fan от neckBase (двусторонний) с alpha-градиентом от центра. */
    private void drawFrillMembrane(VertexConsumer buffer, Matrix4f matrix, Vec3[] contour, int theme) {
        if (contour.length < 3) return;
        Vec3 origin = contour[0];

        double maxDist = 0.0;
        for (int i = 1; i < contour.length; i++) {
            double d = contour[i].distanceTo(origin);
            if (d > maxDist) maxDist = d;
        }
        if (maxDist < 1e-4) maxDist = 1.0;

        int r = ColorUtil.red(theme);
        int g = ColorUtil.green(theme);
        int b = ColorUtil.blue(theme);
        int aOrigin = 130;

        int n = contour.length;
        for (int i = 1; i < n - 1; i++) {
            Vec3 p1 = contour[i];
            Vec3 p2 = contour[i + 1];
            int a1 = (int) ((1.0 - p1.distanceTo(origin) / maxDist) * 90.0 + 30.0);
            int a2 = (int) ((1.0 - p2.distanceTo(origin) / maxDist) * 90.0 + 30.0);

            buffer.vertex(matrix, (float) origin.x, (float) origin.y, (float) origin.z).color(r, g, b, aOrigin);
            buffer.vertex(matrix, (float) p1.x, (float) p1.y, (float) p1.z).color(r, g, b, a1);
            buffer.vertex(matrix, (float) p2.x, (float) p2.y, (float) p2.z).color(r, g, b, a2);
            // Обратная сторона.
            buffer.vertex(matrix, (float) origin.x, (float) origin.y, (float) origin.z).color(r, g, b, aOrigin);
            buffer.vertex(matrix, (float) p2.x, (float) p2.y, (float) p2.z).color(r, g, b, a2);
            buffer.vertex(matrix, (float) p1.x, (float) p1.y, (float) p1.z).color(r, g, b, a1);
        }
    }

    /** Спицы — линии от neckBase к каждому из 5 кончиков лучей. */
    private void drawFrillSpines(VertexConsumer buffer, Matrix4f matrix, Vec3[] contour, int color) {
        if (contour.length < 7) return;
        Vec3 origin = contour[0];
        int r = ColorUtil.red(color);
        int g = ColorUtil.green(color);
        int b = ColorUtil.blue(color);
        int a = ColorUtil.alpha(color);
        int stride = 4;
        for (int f = 0; f < 5; f++) {
            int idx = 1 + f * stride;
            if (idx >= contour.length) break;
            Vec3 tip = contour[idx];
            buffer.vertex(matrix, (float) origin.x, (float) origin.y, (float) origin.z).color(r, g, b, a);
            buffer.vertex(matrix, (float) tip.x, (float) tip.y, (float) tip.z).color(r, g, b, (int) (a * 0.55F));
        }
    }

    /** Контур-line-loop по всем точкам. */
    private void drawFrillOutline(VertexConsumer buffer, Matrix4f matrix, Vec3[] contour, int color) {
        if (contour.length < 2) return;
        int r = ColorUtil.red(color);
        int g = ColorUtil.green(color);
        int b = ColorUtil.blue(color);
        int a = ColorUtil.alpha(color);

        for (int i = 0; i < contour.length; i++) {
            Vec3 cur = contour[i];
            Vec3 next = contour[(i + 1) % contour.length];
            buffer.vertex(matrix, (float) cur.x, (float) cur.y, (float) cur.z).color(r, g, b, a);
            buffer.vertex(matrix, (float) next.x, (float) next.y, (float) next.z).color(r, g, b, a);
        }
    }

    /** Добавляет точки вогнутой арки между двумя точками с прогибом к origin. */
    private void appendBatArch(java.util.List<Vec3> out, Vec3 a, Vec3 b, Vec3 origin, int segments, float depth) {
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        double dz = b.z - a.z;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-4) return;
        double mx = (a.x + b.x) * 0.5;
        double my = (a.y + b.y) * 0.5;
        double mz = (a.z + b.z) * 0.5;
        double pullX = origin.x - mx;
        double pullY = origin.y - my;
        double pullZ = origin.z - mz;
        double pullLen = Math.sqrt(pullX * pullX + pullY * pullY + pullZ * pullZ);
        if (pullLen < 1e-4) return;
        double nx = pullX / pullLen;
        double ny = pullY / pullLen;
        double nz = pullZ / pullLen;

        double archDepth = len * depth;

        for (int i = 1; i < segments; i++) {
            double tParam = i / (double) segments;
            double lx = a.x + dx * tParam;
            double ly = a.y + dy * tParam;
            double lz = a.z + dz * tParam;
            double bow = Math.sin(tParam * Math.PI) * archDepth;
            out.add(new Vec3(lx + nx * bow, ly + ny * bow, lz + nz * bow));
        }
    }
}
