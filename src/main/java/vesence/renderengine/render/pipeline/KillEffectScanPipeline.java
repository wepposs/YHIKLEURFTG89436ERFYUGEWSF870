package vesence.renderengine.render.pipeline;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.api.GpuFormat;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.textures.GpuSampler;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.BindGroupLayouts;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.UniformType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * KillEffectScanPipeline — GPU-порт rich FragEffectScanRenderer.
 *
 * <p>Screen-space depth-scan: копирует depth-буфер, реконструирует мировые
 * позиции из глубины и рисует расширяющуюся сферу-волну ПОВЕРХ геометрии мира
 * и мобов (аддитивно). Именно это накладывает эффект на мир, а не только
 * рисует кольцо в воздухе.
 */
public class KillEffectScanPipeline {
    private static final Identifier PIPELINE_ID = Identifier.fromNamespaceAndPath("vesence", "pipeline/killeffect_scan");
    private static final Identifier VERTEX_SHADER = Identifier.fromNamespaceAndPath("vesence", "core/killeffect_scan");
    private static final Identifier FRAGMENT_SHADER = Identifier.fromNamespaceAndPath("vesence", "core/killeffect_scan");

    // std140 layout: 2×mat4(64) + 6×vec4(16) = 128 + 96 = 224, паддинг до 256.
    private static final int UNIFORM_SIZE = 256;

    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder()
                    .withLocation(PIPELINE_ID)
                    .withVertexShader(VERTEX_SHADER)
                    .withFragmentShader(FRAGMENT_SHADER)
                    .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                    .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                    .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                    .withBindGroupLayout(BindGroupLayout.builder()
                            .withUniform("ScanData", UniformType.UNIFORM_BUFFER)
                            .withUniform("DepthSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                            .build())
                    .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
                    .withDepthStencilState(Optional.empty())
                    .withCull(false)
                    .build()
    );

    private GpuBuffer uniformBuffer;
    private GpuBuffer dummyVertexBuffer;
    private ByteBuffer dataBuffer;

    private GpuTexture depthCopyTexture;
    private GpuTextureView depthCopyTextureView;
    private int lastWidth = -1;
    private int lastHeight = -1;
    private boolean initialized = false;

    private void ensureInitialized() {
        if (initialized) return;
        this.dataBuffer = MemoryUtil.memAlloc(UNIFORM_SIZE);
        ByteBuffer dummyData = MemoryUtil.memAlloc(4);
        dummyData.putInt(0);
        dummyData.flip();
        this.dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "vesence:killeffect_scan_dummy_vertex", GpuBuffer.USAGE_VERTEX, dummyData);
        MemoryUtil.memFree(dummyData);
        this.uniformBuffer = RenderSystem.getDevice().createBuffer(
                () -> "vesence:killeffect_scan_uniform",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, UNIFORM_SIZE);
        initialized = true;
    }

    private void ensureDepthTexture(int width, int height) {
        if (width == lastWidth && height == lastHeight && depthCopyTexture != null) return;
        cleanupDepthTexture();
        depthCopyTexture = RenderSystem.getDevice().createTexture(
                () -> "vesence:killeffect_scan_depth_copy",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                GpuFormat.D32_FLOAT, width, height, 1, 1);
        depthCopyTextureView = RenderSystem.getDevice().createTextureView(depthCopyTexture);
        lastWidth = width;
        lastHeight = height;
    }

    private void cleanupDepthTexture() {
        if (depthCopyTextureView != null) {
            depthCopyTextureView.close();
            depthCopyTextureView = null;
        }
        if (depthCopyTexture != null) {
            depthCopyTexture.close();
            depthCopyTexture = null;
        }
        lastWidth = -1;
        lastHeight = -1;
    }

    /**
     * Рисует одну волну поверх сцены.
     *
     * @param projection проекционная матрица кадра
     * @param view       view-матрица кадра (positionMatrix)
     * @param cameraPos  позиция камеры
     * @param center     центр волны (мировые координаты)
     * @param radius     текущий радиус
     * @param width      толщина кольца
     * @param sharpness  резкость края
     * @param outer/mid/inner/scanline — цвета (ARGB int)
     */
    public void renderWave(Minecraft mc, Matrix4f projection, Matrix4f view, Vec3 cameraPos,
                           Vec3 center, float radius, float width, float sharpness,
                           int outerColor, int midColor, int innerColor, int scanlineColor) {
        if (radius <= 0.0f) return;
        RenderTarget mainFb = mc.gameRenderer.mainRenderTarget();
        if (mainFb == null || mainFb.getColorTexture() == null || mainFb.getDepthTexture() == null) return;

        int w = mainFb.width;
        int h = mainFb.height;
        if (w <= 0 || h <= 0) return;

        ensureInitialized();
        ensureDepthTexture(w, h);

        Matrix4f invProjection = new Matrix4f(projection).invert();
        Matrix4f invView = new Matrix4f(view).invert();

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(mainFb.getDepthTexture(), depthCopyTexture, 0, 0, 0, 0, 0, w, h);

        prepareUniformData(invProjection, invView, cameraPos, radius, center, width,
                outerColor, midColor, innerColor, scanlineColor, sharpness);
        encoder.writeToBuffer(uniformBuffer.slice(), dataBuffer);

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(
                RenderSystem.getModelViewMatrixCopy(),
                new org.joml.Vector4f(1f, 1f, 1f, 1f),
                new org.joml.Vector3f(0f, 0f, 0f),
                new Matrix4f());

        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        GpuTextureView targetView = mainFb.getColorTextureView();

        try (RenderPass pass = encoder.createRenderPass(() -> "vesence:killeffect_scan_pass", targetView, Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(PIPELINE));
            pass.setVertexBuffer(0, dummyVertexBuffer.slice());
            pass.setUniform("DepthSampler", depthCopyTextureView, nearest);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", dynamicTransforms);
            pass.setUniform("ScanData", uniformBuffer);
            pass.draw(0, 6, 0, 1);
        }
    }

    private void prepareUniformData(Matrix4f invProj, Matrix4f invView, Vec3 camPos, float radius,
                                    Vec3 center, float width, int outer, int mid, int inner, int scan, float sharpness) {
        dataBuffer.clear();
        putMatrix(dataBuffer, invProj);
        putMatrix(dataBuffer, invView);
        putVec4(dataBuffer, (float) camPos.x, (float) camPos.y, (float) camPos.z, radius);
        putVec4(dataBuffer, (float) center.x, (float) center.y, (float) center.z, Math.max(width, 1.0E-4f));
        putColor(dataBuffer, outer);
        putColor(dataBuffer, mid);
        putColor(dataBuffer, inner);
        putColor(dataBuffer, scan);
        putVec4(dataBuffer, sharpness, 0f, 0f, 0f);
        // Дополнить до UNIFORM_SIZE
        while (dataBuffer.position() < UNIFORM_SIZE) dataBuffer.putFloat(0f);
        dataBuffer.flip();
    }

    private static void putMatrix(ByteBuffer b, Matrix4f m) {
        b.putFloat(m.m00()).putFloat(m.m01()).putFloat(m.m02()).putFloat(m.m03());
        b.putFloat(m.m10()).putFloat(m.m11()).putFloat(m.m12()).putFloat(m.m13());
        b.putFloat(m.m20()).putFloat(m.m21()).putFloat(m.m22()).putFloat(m.m23());
        b.putFloat(m.m30()).putFloat(m.m31()).putFloat(m.m32()).putFloat(m.m33());
    }

    private static void putVec4(ByteBuffer b, float x, float y, float z, float w) {
        b.putFloat(x).putFloat(y).putFloat(z).putFloat(w);
    }

    private static void putColor(ByteBuffer b, int color) {
        b.putFloat((color >> 16 & 0xFF) / 255.0f);
        b.putFloat((color >> 8 & 0xFF) / 255.0f);
        b.putFloat((color & 0xFF) / 255.0f);
        b.putFloat((color >> 24 & 0xFF) / 255.0f);
    }

    public void close() {
        cleanupDepthTexture();
        if (uniformBuffer != null) { uniformBuffer.close(); uniformBuffer = null; }
        if (dummyVertexBuffer != null) { dummyVertexBuffer.close(); dummyVertexBuffer = null; }
        if (dataBuffer != null) { MemoryUtil.memFree(dataBuffer); dataBuffer = null; }
        initialized = false;
    }
}
