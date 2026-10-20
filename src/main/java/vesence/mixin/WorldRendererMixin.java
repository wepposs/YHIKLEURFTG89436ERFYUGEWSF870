package vesence.mixin;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.DeltaTracker;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.Vesence;
import vesence.renderengine.EntityFramebufferCaptureManager;
import vesence.event.EventManager;
import vesence.event.render.EventRender3D;
import vesence.event.render.WorldRenderEvent;
import vesence.renderengine.providers.GlState;
import vesence.module.impl.visuals.Ambience;
import vesence.module.impl.visuals.ChunksAnimation;
import vesence.renderengine.render.Renderer2D;

@Environment(EnvType.CLIENT)
@Mixin({ LevelRenderer.class })
public class WorldRendererMixin {

   /**
    * ChunksAnimation: сдвиг мира по Y в начале кадра — эффект «мир появляется».
    *
    * <p>Порт 26.3: {@code LevelRenderer#render} больше не принимает position/projection
    * матрицы — они живут в {@link net.minecraft.client.renderer.state.level.CameraRenderState}.
    * Внутри render'а view-матрица собирается как
    * {@code modelViewStack * cameraState.viewRotationMatrix} (для сущностей/скайбокса) и как
    * {@code new Matrix4f(cameraState.viewRotationMatrix)} (для террейна). Поэтому вносим
    * translate прямо в {@code viewRotationMatrix} в HEAD — так смещается всё ровно так же,
    * как раньше смещался positionMatrix. Матрица пересобирается в extract-фазе каждый кадр,
    * так что правка не накапливается.
    */
   @Inject(method = {"render"}, at = {@At("HEAD")})
   private void vesence$chunksAnimationHead(
           GraphicsResourceAllocator allocator,
           boolean renderBlockOutline,
           net.minecraft.client.renderer.state.level.CameraRenderState cameraState,
           GpuBufferSlice fog,
           Vector4f fogColor,
           boolean shouldRenderSky,
           boolean renderSky,
           CallbackInfo ci) {
      ChunksAnimation anim = ChunksAnimation.INSTANCE;
      if (anim == null || !anim.enable || cameraState == null || cameraState.viewRotationMatrix == null) {
         return;
      }
      float offset = anim.computeOffsetY();
      if (offset != 0.0F) {
         cameraState.viewRotationMatrix.translate(0.0F, offset, 0.0F);
      }
   }

   @Inject(method = { "render" }, at = { @At("RETURN") })
   private void publishWorldRenderEvent(
           GraphicsResourceAllocator allocator,
           boolean renderBlockOutline,
           net.minecraft.client.renderer.state.level.CameraRenderState cameraState,
           GpuBufferSlice fog,
           Vector4f fogColor,
           boolean shouldRenderSky,
           boolean renderSky,
           CallbackInfo ci) {
      // 26.3: восстанавливаем эквиваленты старых аргументов из CameraRenderState / Minecraft.
      Minecraft mc = Minecraft.getInstance();
      DeltaTracker tickCounter = mc.getDeltaTracker();
      Camera camera = mc.gameRenderer != null ? mc.gameRenderer.mainCamera() : null;
      // positionMatrix (view) = viewRotation * translate(-cameraPos), как в старом LevelRenderer.render.
      Matrix4f positionMatrix = new Matrix4f(cameraState.viewRotationMatrix);
      if (cameraState.pos != null) {
         positionMatrix.translate((float) -cameraState.pos.x, (float) -cameraState.pos.y, (float) -cameraState.pos.z);
      }
      Matrix4f basicProjectionMatrix = cameraState.projectionMatrix;
      Matrix4f projectionMatrix = cameraState.projectionMatrix;

      // KillEffect: GPU depth-scan волны поверх мира (когда depth-буфер готов).
      try {
         vesence.module.impl.visuals.KillEffect killEffect = vesence.module.impl.visuals.KillEffect.INSTANCE;
         if (killEffect != null && killEffect.enable && camera != null) {
            killEffect.renderScanWaves(new Matrix4f(basicProjectionMatrix), new Matrix4f(positionMatrix), camera.position());
         }
      } catch (Exception ignored) {
      }

      // Custom Level → Размытие мира: depth-masked fog blur поверх готового мира.
      try {
         vesence.module.impl.visuals.Ambience ambience = vesence.module.impl.visuals.Ambience.getInstance();
         if (ambience != null && ambience.isBlurEnabled()) {
            ambience.renderFogBlur();
         }
      } catch (Exception ignored) {
      }

      PoseStack stack = new PoseStack();
      Matrix4f basePositionMatrix = new Matrix4f(positionMatrix);
      stack.mulPose(new Matrix4f(basePositionMatrix));

      vesence.utils.other.Mathf.lastProjMat.set(basicProjectionMatrix);
      vesence.utils.other.Mathf.lastModMat.identity();
      vesence.utils.other.Mathf.lastWorldSpaceMatrix.set(positionMatrix);

      Minecraft client = Minecraft.getInstance();
      if (client != null) {
         GameRenderer gameRenderer = client.gameRenderer;
         if (gameRenderer != null && camera != null) {
            GlState.Snapshot snapshot = GlState.push();
            vesence.utils.render.world.WorldRenderer levelRenderer = null;

            try {
               levelRenderer = vesence.utils.render.world.WorldRenderer.begin(client, tickCounter, camera,
                       positionMatrix, projectionMatrix);
               float frameDepth = levelRenderer.tickDelta();

               try {
                  EventManager.call(new WorldRenderEvent(client, gameRenderer, levelRenderer, frameDepth));
               } finally {
                  if (levelRenderer != null) {
                     try {
                        levelRenderer.flush();
                     } finally {
                        levelRenderer.close();
                     }
                  }
               }
            } finally {
               GlState.pop(snapshot);
            }
         }
      }
      EventManager.call(new EventRender3D(stack, tickCounter.getGameTimeDeltaPartialTick(true), basicProjectionMatrix, camera));
      EntityFramebufferCaptureManager.getInstance().endFrame();
   }

   /**
    * Shader ESP: пока модуль включён, гасим ванильную отрисовку entity-outline фреймбуфера
    * (обычно это цветная обводка светящихся сущностей). Сам силуэт в этот фреймбуфер всё равно
    * рендерится (мы форсим его через Minecraft.hasOutline), а пост-обработку
    * (свечение/заливка) делает модуль в HUD-фазе, читая этот фреймбуфер как маску.
    */
   // 26.3: drawEntityOutlinesFramebuffer переименован в blitEntityOutline.
   @Inject(method = "blitEntityOutline", at = @At("HEAD"), cancellable = true)
   private void vesence$suppressVanillaEntityOutline(CallbackInfo ci) {
      vesence.module.impl.visuals.ShaderESP esp = vesence.module.impl.visuals.ShaderESP.INSTANCE;
      if (esp != null && esp.enable) {
         ci.cancel();
      }
   }
}
