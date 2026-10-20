package vesence.utils.render.world;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Environment(EnvType.CLIENT)
public class WorldRenderer {
    private static final int DEFAULT_BUFFER_CAPACITY_BYTES = 262144;
    private final Camera camera;
    private final PoseStack matrixStack;
    private final Matrix4f positionMatrix;
    private final Matrix4f worldMatrix;
    private final Matrix4f projectionMatrix;
    private final ByteBufferBuilder bufferAllocator;
    private final ImmediateBuffers immediate;
    private final float tickDelta;
    private boolean closed;

    private WorldRenderer(
            Camera camera,
            PoseStack matrixStack,
            Matrix4f positionMatrix,
            Matrix4f worldMatrix,
            Matrix4f projectionMatrix,
            ByteBufferBuilder bufferAllocator,
            ImmediateBuffers immediate,
            float tickDelta
    ) {
        this.camera = camera;
        this.matrixStack = matrixStack;
        this.positionMatrix = positionMatrix;
        this.worldMatrix = worldMatrix;
        this.projectionMatrix = projectionMatrix;
        this.bufferAllocator = bufferAllocator;
        this.immediate = immediate;
        this.tickDelta = tickDelta;
    }

    public static WorldRenderer begin(Minecraft client, DeltaTracker tickCounter, Camera camera, Matrix4f positionMatrix, Matrix4f projectionMatrix) {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(tickCounter, "tickCounter");
        Objects.requireNonNull(camera, "camera");
        Objects.requireNonNull(positionMatrix, "positionMatrix");
        Objects.requireNonNull(projectionMatrix, "projectionMatrix");
        PoseStack stack = new PoseStack();
        Matrix4f basePositionMatrix = new Matrix4f(positionMatrix);
        Matrix4f baseWorldMatrix = new Matrix4f(basePositionMatrix);
        stack.mulPose(new Matrix4f(basePositionMatrix));
        ByteBufferBuilder allocator = new ByteBufferBuilder(262144);
        ImmediateBuffers immediate = new ImmediateBuffers(allocator);
        float tickDelta = tickCounter.getGameTimeDeltaPartialTick(false);
        return new WorldRenderer(camera, stack, basePositionMatrix, baseWorldMatrix, new Matrix4f(projectionMatrix), allocator, immediate, tickDelta);
    }

    public Camera camera() {
        return this.camera;
    }

    public PoseStack matrixStack() {
        return this.matrixStack;
    }

    public Matrix4f positionMatrix() {
        return new Matrix4f(this.positionMatrix);
    }

    public Matrix4f worldMatrix() {
        return new Matrix4f(this.worldMatrix);
    }

    public Matrix4f projectionMatrix() {
        return new Matrix4f(this.projectionMatrix);
    }

    public float tickDelta() {
        return this.tickDelta;
    }

    public ImmediateBuffers bufferSource() {
        if (this.closed) {
            throw new IllegalStateException("Cannot access buffers after the world renderer has been closed.");
        } else {
            return this.immediate;
        }
    }

    public VertexConsumer getBuffer(RenderType layer) {
        Objects.requireNonNull(layer, "layer");
        if (this.closed) {
            throw new IllegalStateException("Cannot request buffers after the world renderer has been closed.");
        } else {
            return this.immediate.getBuffer(layer);
        }
    }

    public void drawQuad(Vec3 v0, Vec3 v1, Vec3 v2, Vec3 v3, int rgbaColor, boolean depthTest) {
        Objects.requireNonNull(v0, "v0");
        Objects.requireNonNull(v1, "v1");
        Objects.requireNonNull(v2, "v2");
        Objects.requireNonNull(v3, "v3");
        RenderType layer = depthTest ? WorldRenderLayers.POSITION_COLOR_QUADS() : WorldRenderLayers.POSITION_COLOR_QUADS_NO_DEPTH();
        WorldGeometryEmitter emitter = new WorldGeometryEmitter(this, this.matrixStack.last(), this.getBuffer(layer));
        emitter.emitQuad(v0, v1, v2, v3, rgbaColor);
    }

    public void drawCube(Vec3 min, Vec3 max, int rgbaColor, boolean depthTest) {
        Objects.requireNonNull(min, "min");
        Objects.requireNonNull(max, "max");
        RenderType layer = depthTest ? WorldRenderLayers.POSITION_COLOR_QUADS() : WorldRenderLayers.POSITION_COLOR_QUADS_NO_DEPTH_BLEND();
        WorldGeometryEmitter emitter = new WorldGeometryEmitter(this, this.matrixStack.last(), this.getBuffer(layer));
        emitter.emitCube(min, max, rgbaColor);
    }

    public void drawLine(Vec3 start, Vec3 end, double width, int rgbaColor, boolean depthTest) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (!Double.isFinite(width)) {
            throw new IllegalArgumentException("Line width must be finite.");
        } else if (width < 0.0) {
            throw new IllegalArgumentException("Line width cannot be negative.");
        } else {
            RenderType layer = depthTest ? WorldRenderLayers.LINES(width) : WorldRenderLayers.LINES_NO_DEPTH(width);
            WorldGeometryEmitter emitter = new WorldGeometryEmitter(this, this.matrixStack.last(), this.getBuffer(layer));
            emitter.emitLine(start, end, rgbaColor);
        }
    }

    public void drawTexturedQuad(
            Vec3 v0,
            Vec3 v1,
            Vec3 v2,
            Vec3 v3,
            float u0,
            float v0Coord,
            float u1,
            float v1Coord,
            float u2,
            float v2Coord,
            float u3,
            float v3Coord,
            int rgbaColor
    ) {
        Objects.requireNonNull(v0, "v0");
        Objects.requireNonNull(v1, "v1");
        Objects.requireNonNull(v2, "v2");
        Objects.requireNonNull(v3, "v3");
        RenderType layer = WorldRenderLayers.TEXTURED_QUADS();
        WorldGeometryEmitter emitter = new WorldGeometryEmitter(this, this.matrixStack.last(), this.getBuffer(layer));
        emitter.emitTexturedQuad(v0, v1, v2, v3, u0, v0Coord, u1, v1Coord, u2, v2Coord, u3, v3Coord, rgbaColor);
    }

    public void flush() {
        if (!this.closed) {
            this.immediate.draw();
        }
    }

    public void close() {
        if (!this.closed) {
            this.closed = true;

            try {
                this.bufferAllocator.close();
            } catch (RuntimeException var2) {
                throw var2;
            } catch (Exception var3) {
                throw new IllegalStateException("Failed to release world renderer buffers.", var3);
            }
        }
    }

    // 26.3: MultiBufferSource удалён. Это его замена: копим геометрию по RenderType,
    // а draw() отправляет её на GPU.
    //
    // ВАЖНО про момент отправки: эти draw() вызываются из EventRender3D, то есть
    // ВНУТРИ отрисовки мира, когда frame graph ещё кодируется. Создавать там свой
    // RenderPass нельзя — это вешает кадр. Поэтому draw() лишь складывает работу в
    // Vesence.deferOverlay(...), а реальная отправка происходит в пост-GUI проходе
    // GameRenderer.render() (тот же безопасный момент, где рисуются HUD и MainScreen).
    public static final class ImmediateBuffers {
        private final ByteBufferBuilder allocator;
        private final Map<RenderType, BufferBuilder> buffers = new HashMap<>();

        ImmediateBuffers(ByteBufferBuilder allocator) {
            this.allocator = allocator;
        }

        /** Публичный конструктор для переиспользования из других модулей (AimBot). */
        public static ImmediateBuffers create(ByteBufferBuilder allocator) {
            return new ImmediateBuffers(allocator);
        }

        public VertexConsumer getBuffer(RenderType layer) {
            return this.buffers.computeIfAbsent(layer,
                    l -> new BufferBuilder(this.allocator, l.primitiveTopology(), l.format()));
        }

        public void draw() {
            draw(null);
        }

        /**
         * Ставит отправку накопленной геометрии в очередь пост-GUI прохода.
         * Меши строятся позже, поэтому в буферы нельзя добавлять вершины между
         * вызовом draw() и концом кадра.
         */
        public void draw(com.mojang.blaze3d.pipeline.RenderTarget target) {
            if (this.buffers.isEmpty()) {
                return;
            }
            final com.mojang.blaze3d.pipeline.RenderTarget renderTarget = target;
            vesence.Vesence.deferOverlay(() -> this.submitNow(renderTarget));
        }

        /**
         * Собственно отправка на GPU. Выполняется в пост-GUI проходе, когда кадровый
         * граф уже не кодируется — создавать здесь RenderPass безопасно.
         */
        private void submitNow(com.mojang.blaze3d.pipeline.RenderTarget target) {
            if (this.buffers.isEmpty()) {
                return;
            }
            if (target == null) {
                Minecraft mc = Minecraft.getInstance();
                target = mc != null && mc.gameRenderer != null
                        ? mc.gameRenderer.mainRenderTarget()
                        : null;
            }
            if (target == null) {
                this.closeMeshes();
                return;
            }

            com.mojang.renderpearl.api.device.GpuDevice device =
                    com.mojang.blaze3d.systems.RenderSystem.getDevice();
            com.mojang.renderpearl.api.commands.CommandEncoder encoder = device.createCommandEncoder();
            try (com.mojang.renderpearl.api.commands.RenderPass pass = encoder.createRenderPass(
                    () -> "vesence world",
                    target.getColorTextureView(), java.util.Optional.empty(),
                    target.getDepthTextureView(), java.util.OptionalDouble.empty())) {
                for (Map.Entry<RenderType, BufferBuilder> entry : this.buffers.entrySet()) {
                    RenderType layer = entry.getKey();
                    MeshData mesh = entry.getValue().build();
                    if (mesh == null) {
                        continue;
                    }
                    try (mesh) {
                        MeshData.DrawState state = mesh.drawState();
                        java.nio.ByteBuffer indices = mesh.indexBuffer();
                        if (state.indexCount() <= 0 || state.indexType() == null || indices == null) {
                            continue;
                        }
                        com.mojang.renderpearl.api.buffers.GpuBuffer vertexBuffer = device.createBuffer(
                                () -> "vesence/world/vertices",
                                com.mojang.renderpearl.api.buffers.GpuBuffer.USAGE_VERTEX
                                        | com.mojang.renderpearl.api.buffers.GpuBuffer.USAGE_COPY_DST,
                                mesh.vertexBuffer());
                        com.mojang.renderpearl.api.buffers.GpuBuffer indexBuffer = null;
                        try {
                            indexBuffer = device.createBuffer(
                                    () -> "vesence/world/indices",
                                    com.mojang.renderpearl.api.buffers.GpuBuffer.USAGE_INDEX
                                            | com.mojang.renderpearl.api.buffers.GpuBuffer.USAGE_COPY_DST,
                                    indices);
                            net.minecraft.client.renderer.rendertype.PreparedRenderType prepared = layer.prepare();
                            prepared.drawFromBuffer(new net.minecraft.client.renderer.StagedVertexBuffer.ExecuteInfo(
                                    vertexBuffer, indexBuffer, state.indexType(), 0, 0,
                                    state.indexCount(), state.primitiveTopology()), pass);
                        } finally {
                            vertexBuffer.close();
                            if (indexBuffer != null) {
                                indexBuffer.close();
                            }
                        }
                    }
                }
            } finally {
                this.buffers.clear();
            }
        }

        /** Освобождает собранные mesh'и, ничего не рисуя. */
        private void closeMeshes() {
            for (BufferBuilder builder : this.buffers.values()) {
                MeshData mesh = builder.build();
                if (mesh != null) {
                    mesh.close();
                }
            }
            this.buffers.clear();
        }
    }
}
