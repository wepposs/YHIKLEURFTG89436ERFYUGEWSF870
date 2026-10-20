package vesence.renderengine.render.pipeline;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
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
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Optional;

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
    private static final Identifier GAUSS_ID = Identifier.fromNamespaceAndPath("vesence", "pipeline/fogblur_gaussian");
    private static final Identifier GAUSS_ID2 = Identifier.fromNamespaceAndPath("vesence", "pipeline/fogblur_gaussian2");
    private static final Identifier COMPOSITE_ID = Identifier.fromNamespaceAndPath("vesence", "pipeline/fogblur_composite");
    private static final Identifier TINT_ID = Identifier.fromNamespaceAndPath("vesence", "pipeline/fogblur_tint");
    private static final Identifier VSH = Identifier.fromNamespaceAndPath("vesence", "core/fogblur");
    private static final Identifier GAUSS_FSH = Identifier.fromNamespaceAndPath("vesence", "core/fogblur_gaussian");
    private static final Identifier COMPOSITE_FSH = Identifier.fromNamespaceAndPath("vesence", "core/fogblur_composite");
    private static final Identifier TINT_FSH = Identifier.fromNamespaceAndPath("vesence", "core/fogblur_tint");

    private static final BlendFunction REPLACE_BLEND =
            new BlendFunction(BlendFactor.ONE, BlendFactor.ZERO, BlendFactor.ONE, BlendFactor.ZERO);

    // Гаусс-пасс: пишем в промежуточную текстуру, замещая содержимое.
    private static final RenderPipeline GAUSS_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder()
                    .withLocation(GAUSS_ID)
                    .withVertexShader(VSH)
                    .withFragmentShader(GAUSS_FSH)
                    .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                    .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                    .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                    .withBindGroupLayout(BindGroupLayout.builder()
                            .withUniform("BlurData", UniformType.UNIFORM_BUFFER)
                            .withUniform("SceneSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                            .build())
                    .withColorTargetState(new ColorTargetState(REPLACE_BLEND))
                    .withDepthStencilState(Optional.empty())
                    .withCull(false)
                    .build()
    );

    // Второй гаусс-пасс — отдельный pipeline id (тот же шейдер), чтобы не путать
    // биндинги при последовательном рендере в разные цели.
    private static final RenderPipeline GAUSS_PIPELINE2 = RenderPipelines.register(
            RenderPipeline.builder()
                    .withLocation(GAUSS_ID2)
                    .withVertexShader(VSH)
                    .withFragmentShader(GAUSS_FSH)
                    .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                    .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                    .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                    .withBindGroupLayout(BindGroupLayout.builder()
                            .withUniform("BlurData", UniformType.UNIFORM_BUFFER)
                            .withUniform("SceneSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                            .build())
                    .withColorTargetState(new ColorTargetState(REPLACE_BLEND))
                    .withDepthStencilState(Optional.empty())
                    .withCull(false)
                    .build()
    );

    // Композит: альфа-блендинг размытия поверх основного кадра по depth-маске.
    private static final RenderPipeline COMPOSITE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder()
                    .withLocation(COMPOSITE_ID)
                    .withVertexShader(VSH)
                    .withFragmentShader(COMPOSITE_FSH)
                    .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                    .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                    .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                    .withBindGroupLayout(BindGroupLayout.builder()
                            .withUniform("DepthData", UniformType.UNIFORM_BUFFER)
                            .withUniform("BlurSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                            .withUniform("DepthSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                            .build())
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withDepthStencilState(Optional.empty())
                    .withCull(false)
                    .build()
    );

    // Tint (RGB Puke): подкрашивает сцену перед размытием.
    private static final RenderPipeline TINT_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder()
                    .withLocation(TINT_ID)
                    .withVertexShader(VSH)
                    .withFragmentShader(TINT_FSH)
                    .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                    .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                    .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                    .withBindGroupLayout(BindGroupLayout.builder()
                            .withUniform("TintData", UniformType.UNIFORM_BUFFER)
                            .withUniform("SceneSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                            .build())
                    .withColorTargetState(new ColorTargetState(REPLACE_BLEND))
                    .withDepthStencilState(Optional.empty())
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
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, GpuFormat.RGBA8_UNORM, w, h, 1, 1);
        depthCopy = RenderSystem.getDevice().createTexture(() -> "vesence:fogblur_depth",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, GpuFormat.D32_FLOAT, w, h, 1, 1);
        fbo1 = RenderSystem.getDevice().createTexture(() -> "vesence:fogblur_fbo1", rtUsage, GpuFormat.RGBA8_UNORM, w, h, 1, 1);
        fbo2 = RenderSystem.getDevice().createTexture(() -> "vesence:fogblur_fbo2", rtUsage, GpuFormat.RGBA8_UNORM, w, h, 1, 1);
        tintTex = RenderSystem.getDevice().createTexture(() -> "vesence:fogblur_tint", rtUsage, GpuFormat.RGBA8_UNORM, w, h, 1, 1);
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
        RenderTarget mainFb = mc.gameRenderer.mainRenderTarget();
        if (mainFb == null || mainFb.getColorTexture() == null || mainFb.getDepthTexture() == null) return;
        int w = mainFb.width;
        int h = mainFb.height;
        if (w <= 0 || h <= 0) return;

        ensureInitialized();
        ensureTextures(w, h);

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(mainFb.getColorTexture(), sceneCopy, 0, 0, 0, 0, 0, w, h);
        encoder.copyTextureToTexture(mainFb.getDepthTexture(), depthCopy, 0, 0, 0, 0, 0, w, h);

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

        GpuBufferSlice dyn = RenderSystem.getDynamicUniforms().writeTransform(
                RenderSystem.getModelViewMatrixCopy(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(linear ? FilterMode.LINEAR : FilterMode.NEAREST);
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);

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
            try (RenderPass pass = encoder.createRenderPass(() -> "vesence:fogblur_tint", tintView, Optional.empty())) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(TINT_PIPELINE));
                pass.setVertexBuffer(0, dummyVertexBuffer.slice());
                pass.setUniform("SceneSampler", sceneCopyView, sampler);
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", dyn);
                pass.setUniform("TintData", tintUbo);
                pass.draw(0, 6, 0, 1);
            }
            blurSource = tintView;
        }

        // Проход 1: горизонт (scene|tint) -> fbo1.
        try (RenderPass pass = encoder.createRenderPass(() -> "vesence:fogblur_h", fbo1View, Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(GAUSS_PIPELINE));
            pass.setVertexBuffer(0, dummyVertexBuffer.slice());
            pass.setUniform("SceneSampler", blurSource, sampler);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", dyn);
            pass.setUniform("BlurData", blurUboH);
            pass.draw(0, 6, 0, 1);
        }

        // Проход 2: вертикаль fbo1 -> fbo2.
        try (RenderPass pass = encoder.createRenderPass(() -> "vesence:fogblur_v", fbo2View, Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(GAUSS_PIPELINE2));
            pass.setVertexBuffer(0, dummyVertexBuffer.slice());
            pass.setUniform("SceneSampler", fbo1View, sampler);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", dyn);
            pass.setUniform("BlurData", blurUboV);
            pass.draw(0, 6, 0, 1);
        }

        // Depth-данные для композита (как в Blade DepthShader).
        float near = 0.05f;
        // 26.3: GameRenderer#getFarPlaneDistance удалён, точное значение дальности
        // камеры лежит в CameraRenderState#depthFar (его же использует FogRenderer).
        // Если состояния кадра ещё нет — падаем на прежнюю оценку по render distance.
        float far = 0.0f;
        try {
            var renderState = mc.gameRenderer.gameRenderState();
            if (renderState != null && renderState.levelRenderState != null
                    && renderState.levelRenderState.cameraRenderState != null) {
                far = renderState.levelRenderState.cameraRenderState.depthFar;
            }
        } catch (Throwable ignored) {
        }
        if (!(far > 0.0f)) {
            far = Math.max(mc.options.getEffectiveRenderDistance(), 4) * 16 * 4.0f;
        }
        float minThreshold = 0.10f * distance / 100f;
        float maxThreshold = 0.28f * distance / 100f;
        if (maxThreshold <= minThreshold) maxThreshold = minThreshold + 0.001f;
        scratch.clear();
        scratch.putFloat(near).putFloat(far).putFloat(minThreshold).putFloat(maxThreshold);
        scratch.flip();
        encoder.writeToBuffer(depthUbo.slice(), scratch);

        // Композит: подмешиваем размытие поверх основного кадра по глубине.
        GpuTextureView targetView = mainFb.getColorTextureView();
        try (RenderPass pass = encoder.createRenderPass(() -> "vesence:fogblur_composite", targetView, Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(COMPOSITE_PIPELINE));
            pass.setVertexBuffer(0, dummyVertexBuffer.slice());
            pass.setUniform("BlurSampler", fbo2View, sampler);
            pass.setUniform("DepthSampler", depthCopyView, nearest);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", dyn);
            pass.setUniform("DepthData", depthUbo);
            pass.draw(0, 6, 0, 1);
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
