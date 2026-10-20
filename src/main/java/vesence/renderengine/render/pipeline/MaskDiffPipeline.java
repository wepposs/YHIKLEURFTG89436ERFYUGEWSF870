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
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.nio.ByteBuffer;
import java.util.Optional;
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

public class MaskDiffPipeline {
    private static final Identifier PIPELINE_ID = Identifier.fromNamespaceAndPath("vesence", "pipeline/mask_diff");
    private static final Identifier VERTEX_SHADER = Identifier.fromNamespaceAndPath("vesence", "core/mask_diff");
    private static final Identifier FRAGMENT_SHADER = Identifier.fromNamespaceAndPath("vesence", "core/mask_diff");
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
                    .withUniform("MaskData", UniformType.UNIFORM_BUFFER)
                    .withUniform("BeforeSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("AfterSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("DepthBeforeSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("DepthAfterSampler", UniformType.COMBINED_IMAGE_SAMPLER)
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
    private boolean initialized = false;

    private void ensureInitialized() {
        if (initialized) {
            return;
        }

        this.dataBuffer = MemoryUtil.memAlloc(BUFFER_SIZE);

        ByteBuffer dummyData = MemoryUtil.memAlloc(4);
        dummyData.putInt(0);
        dummyData.flip();
        this.dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
            () -> "vesence:mask_diff_dummy_vertex",
            GpuBuffer.USAGE_VERTEX,
            dummyData
        );
        MemoryUtil.memFree(dummyData);
        initialized = true;
    }

    public void createMask(
        GpuTextureView targetView,
        GpuTextureView beforeView,
        GpuTextureView afterView,
        GpuTextureView depthBeforeView,
        GpuTextureView depthAfterView,
        int width,
        int height
    ) {
        ensureInitialized();

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        GpuSampler nearestSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);

        prepareUniformData(width, height);
        int size = dataBuffer.remaining();
        if (uniformBuffer == null || uniformBuffer.size() < size) {
            if (uniformBuffer != null) {
                uniformBuffer.close();
            }
            uniformBuffer = RenderSystem.getDevice().createBuffer(
                () -> "vesence:mask_diff_uniform",
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

        try (RenderPass renderPass = encoder.createRenderPass(() -> "vesence:mask_diff_pass", targetView, Optional.of(new Vector4f(0.0f, 0.0f, 0.0f, 0.0f)))) {
            renderPass.setPipeline(RenderSystem.getCompiledPipeline(PIPELINE));
            renderPass.setVertexBuffer(0, dummyVertexBuffer.slice());
            renderPass.setUniform("BeforeSampler", beforeView, sampler);
            renderPass.setUniform("AfterSampler", afterView, sampler);
            renderPass.setUniform("DepthBeforeSampler", depthBeforeView, nearestSampler);
            renderPass.setUniform("DepthAfterSampler", depthAfterView, nearestSampler);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.setUniform("MaskData", uniformBuffer);
            renderPass.draw(0, 6, 0, 1);
        }
    }

    private void prepareUniformData(int width, int height) {
        dataBuffer.clear();
        dataBuffer.putFloat(width);
        dataBuffer.putFloat(height);
        dataBuffer.putFloat(0);
        dataBuffer.putFloat(0);
        dataBuffer.flip();
    }

    public void close() {
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
