package vesence.module.impl.visuals.attributes;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import vesence.event.render.EventRender3D;
import vesence.module.impl.visuals.Attributes;
import vesence.utils.friends.FriendStorage;
import vesence.utils.render.ColorUtil;

/**
 * Крылья — полупрозрачные крылья за спиной в цвете темы клиента.
 *
 * <p>Порт пользовательского модуля {@code Wings} (Forge 1.16.5, GL immediate-mode)
 * на render-API MC 1.21.11: заливка — translucent-слой, свечение — аддитивный
 * слой, контур и «жилки» — линии. Рисуется у себя и у друзей.
 */
public final class AttributesWings {

    private static final float DEFAULT_SPREAD = 8.0F;
    private static final int DEFAULT_ALPHA = 220;

    private static final WingPoint[] SHAPE = {
        new WingPoint(0.08F, 0.10F, 0.88F),
        new WingPoint(0.28F, 0.34F, 0.78F),
        new WingPoint(0.56F, 0.82F, 0.62F),
        new WingPoint(0.86F, 0.30F, 0.52F),
        new WingPoint(1.14F, 0.46F, 0.40F),
        new WingPoint(1.24F, 0.04F, 0.30F),
        new WingPoint(1.02F, -0.18F, 0.28F),
        new WingPoint(1.18F, -0.64F, 0.22F),
        new WingPoint(0.86F, -0.46F, 0.20F),
        new WingPoint(0.80F, -0.98F, 0.14F),
        new WingPoint(0.54F, -0.74F, 0.16F),
        new WingPoint(0.30F, -1.16F, 0.12F),
        new WingPoint(0.10F, -0.54F, 0.18F)
    };

    /** Заливка крыла — translucent цветные треугольники. */
    private static final RenderPipeline FILL_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(new Snippet[]{RenderPipelines.POSITION_COLOR_SNIPPET})
                    .withLocation(Identifier.of("vesence", "pipeline/world/attributes_wings_fill"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.TRIANGLES)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .build());

    private static final RenderLayer FILL_LAYER = RenderLayer.of(
            "vesence_attributes_wings_fill",
            RenderSetup.builder(FILL_PIPELINE).expectedBufferSize(32768).translucent().build());

    /** Свечение крыла — аддитивные (LIGHTNING) треугольники. */
    private static final RenderPipeline GLOW_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(new Snippet[]{RenderPipelines.POSITION_COLOR_SNIPPET})
                    .withLocation(Identifier.of("vesence", "pipeline/world/attributes_wings_glow"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.TRIANGLES)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build());

    private static final RenderLayer GLOW_LAYER = RenderLayer.of(
            "vesence_attributes_wings_glow",
            RenderSetup.builder(GLOW_PIPELINE).expectedBufferSize(32768).translucent().build());

    /** Контур и жилки — цветные линии. */
    private static final RenderPipeline LINES_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(new Snippet[]{RenderPipelines.POSITION_COLOR_SNIPPET})
                    .withLocation(Identifier.of("vesence", "pipeline/world/attributes_wings_lines"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.DEBUG_LINES)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .build());

    private static final RenderLayer LINES_LAYER = RenderLayer.of(
            "vesence_attributes_wings_lines",
            RenderSetup.builder(LINES_PIPELINE).expectedBufferSize(8192).translucent().build());

    private final Attributes parent;
    private final MinecraftClient mc = MinecraftClient.getInstance();

    public AttributesWings(Attributes parent) {
        this.parent = parent;
    }

    public void deactivate() {}

    public void onRender3D(EventRender3D event) {
        if (mc.player == null || mc.world == null) return;

        float tickDelta = event.getTickDelta();
        Vec3d cameraPos = event.getCameraPos();
        MatrixStack matrices = event.getMatrixStack();

        int base = ColorUtil.theme(255);
        int glow = ColorUtil.interpolate(base, 0xFFFFFFFF, 0.28);
        int core = ColorUtil.interpolate(base, 0xFFFFFFFF, 0.55);

        // Собираем матрицы всех сторон крыльев всех игроков заранее, затем
        // рисуем послойно (заливка → свечение → линии). Переключать RenderLayer
        // можно только последовательно: писать в один слой полностью, потом
        // в следующий — иначе BufferBuilder бросает "Not building!".
        java.util.List<WingSide> sides = new java.util.ArrayList<>();
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (!shouldRender(player)) continue;
            if (!parent.shouldRenderFor(player, cameraPos)) continue;
            collectWingSides(player, tickDelta, cameraPos, matrices, sides);
        }
        if (sides.isEmpty()) return;

        Immediate immediate = VertexConsumerProvider.immediate(parent.allocator());

        // Заливка (translucent).
        VertexConsumer fillBuf = immediate.getBuffer(FILL_LAYER);
        for (WingSide s : sides) {
            drawWingLayer(fillBuf, s.matrix, s.side, 1.0f, setAlpha(base, 180), setAlpha(base, 10));
        }

        // Свечение (additive).
        VertexConsumer glowBuf = immediate.getBuffer(GLOW_LAYER);
        for (WingSide s : sides) {
            drawWingLayer(glowBuf, s.matrix, s.side, 1.22f, setAlpha(glow, (int) (DEFAULT_ALPHA * 0.22f)), setAlpha(glow, 0));
            drawWingLayer(glowBuf, s.matrix, s.side, 0.84f, setAlpha(core, (int) (DEFAULT_ALPHA * 0.26f)), setAlpha(core, 0));
        }

        // Контур + жилки.
        VertexConsumer lineBuf = immediate.getBuffer(LINES_LAYER);
        for (WingSide s : sides) {
            drawWingOutline(lineBuf, s.matrix, s.side, 1.0f, setAlpha(base, (int) (DEFAULT_ALPHA * 0.62f)));
            drawWingRibs(lineBuf, s.matrix, s.side, 0.96f, setAlpha(glow, (int) (DEFAULT_ALPHA * 0.20f)));
        }

        immediate.draw();
    }

    private boolean shouldRender(PlayerEntity player) {
        if (mc.player == null) return false;
        if (!player.isAlive() || player.isInvisible()) return false;
        EntityPose pose = player.getPose();
        if (pose == EntityPose.SWIMMING || player.isInSwimmingPose()) return false;
        if (player == mc.player) {
            return !mc.options.getPerspective().isFirstPerson();
        }
        return FriendStorage.isFriend(player.getName().getString());
    }

    private void collectWingSides(PlayerEntity player, float tickDelta, Vec3d camera, MatrixStack matrices,
                                  java.util.List<WingSide> out) {
        WingPose pose = resolvePose(player, tickDelta);
        if (pose == null) return;

        Vec3d pos = player.getLerpedPos(tickDelta);
        double x = pos.x - camera.x;
        double y = pos.y - camera.y;
        double z = pos.z - camera.z;

        float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, player.lastBodyYaw, player.bodyYaw);
        float move = (float) MathHelper.clamp(player.getVelocity().horizontalLength() / 0.18, 0.0, 1.0);

        float flap = (float) Math.sin((player.age + tickDelta) * pose.flapSpeed) * pose.flapAmplitude;
        float open = (DEFAULT_SPREAD + flap + move * pose.motionSpreadBoost) * pose.openMultiplier;
        float wingScale = pose.scaleMultiplier;

        matrices.push();
        matrices.translate(x, y, z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - bodyYaw));
        if (pose.preTranslateY != 0f || pose.preTranslateZ != 0f)
            matrices.translate(0f, pose.preTranslateY, pose.preTranslateZ);
        if (pose.pitchRotation != 0f)
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pose.pitchRotation));
        if (pose.rollRotation != 0f)
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(pose.rollRotation));
        matrices.translate(0f, pose.anchorY, pose.anchorZ);
        matrices.scale(wingScale, wingScale, wingScale);

        for (float side : new float[]{-1f, 1f}) {
            matrices.push();
            matrices.translate(side * pose.sideOffset, pose.sideYOffset, pose.sideZOffset);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * open));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * pose.sideRoll));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pose.sidePitch));
            out.add(new WingSide(new Matrix4f(matrices.peek().getPositionMatrix()), side));
            matrices.pop();
        }
        matrices.pop();
    }

    private void drawWingLayer(VertexConsumer buffer, Matrix4f matrix, float side, float scale, int rootColor, int edgeColor) {
        for (int i = 0; i < SHAPE.length; i++) {
            WingPoint cur = SHAPE[i];
            WingPoint next = SHAPE[(i + 1) % SHAPE.length];
            vertex(buffer, matrix, 0f, 0f, 0f, rootColor);
            vertex(buffer, matrix, side * cur.x * scale, cur.y * scale, 0f, applyPointAlpha(edgeColor, cur.alphaMul));
            vertex(buffer, matrix, side * next.x * scale, next.y * scale, 0f, applyPointAlpha(edgeColor, next.alphaMul));
        }
    }

    private void drawWingOutline(VertexConsumer buffer, Matrix4f matrix, float side, float scale, int color) {
        for (int i = 0; i < SHAPE.length; i++) {
            WingPoint cur = SHAPE[i];
            WingPoint next = SHAPE[(i + 1) % SHAPE.length];
            vertex(buffer, matrix, side * cur.x * scale, cur.y * scale, 0f, color);
            vertex(buffer, matrix, side * next.x * scale, next.y * scale, 0f, color);
        }
    }

    private void drawWingRibs(VertexConsumer buffer, Matrix4f matrix, float side, float scale, int color) {
        int[] ribIndices = {2, 4, 7, 9, 11};
        int rootColor = setAlpha(color, Math.max(8, (int)(alpha(color) * 0.75f)));
        for (int idx : ribIndices) {
            WingPoint point = SHAPE[idx];
            vertex(buffer, matrix, 0f, 0f, 0f, rootColor);
            vertex(buffer, matrix, side * point.x * scale, point.y * scale, 0f, applyPointAlpha(color, point.alphaMul));
        }
    }

    private static int applyPointAlpha(int color, float multiplier) {
        return setAlpha(color, Math.max(0, Math.min(255, (int)(alpha(color) * multiplier))));
    }

    private static int setAlpha(int color, int a) {
        return ColorUtil.replAlpha(color, MathHelper.clamp(a, 0, 255));
    }

    private static int alpha(int color) { return ColorUtil.alpha(color); }

    private static void vertex(VertexConsumer buffer, Matrix4f matrix, float x, float y, float z, int color) {
        buffer.vertex(matrix, x, y, z)
                .color(ColorUtil.red(color), ColorUtil.green(color), ColorUtil.blue(color), ColorUtil.alpha(color));
    }

    private WingPose resolvePose(PlayerEntity player, float tickDelta) {
        float pitch = player.getPitch(tickDelta);

        if (player.isGliding()) {
            float flightTicks = (float) player.age + tickDelta;
            float flightProgress = MathHelper.clamp(flightTicks * flightTicks / 100f, 0f, 1f);
            float pitchRotation = flightProgress * (-90f - pitch);
            return new WingPose(0.34f, 0.46f, 0f, 0f, pitchRotation, 0f,
                0.76f, 0.92f, 0.10f, 0.58f, 0.05f, 0.06f, -5f, -2f, 0.13f);
        }

        if (player.isTouchingWater()) {
            return null;
        }

        if (player.isSneaking()) {
            return new WingPose(0f, 0f, 0.96f, 0.10f, 18f, 0f,
                1f, 1f, 0.18f, 4.5f, 0.06f, 0.02f, -11f, -4f, 0.12f);
        }

        return new WingPose(0f, 0f, 1.38f, 0.10f, 0f, 0f,
            1f, 1f, 0.18f, 4.5f, 0.06f, 0.02f, -11f, -4f, 0.12f);
    }

    private static final class WingSide {
        final Matrix4f matrix;
        final float side;
        WingSide(Matrix4f matrix, float side) {
            this.matrix = matrix;
            this.side = side;
        }
    }

    private static final class WingPoint {
        final float x, y, alphaMul;
        WingPoint(float x, float y, float alphaMul) {
            this.x = x;
            this.y = y;
            this.alphaMul = alphaMul;
        }
    }

    private static final class WingPose {
        final float preTranslateY, preTranslateZ;
        final float anchorY, anchorZ;
        final float pitchRotation, rollRotation;
        final float openMultiplier, scaleMultiplier;
        final float motionSpreadBoost, flapAmplitude;
        final float sideOffset, sideYOffset, sideZOffset;
        final float sideRoll, sidePitch, flapSpeed;

        WingPose(float preTranslateY, float preTranslateZ, float anchorY, float anchorZ,
                 float pitchRotation, float rollRotation, float openMultiplier, float scaleMultiplier,
                 float motionSpreadBoost, float flapAmplitude, float sideOffset, float sideZOffset,
                 float sideRoll, float sidePitch, float flapSpeed) {
            this(preTranslateY, preTranslateZ, anchorY, anchorZ, pitchRotation, rollRotation,
                openMultiplier, scaleMultiplier, motionSpreadBoost, flapAmplitude,
                sideOffset, 0f, sideZOffset, sideRoll, sidePitch, flapSpeed);
        }

        WingPose(float preTranslateY, float preTranslateZ, float anchorY, float anchorZ,
                 float pitchRotation, float rollRotation, float openMultiplier, float scaleMultiplier,
                 float motionSpreadBoost, float flapAmplitude, float sideOffset, float sideYOffset,
                 float sideZOffset, float sideRoll, float sidePitch, float flapSpeed) {
            this.preTranslateY = preTranslateY;
            this.preTranslateZ = preTranslateZ;
            this.anchorY = anchorY;
            this.anchorZ = anchorZ;
            this.pitchRotation = pitchRotation;
            this.rollRotation = rollRotation;
            this.openMultiplier = openMultiplier;
            this.scaleMultiplier = scaleMultiplier;
            this.motionSpreadBoost = motionSpreadBoost;
            this.flapAmplitude = flapAmplitude;
            this.sideOffset = sideOffset;
            this.sideYOffset = sideYOffset;
            this.sideZOffset = sideZOffset;
            this.sideRoll = sideRoll;
            this.sidePitch = sidePitch;
            this.flapSpeed = flapSpeed;
        }
    }
}
