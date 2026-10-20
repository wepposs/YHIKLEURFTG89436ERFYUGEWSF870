package vesence.module.impl.visuals;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.device.GpuDevice;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * TODO(port26): замена удалённому в 26.3 {@code Immediate}
 * (yarn {@code VertexConsumerProvider.Immediate}). В 26.3 нет публичного
 * батчера RenderType → VertexConsumer: геометрия собирается в {@link BufferBuilder}
 * на слой, а на flush загружается в GpuBuffer и рисуется через
 * {@link PreparedRenderType#drawFromBuffer} в собственном RenderPass поверх
 * основного рендер-таргета (load = KEEP).
 *
 * <p>Каждый вызов {@link #draw()} закрывает и пересоздаёт буферы, как это делал
 * ванильный immediate.draw().
 */
public final class Immediate implements AutoCloseable {

   private static final int BUFFER_CAPACITY_BYTES = 262144;

   private final Map<RenderType, BufferBuilder> builders = new HashMap<>();
   private final Map<RenderType, ByteBufferBuilder> allocators = new HashMap<>();

   public VertexConsumer getBuffer(RenderType layer) {
      BufferBuilder builder = this.builders.get(layer);
      if (builder == null) {
         ByteBufferBuilder allocator = new ByteBufferBuilder(BUFFER_CAPACITY_BYTES);
         builder = new BufferBuilder(allocator, layer.primitiveTopology(), layer.format());
         this.allocators.put(layer, allocator);
         this.builders.put(layer, builder);
      }
      return builder;
   }

   public void draw() {
      RenderTarget target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
      if (target == null) {
         this.reset();
         return;
      }
      this.draw(target);
   }

   public void draw(RenderTarget target) {
      if (this.builders.isEmpty()) {
         return;
      }
      GpuDevice device = RenderSystem.getDevice();
      CommandEncoder encoder = device.createCommandEncoder();
      try (RenderPass pass = encoder.createRenderPass(() -> "vesence visuals",
            target.getColorTextureView(), Optional.empty(),
            target.getDepthTextureView(), OptionalDouble.empty())) {
         for (Map.Entry<RenderType, BufferBuilder> entry : this.builders.entrySet()) {
            RenderType layer = entry.getKey();
            MeshData mesh = entry.getValue().build();
            if (mesh == null) {
               continue;
            }
            try (mesh) {
               MeshData.DrawState state = mesh.drawState();
               ByteBuffer indices = mesh.indexBuffer();
               if (state.indexCount() <= 0 || state.indexType() == null || indices == null) {
                  continue;
               }
               GpuBuffer vertexBuffer = device.createBuffer(() -> "vesence/" + layer + "/vertices",
                     GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, mesh.vertexBuffer());
               GpuBuffer indexBuffer = null;
               try {
                  indexBuffer = device.createBuffer(() -> "vesence/" + layer + "/indices",
                        GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_COPY_DST, indices);
                  PreparedRenderType prepared = layer.prepare();
                  prepared.drawFromBuffer(new StagedVertexBuffer.ExecuteInfo(
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
         this.reset();
      }
   }

   /** Сбрасывает все буферы (после draw или при выключении модуля). */
   public void reset() {
      for (ByteBufferBuilder allocator : this.allocators.values()) {
         allocator.close();
      }
      this.allocators.clear();
      this.builders.clear();
   }

   @Override
   public void close() {
      this.reset();
   }
}
