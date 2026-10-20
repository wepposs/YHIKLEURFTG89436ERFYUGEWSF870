package vesence.renderengine;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import com.mojang.blaze3d.platform.Window;
import com.mojang.renderpearl.backend.opengl.GlTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Quaternionf;
import org.joml.Vector4f;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import vesence.renderengine.render.Renderer2D;

/**
 * Wave-стиль захвата силуэта сущностей в собственный офф-скрин фреймбуфер
 * (адаптация wave.client.utils.render.framebuffer.CustomFramebuffer + ручного
 * рендера сущностей через RenderManager из Wave 1.16.5 на конвейер 26.3).
 *
 * <p>В отличие от ванильного entityOutlineFramebuffer, здесь выбранные сущности
 * рендерятся <b>полными моделями</b> (с бронёй, предметами в руках и дропнутыми
 * ItemEntity) в свой TextureTarget через submit-очередь
 * ({@link EntityRenderDispatcher#submit} + {@link SubmitNodeStorage}) с выводом
 * через {@link FeatureRenderDispatcher.PreparedFrame}. Полученная текстура затем
 * пост-обрабатывается ShaderEspPipeline (свечение, заливка модели, обводка) —
 * поэтому «предметы видны внутри».</p>
 */
@Environment(EnvType.CLIENT)
public final class EntityFramebufferCaptureManager {
   private static final Logger LOGGER = LogManager.getLogger("EntityFramebufferCapture");
   private static final EntityFramebufferCaptureManager INSTANCE = new EntityFramebufferCaptureManager();

   private volatile TextureTarget captureFramebuffer;
   private volatile boolean enabled;
   private volatile boolean frameAvailable;
   private volatile boolean executingCapturePass;
   private volatile boolean captureFrameActive;
   private volatile boolean submittedThisFrame;
   private volatile int framebufferWidth = -1;
   private volatile int framebufferHeight = -1;

   // Кэшируемые ресурсы рендера (создаются один раз, переиспользуются между кадрами).
   private SubmitNodeStorage captureQueue;
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

   public void beginFrame(Vec3 cameraPos, Quaternionf cameraRotation) {
      if (!this.enabled) {
         this.resetFrameState();
         return;
      }
      Objects.requireNonNull(cameraPos, "cameraPos");
      Objects.requireNonNull(cameraRotation, "cameraRotation");

      Minecraft client = Minecraft.getInstance();
      if (client == null || client.level == null || client.gameRenderer == null) {
         this.resetFrameState();
         return;
      }
      // 26.3: GameRenderer#isRenderingPanorama удалён, но флаг переехал в
      // Camera#isPanoramicMode() — на панораме захват не нужен, пропускаем кадр.
      net.minecraft.client.Camera activeCamera = client.gameRenderer.mainCamera();
      if (activeCamera != null && activeCamera.isPanoramicMode()) {
         this.resetFrameState();
         return;
      }
      RenderTarget mainFramebuffer = client.gameRenderer.mainRenderTarget();
      if (mainFramebuffer == null) {
         this.resetFrameState();
         return;
      }
      Window window = client.getWindow();
      int width = window != null ? window.getWidth() : mainFramebuffer.width;
      int height = window != null ? window.getHeight() : mainFramebuffer.height;
      if (width <= 0 || height <= 0) {
         this.resetFrameState();
         return;
      }
      if (!this.ensureCaptureFramebuffer(width, height)) {
         this.resetFrameState();
         return;
      }

      TextureTarget framebuffer = this.captureFramebuffer;
      if (framebuffer == null) {
         this.resetFrameState();
         return;
      }
      GpuTextureView colorView = framebuffer.getColorTextureView();
      if (colorView == null || colorView.isClosed()) {
         this.resetFrameState();
         return;
      }

      // Очистка захватного фреймбуфера (цвет + глубина) — как entityBuffer.setup(true) в Wave.
      try {
         GpuTextureView depthView = framebuffer.getDepthTextureView();
         CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
         GpuTexture colorTexture = colorView.texture();
         if (depthView != null && !depthView.isClosed()) {
            encoder.clearColorAndDepthTextures(colorTexture, new Vector4f(0.0f, 0.0f, 0.0f, 0.0f), depthView.texture(), 1.0);
         } else {
            encoder.clearColorTexture(colorTexture, new Vector4f(0.0f, 0.0f, 0.0f, 0.0f));
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

      // CameraRenderState — как в ванильном world-render (для билбордов/ориентации).
      this.cameraRenderState.pos = cameraPos;
      // 26.3: отдельного поля entityPos в CameraRenderState больше нет — в 1.21.x
      // и pos, и entityPos заполнялись одним и тем же cameraPos, так что pos его
      // полностью покрывает. BlockPos.ofFloored переименован в BlockPos.containing.
      this.cameraRenderState.blockPos = BlockPos.containing(cameraPos);
      this.cameraRenderState.orientation = new Quaternionf(cameraRotation);
      this.cameraRenderState.initialized = true;

      this.frameAvailable = false;
      this.submittedThisFrame = false;
      this.captureFrameActive = true;
   }

   // ------------------------------------------------------------------
   //  Сабмит одной сущности в очередь (без отрисовки — как renderEntityStatic)
   // ------------------------------------------------------------------

   public void captureEntity(Entity entity, double cameraX, double cameraY, double cameraZ, float tickDelta, PoseStack matrices) {
      if (!this.enabled || !this.captureFrameActive || this.executingCapturePass) {
         return;
      }
      Objects.requireNonNull(entity, "entity");
      Objects.requireNonNull(matrices, "matrices");
      if (this.captureQueue == null) {
         return;
      }
      Minecraft client = Minecraft.getInstance();
      if (client == null || client.level == null) {
         return;
      }
      EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
      if (dispatcher == null) {
         return;
      }

      try {
         PoseStack captureMatrices = borrowMatrixStack(matrices);
         if (captureMatrices == null) {
            return;
         }
         Vec3 interpolated = entity.getPosition(tickDelta);
         double renderX = interpolated.x - cameraX;
         double renderY = interpolated.y - cameraY;
         double renderZ = interpolated.z - cameraZ;

         EntityRenderState state = dispatcher.extractEntity(entity, tickDelta);
         if (state == null) {
            return;
         }
         dispatcher.submit(state, this.cameraRenderState, renderX, renderY, renderZ, captureMatrices, this.captureQueue);
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

      TextureTarget framebuffer = this.captureFramebuffer;
      if (framebuffer == null || !this.submittedThisFrame) {
         this.frameAvailable = false;
         return;
      }
      Minecraft client = Minecraft.getInstance();
      if (client == null || client.gameRenderer == null || this.captureQueue == null) {
         this.frameAvailable = false;
         return;
      }
      GpuTextureView colorView = framebuffer.getColorTextureView();
      if (colorView == null || colorView.isClosed()) {
         this.frameAvailable = false;
         return;
      }
      GpuTextureView depthView = framebuffer.getDepthTextureView();

      this.executingCapturePass = true;

      try {
         FeatureRenderDispatcher dispatcher = client.gameRenderer.featureRenderDispatcher();
         CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
         try (FeatureRenderDispatcher.PreparedFrame prepared = dispatcher.prepareFrame(this.captureQueue);
              RenderPass pass = encoder.createRenderPass(
                      () -> "vesence:entity_capture",
                      colorView,
                      Optional.empty(),
                      (depthView != null && !depthView.isClosed()) ? depthView : null,
                      OptionalDouble.empty())) {
            prepared.executeSolid(pass);
            prepared.executeTranslucent(pass);
            this.frameAvailable = true;
         }
      } catch (RuntimeException var9) {
         LOGGER.warn("Failed to flush capture queue", var9);
         this.frameAvailable = false;
      } finally {
         this.executingCapturePass = false;
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
      if (this.captureQueue != null) {
         return true;
      }
      Minecraft client = Minecraft.getInstance();
      if (client == null) {
         return false;
      }
      try {
         this.captureQueue = new SubmitNodeStorage();
         return true;
      } catch (RuntimeException var2) {
         LOGGER.warn("Failed to allocate capture render resources", var2);
         this.disposeRenderResources();
         return false;
      }
   }

   private void disposeRenderResources() {
      this.captureQueue = null;
   }

   private static PoseStack borrowMatrixStack(PoseStack source) {
      if (source == null) {
         return null;
      }
      PoseStack copy = new PoseStack();
      Pose sourceEntry = source.last();
      Pose copyEntry = copy.last();
      copyEntry.pose().set(sourceEntry.pose());
      copyEntry.normal().set(sourceEntry.normal());
      return copy;
   }

   private boolean ensureCaptureFramebuffer(int width, int height) {
      TextureTarget framebuffer = this.captureFramebuffer;
      if (framebuffer == null) {
         try {
            framebuffer = new TextureTarget("vesence_entity_capture", width, height, GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT);
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
      RenderTarget framebuffer = this.captureFramebuffer;
      if (framebuffer != null) {
         try {
            framebuffer.destroyBuffers();
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

   private static boolean hasColorAttachment(RenderTarget framebuffer) {
      if (framebuffer == null) {
         return false;
      }
      return framebuffer.getColorTexture() instanceof GlTexture glTexture && glTexture.glId() > 0;
   }

   private static int extractColorTextureId(RenderTarget framebuffer) {
      if (framebuffer == null) {
         return 0;
      }
      return framebuffer.getColorTexture() instanceof GlTexture glTexture ? glTexture.glId() : 0;
   }
}
