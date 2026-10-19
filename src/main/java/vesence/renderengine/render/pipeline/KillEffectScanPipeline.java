package vesence.renderengine.render.pipeline;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.OptionalInt;

/**
 * KillEffectScanPipeline — GPU-порт rich FragEffectScanRenderer.
 *
 * <p>Screen-space depth-scan: копирует depth-буфер, реконструирует мировые
 * позиции из глубины и рисует расширяющуюся сферу-волну ПОВЕРХ геометрии мира
 * и мобов (аддитивно). Именно это накладывает эффект на мир, а не только
 * рисует кольцо в воздухе.
 */
public class KillEffectScanPipeline {
    private static final Identifier PIPELINE_ID = Identifier.of("vesence", "pipeline/killeffect_scan");
    private static final Identifier VERTEX_SHADER = Identifier.of("vesence", "core/killeffect_scan");
    private static final Identifier FRAGMENT_SHADER = Identifier.of("vesence", "core/killeffect_scan");

    // std140 layout: 2×mat4(64) + 6×vec4(16) = 128 + 96 = 224, паддинг до 256.
    private static final int UNIFORM_SIZE = 256;

    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(PIPELINE_ID)
                    .withVertexShader(VERTEX_SHADER)
                    .withFragmentShader(FRAGMENT_SHADER)
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("ScanData", UniformType.UNIFORM_BUFFER)
                    .withSampler("DepthSampler")
                    .withBlend(BlendFunction.ADDITIVE)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
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
                TextureFormat.DEPTH32, width, height, 1, 1);
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
    public void renderWave(MinecraftClient mc, Matrix4f projection, Matrix4f view, Vec3d cameraPos,
                           Vec3d center, float radius, float width, float sharpness,
                           int outerColor, int midColor, int innerColor, int scanlineColor) {
        if (radius <= 0.0f) return;
        Framebuffer mainFb = mc.getFramebuffer();
        if (mainFb == null || mainFb.getColorAttachment() == null || mainFb.getDepthAttachment() == null) return;

        int w = mainFb.textureWidth;
        int h = mainFb.textureHeight;
        if (w <= 0 || h <= 0) return;

        ensureInitialized();
        ensureDepthTexture(w, h);

        Matrix4f invProjection = new Matrix4f(projection).invert();
        Matrix4f invView = new Matrix4f(view).invert();

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(mainFb.getDepthAttachment(), depthCopyTexture, 0, 0, 0, 0, 0, w, h);

        prepareUniformData(invProjection, invView, cameraPos, radius, center, width,
                outerColor, midColor, innerColor, scanlineColor, sharpness);
        encoder.writeToBuffer(uniformBuffer.slice(), dataBuffer);

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms().write(
                RenderSystem.getModelViewMatrix(),
                new org.joml.Vector4f(1f, 1f, 1f, 1f),
                new org.joml.Vector3f(0f, 0f, 0f),
                new Matrix4f());

        GpuSampler nearest = RenderSystem.getSamplerCache().get(FilterMode.NEAREST);
        GpuTextureView targetView = mainFb.getColorAttachmentView();

        try (RenderPass pass = encoder.createRenderPass(() -> "vesence:killeffect_scan_pass", targetView, OptionalInt.empty())) {
            pass.setPipeline(PIPELINE);
            pass.setVertexBuffer(0, dummyVertexBuffer);
            pass.bindTexture("DepthSampler", depthCopyTextureView, nearest);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", dynamicTransforms);
            pass.setUniform("ScanData", uniformBuffer);
            pass.draw(0, 6);
        }
    }

    private void prepareUniformData(Matrix4f invProj, Matrix4f invView, Vec3d camPos, float radius,
                                    Vec3d center, float width, int outer, int mid, int inner, int scan, float sharpness) {
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
