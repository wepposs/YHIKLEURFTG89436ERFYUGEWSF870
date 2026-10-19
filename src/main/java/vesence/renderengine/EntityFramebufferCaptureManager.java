package vesence.renderengine;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.util.Window;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack.Entry;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import net.minecraft.client.render.command.RenderDispatcher;
import org.joml.Quaternionf;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import vesence.renderengine.render.Renderer2D;

/**
 * Wave-стиль захвата силуэта сущностей в собственный офф-скрин фреймбуфер
 * (адаптация wave.client.utils.render.framebuffer.CustomFramebuffer + ручного
 * рендера сущностей через RenderManager из Wave 1.16.5 на конвейер 1.21.11).
 *
 * <p>В отличие от ванильного entityOutlineFramebuffer, здесь выбранные сущности
 * рендерятся <b>полными моделями</b> (с бронёй, предметами в руках и дропнутыми
 * ItemEntity) в свой SimpleFramebuffer через render-command-queue
 * ({@link EntityRenderManager#render}) + {@link RenderDispatcher}, с выводом в
 * захватный фреймбуфер через {@code RenderSystem.outputColorTextureOverride}.
 * Полученная текстура затем пост-обрабатывается ShaderEspPipeline (свечение,
 * заливка модели, обводка) — поэтому «предметы видны внутри».</p>
 */
@Environment(EnvType.CLIENT)
public final class EntityFramebufferCaptureManager {
   private static final int CAPTURE_BUFFER_CAPACITY_BYTES = 2097152;
   private static final Logger LOGGER = LogManager.getLogger("EntityFramebufferCapture");
   private static final EntityFramebufferCaptureManager INSTANCE = new EntityFramebufferCaptureManager();

   private volatile SimpleFramebuffer captureFramebuffer;
   private volatile boolean enabled;
   private volatile boolean frameAvailable;
   private volatile boolean executingCapturePass;
   private volatile boolean captureFrameActive;
   private volatile boolean submittedThisFrame;
   private volatile int framebufferWidth = -1;
   private volatile int framebufferHeight = -1;

   // Кэшируемые ресурсы рендера (создаются один раз, переиспользуются между кадрами).
   private BufferAllocator captureAllocator;
   private Immediate captureConsumers;
   private OrderedRenderCommandQueueImpl captureQueue;
   private RenderDispatcher captureDispatcher;
   private final CameraRenderState cameraRenderState = new CameraRenderState();

   private EntityFramebufferCaptureManager() {
   }

   public static EntityFramebufferCaptureManager getInstance() {
      return INSTANCE;
   }

   public void setEnabled(boolean enabled) {
      if (this.enabled != enabled) {
         this.enabled = enabled;
         if (!enabled) {
            this.frameAvailable = false;
            this.executingCapturePass = false;
            this.captureFrameActive = false;
            this.submittedThisFrame = false;
            this.framebufferWidth = -1;
            this.framebufferHeight = -1;
            this.disposeRenderResources();
            this.deleteFramebuffer();
         }
      }
   }

   public boolean isEnabled() {
      return this.enabled;
   }

   public boolean hasCapturedFrame() {
      return this.enabled && this.frameAvailable && extractColorTextureId(this.captureFramebuffer) > 0;
   }

   public boolean isExecutingCapturePass() {
      return this.executingCapturePass;
   }

   public boolean isCaptureFrameActive() {
      return this.enabled && this.captureFrameActive && this.captureFramebuffer != null;
   }

   public int getCapturedColorTextureId() {
      return this.hasCapturedFrame() ? extractColorTextureId(this.captureFramebuffer) : 0;
   }

   // ------------------------------------------------------------------
   //  Начало кадра: подготовка/очистка захватного фреймбуфера и очереди
   // ------------------------------------------------------------------

   public void beginFrame(Vec3d cameraPos, Quaternionf cameraRotation) {
      if (!this.enabled) {
         this.resetFrameState();
         return;
      }
      Objects.requireNonNull(cameraPos, "cameraPos");
      Objects.requireNonNull(cameraRotation, "cameraRotation");

      MinecraftClient client = MinecraftClient.getInstance();
      if (client == null || client.world == null || client.gameRenderer == null) {
         this.resetFrameState();
         return;
      }
      if (client.gameRenderer.isRenderingPanorama()) {
         this.resetFrameState();
         return;
      }
      Framebuffer mainFramebuffer = client.getFramebuffer();
      if (mainFramebuffer == null) {
         this.resetFrameState();
         return;
      }
      Window window = client.getWindow();
      int width = window != null ? window.getFramebufferWidth() : mainFramebuffer.textureWidth;
      int height = window != null ? window.getFramebufferHeight() : mainFramebuffer.textureHeight;
      if (width <= 0 || height <= 0) {
         this.resetFrameState();
         return;
      }
      if (!this.ensureCaptureFramebuffer(width, height)) {
         this.resetFrameState();
         return;
      }

      SimpleFramebuffer framebuffer = this.captureFramebuffer;
      if (framebuffer == null) {
         this.resetFrameState();
         return;
      }
      GpuTextureView colorView = framebuffer.getColorAttachmentView();
      if (colorView == null || colorView.isClosed()) {
         this.resetFrameState();
         return;
      }

      // Очистка захватного фреймбуфера (цвет + глубина) — как entityBuffer.setup(true) в Wave.
      try {
         GpuTextureView depthView = framebuffer.getDepthAttachmentView();
         CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
         GpuTexture colorTexture = colorView.texture();
         if (depthView != null && !depthView.isClosed()) {
            encoder.clearColorAndDepthTextures(colorTexture, 0, depthView.texture(), 1.0);
         } else {
            encoder.clearColorTexture(colorTexture, 0);
         }
      } catch (RuntimeException var10) {
         LOGGER.warn("Failed to clear capture framebuffer", var10);
         this.resetFrameState();
         return;
      }

      if (!this.ensureRenderResources()) {
         this.resetFrameState();
         return;
      }

      // Сброс очереди перед новым кадром.
      try {
         this.captureQueue.onNextFrame();
         this.captureQueue.clear();
      } catch (RuntimeException ignored) {
      }

      // CameraRenderState — как в ванильном world-render (для билбордов/ориентации).
      this.cameraRenderState.pos = cameraPos;
      this.cameraRenderState.entityPos = cameraPos;
      this.cameraRenderState.blockPos = BlockPos.ofFloored(cameraPos);
      this.cameraRenderState.orientation = new Quaternionf(cameraRotation);
      this.cameraRenderState.initialized = true;

      this.frameAvailable = false;
      this.submittedThisFrame = false;
      this.captureFrameActive = true;
   }

   // ------------------------------------------------------------------
   //  Сабмит одной сущности в очередь (без отрисовки — как renderEntityStatic)
   // ------------------------------------------------------------------

   public void captureEntity(Entity entity, double cameraX, double cameraY, double cameraZ, float tickDelta, MatrixStack matrices) {
      if (!this.enabled || !this.captureFrameActive || this.executingCapturePass) {
         return;
      }
      Objects.requireNonNull(entity, "entity");
      Objects.requireNonNull(matrices, "matrices");
      if (this.captureQueue == null) {
         return;
      }
      MinecraftClient client = MinecraftClient.getInstance();
      if (client == null || client.world == null) {
         return;
      }
      EntityRenderManager dispatcher = client.getEntityRenderDispatcher();
      if (dispatcher == null) {
         return;
      }

      try {
         MatrixStack captureMatrices = borrowMatrixStack(matrices);
         if (captureMatrices == null) {
            return;
         }
         Vec3d interpolated = entity.getLerpedPos(tickDelta);
         double renderX = interpolated.x - cameraX;
         double renderY = interpolated.y - cameraY;
         double renderZ = interpolated.z - cameraZ;

         EntityRenderState state = dispatcher.getAndUpdateRenderState(entity, tickDelta);
         if (state == null) {
            return;
         }
         dispatcher.render(state, this.cameraRenderState, renderX, renderY, renderZ, captureMatrices, this.captureQueue);
         this.submittedThisFrame = true;
      } catch (RuntimeException var16) {
         LOGGER.warn("Failed to submit entity {} into capture queue", entity.getName().getString(), var16);
      }
   }

   // ------------------------------------------------------------------
   //  Конец кадра: прогон очереди с выводом в захватный фреймбуфер
   // ------------------------------------------------------------------

   public void endFrame() {
      if (!this.captureFrameActive) {
         this.frameAvailable = this.frameAvailable && extractColorTextureId(this.captureFramebuffer) > 0;
         return;
      }
      this.captureFrameActive = false;

      SimpleFramebuffer framebuffer = this.captureFramebuffer;
      if (framebuffer == null || this.captureDispatcher == null || !this.submittedThisFrame) {
         this.frameAvailable = false;
         return;
      }
      GpuTextureView colorView = framebuffer.getColorAttachmentView();
      if (colorView == null || colorView.isClosed()) {
         this.frameAvailable = false;
         return;
      }
      GpuTextureView depthView = framebuffer.getDepthAttachmentView();

      GpuTextureView previousColorOverride = RenderSystem.outputColorTextureOverride;
      GpuTextureView previousDepthOverride = RenderSystem.outputDepthTextureOverride;
      RenderSystem.outputColorTextureOverride = colorView;
      RenderSystem.outputDepthTextureOverride = (depthView != null && !depthView.isClosed()) ? depthView : null;
      this.executingCapturePass = true;

      try {
         this.captureDispatcher.render();
         this.captureDispatcher.endLayeredCustoms();
         this.captureConsumers.draw();
         this.frameAvailable = true;
      } catch (RuntimeException var9) {
         LOGGER.warn("Failed to flush capture queue", var9);
         this.frameAvailable = false;
      } finally {
         this.executingCapturePass = false;
         RenderSystem.outputColorTextureOverride = previousColorOverride;
         RenderSystem.outputDepthTextureOverride = previousDepthOverride;
      }

      this.frameAvailable = this.frameAvailable && extractColorTextureId(this.captureFramebuffer) > 0;
   }

   public void renderFramebuffer(Renderer2D renderer, int viewportWidth, int viewportHeight) {
      if (renderer != null && viewportWidth > 0 && viewportHeight > 0 && this.hasCapturedFrame()) {
         int textureId = extractColorTextureId(this.captureFramebuffer);
         if (textureId > 0) {
            renderer.drawPremultipliedRgbaTexture(textureId, 0.0F, 0.0F, viewportWidth, viewportHeight);
         }
      }
   }

   // ------------------------------------------------------------------
   //  Ресурсы рендера
   // ------------------------------------------------------------------

   private boolean ensureRenderResources() {
      if (this.captureDispatcher != null && this.captureQueue != null && this.captureConsumers != null) {
         return true;
      }
      MinecraftClient client = MinecraftClient.getInstance();
      if (client == null) {
         return false;
      }
      try {
         if (this.captureConsumers == null) {
            // Многобуферный entity-immediate из ванильного BufferBuilderStorage.
            // Одиночный VertexConsumerProvider.immediate(allocator) держит только ОДИН
            // строящийся слой за раз, из-за чего предметы с зачарованием падали с
            // "Not building!" (glint рендерится через VertexConsumers.Dual — базовый
            // слой + слой блеска одновременно). Ванильные entity-consumers многобуферные
            // и корректно тянут glint / несколько RenderLayer сразу.
            this.captureConsumers = client.getBufferBuilders().getEntityVertexConsumers();
         }
         if (this.captureQueue == null) {
            this.captureQueue = new OrderedRenderCommandQueueImpl();
         }
         if (this.captureDispatcher == null) {
            this.captureDispatcher = new RenderDispatcher(
                    this.captureQueue,
                    client.getBlockRenderManager(),
                    this.captureConsumers,
                    client.getAtlasManager(),
                    client.getBufferBuilders().getOutlineVertexConsumers(),
                    client.getBufferBuilders().getEffectVertexConsumers(),
                    client.textRenderer);
         }
         return true;
      } catch (RuntimeException var2) {
         LOGGER.warn("Failed to allocate capture render resources", var2);
         this.disposeRenderResources();
         return false;
      }
   }

   private void disposeRenderResources() {
      if (this.captureDispatcher != null) {
         try {
            this.captureDispatcher.close();
         } catch (RuntimeException ignored) {
         }
         this.captureDispatcher = null;
      }
      this.captureQueue = null;
      this.captureConsumers = null;
      if (this.captureAllocator != null) {
         try {
            this.captureAllocator.close();
         } catch (RuntimeException ignored) {
         }
         this.captureAllocator = null;
      }
   }

   private static MatrixStack borrowMatrixStack(MatrixStack source) {
      if (source == null) {
         return null;
      }
      MatrixStack copy = new MatrixStack();
      Entry sourceEntry = source.peek();
      Entry copyEntry = copy.peek();
      copyEntry.getPositionMatrix().set(sourceEntry.getPositionMatrix());
      copyEntry.getNormalMatrix().set(sourceEntry.getNormalMatrix());
      return copy;
   }

   private boolean ensureCaptureFramebuffer(int width, int height) {
      SimpleFramebuffer framebuffer = this.captureFramebuffer;
      if (framebuffer == null) {
         try {
            framebuffer = new SimpleFramebuffer("vesence_entity_capture", width, height, true);
            this.captureFramebuffer = framebuffer;
            this.framebufferWidth = width;
            this.framebufferHeight = height;
         } catch (RuntimeException var6) {
            LOGGER.warn("Failed to create capture framebuffer {}x{}", width, height, var6);
            this.captureFramebuffer = null;
            this.framebufferWidth = -1;
            this.framebufferHeight = -1;
            return false;
         }
      }

      if (this.framebufferWidth != width || this.framebufferHeight != height) {
         try {
            framebuffer.resize(width, height);
            this.framebufferWidth = width;
            this.framebufferHeight = height;
         } catch (RuntimeException var5) {
            LOGGER.warn("Failed to resize capture framebuffer to {}x{}", width, height, var5);
            this.deleteFramebuffer();
            this.framebufferWidth = -1;
            this.framebufferHeight = -1;
            return false;
         }
      }

      return hasColorAttachment(framebuffer);
   }

   private void deleteFramebuffer() {
      Framebuffer framebuffer = this.captureFramebuffer;
      if (framebuffer != null) {
         try {
            framebuffer.delete();
         } catch (RuntimeException var3) {
            LOGGER.warn("Failed to delete capture framebuffer", var3);
         }
         this.captureFramebuffer = null;
      }
   }

   private void resetFrameState() {
      this.frameAvailable = false;
      this.captureFrameActive = false;
      this.submittedThisFrame = false;
   }

   private static boolean hasColorAttachment(Framebuffer framebuffer) {
      if (framebuffer == null) {
         return false;
      }
      return framebuffer.getColorAttachment() instanceof GlTexture glTexture && glTexture.getGlId() > 0;
   }

   private static int extractColorTextureId(Framebuffer framebuffer) {
      if (framebuffer == null) {
         return 0;
      }
      return framebuffer.getColorAttachment() instanceof GlTexture glTexture ? glTexture.getGlId() : 0;
   }
}
