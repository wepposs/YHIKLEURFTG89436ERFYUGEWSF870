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

public class TrailAccumPipeline {
    private static final Identifier PIPELINE_ID = Identifier.fromNamespaceAndPath("vesence", "pipeline/trail_accum");
    private static final Identifier VERTEX_SHADER = Identifier.fromNamespaceAndPath("vesence", "core/trail_accum");
    private static final Identifier FRAGMENT_SHADER = Identifier.fromNamespaceAndPath("vesence", "core/trail_accum");
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
                    .withUniform("TrailAccumData", UniformType.UNIFORM_BUFFER)
                    .withUniform("PrevTrailSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("CurrentMaskSampler", UniformType.COMBINED_IMAGE_SAMPLER)
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
        if (initialized) return;

        this.dataBuffer = MemoryUtil.memAlloc(BUFFER_SIZE);

        ByteBuffer dummyData = MemoryUtil.memAlloc(4);
        dummyData.putInt(0);
        dummyData.flip();
        this.dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
            () -> "vesence:trail_accum_dummy_vertex",
            GpuBuffer.USAGE_VERTEX,
            dummyData
        );
        MemoryUtil.memFree(dummyData);
        initialized = true;
    }

    public void accumulate(
        GpuTextureView targetView,
        GpuTextureView prevTrailView,
        GpuTextureView currentMaskView,
        float decay
    ) {
        ensureInitialized();
        prepareUniformData(decay);

        int size = dataBuffer.remaining();
        if (uniformBuffer == null || uniformBuffer.size() < size) {
            if (uniformBuffer != null) uniformBuffer.close();
            uniformBuffer = RenderSystem.getDevice().createBuffer(
                () -> "vesence:trail_accum_uniform",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                size
            );
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.writeToBuffer(uniformBuffer.slice(), dataBuffer);

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(
            RenderSystem.getModelViewMatrixCopy(),
            COLOR_MODULATOR,
            MODEL_OFFSET,
            TEXTURE_MATRIX
        );

        GpuSampler linearSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);

        try (RenderPass renderPass = encoder.createRenderPass(() -> "vesence:trail_accum_pass", targetView, Optional.empty())) {
            renderPass.setPipeline(RenderSystem.getCompiledPipeline(PIPELINE));
            renderPass.setVertexBuffer(0, dummyVertexBuffer.slice());
            renderPass.setUniform("PrevTrailSampler", prevTrailView, linearSampler);
            renderPass.setUniform("CurrentMaskSampler", currentMaskView, linearSampler);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.setUniform("TrailAccumData", uniformBuffer);
            renderPass.draw(0, 6, 0, 1);
        }
    }

    private void prepareUniformData(float decay) {
        dataBuffer.clear();
        dataBuffer.putFloat(decay);
        dataBuffer.putFloat(0);
        dataBuffer.putFloat(0);
        dataBuffer.putFloat(0);
        dataBuffer.flip();
    }

    public void close() {
        if (uniformBuffer != null) { uniformBuffer.close(); uniformBuffer = null; }
        if (dummyVertexBuffer != null) { dummyVertexBuffer.close(); dummyVertexBuffer = null; }
        if (dataBuffer != null) { MemoryUtil.memFree(dataBuffer); dataBuffer = null; }
        initialized = false;
    }
}
