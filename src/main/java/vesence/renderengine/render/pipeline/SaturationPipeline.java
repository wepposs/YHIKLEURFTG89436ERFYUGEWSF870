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

public class SaturationPipeline {
    private static final Identifier PIPELINE_ID = Identifier.fromNamespaceAndPath("vesence", "pipeline/saturation");
    private static final Identifier VERTEX_SHADER = Identifier.fromNamespaceAndPath("vesence", "core/saturation");
    private static final Identifier FRAGMENT_SHADER = Identifier.fromNamespaceAndPath("vesence", "core/saturation");
    private static final BlendFunction REPLACE_BLEND = new BlendFunction(BlendFactor.ONE, BlendFactor.ZERO, BlendFactor.ONE, BlendFactor.ZERO);
    private static final RenderPipeline PIPELINE = RenderPipelines.register(
        RenderPipeline.builder()
            .withLocation(PIPELINE_ID)
            .withVertexShader(VERTEX_SHADER)
            .withFragmentShader(FRAGMENT_SHADER)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayout.builder()
                    .withUniform("SaturationData", UniformType.UNIFORM_BUFFER)
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
    private static final int BUFFER_SIZE = 16;

    private GpuBuffer uniformBuffer;
    private GpuBuffer dummyVertexBuffer;
    private ByteBuffer dataBuffer;

    private GpuTexture sceneCopyTexture;
    private GpuTextureView sceneCopyTextureView;
    private int lastWidth = 0;
    private int lastHeight = 0;
    private boolean initialized = false;

    private void ensureInitialized() {
        if (initialized) return;

        this.dataBuffer = MemoryUtil.memAlloc(BUFFER_SIZE);

        ByteBuffer dummyData = MemoryUtil.memAlloc(4);
        dummyData.putInt(0);
        dummyData.flip();
        this.dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
            () -> "vesence:saturation_dummy_vertex",
            GpuBuffer.USAGE_VERTEX,
            dummyData
        );
        MemoryUtil.memFree(dummyData);
        initialized = true;
    }

    private void ensureTextures(int width, int height) {
        if (width == lastWidth && height == lastHeight && sceneCopyTexture != null) {
            return;
        }
        cleanupTextures();

        sceneCopyTexture = RenderSystem.getDevice().createTexture(
            () -> "vesence:saturation_scene_copy",
            GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
            GpuFormat.RGBA8_UNORM,
            width,
            height,
            1,
            1
        );
        sceneCopyTextureView = RenderSystem.getDevice().createTextureView(sceneCopyTexture);

        lastWidth = width;
        lastHeight = height;
    }

    private void cleanupTextures() {
        if (sceneCopyTextureView != null) {
            sceneCopyTextureView.close();
            sceneCopyTextureView = null;
        }
        if (sceneCopyTexture != null) {
            sceneCopyTexture.close();
            sceneCopyTexture = null;
        }
    }

    public void applySaturation(Minecraft mc, float saturation) {
        if (saturation == 1.0f) return;

        RenderTarget mainFb = mc.gameRenderer.mainRenderTarget();
        if (mainFb == null || mainFb.getColorTexture() == null) return;

        int width = mainFb.width;
        int height = mainFb.height;
        if (width <= 0 || height <= 0) return;

        ensureInitialized();
        ensureTextures(width, height);

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(mainFb.getColorTexture(), sceneCopyTexture, 0, 0, 0, 0, 0, width, height);

        prepareUniformData(saturation);

        int size = dataBuffer.remaining();
        if (uniformBuffer == null || uniformBuffer.size() < size) {
            if (uniformBuffer != null) {
                uniformBuffer.close();
            }
            uniformBuffer = RenderSystem.getDevice().createBuffer(
                () -> "vesence:saturation_uniform",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                size
            );
        }

        encoder.writeToBuffer(uniformBuffer.slice(), dataBuffer);

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(
            RenderSystem.getModelViewMatrixCopy(),
            COLOR_MODULATOR,
            MODEL_OFFSET,
            TEXTURE_MATRIX
        );

        GpuSampler linearSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        GpuTextureView targetView = mainFb.getColorTextureView();

        try (RenderPass renderPass = encoder.createRenderPass(() -> "vesence:saturation_pass", targetView, Optional.empty())) {
            renderPass.setPipeline(RenderSystem.getCompiledPipeline(PIPELINE));
            renderPass.setVertexBuffer(0, dummyVertexBuffer.slice());
            renderPass.setUniform("SceneSampler", sceneCopyTextureView, linearSampler);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.setUniform("SaturationData", uniformBuffer);
            renderPass.draw(0, 6, 0, 1);
        }
    }

    private void prepareUniformData(float saturation) {
        dataBuffer.clear();
        dataBuffer.putFloat(saturation);
        dataBuffer.putFloat(0);
        dataBuffer.putFloat(0);
        dataBuffer.putFloat(0);
        dataBuffer.flip();
    }

    public void close() {
        cleanupTextures();
        if (uniformBuffer != null) {
            uniformBuffer.close();
            uniformBuffer = null;
        }
        if (dummyVertexBuffer != null) {
            dummyVertexBuffer.close();
            dummyVertexBuffer = null;
        }
        if (dataBuffer != null) {
            MemoryUtil.memFree(dataBuffer);
            dataBuffer = null;
        }
        initialized = false;
    }
}
