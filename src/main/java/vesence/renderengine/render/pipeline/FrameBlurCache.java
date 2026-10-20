package vesence.renderengine.render.pipeline;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.backend.opengl.GlTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import vesence.Vesence;
import vesence.renderengine.providers.GlState;
import vesence.renderengine.providers.ShaderProgram;

import java.nio.ByteBuffer;

public final class FrameBlurCache {

   private static ShaderProgram shader;
   private static boolean initFailed = false;
   private static int vao;
   private static int vbo;

   private static final int[] tex = new int[2];
   private static final int[] fbo = new int[2];
   private static int bufW, bufH;

   private static int locTex = -1;
   private static int locTexel = -1;
   private static int locDir = -1;
   private static int locSigma = -1;

   private static long lastFrameId = -1L;
   private static float lastSigma = -1f;
   private static int resultTex = 0;
   private static int cachedWidth, cachedHeight;

   private FrameBlurCache() {
   }

   public static float sigmaForRadius(float blurRadius) {
      return Math.max(blurRadius * 0.85f, 2.0f);
   }

   private static boolean ensureInit() {
      if (shader != null && vao != 0) return true;
      if (initFailed) return false;
      try {
         shader = ShaderProgram.fromResources(
               "assets/vesence/shaders/core/blur_prepass.vsh",
               "assets/vesence/shaders/core/blur_prepass.fsh");
         locTex = shader.getUniformLocation("uTex");
         locTexel = shader.getUniformLocation("uTexel");
         locDir = shader.getUniformLocation("uDir");
         locSigma = shader.getUniformLocation("uSigma");

         int aPosLoc = GL20.glGetAttribLocation(shader.id(), "aPos");
         if (aPosLoc < 0) aPosLoc = 0;

         vao = GL30.glGenVertexArrays();
         vbo = GL15.glGenBuffers();
         GL30.glBindVertexArray(vao);
         GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
         float[] verts = {
               -1f, -1f,
                3f, -1f,
               -1f,  3f
         };
         GL15.glBufferData(GL15.GL_ARRAY_BUFFER, verts, GL15.GL_STATIC_DRAW);
         GL20.glEnableVertexAttribArray(aPosLoc);
         GL20.glVertexAttribPointer(aPosLoc, 2, GL11.GL_FLOAT, false, 0, 0);
         GL30.glBindVertexArray(0);
         GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
         return true;
      } catch (Exception e) {
         System.err.println("FrameBlurCache init failed: " + e.getMessage());
         e.printStackTrace();
         initFailed = true;
         return false;
      }
   }

   private static void ensureTargets(int w, int h) {
      if (w == bufW && h == bufH && tex[0] != 0) return;
      releaseTargets();
      bufW = w;
      bufH = h;
      for (int i = 0; i < 2; i++) {
         tex[i] = GL11.glGenTextures();
         GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex[i]);
         GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
         GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
         GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
         GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
         GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

         fbo[i] = GL30.glGenFramebuffers();
         GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo[i]);
         GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, tex[i], 0);
         GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
      }
      GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
   }

   public static int prepareForRadius(float blurRadius) {
      // NOTE: the half-resolution separable pre-pass below currently renders a black
      // target on the live pipeline (its result texture comes out empty), which made all
      // blur regions appear solid black. Until the pre-pass is verified against the game's
      // framebuffer at runtime, we short-circuit here and return 0. Returning 0 makes
      // BlurPipeline / BlurSquirclePipeline fall back to sampling the main framebuffer
      // directly and blurring it in-shader — the proven path that renders correctly.
      if (true) return 0;

      try {
         if (!ensureInit()) return 0;

         float sigma = sigmaForRadius(blurRadius);
         long frame = Vesence.getRenderFrameId();

         // Раз в кадр на одну и ту же сигму — переиспользуем готовую текстуру.
         if (frame == lastFrameId && Math.abs(sigma - lastSigma) < 0.01f && resultTex != 0) {
            return resultTex;
         }

         Minecraft mc = Minecraft.getInstance();
         RenderTarget mainFb = mc.gameRenderer.mainRenderTarget();
         int srcTex = extractGlTexId(mainFb);
         if (srcTex == 0) return 0;

         int fullW = mc.getWindow().getWidth();
         int fullH = mc.getWindow().getHeight();
         if (fullW <= 0 || fullH <= 0) return 0;

         // Полуразрешение: широкое ядро дёшево, деталей для мягкого фона хватает.
         int halfW = Math.max(1, fullW / 2);
         int halfH = Math.max(1, fullH / 2);

         GlState.Snapshot state = GlState.push();
         try {
            ensureTargets(halfW, halfH);
            if (tex[0] == 0 || tex[1] == 0) {
               return 0;
            }

            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glColorMask(true, true, true, true);
            GL11.glDepthMask(false);

            shader.use();
            GL30.glBindVertexArray(vao);
            GL11.glViewport(0, 0, halfW, halfH);

            // Смещения задаём в текселях ПОЛНОГО кадра для обеих осей — так экранный
            // радиус размытия одинаков по X и Y несмотря на полуразрешение буферов.
            float texelX = 1.0f / fullW;
            float texelY = 1.0f / fullH;

            // Проход 1: горизонталь. Источник — полноразмерный кадр (только чтение,
            // цель другая — фидбэка «сетки» нет), результат в tex[0] (полуразрешение).
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo[0]);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, srcTex);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            if (locTex != -1) GL20.glUniform1i(locTex, 0);
            if (locTexel != -1) GL20.glUniform2f(locTexel, texelX, texelY);
            if (locDir != -1) GL20.glUniform2f(locDir, 1.0f, 0.0f);
            if (locSigma != -1) GL20.glUniform1f(locSigma, sigma);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);

            // Проход 2: вертикаль. tex[0] -> tex[1]. Тексель по-прежнему от полного кадра.
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo[1]);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex[0]);
            if (locTex != -1) GL20.glUniform1i(locTex, 0);
            if (locTexel != -1) GL20.glUniform2f(locTexel, texelX, texelY);
            if (locDir != -1) GL20.glUniform2f(locDir, 0.0f, 1.0f);
            if (locSigma != -1) GL20.glUniform1f(locSigma, sigma);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);

            resultTex = tex[1];
            cachedWidth = halfW;
            cachedHeight = halfH;
            lastFrameId = frame;
            lastSigma = sigma;
         } finally {
            GlState.pop(state);
         }
         return resultTex;
      } catch (Exception e) {
         System.err.println("FrameBlurCache.prepareForRadius failed: " + e.getMessage());
         return 0;
      }
   }

   public static void captureLiquidBlurFrame() {

   }

   public static int getBlurredTexture() {
      return resultTex;
   }

   public static int getWidth() {
      return cachedWidth;
   }

   public static int getHeight() {
      return cachedHeight;
   }

   private static void releaseTargets() {
      for (int i = 0; i < 2; i++) {
         if (tex[i] != 0) {
            GL11.glDeleteTextures(tex[i]);
            tex[i] = 0;
         }
         if (fbo[i] != 0) {
            GL30.glDeleteFramebuffers(fbo[i]);
            fbo[i] = 0;
         }
      }
      bufW = 0;
      bufH = 0;
   }

   public static void invalidate() {
      lastFrameId = -1L;
      lastSigma = -1f;
      resultTex = 0;
   }

   public static void close() {
      releaseTargets();
      if (shader != null) {
         shader.delete();
         shader = null;
      }
      if (vao != 0) {
         GL30.glDeleteVertexArrays(vao);
         vao = 0;
      }
      if (vbo != 0) {
         GL15.glDeleteBuffers(vbo);
         vbo = 0;
      }
      initFailed = false;
      invalidate();
   }

   private static int extractGlTexId(RenderTarget framebuffer) {
      if (framebuffer == null) {
         return 0;
      }
      try {
         GpuTextureView view = framebuffer.getColorTextureView();
         if (view != null && !view.isClosed() && view.texture() instanceof GlTexture glTex) {
            return glTex.glId();
         }
      } catch (Exception ignored) {
      }
      try {
         if (framebuffer.getColorTexture() instanceof GlTexture glTex) {
            return glTex.glId();
         }
      } catch (Exception ignored) {
      }
      return 0;
   }
}
