package vesence.renderengine.render.pipeline;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.textures.GpuSampler;
import net.minecraft.client.renderer.RenderPipelines;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.util.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.OptionalInt;

/**
 * FogBlurPipeline — полный порт Relevant/Blade FogBlurRender на GPU-конвейер 1.21.11.
 *
 * <p>Алгоритм 1:1 как в оригинале:
 * <ol>
 *   <li>копируем цвет + глубину основного фреймбуфера;</li>
 *   <li>двухпроходное гауссово размытие (горизонт → вертикаль) во внутренние
 *       render-target текстуры;</li>
 *   <li>композит с depth-маской: подмешиваем размытие ТОЛЬКО на пикселях
 *       дальше порога глубины (туман по расстоянию), близкие пиксели остаются
 *       чёткими — поэтому размывается НЕ весь экран, а даль/туман.</li>
 * </ol>
 */
public class FogBlurPipeline {
    private static final ResourceLocation GAUSS_ID = ResourceLocation.of("vesence", "pipeline/fogblur_gaussian");
    private static final ResourceLocation GAUSS_ID2 = ResourceLocation.of("vesence", "pipeline/fogblur_gaussian2");
    private static final ResourceLocation COMPOSITE_ID = ResourceLocation.of("vesence", "pipeline/fogblur_composite");
    private static final ResourceLocation TINT_ID = ResourceLocation.of("vesence", "pipeline/fogblur_tint");
    private static final ResourceLocation VSH = ResourceLocation.of("vesence", "core/fogblur");
    private static final ResourceLocation GAUSS_FSH = ResourceLocation.of("vesence", "core/fogblur_gaussian");
    private static final ResourceLocation COMPOSITE_FSH = ResourceLocation.of("vesence", "core/fogblur_composite");
    private static final ResourceLocation TINT_FSH = ResourceLocation.of("vesence", "core/fogblur_tint");

    private static final BlendFunction REPLACE_BLEND =
            new BlendFunction(SourceFactor.ONE, DestFactor.ZERO, SourceFactor.ONE, DestFactor.ZERO);

    // Гаусс-пасс: пишем в промежуточную текстуру, замещая содержимое.
    private static final RenderPipeline GAUSS_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(GAUSS_ID)
                    .withVertexShader(VSH)
                    .withFragmentShader(GAUSS_FSH)
                    .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("BlurData", UniformType.UNIFORM_BUFFER)
                    .withSampler("SceneSampler")
                    .withBlend(REPLACE_BLEND)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    // Второй гаусс-пасс — отдельный pipeline id (тот же шейдер), чтобы не путать
    // биндинги при последовательном рендере в разные цели.
    private static final RenderPipeline GAUSS_PIPELINE2 = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(GAUSS_ID2)
                    .withVertexShader(VSH)
                    .withFragmentShader(GAUSS_FSH)
                    .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("BlurData", UniformType.UNIFORM_BUFFER)
                    .withSampler("SceneSampler")
                    .withBlend(REPLACE_BLEND)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    // Композит: альфа-блендинг размытия поверх основного кадра по depth-маске.
    private static final RenderPipeline COMPOSITE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(COMPOSITE_ID)
                    .withVertexShader(VSH)
                    .withFragmentShader(COMPOSITE_FSH)
                    .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("DepthData", UniformType.UNIFORM_BUFFER)
                    .withSampler("BlurSampler")
                    .withSampler("DepthSampler")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    // Tint (RGB Puke): подкрашивает сцену перед размытием.
    private static final RenderPipeline TINT_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(TINT_ID)
                    .withVertexShader(VSH)
                    .withFragmentShader(TINT_FSH)
                    .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("TintData", UniformType.UNIFORM_BUFFER)
                    .withSampler("SceneSampler")
                    .withBlend(REPLACE_BLEND)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f(0, 0, 0);
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();

    private GpuBuffer dummyVertexBuffer;
    private GpuBuffer blurUboH;
    private GpuBuffer blurUboV;
    private GpuBuffer depthUbo;
    private GpuBuffer tintUbo;
    private ByteBuffer scratch;

    private GpuTexture sceneCopy, depthCopy, fbo1, fbo2, tintTex;
    private GpuTextureView sceneCopyView, depthCopyView, fbo1View, fbo2View, tintView;
    private int lastW = -1, lastH = -1;
    private boolean initialized = false;
    private final long startTime = System.currentTimeMillis();

    private void ensureInitialized() {
        if (initialized) return;
        this.scratch = MemoryUtil.memAlloc(48);
        ByteBuffer dummy = MemoryUtil.memAlloc(4);
        dummy.putInt(0);
        dummy.flip();
        this.dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "vesence:fogblur_dummy_vertex", GpuBuffer.USAGE_VERTEX, dummy);
        MemoryUtil.memFree(dummy);
        this.blurUboH = RenderSystem.getDevice().createBuffer(
                () -> "vesence:fogblur_ubo_h", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 48);
        this.blurUboV = RenderSystem.getDevice().createBuffer(
                () -> "vesence:fogblur_ubo_v", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 48);
        this.depthUbo = RenderSystem.getDevice().createBuffer(
                () -> "vesence:fogblur_ubo_depth", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 16);
        this.tintUbo = RenderSystem.getDevice().createBuffer(
                () -> "vesence:fogblur_ubo_tint", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 32);
        initialized = true;
    }

    private void ensureTextures(int w, int h) {
        if (w == lastW && h == lastH && sceneCopy != null) return;
        cleanupTextures();
        int rtUsage = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT;
        sceneCopy = RenderSystem.getDevice().createTexture(() -> "vesence:fogblur_scene",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, TextureFormat.RGBA8, w, h, 1, 1);
        depthCopy = RenderSystem.getDevice().createTexture(() -> "vesence:fogblur_depth",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, TextureFormat.DEPTH32, w, h, 1, 1);
        fbo1 = RenderSystem.getDevice().createTexture(() -> "vesence:fogblur_fbo1", rtUsage, TextureFormat.RGBA8, w, h, 1, 1);
        fbo2 = RenderSystem.getDevice().createTexture(() -> "vesence:fogblur_fbo2", rtUsage, TextureFormat.RGBA8, w, h, 1, 1);
        tintTex = RenderSystem.getDevice().createTexture(() -> "vesence:fogblur_tint", rtUsage, TextureFormat.RGBA8, w, h, 1, 1);
        sceneCopyView = RenderSystem.getDevice().createTextureView(sceneCopy);
        depthCopyView = RenderSystem.getDevice().createTextureView(depthCopy);
        fbo1View = RenderSystem.getDevice().createTextureView(fbo1);
        fbo2View = RenderSystem.getDevice().createTextureView(fbo2);
        tintView = RenderSystem.getDevice().createTextureView(tintTex);
        lastW = w;
        lastH = h;
    }

    private void cleanupTextures() {
        if (sceneCopyView != null) { sceneCopyView.close(); sceneCopyView = null; }
        if (depthCopyView != null) { depthCopyView.close(); depthCopyView = null; }
        if (fbo1View != null) { fbo1View.close(); fbo1View = null; }
        if (fbo2View != null) { fbo2View.close(); fbo2View = null; }
        if (tintView != null) { tintView.close(); tintView = null; }
        if (sceneCopy != null) { sceneCopy.close(); sceneCopy = null; }
        if (depthCopy != null) { depthCopy.close(); depthCopy = null; }
        if (fbo1 != null) { fbo1.close(); fbo1 = null; }
        if (fbo2 != null) { fbo2.close(); fbo2 = null; }
        if (tintTex != null) { tintTex.close(); tintTex = null; }
        lastW = -1;
        lastH = -1;
    }

    /**
     * @param strength  сила размытия (sigma)
     * @param distance  дистанция тумана 0..200 (порог глубины)
     * @param linear    линейная выборка (быстрее, чуть менее точно)
     */
    public void apply(Minecraft mc, float strength, float distance, boolean linear,
                      boolean usePuke, float pukeOpacity, float pukeSaturation, float pukeBrightness) {
        RenderTarget mainFb = mc.getMainRenderTarget();
        if (mainFb == null || mainFb.getColorAttachment() == null || mainFb.getDepthAttachment() == null) return;
        int w = mainFb.textureWidth;
        int h = mainFb.textureHeight;
        if (w <= 0 || h <= 0) return;

        ensureInitialized();
        ensureTextures(w, h);

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(mainFb.getColorAttachment(), sceneCopy, 0, 0, 0, 0, 0, w, h);
        encoder.copyTextureToTexture(mainFb.getDepthAttachment(), depthCopy, 0, 0, 0, 0, 0, w, h);

        // Гаусс-коэффициенты (как в Blade GaussianShader).
        float sigma = Math.max(0.5f, strength);
        float sqrt2PI = 2.50662f;
        float gy = (float) Math.exp(-0.5f / (sigma * sigma));
        float g0 = 1f / (sqrt2PI * sigma);
        int support = (int) Math.ceil(sigma * 3);

        // UBO горизонтального прохода.
        writeBlurUbo(encoder, blurUboH, 1f, 0f, 1f / w, 1f / h, g0, gy, gy * gy, support, linear);
        // UBO вертикального прохода.
        writeBlurUbo(encoder, blurUboV, 0f, 1f, 1f / w, 1f / h, g0, gy, gy * gy, support, linear);

        GpuBufferSlice dyn = RenderSystem.getDynamicUniforms().write(
                RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        GpuSampler sampler = RenderSystem.getSamplerCache().get(linear ? FilterMode.LINEAR : FilterMode.NEAREST);
        GpuSampler nearest = RenderSystem.getSamplerCache().get(FilterMode.NEAREST);

        // Опциональный tint-пасс (RGB Puke) перед размытием: sceneCopy -> tintTex.
        GpuTextureView blurSource = sceneCopyView;
        if (usePuke) {
            float time = (System.nanoTime() - startTime * 1_000_000L) / 1_000_000_000.0f;
            float yaw = mc.player != null ? mc.player.getYRot() : 0f;
            float pitch = mc.player != null ? mc.player.getXRot() : 0f;
            scratch.clear();
            scratch.putFloat(pukeSaturation).putFloat(pukeBrightness).putFloat(pukeOpacity).putFloat(time);
            scratch.putFloat(yaw).putFloat(pitch).putFloat(0f).putFloat(0f);
            scratch.flip();
            encoder.writeToBuffer(tintUbo.slice(), scratch);
            try (RenderPass pass = encoder.createRenderPass(() -> "vesence:fogblur_tint", tintView, OptionalInt.empty())) {
                pass.setPipeline(TINT_PIPELINE);
                pass.setVertexBuffer(0, dummyVertexBuffer);
                pass.bindTexture("SceneSampler", sceneCopyView, sampler);
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", dyn);
                pass.setUniform("TintData", tintUbo);
                pass.draw(0, 6);
            }
            blurSource = tintView;
        }

        // Проход 1: горизонт (scene|tint) -> fbo1.
        try (RenderPass pass = encoder.createRenderPass(() -> "vesence:fogblur_h", fbo1View, OptionalInt.empty())) {
            pass.setPipeline(GAUSS_PIPELINE);
            pass.setVertexBuffer(0, dummyVertexBuffer);
            pass.bindTexture("SceneSampler", blurSource, sampler);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", dyn);
            pass.setUniform("BlurData", blurUboH);
            pass.draw(0, 6);
        }

        // Проход 2: вертикаль fbo1 -> fbo2.
        try (RenderPass pass = encoder.createRenderPass(() -> "vesence:fogblur_v", fbo2View, OptionalInt.empty())) {
            pass.setPipeline(GAUSS_PIPELINE2);
            pass.setVertexBuffer(0, dummyVertexBuffer);
            pass.bindTexture("SceneSampler", fbo1View, sampler);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", dyn);
            pass.setUniform("BlurData", blurUboV);
            pass.draw(0, 6);
        }

        // Depth-данные для композита (как в Blade DepthShader).
        float near = 0.05f;
        float far = mc.gameRenderer.getFarPlaneDistance();
        float minThreshold = 0.10f * distance / 100f;
        float maxThreshold = 0.28f * distance / 100f;
        if (maxThreshold <= minThreshold) maxThreshold = minThreshold + 0.001f;
        scratch.clear();
        scratch.putFloat(near).putFloat(far).putFloat(minThreshold).putFloat(maxThreshold);
        scratch.flip();
        encoder.writeToBuffer(depthUbo.slice(), scratch);

        // Композит: подмешиваем размытие поверх основного кадра по глубине.
        GpuTextureView targetView = mainFb.getColorAttachmentView();
        try (RenderPass pass = encoder.createRenderPass(() -> "vesence:fogblur_composite", targetView, OptionalInt.empty())) {
            pass.setPipeline(COMPOSITE_PIPELINE);
            pass.setVertexBuffer(0, dummyVertexBuffer);
            pass.bindTexture("BlurSampler", fbo2View, sampler);
            pass.bindTexture("DepthSampler", depthCopyView, nearest);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", dyn);
            pass.setUniform("DepthData", depthUbo);
            pass.draw(0, 6);
        }
    }

    private void writeBlurUbo(CommandEncoder encoder, GpuBuffer ubo,
                              float dirX, float dirY, float texelX, float texelY,
                              float g0, float g1, float g2, int support, boolean linear) {
        scratch.clear();
        scratch.putFloat(dirX).putFloat(dirY).putFloat(texelX).putFloat(texelY);
        scratch.putFloat(g0).putFloat(g1).putFloat(g2).putFloat(support);
        scratch.putFloat(linear ? 1f : 0f).putFloat(0f).putFloat(0f).putFloat(0f);
        scratch.flip();
        encoder.writeToBuffer(ubo.slice(), scratch);
    }

    public void close() {
        cleanupTextures();
        if (blurUboH != null) { blurUboH.close(); blurUboH = null; }
        if (blurUboV != null) { blurUboV.close(); blurUboV = null; }
        if (depthUbo != null) { depthUbo.close(); depthUbo = null; }
        if (tintUbo != null) { tintUbo.close(); tintUbo = null; }
        if (dummyVertexBuffer != null) { dummyVertexBuffer.close(); dummyVertexBuffer = null; }
        if (scratch != null) { MemoryUtil.memFree(scratch); scratch = null; }
        initialized = false;
    }
}
