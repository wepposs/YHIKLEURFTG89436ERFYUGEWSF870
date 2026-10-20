package vesence.module.impl.visuals;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import vesence.event.EventInit;
import vesence.event.render.EventRender3D;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.renderengine.render.Renderer2D;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;

@IModule(name = "Shader Outline", description = "Обводка блока с анимацией", category = Category.VISUALS, bind = -1)
@Environment(EnvType.CLIENT)
public class BlockOutline extends Module {

    public final BooleanSetting breakEffect = new BooleanSetting("Эффект ломания", true);
    public final SliderSetting breakSpeed = new SliderSetting("Скорость эффекта", 1.0, 0.5, 3.0, 0.1, false);

    private float smoothTopAlpha = -1.0f;
    private float smoothBottomAlpha = -1.0f;
    private ByteBufferBuilder blockOutlineAllocator = null;
    private BlockPos lastBreakPos = null;
    private long breakStartTime = 0L;
    private boolean isBreaking = false;

    private static final RenderPipeline QUADS_PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(new RenderPipeline.Snippet[]{ RenderPipelines.MATRICES_FOG_SNIPPET })
            .withLocation(Identifier.fromNamespaceAndPath("vesence", "pipeline/blockoutline_quads"))
            .withVertexShader("core/position_color")
            .withFragmentShader("core/position_color")
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withCull(false)
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .build());
    private static final RenderPipeline LINES_PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(new RenderPipeline.Snippet[]{ RenderPipelines.MATRICES_FOG_SNIPPET })
            .withLocation(Identifier.fromNamespaceAndPath("vesence", "pipeline/blockoutline_lines"))
            .withVertexShader("core/position_color")
            .withFragmentShader("core/position_color")
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)            .withPrimitiveTopology(PrimitiveTopology.DEBUG_LINES)
            .withCull(false)
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .build());

    private static final RenderType QUADS_LAYER = RenderType.create("blockoutline_quads",
        RenderSetup.builder(QUADS_PIPELINE).createRenderSetup());
    private static final RenderType LINES_LAYER = RenderType.create("blockoutline_lines",
        RenderSetup.builder(LINES_PIPELINE).createRenderSetup());

    public BlockOutline() {
        addSettings(breakEffect, breakSpeed);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (blockOutlineAllocator != null) { blockOutlineAllocator.close(); blockOutlineAllocator = null; }
    }

    @EventInit
    public void onRender3D(EventRender3D event) {
        if (!this.enable) return;
        if (mc.level == null || mc.player == null) return;
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) {
            isBreaking = false;
            return;
        }

        BlockHitResult blockHit = (BlockHitResult) mc.hitResult;
        BlockPos blockPos = blockHit.getBlockPos();

        boolean attacking = mc.options.keyAttack.isDown();
        if (attacking) {
            if (lastBreakPos == null || !lastBreakPos.equals(blockPos)) {
                lastBreakPos = blockPos;
                breakStartTime = System.currentTimeMillis();
            }
            isBreaking = true;
        } else {
            isBreaking = false;
        }

        BlockState state = mc.level.getBlockState(blockPos);
        VoxelShape shape = state.getShape(mc.level, blockPos);
        if (shape.isEmpty()) return;

        Vec3 camPos = mc.gameRenderer.mainCamera().position();
        AABB rawBB = shape.bounds();
        AABB bb = new AABB(
            rawBB.minX + blockPos.getX() - camPos.x - 0.002,
            rawBB.minY + blockPos.getY() - camPos.y - 0.002,
            rawBB.minZ + blockPos.getZ() - camPos.z - 0.002,
            rawBB.maxX + blockPos.getX() - camPos.x + 0.002,
            rawBB.maxY + blockPos.getY() - camPos.y + 0.002,
            rawBB.maxZ + blockPos.getZ() - camPos.z + 0.002
        );

        int color = Renderer2D.ColorUtil.getClientColor();

        PoseStack matrices = event.getMatrixStack();
        Matrix4f matrix = matrices.last().pose();

        ByteBufferBuilder allocator = blockOutlineAllocator;
        if (allocator == null) {
            allocator = new ByteBufferBuilder(2097152);
            blockOutlineAllocator = allocator;
        }
        Immediate immediate = new Immediate();

        try {
            VertexConsumer quadBuf = immediate.getBuffer(QUADS_LAYER);

            double timeSec = System.currentTimeMillis() / 1000.0;
            float t = (float) ((Math.sin(timeSec * 2.0 * Math.PI * 0.35) + 1.0) * 0.5);
            int targetTop = (int) (255 * (1.0f - t) + 30 * t);
            int targetBottom = (int) (30 * (1.0f - t) + 255 * t);

            if (smoothTopAlpha < 0) {
                smoothTopAlpha = targetTop;
                smoothBottomAlpha = targetBottom;
            } else {
                smoothTopAlpha += (targetTop - smoothTopAlpha) * 0.06f;
                smoothBottomAlpha += (targetBottom - smoothBottomAlpha) * 0.06f;
            }

            int topColor = setAlpha(color, (int) smoothTopAlpha);
            int bottomColor = setAlpha(color, (int) smoothBottomAlpha);
            renderGradientBox(quadBuf, matrix, bb, topColor, bottomColor);

            if (breakEffect.get() && isBreaking) {
                long now = System.currentTimeMillis();
                float timeSinceBreak = (now - breakStartTime) / 1000.0f;
                float speed = (float) breakSpeed.current;
                VertexConsumer lineBuf = immediate.getBuffer(LINES_LAYER);
                renderBreakWaves(lineBuf, matrix, bb, color, timeSinceBreak, speed);
            }

            immediate.draw();
        } finally {
            // allocator reused across frames; freed on disable
        }
    }

    private static int setAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private void renderGradientBox(VertexConsumer buf, Matrix4f m, AABB bb, int top, int bot) {
        float rT = ((top >> 16) & 0xFF) / 255f, gT = ((top >> 8) & 0xFF) / 255f, bT = (top & 0xFF) / 255f, aT = ((top >> 24) & 0xFF) / 255f;
        float rB = ((bot >> 16) & 0xFF) / 255f, gB = ((bot >> 8) & 0xFF) / 255f, bB = (bot & 0xFF) / 255f, aB = ((bot >> 24) & 0xFF) / 255f;
        float x0 = (float)bb.minX, y0 = (float)bb.minY, z0 = (float)bb.minZ;
        float x1 = (float)bb.maxX, y1 = (float)bb.maxY, z1 = (float)bb.maxZ;

        buf.addVertex(m, x0, y0, z0).setColor(rB, gB, bB, aB);
        buf.addVertex(m, x1, y0, z0).setColor(rB, gB, bB, aB);
        buf.addVertex(m, x1, y0, z1).setColor(rB, gB, bB, aB);
        buf.addVertex(m, x0, y0, z1).setColor(rB, gB, bB, aB);
        buf.addVertex(m, x0, y1, z0).setColor(rT, gT, bT, aT);
        buf.addVertex(m, x0, y1, z1).setColor(rT, gT, bT, aT);
        buf.addVertex(m, x1, y1, z1).setColor(rT, gT, bT, aT);
        buf.addVertex(m, x1, y1, z0).setColor(rT, gT, bT, aT);
        buf.addVertex(m, x0, y0, z1).setColor(rB, gB, bB, aB); buf.addVertex(m, x1, y0, z1).setColor(rB, gB, bB, aB);
        buf.addVertex(m, x1, y1, z1).setColor(rT, gT, bT, aT); buf.addVertex(m, x0, y1, z1).setColor(rT, gT, bT, aT);
        buf.addVertex(m, x0, y0, z0).setColor(rB, gB, bB, aB); buf.addVertex(m, x0, y1, z0).setColor(rT, gT, bT, aT);
        buf.addVertex(m, x1, y1, z0).setColor(rT, gT, bT, aT); buf.addVertex(m, x1, y0, z0).setColor(rB, gB, bB, aB);
        buf.addVertex(m, x0, y0, z0).setColor(rB, gB, bB, aB); buf.addVertex(m, x0, y0, z1).setColor(rB, gB, bB, aB);
        buf.addVertex(m, x0, y1, z1).setColor(rT, gT, bT, aT); buf.addVertex(m, x0, y1, z0).setColor(rT, gT, bT, aT);
        buf.addVertex(m, x1, y0, z0).setColor(rB, gB, bB, aB); buf.addVertex(m, x1, y1, z0).setColor(rT, gT, bT, aT);
        buf.addVertex(m, x1, y1, z1).setColor(rT, gT, bT, aT); buf.addVertex(m, x1, y0, z1).setColor(rB, gB, bB, aB);
    }

    private void renderBreakWaves(VertexConsumer buf, Matrix4f m, AABB bb, int color, float timeSince, float speed) {
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        for (int wave = 0; wave < 3; wave++) {
            float wt = (timeSince * speed + wave * 0.3f) % 1.5f;
            if (wt > 1.0f) continue;
            float expand = wt * 0.3f;
            float alpha = (1.0f - wt);
            AABB wb = bb.inflate(expand);
            float x0 = (float)wb.minX, y0 = (float)wb.minY, z0 = (float)wb.minZ;
            float x1 = (float)wb.maxX, y1 = (float)wb.maxY, z1 = (float)wb.maxZ;
            int a = (int)(alpha*255);
            buf.addVertex(m, x0, y0, z0).setColor(r, g, b, a); buf.addVertex(m, x1, y0, z0).setColor(r, g, b, a);
            buf.addVertex(m, x1, y0, z0).setColor(r, g, b, a); buf.addVertex(m, x1, y0, z1).setColor(r, g, b, a);
            buf.addVertex(m, x1, y0, z1).setColor(r, g, b, a); buf.addVertex(m, x0, y0, z1).setColor(r, g, b, a);
            buf.addVertex(m, x0, y0, z1).setColor(r, g, b, a); buf.addVertex(m, x0, y0, z0).setColor(r, g, b, a);
            buf.addVertex(m, x0, y1, z0).setColor(r, g, b, a); buf.addVertex(m, x1, y1, z0).setColor(r, g, b, a);
            buf.addVertex(m, x1, y1, z0).setColor(r, g, b, a); buf.addVertex(m, x1, y1, z1).setColor(r, g, b, a);
            buf.addVertex(m, x1, y1, z1).setColor(r, g, b, a); buf.addVertex(m, x0, y1, z1).setColor(r, g, b, a);
            buf.addVertex(m, x0, y1, z1).setColor(r, g, b, a); buf.addVertex(m, x0, y1, z0).setColor(r, g, b, a);
        }
    }
}
