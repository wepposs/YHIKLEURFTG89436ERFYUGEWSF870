package vesence.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.ObjectAllocator;
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
@Mixin({ WorldRenderer.class })
public class WorldRendererMixin {

   /**
    * ChunksAnimation: смещаем positionMatrix (view) по Y в начале рендера —
    * это сдвигает весь мир вверх/вниз, давая эффект "мир появляется".
    */
   @Inject(method = "render", at = @At("HEAD"))
   private void vesence$chunksAnimationHead(
           ObjectAllocator allocator,
           RenderTickCounter tickCounter,
           boolean renderBlockOutline,
           Camera camera,
           Matrix4f positionMatrix,
           Matrix4f basicProjectionMatrix,
           Matrix4f projectionMatrix,
           GpuBufferSlice fog,
           Vector4f fogColor,
           boolean shouldRenderSky,
           CallbackInfo ci) {
      ChunksAnimation anim = ChunksAnimation.INSTANCE;
      if (anim != null && anim.enable) {
         float offset = anim.computeOffsetY();
         if (offset != 0.0F) {
            positionMatrix.translate(0.0F, offset, 0.0F);
         }
      }
   }

   @Inject(method = { "render" }, at = { @At("RETURN") })
   private void publishWorldRenderEvent(
           ObjectAllocator allocator,
           RenderTickCounter tickCounter,
           boolean renderBlockOutline,
           Camera camera,
           Matrix4f positionMatrix,
           Matrix4f basicProjectionMatrix,
           Matrix4f projectionMatrix,
           GpuBufferSlice fog,
           Vector4f fogColor,
           boolean shouldRenderSky,
           CallbackInfo ci) {
      // Убираем ChunksAnimation смещение перед отправкой EventRender3D,
      // чтобы модули (SkeletonESP, TargetESP и т.д.) работали в корректных координатах.
      ChunksAnimation anim = ChunksAnimation.INSTANCE;
      if (anim != null && anim.enable) {
         float offset = anim.computeOffsetY();
         if (offset != 0.0F) {
            positionMatrix.translate(0.0F, -offset, 0.0F);
         }
      }

      // KillEffect: GPU depth-scan волны поверх мира (когда depth-буфер готов).
      try {
         vesence.module.impl.visuals.KillEffect killEffect = vesence.module.impl.visuals.KillEffect.INSTANCE;
         if (killEffect != null && killEffect.enable && camera != null) {
            killEffect.renderScanWaves(new Matrix4f(basicProjectionMatrix), new Matrix4f(positionMatrix), camera.getCameraPos());
         }
      } catch (Exception ignored) {
      }

      // Custom World → Размытие мира: depth-masked fog blur поверх готового мира.
      try {
         vesence.module.impl.visuals.Ambience ambience = vesence.module.impl.visuals.Ambience.getInstance();
         if (ambience != null && ambience.isBlurEnabled()) {
            ambience.renderFogBlur();
         }
      } catch (Exception ignored) {
      }

      MatrixStack stack = new MatrixStack();
      Matrix4f basePositionMatrix = new Matrix4f(positionMatrix);
      stack.multiplyPositionMatrix(new Matrix4f(basePositionMatrix));

      vesence.utils.other.Mathf.lastProjMat.set(basicProjectionMatrix);
      vesence.utils.other.Mathf.lastModMat.identity();
      vesence.utils.other.Mathf.lastWorldSpaceMatrix.set(positionMatrix);

      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null) {
         GameRenderer gameRenderer = client.gameRenderer;
         if (gameRenderer != null && camera != null) {
            GlState.Snapshot snapshot = GlState.push();
            vesence.utils.render.world.WorldRenderer worldRenderer = null;

            try {
               worldRenderer = vesence.utils.render.world.WorldRenderer.begin(client, tickCounter, camera,
                       positionMatrix, projectionMatrix);
               float frameDepth = worldRenderer.tickDelta();

               try {
                  EventManager.call(new WorldRenderEvent(client, gameRenderer, worldRenderer, frameDepth));
               } finally {
                  if (worldRenderer != null) {
                     try {
                        worldRenderer.flush();
                     } finally {
                        worldRenderer.close();
                     }
                  }
               }
            } finally {
               GlState.pop(snapshot);
            }
         }
      }
      EventManager.call(new EventRender3D(stack, tickCounter.getTickProgress(true), basicProjectionMatrix, camera));
      EntityFramebufferCaptureManager.getInstance().endFrame();
   }

   /**
    * Shader ESP: пока модуль включён, гасим ванильную отрисовку entity-outline фреймбуфера
    * (обычно это цветная обводка светящихся сущностей). Сам силуэт в этот фреймбуфер всё равно
    * рендерится (мы форсим его через MinecraftClient.hasOutline), а пост-обработку
    * (свечение/заливка) делает модуль в HUD-фазе, читая этот фреймбуфер как маску.
    */
   @Inject(method = "drawEntityOutlinesFramebuffer", at = @At("HEAD"), cancellable = true)
   private void vesence$suppressVanillaEntityOutline(CallbackInfo ci) {
      vesence.module.impl.visuals.ShaderESP esp = vesence.module.impl.visuals.ShaderESP.INSTANCE;
      if (esp != null && esp.enable) {
         ci.cancel();
      }
   }
}
