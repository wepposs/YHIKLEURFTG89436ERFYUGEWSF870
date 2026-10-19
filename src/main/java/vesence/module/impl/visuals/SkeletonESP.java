package vesence.module.impl.visuals;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import vesence.event.EventInit;
import vesence.event.render.EventRender3D;
import vesence.access.ILivingEntityRendererAccess;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.utils.render.ColorUtil;

/**
 * SkeletonESP — скелет игрока, отрисованный только линиями (полосами) сквозь стены (X-Ray).
 * Без свечения и без лишних настроек.
 */
@IModule(name = "SkeletonESP", description = "Скелет игрока (линии)", category = Category.VISUALS, bind = -1)
@Environment(EnvType.CLIENT)
public class SkeletonESP extends Module {

    private static final float MODEL_SCALE = 0.0625F;
    private static final Matrix4f IDENTITY_MATRIX = new Matrix4f();

    private BufferAllocator skeletonLineAlloc = null;

    // ─── Линии (кости), всегда сквозь стены (X-Ray) ──────────────────────────
    private static final RenderPipeline SKELETON_LINES_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(new RenderPipeline.Snippet[]{RenderPipelines.POSITION_COLOR_SNIPPET})
                    .withLocation(Identifier.of("vesence", "pipeline/world/skeleton_lines"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.DEBUG_LINES)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .build());

    private static final RenderLayer SKELETON_LAYER = RenderLayer.of(
            "vesence_skeleton",
            RenderSetup.builder(SKELETON_LINES_PIPELINE).expectedBufferSize(4096).translucent().build());

    public SkeletonESP() {
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (skeletonLineAlloc != null) { skeletonLineAlloc.close(); skeletonLineAlloc = null; }
    }

    @EventInit
    public void onRender3D(EventRender3D event) {
        if (mc.player == null || mc.world == null) return;

        float tickDelta = event.getTickDelta();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();

        if (skeletonLineAlloc == null) { skeletonLineAlloc = new BufferAllocator(16384); }
        VertexConsumerProvider.Immediate lineImm = VertexConsumerProvider.immediate(skeletonLineAlloc);
        VertexConsumer lineBuffer = lineImm.getBuffer(SKELETON_LAYER);
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            if (player.isInvisible()) continue;
            this.renderPlayerPass(event.getMatrixStack(), cameraPos, player, tickDelta, lineBuffer);
        }
        lineImm.draw();
    }

    private void renderPlayerPass(MatrixStack matrices, Vec3d cameraPos,
                              PlayerEntity player, float tickDelta, VertexConsumer lineBuffer) {
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;

        EntityRenderer<?, ?> rawRenderer = mc.getEntityRenderDispatcher().getRenderer(player);
        if (!(rawRenderer instanceof PlayerEntityRenderer renderer)) return;

        PlayerEntityRenderState state = renderer.createRenderState();
        renderer.updateRenderState(clientPlayer, state, tickDelta);
        PlayerEntityModel model = (PlayerEntityModel) renderer.getModel();
        model.setAngles(state);

        matrices.push();
        this.setupModelMatrix(matrices, state, renderer, cameraPos, player, tickDelta);

        int color = ColorUtil.fade();
        if ((color >> 24 & 0xFF) == 0) color |= 0xFF000000;

        this.drawSkeleton(matrices, model, lineBuffer, color);
        matrices.pop();
    }

    private void setupModelMatrix(MatrixStack matrices, PlayerEntityRenderState state,
                                  PlayerEntityRenderer renderer, Vec3d cameraPos,
                                  PlayerEntity player, float tickDelta) {
        Vec3d pos = player.getLerpedPos(tickDelta);
        matrices.translate(pos.x - cameraPos.x, pos.y - cameraPos.y, pos.z - cameraPos.z);
        if (state.sleepingDirection != null) {
            float eyeOffset = state.standingEyeHeight - 0.1F;
            matrices.translate(
                    -state.sleepingDirection.getOffsetX() * eyeOffset, 0.0F,
                    -state.sleepingDirection.getOffsetZ() * eyeOffset);
        }
        float baseScale = state.baseScale;
        matrices.scale(baseScale, baseScale, baseScale);
        ILivingEntityRendererAccess accessor = (ILivingEntityRendererAccess) renderer;
        accessor.vesence$invokeSetupTransforms(state, matrices, state.bodyYaw, baseScale);
        matrices.scale(-1.0F, -1.0F, 1.0F);
        accessor.vesence$invokeScale(state, matrices);
        matrices.translate(0.0F, -1.501F, 0.0F);
    }

    private void drawSkeleton(MatrixStack matrices, PlayerEntityModel model,
                              VertexConsumer lineBuffer, int color) {
        ModelPart root = model.getRootPart();

        Vec3d headTop = transformPt(matrices, root, model.head, 0, -8, 0);
        Vec3d headBase = transformPt(matrices, root, model.head, 0, 0, 0);
        Vec3d chestTop = transformPt(matrices, root, model.body, 0, 0, 0);
        Vec3d chestMid = transformPt(matrices, root, model.body, 0, 6, 0);
        Vec3d pelvis = transformPt(matrices, root, model.body, 0, 12, 0);
        Vec3d lShoulder = transformPt(matrices, root, model.body, 5, 2, 0);
        Vec3d rShoulder = transformPt(matrices, root, model.body, -5, 2, 0);
        Vec3d lHip = transformPt(matrices, root, model.body, 2, 12, 0);
        Vec3d rHip = transformPt(matrices, root, model.body, -2, 12, 0);
        Vec3d laTop = transformPt(matrices, root, model.leftArm, 0, 0, 0);
        Vec3d laMid = transformPt(matrices, root, model.leftArm, 0, 4, 0);
        Vec3d laBot = transformPt(matrices, root, model.leftArm, 0, 10, 0);
        Vec3d raTop = transformPt(matrices, root, model.rightArm, 0, 0, 0);
        Vec3d raMid = transformPt(matrices, root, model.rightArm, 0, 4, 0);
        Vec3d raBot = transformPt(matrices, root, model.rightArm, 0, 10, 0);
        Vec3d llTop = transformPt(matrices, root, model.leftLeg, 0, 0, 0);
        Vec3d llMid = transformPt(matrices, root, model.leftLeg, 0, 6, 0);
        Vec3d llBot = transformPt(matrices, root, model.leftLeg, 0, 12, 0);
        Vec3d rlTop = transformPt(matrices, root, model.rightLeg, 0, 0, 0);
        Vec3d rlMid = transformPt(matrices, root, model.rightLeg, 0, 6, 0);
        Vec3d rlBot = transformPt(matrices, root, model.rightLeg, 0, 12, 0);

        Vec3d[][] bones = {
            {headTop, headBase}, {headBase, chestTop}, {chestTop, chestMid}, {chestMid, pelvis},
            {lShoulder, rShoulder}, {lHip, rHip},
            {lShoulder, laTop}, {rShoulder, raTop}, {lHip, llTop}, {rHip, rlTop},
            {laTop, laMid}, {laMid, laBot}, {raTop, raMid}, {raMid, raBot},
            {llTop, llMid}, {llMid, llBot}, {rlTop, rlMid}, {rlMid, rlBot}
        };

        for (Vec3d[] bone : bones) {
            lineBuffer.vertex(IDENTITY_MATRIX, (float) bone[0].x, (float) bone[0].y, (float) bone[0].z).color(color);
            lineBuffer.vertex(IDENTITY_MATRIX, (float) bone[1].x, (float) bone[1].y, (float) bone[1].z).color(color);
        }
    }

    private Vec3d transformPt(MatrixStack baseStack, ModelPart root, ModelPart part, float x, float y, float z) {
        baseStack.push();
        applyPartTransform(baseStack, root);
        applyPartTransform(baseStack, part);
        Vector3f pos = baseStack.peek().getPositionMatrix()
                .transformPosition(x * MODEL_SCALE, y * MODEL_SCALE, z * MODEL_SCALE, new Vector3f());
        baseStack.pop();
        return new Vec3d(pos.x, pos.y, pos.z);
    }

    private void applyPartTransform(MatrixStack matrices, ModelPart part) {
        matrices.translate(part.originX * MODEL_SCALE, part.originY * MODEL_SCALE, part.originZ * MODEL_SCALE);
        if (part.roll != 0.0F || part.yaw != 0.0F || part.pitch != 0.0F) {
            matrices.multiply(new Quaternionf().rotateZYX(part.roll, part.yaw, part.pitch));
        }
        if (part.xScale != 1.0F || part.yScale != 1.0F || part.zScale != 1.0F) {
            matrices.scale(part.xScale, part.yScale, part.zScale);
        }
    }
}
