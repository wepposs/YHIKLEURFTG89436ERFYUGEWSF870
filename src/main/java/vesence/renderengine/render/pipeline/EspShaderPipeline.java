package vesence.renderengine.render.pipeline;

import org.lwjgl.opengl.*;
import vesence.renderengine.providers.GlState;
import vesence.renderengine.providers.ShaderProgram;

import java.nio.ByteBuffer;

/**
 * Оффскрин-пайплайн для реальных шейдеров Wave ESP ({@code esp_outline.fsh} / {@code esp_gradient.fsh}).
 *
 * <p>Построен точно по образцу {@link KawaseBlurPipeline}: полноэкранный квад в clip-space,
 * собственные текстуры/FBO, безопасное сохранение/восстановление GL-состояния через
 * {@link GlState}. На вход подаётся текстура-маска силуэта (сплошной силуэт из ванильного
 * entity-outline фреймбуфера, где {@code alpha > 0} на сущностях); на выходе — id текстуры
 * RGBA с наложенным эффектом (straight alpha).</p>
 */
public class EspShaderPipeline {

   private ShaderProgram outlineShader;
   private ShaderProgram gradientShader;

   private int vbo;
   private int outlineVao;
   private int gradientVao;

   private int outlineTex, outlineFbo;
   private int fillTex, fillFbo;

   private int lastWidth, lastHeight;
   private boolean initialized;

   private void ensureInitialized() {
      if (initialized) return;
      outlineShader = ShaderProgram.fromResources(
            "assets/vesence/shaders/core/esp.vsh",
            "assets/vesence/shaders/core/esp_outline.fsh");
      gradientShader = ShaderProgram.fromResources(
            "assets/vesence/shaders/core/esp.vsh",
            "assets/vesence/shaders/core/esp_gradient.fsh");

      vbo = GL15.glGenBuffers();
      GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
      float[] vertices = {
            -1f, -1f, 0f, 0f, 0f, 1f, 1f, 1f, 1f,
             1f, -1f, 0f, 1f, 0f, 1f, 1f, 1f, 1f,
             1f,  1f, 0f, 1f, 1f, 1f, 1f, 1f, 1f,
            -1f, -1f, 0f, 0f, 0f, 1f, 1f, 1f, 1f,
             1f,  1f, 0f, 1f, 1f, 1f, 1f, 1f, 1f,
            -1f,  1f, 0f, 0f, 1f, 1f, 1f, 1f, 1f
      };
      GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_STATIC_DRAW);

      // Отдельный VAO на каждую программу: в GLSL 150 локации атрибутов не гарантированы
      // одинаковыми между программами, даже при общем вершинном шейдере.
      outlineVao = GL30.glGenVertexArrays();
      GL30.glBindVertexArray(outlineVao);
      GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
      setupAttributes(outlineShader);

      gradientVao = GL30.glGenVertexArrays();
      GL30.glBindVertexArray(gradientVao);
      GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
      setupAttributes(gradientShader);

      GL30.glBindVertexArray(0);
      initialized = true;
   }

   private void setupAttributes(ShaderProgram s) {
      int posLoc = GL20.glGetAttribLocation(s.id(), "Position");
      int uvLoc = GL20.glGetAttribLocation(s.id(), "UV0");
      if (posLoc != -1) {
         GL20.glEnableVertexAttribArray(posLoc);
         GL20.glVertexAttribPointer(posLoc, 3, GL11.GL_FLOAT, false, 36, 0);
      }
      if (uvLoc != -1) {
         GL20.glEnableVertexAttribArray(uvLoc);
         GL20.glVertexAttribPointer(uvLoc, 2, GL11.GL_FLOAT, false, 36, 12);
      }
   }

   private void ensureResources(int width, int height) {
      if (width == lastWidth && height == lastHeight && outlineTex != 0) return;
      cleanupResources();
      outlineTex = createTexture(width, height);
      outlineFbo = createFbo(outlineTex);
      fillTex = createTexture(width, height);
      fillFbo = createFbo(fillTex);
      lastWidth = width;
      lastHeight = height;
   }

   private int createTexture(int w, int h) {
      int tex = GL11.glGenTextures();
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
      GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
      return tex;
   }

   private int createFbo(int tex) {
      int fbo = GL30.glGenFramebuffers();
      GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
      GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, tex, 0);
      GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
      return fbo;
   }

   /**
    * Реальная обводка Wave ({@code esp_outline.fsh}): резкое цветное кольцо снаружи силуэта,
    * толщина управляется {@code radius}.
    *
    * @return id текстуры с обводкой либо 0 при ошибке.
    */
   public int outline(int sourceTex, int width, int height, float radius,
                      float r, float g, float b, float a) {
      if (sourceTex == 0 || width <= 0 || height <= 0) return 0;
      try {
         ensureInitialized();
      } catch (Exception e) {
         System.err.println("EspShaderPipeline init failed: " + e.getMessage());
         return 0;
      }
      ensureResources(width, height);

      GlState.Snapshot state = GlState.push();
      try {
         GL11.glDisable(GL11.GL_BLEND);
         GL11.glDisable(GL11.GL_DEPTH_TEST);
         GL11.glDisable(GL11.GL_CULL_FACE);
         GL11.glDisable(GL11.GL_SCISSOR_TEST);

         GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, outlineFbo);
         GL11.glViewport(0, 0, width, height);
         GL11.glClearColor(0f, 0f, 0f, 0f);
         GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

         GL30.glBindVertexArray(outlineVao);
         outlineShader.use();
         uniform2f(outlineShader, "texelSize", 1f / width, 1f / height);
         uniform4f(outlineShader, "color", r, g, b, a);
         uniform1f(outlineShader, "radius", Math.max(1f, radius));

         GL13.glActiveTexture(GL13.GL_TEXTURE0);
         GL11.glBindTexture(GL11.GL_TEXTURE_2D, sourceTex);
         uniform1i(outlineShader, "Sampler0", 0);

         GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
         return outlineTex;
      } catch (Exception e) {
         System.err.println("EspShaderPipeline outline failed: " + e.getMessage());
         return 0;
      } finally {
         GL30.glBindVertexArray(0);
         GlState.pop(state);
      }
   }

   /**
    * Реальная заливка Wave ({@code esp_gradient.fsh}): закраска тела силуэта, при
    * {@code animate} — анимированный переход между {@code color1} и {@code color2}.
    *
    * @return id текстуры с заливкой либо 0 при ошибке.
    */
   public int fill(int sourceTex, int width, int height,
                   float r1, float g1, float b1, float a1,
                   float r2, float g2, float b2, float a2,
                   boolean animate, float yOffset, float gradStrength) {
      if (sourceTex == 0 || width <= 0 || height <= 0) return 0;
      try {
         ensureInitialized();
      } catch (Exception e) {
         System.err.println("EspShaderPipeline init failed: " + e.getMessage());
         return 0;
      }
      ensureResources(width, height);

      GlState.Snapshot state = GlState.push();
      try {
         GL11.glDisable(GL11.GL_BLEND);
         GL11.glDisable(GL11.GL_DEPTH_TEST);
         GL11.glDisable(GL11.GL_CULL_FACE);
         GL11.glDisable(GL11.GL_SCISSOR_TEST);

         GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fillFbo);
         GL11.glViewport(0, 0, width, height);
         GL11.glClearColor(0f, 0f, 0f, 0f);
         GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

         GL30.glBindVertexArray(gradientVao);
         gradientShader.use();
         uniform2f(gradientShader, "location", 0f, 0f);
         uniform2f(gradientShader, "rectSize", width, height);
         uniform4f(gradientShader, "color1", r1, g1, b1, a1);
         uniform4f(gradientShader, "color2", r2, g2, b2, a2);
         uniform1f(gradientShader, "yOffset", yOffset);
         uniform1f(gradientShader, "gradStrength", gradStrength);
         uniform1i(gradientShader, "animate", animate ? 1 : 0);

         GL13.glActiveTexture(GL13.GL_TEXTURE0);
         GL11.glBindTexture(GL11.GL_TEXTURE_2D, sourceTex);
         uniform1i(gradientShader, "Sampler0", 0);

         GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
         return fillTex;
      } catch (Exception e) {
         System.err.println("EspShaderPipeline fill failed: " + e.getMessage());
         return 0;
      } finally {
         GL30.glBindVertexArray(0);
         GlState.pop(state);
      }
   }

   private void uniform1i(ShaderProgram s, String name, int v) {
      int loc = s.getUniformLocation(name);
      if (loc != -1) GL20.glUniform1i(loc, v);
   }

   private void uniform1f(ShaderProgram s, String name, float v) {
      int loc = s.getUniformLocation(name);
      if (loc != -1) GL20.glUniform1f(loc, v);
   }

   private void uniform2f(ShaderProgram s, String name, float x, float y) {
      int loc = s.getUniformLocation(name);
      if (loc != -1) GL20.glUniform2f(loc, x, y);
   }

   private void uniform4f(ShaderProgram s, String name, float x, float y, float z, float w) {
      int loc = s.getUniformLocation(name);
      if (loc != -1) GL20.glUniform4f(loc, x, y, z, w);
   }

   private void cleanupResources() {
      if (outlineTex != 0) GL11.glDeleteTextures(outlineTex);
      if (outlineFbo != 0) GL30.glDeleteFramebuffers(outlineFbo);
      if (fillTex != 0) GL11.glDeleteTextures(fillTex);
      if (fillFbo != 0) GL30.glDeleteFramebuffers(fillFbo);
      outlineTex = 0;
      outlineFbo = 0;
      fillTex = 0;
      fillFbo = 0;
      lastWidth = 0;
      lastHeight = 0;
   }

   public void close() {
      cleanupResources();
      if (outlineShader != null) outlineShader.delete();
      if (gradientShader != null) gradientShader.delete();
      if (outlineVao != 0) GL30.glDeleteVertexArrays(outlineVao);
      if (gradientVao != 0) GL30.glDeleteVertexArrays(gradientVao);
      if (vbo != 0) GL15.glDeleteBuffers(vbo);
      initialized = false;
   }
}
