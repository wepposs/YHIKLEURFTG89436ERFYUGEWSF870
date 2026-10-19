package vesence.renderengine.render;

import com.mojang.blaze3d.opengl.GlStateManager;
import java.awt.Color;
import java.lang.reflect.Method;
import java.util.*;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import org.joml.Quaternionf;
import org.joml.Vector2d;
import org.joml.Vector3f;
import org.lwjgl.opengl.*;
import vesence.module.Theme;
import vesence.renderengine.providers.GlBackend;
import vesence.renderengine.providers.GlState;
import vesence.renderengine.providers.ShaderProgram;
import vesence.renderengine.render.pipeline.BlurPipeline;
import vesence.renderengine.render.pipeline.BlurSquirclePipeline;
import vesence.renderengine.render.pipeline.ShapePipeline;
import vesence.renderengine.render.pipeline.TextBlurPipeline;
import vesence.renderengine.render.pipeline.BlendModePipeline;
import vesence.ui.clickgui.GuiScreen;
import vesence.utils.render.BorderRadius;
import vesence.utils.render.text.FontObject;
import vesence.utils.render.text.TextRenderer;
import vesence.renderengine.utils.MathHelper;
import vesence.renderengine.utils.animation.anim2.Interpolator;

@Environment(EnvType.CLIENT)
public final class Renderer2D {
   private final GlBackend backend;
   private final ArrayDeque<Renderer2D.ClipState> clipStack = new ArrayDeque<>();
   private final Deque<Float> alphaStack = new ArrayDeque<>();

   private float cachedAlpha = 1.0F;
   private float fontWeight = 0.0f;
   private final Deque<Float> fontWeightStack = new ArrayDeque<>();
   private final TransformStack transformStack = new TransformStack();
   private Map<String, TextRenderer> idToTextRenderer = new HashMap<>();
   private final ShapeBatcher batcher;
   private boolean frameBegun = false;
   private int frameWidth = 0;
   private int frameHeight = 0;
   public static MinecraftClient mc = MinecraftClient.getInstance();
   private int liquidGlassVao;
   private int liquidGlassVbo;
   private int liquidGlassIbo;
   private boolean liquidGlassInitialized = false;
   private static final float[] IDENTITY = {1,0,0, 0,1,0, 0,0,1};
   private BlurPipeline blurPipeline;
   private BlurSquirclePipeline blurSquirclePipeline;
   private TextBlurPipeline textBlurPipeline;
   private BlendModePipeline blendModePipeline;
   private ShapePipeline squirclePipeline;
   private vesence.renderengine.render.pipeline.SquircleTexturePipeline squircleTexturePipeline;
   private ShaderProgram liquidGlassShader;
   private ShaderProgram rainShader;
   private int rainVao;
   private int rainVbo;
   private int rainIbo;
   private boolean rainInitialized = false;
   private ShaderProgram skyShader;
   private ShaderProgram skyShaderStorm;
   private ShaderProgram skyShaderSunset;
   private int skyShaderVao;
   private int skyShaderVbo;
   private int skyShaderIbo;
   private boolean skyShaderInitialized = false;
   private ShaderProgram orbBgShader;
   private ShaderProgram orbBg2Shader;
   private int orbBgVao;
   private int orbBgVbo;
   private int orbBgIbo;
   private boolean orbBgInitialized = false;
   private boolean orbBg2Initialized = false;
   private ShaderProgram orbBgRoundedShader;
   private ShaderProgram orbBg2RoundedShader;
   private boolean orbBgRoundedInitialized = false;
   private boolean orbBg2RoundedInitialized = false;
   private int previewVao = 0;
   private int previewVbo = 0;
   private int previewIbo = 0;
   private int orbBgFbo = 0;
   private int orbBgTex = 0;
   private int orbBgFboW = 0;
   private int orbBgFboH = 0;

   public boolean isInBatch() {
      return this.frameBegun;
   }

   public Renderer2D(GlBackend backend) {
      if (backend == null) {
         throw new IllegalArgumentException("GlBackend cannot be null");
      } else {
         this.backend = backend;
         this.batcher = new ShapeBatcher(backend);
         this.resetAlphaStack();
      }
   }

   public void begin(int width, int height) {
      if (width > 0 && height > 0) {
         if (this.frameBegun) {
            this.forceEnd();
         }

         try {
            this.frameBegun = true;
            this.frameWidth = width;
            this.frameHeight = height;
            if (this.backend != null) {
               RenderFrameMetrics.getInstance().beginFrame(width, height);
               this.backend.beginFrame(width, height);
               this.backend.setScissorEnabled(false);
            }

            this.clipStack.clear();
            this.transformStack.clear();
            this.resetAlphaStack();
         } catch (Exception var4) {
            this.frameBegun = false;
            System.err.println("Error in Renderer2D.begin(): " + var4.getMessage());
            var4.printStackTrace();
            throw new RuntimeException("Failed to begin render frame", var4);
         }
      } else {
         throw new IllegalArgumentException("Width and height must be positive, got: " + width + "x" + height);
      }
   }

   public void begin(DrawContext context) {
      if (context != null) {
         this.begin(mc.getWindow().getFramebufferWidth(), mc.getWindow().getFramebufferHeight());
      }
   }

   public void reset() {
      if (blurPipeline != null) { blurPipeline.close(); blurPipeline = null; }
      if (blurSquirclePipeline != null) { blurSquirclePipeline.close(); blurSquirclePipeline = null; }
      vesence.renderengine.render.pipeline.FrameBlurCache.close();
      if (textBlurPipeline != null) { textBlurPipeline.close(); textBlurPipeline = null; }
      if (blendModePipeline != null) { blendModePipeline.close(); blendModePipeline = null; }
      squirclePipeline = null;
      squircleTexturePipeline = null;
      skyShader = null;
      skyShaderStorm = null;
      skyShaderSunset = null;
      skyShaderInitialized = false;
      liquidGlassShader = null;
      liquidGlassInitialized = false;
      rainShader = null;
      rainInitialized = false;
      orbBgShader = null;
      orbBg2Shader = null;
      orbBgInitialized = false;
      orbBg2Initialized = false;
      orbBgRoundedShader = null;
      orbBg2RoundedShader = null;
      orbBgRoundedInitialized = false;
      orbBg2RoundedInitialized = false;
      orbBgFbo = 0;
      orbBgTex = 0;
      orbBgFboW = 0;
      orbBgFboH = 0;
      previewVao = 0;
      previewVbo = 0;
      previewIbo = 0;
      idToTextRenderer.clear();
      clipStack.clear();
      transformStack.clear();
      alphaStack.clear();
      cachedAlpha = 1.0F;
      fontWeightStack.clear();
      frameBegun = false;
   }

   public static void clearImageTextureCache() {

      for (Integer id : imageTextureCache.values()) {
         try {
            if (id != null && id > 0 && GL11.glIsTexture(id)) {
               GL11.glDeleteTextures(id);
            }
         } catch (Exception ignored) {
         }
      }
      imageTextureCache.clear();

      textureManagerBacked.clear();
      textureManagerConfigured.clear();
   }

   private boolean transformHasRotation(float[] m) {
      return Math.abs(m[1]) > 1.0E-6F || Math.abs(m[3]) > 1.0E-6F;
   }

   private float[] toGlMatrix(float[] m) {
      return new float[]{ m[0], m[3], m[6], m[1], m[4], m[7], m[2], m[5], m[8] };
   }

   public void drawSquircle(float x, float y, float width, float height, float squirt, BorderRadius borderRadius, int color) {
      drawSquircle2(x, y, width, height, borderRadius, color, squirt);
   }

   public void drawSquircle2(float x, float y, float w, float h, BorderRadius borderRadius, int color, float squirt) {
      this.ensureFrame();
      this.batcher.flush();
      if (squirclePipeline == null) squirclePipeline = new ShapePipeline("squircle");
      int modulatedColor = this.modulateColor(color);
      float[] m = this.transformStack.current();
      this.beginPipelineClip();
      try {
         if (transformHasRotation(m)) {
            squirclePipeline.draw(x, y, w, h, new float[]{borderRadius.topLeftRadius(), borderRadius.bottomLeftRadius(), borderRadius.topRightRadius(), borderRadius.bottomRightRadius()}, modulatedColor, 1.0f, squirt, toGlMatrix(m), this.frameWidth, this.frameHeight);
            return;
         }
         float nx = m[0] * x + m[1] * y + m[2];
         float ny = m[3] * x + m[4] * y + m[5];
         float nw = m[0] * w;
         float nh = m[4] * h;
         squirclePipeline.draw(nx, ny, nw, nh, new float[]{borderRadius.topLeftRadius() * m[0], borderRadius.bottomLeftRadius() * m[0], borderRadius.topRightRadius() * m[0], borderRadius.bottomRightRadius() * m[0]}, modulatedColor, 1.0f, squirt, IDENTITY, this.frameWidth, this.frameHeight);
      } finally {
         this.endPipelineClip();
      }
   }

   public void drawSquircleOutline(float x, float y, float width, float height, float squirt, BorderRadius borderRadius, int color, float thickness) {
      this.ensureFrame();
      this.batcher.flush();
      if (squirclePipeline == null) squirclePipeline = new ShapePipeline("squircle");
      int modulatedColor = this.modulateColor(color);
      float[] m = this.transformStack.current();
      this.beginPipelineClip();
      try {
         if (transformHasRotation(m)) {
            squirclePipeline.drawOutline(x, y, width, height, new float[]{borderRadius.topLeftRadius(), borderRadius.bottomLeftRadius(), borderRadius.topRightRadius(), borderRadius.bottomRightRadius()}, modulatedColor, 1.0f, squirt, thickness, toGlMatrix(m), this.frameWidth, this.frameHeight);
            return;
         }
         float nx = m[0] * x + m[1] * y + m[2];
         float ny = m[3] * x + m[4] * y + m[5];
         float nw = m[0] * width;
         float nh = m[4] * height;
         squirclePipeline.drawOutline(nx, ny, nw, nh, new float[]{borderRadius.topLeftRadius() * m[0], borderRadius.bottomLeftRadius() * m[0], borderRadius.topRightRadius() * m[0], borderRadius.bottomRightRadius() * m[0]}, modulatedColor, 1.0f, squirt, thickness, IDENTITY, this.frameWidth, this.frameHeight);
      } finally {
         this.endPipelineClip();
      }
   }

   public void drawSquircleShadow(float x, float y, float width, float height, float squirt, BorderRadius borderRadius, float blurStrength, float spread, int color) {
      this.ensureFrame();
      this.batcher.flush();
      if (squirclePipeline == null) squirclePipeline = new ShapePipeline("squircle");
      int modulatedColor = this.modulateColor(color);
      float[] m = this.transformStack.current();
      this.beginPipelineClip();
      try {
         if (transformHasRotation(m)) {
            squirclePipeline.drawShadow(x, y, width, height, new float[]{borderRadius.topLeftRadius(), borderRadius.bottomLeftRadius(), borderRadius.topRightRadius(), borderRadius.bottomRightRadius()}, modulatedColor, 1.0f, squirt, blurStrength, spread, toGlMatrix(m), this.frameWidth, this.frameHeight);
            return;
         }
         float nx = m[0] * x + m[1] * y + m[2];
         float ny = m[3] * x + m[4] * y + m[5];
         float nw = m[0] * width;
         float nh = m[4] * height;
         squirclePipeline.drawShadow(nx, ny, nw, nh, new float[]{borderRadius.topLeftRadius() * m[0], borderRadius.bottomLeftRadius() * m[0], borderRadius.topRightRadius() * m[0], borderRadius.bottomRightRadius() * m[0]}, modulatedColor, 1.0f, squirt, blurStrength, spread, IDENTITY, this.frameWidth, this.frameHeight);
      } finally {
         this.endPipelineClip();
      }
   }

   public void drawSquircleGradient(float x, float y, float width, float height, float squirt, BorderRadius borderRadius, int c00, int c10, int c11, int c01) {
      this.ensureFrame();
      this.batcher.flush();
      if (squirclePipeline == null) squirclePipeline = new ShapePipeline("squircle");
      int nc00 = this.modulateColor(c00);
      int nc10 = this.modulateColor(c10);
      int nc11 = this.modulateColor(c11);
      int nc01 = this.modulateColor(c01);
      float[] m = this.transformStack.current();
      this.beginPipelineClip();
      try {
         if (transformHasRotation(m)) {
            squirclePipeline.drawGradient(x, y, width, height, new float[]{borderRadius.topLeftRadius(), borderRadius.bottomLeftRadius(), borderRadius.topRightRadius(), borderRadius.bottomRightRadius()}, nc00, nc10, nc11, nc01, 1.0f, squirt, toGlMatrix(m), this.frameWidth, this.frameHeight);
            return;
         }
         float nx = m[0] * x + m[1] * y + m[2];
         float ny = m[3] * x + m[4] * y + m[5];
         float nw = m[0] * width;
         float nh = m[4] * height;
         squirclePipeline.drawGradient(nx, ny, nw, nh, new float[]{borderRadius.topLeftRadius() * m[0], borderRadius.bottomLeftRadius() * m[0], borderRadius.topRightRadius() * m[0], borderRadius.bottomRightRadius() * m[0]}, nc00, nc10, nc11, nc01, 1.0f, squirt, IDENTITY, this.frameWidth, this.frameHeight);
      } finally {
         this.endPipelineClip();
      }
   }

   public void drawSquircleGradientOutline(float x, float y, float width, float height, float squirt, BorderRadius borderRadius, int c00, int c10, int c11, int c01, float thickness) {
      this.ensureFrame();
      this.batcher.flush();
      if (squirclePipeline == null) squirclePipeline = new ShapePipeline("squircle");
      int nc00 = this.modulateColor(c00);
      int nc10 = this.modulateColor(c10);
      int nc11 = this.modulateColor(c11);
      int nc01 = this.modulateColor(c01);
      float[] m = this.transformStack.current();
      this.beginPipelineClip();
      try {
         if (transformHasRotation(m)) {
            squirclePipeline.drawGradientOutline(x, y, width, height, new float[]{borderRadius.topLeftRadius(), borderRadius.bottomLeftRadius(), borderRadius.topRightRadius(), borderRadius.bottomRightRadius()}, nc00, nc10, nc11, nc01, 1.0f, squirt, thickness, toGlMatrix(m), this.frameWidth, this.frameHeight);
            return;
         }
         float nx = m[0] * x + m[1] * y + m[2];
         float ny = m[3] * x + m[4] * y + m[5];
         float nw = m[0] * width;
         float nh = m[4] * height;
         squirclePipeline.drawGradientOutline(nx, ny, nw, nh, new float[]{borderRadius.topLeftRadius() * m[0], borderRadius.bottomLeftRadius() * m[0], borderRadius.topRightRadius() * m[0], borderRadius.bottomRightRadius() * m[0]}, nc00, nc10, nc11, nc01, 1.0f, squirt, thickness, IDENTITY, this.frameWidth, this.frameHeight);
      } finally {
         this.endPipelineClip();
      }
   }
   private void forceEnd() {
      try {
         if (this.batcher != null) {
            this.batcher.flush();
         }

         if (this.backend != null) {
            this.backend.endFrame();
         }

         RenderFrameMetrics.getInstance().endFrame();
      } catch (Exception var5) {
      } finally {
         this.frameBegun = false;
         this.frameWidth = 0;
         this.frameHeight = 0;
         this.clipStack.clear();
         this.transformStack.clear();
         this.resetAlphaStack();
      }
   }

   private void ensureFrame() {
      if (!this.frameBegun) {
         throw new IllegalStateException("begin() must be called before issuing draw commands");
      } else if (this.backend == null) {
         throw new IllegalStateException("Renderer2D backend is null - initialization failed");
      } else if (this.batcher == null) {
         throw new IllegalStateException("Renderer2D batcher is null - initialization failed");
      }
   }

   public void rect(float x, float y, float w, float h, int rgbaPremul) {
      this.ensureFrame();
      this.batcher.enqueueRect(x, y, w, h, 0.0F, 0.0F, 0.0F, 0.0F, this.modulateColor(rgbaPremul),
              this.transformStack.current());
   }

   public void rect(float x, float y, float w, float h, float rounding, float squirt, int rgbaPremul) {
      float r1 = rounding * squirt / 2.0f;
      this.rect(x, y, w, h, r1, r1, r1, r1, rgbaPremul);
   }

   public void rect(float x, float y, float w, float h, float rounding, int rgbaPremul) {
      float r1 = rounding;
      this.rect(x, y, w, h, r1, r1, r1, r1, rgbaPremul);
   }

   public void rect(float x, float y, float w, float h, float roundTopLeft, float roundTopRight, float roundBottomRight,
                    float roundBottomLeft, int rgbaPremul) {
      this.ensureFrame();
      float[] radii = scratchRadii(roundTopLeft, roundTopRight, roundBottomRight, roundBottomLeft);
      normalizeCornerRadii(w, h, radii);
      this.batcher.enqueueRect(x, y, w, h, radii[0], radii[1], radii[2], radii[3], this.modulateColor(rgbaPremul),
              this.transformStack.current());
   }

   public void drawRgbaTexture(int texture, float x, float y, float w, float h) {
      this.drawRgbaTextureInternal(texture, x, y, w, h, -1, true, false);
   }

   public void drawRgbaTexture(int texture, float x, float y, float w, float h, int tintRgba) {
      this.drawRgbaTextureInternal(texture, x, y, w, h, tintRgba, true, false);
   }

   public void drawRgbaTexture(int texture, float x, float y, float w, float h, int tintRgba, boolean flipVertically) {
      this.drawRgbaTextureInternal(texture, x, y, w, h, tintRgba, flipVertically, false);
   }

   public void drawPremultipliedRgbaTexture(int texture, float x, float y, float w, float h) {
      this.drawRgbaTextureInternal(texture, x, y, w, h, -1, true, true);
   }

   public void drawPremultipliedRgbaTexture(int texture, float x, float y, float w, float h, int tintRgba,
                                            boolean flipVertically) {
      this.drawRgbaTextureInternal(texture, x, y, w, h, tintRgba, flipVertically, true);
   }

   public void drawRgbaTextureWithUV(int texture, float x, float y, float w, float h, float u0, float v0, float u1,
                                     float v1) {
      this.ensureFrame();
      if (texture > 0) {
         this.backend.drawRgbaTexturedQuad(texture, x, y, w, h, u0, v0, u1, v1, this.modulateColor(-1),
                 this.transformStack.current(), false);
      }
   }

   public void drawRgbaTextureWithUVRounded(int texture, float x, float y, float w, float h, float u0, float v0,
                                            float u1, float v1, float rounding) {
      this.ensureFrame();
      if (texture > 0) {
         this.backend.drawRgbaTexturedQuadRounded(texture, x, y, w, h, u0, v0, u1, v1, rounding, this.modulateColor(-1),
                 this.transformStack.current(), false);
      }
   }

   public static int getMcFramebufferTex() {
      if (mc == null || mc.getFramebuffer() == null) return 0;
      try {
         var view = mc.getFramebuffer().getColorAttachmentView();
         if (view != null && !view.isClosed()) {
            var tex = view.texture();
            if (tex instanceof GlTexture glTex) return glTex.getGlId();
         }
      } catch (Exception ignored) {}
      try {
         if (mc.getFramebuffer().getColorAttachment() instanceof GlTexture glTex) return glTex.getGlId();
      } catch (Exception ignored) {}
      return 0;
   }

   public void drawRgbaTextureWithUVRoundedNearest(int texture, float x, float y, float w, float h, float u0, float v0,
                                                   float u1, float v1, float rounding) {
      this.ensureFrame();
      if (texture > 0) {
         float[] radii = scratchRadii(rounding, rounding, rounding, rounding);
         normalizeCornerRadii(w, h, radii);
         this.backend.useNearestFilter(texture);
         this.backend.drawRgbaTexturedQuadRounded(texture, x, y, w, h, u0, v0, u1, v1, radii[0], this.modulateColor(-1),
                 this.transformStack.current(), false);
      }
   }

   public void drawRgbaTextureWithUVSquircle(int texture, float x, float y, float w, float h,
                                             float u0, float v0, float u1, float v1, BorderRadius borderRadius, float cornerSmoothness, boolean nearest) {
      this.ensureFrame();
      this.batcher.flush();
      if (texture <= 0) return;

      if (squircleTexturePipeline == null) {
         squircleTexturePipeline = new vesence.renderengine.render.pipeline.SquircleTexturePipeline();
      }

      int modulatedColor = this.modulateColor(-1);
      float[] m = this.transformStack.current();
      float nx = m[0] * x + m[1] * y + m[2];
      float ny = m[3] * x + m[4] * y + m[5];
      float nw = m[0] * w;
      float nh = m[4] * h;

      squircleTexturePipeline.draw(texture, nx, ny, nw, nh, u0, v0, u1, v1,
              new float[]{borderRadius.topLeftRadius() * m[0], borderRadius.bottomLeftRadius() * m[0],
                      borderRadius.topRightRadius() * m[0], borderRadius.bottomRightRadius() * m[0]},
              modulatedColor, 1.0f, cornerSmoothness, IDENTITY, this.frameWidth, this.frameHeight, nearest);
   }

   public void drawImage(int texture, float x, float y, float w, float h) {
      this.drawImage(texture, x, y, w, h, -1);
   }

   public void drawImage(int texture, float x, float y, float w, float h, int tintRgba) {
      this.drawImage(texture, x, y, w, h, tintRgba, false);
   }

   public void drawImage(int texture, float x, float y, float w, float h, int tintRgba, boolean flipVertically) {
      this.drawImage(texture, x, y, w, h, tintRgba, flipVertically, 0.0f);
   }

   public void drawImage(int texture, float x, float y, float w, float h, int tintRgba, boolean flipVertically, float rounding) {
      this.ensureFrame();
      if (texture <= 0) {
         return;
      }

      float v0 = flipVertically ? 1.0F : 0.0F;
      float v1 = flipVertically ? 0.0F : 1.0F;

      if (rounding > 0.0f) {
         this.backend.drawRgbaTexturedQuadRounded(
                 texture, x, y, w, h,
                 0.0F, v0, 1.0F, v1,
                 rounding,
                 this.modulateColor(tintRgba),
                 this.transformStack.current(),
                 false
         );
      } else {
         this.backend.drawRgbaTexturedQuad(
                 texture, x, y, w, h,
                 0.0F, v0, 1.0F, v1,
                 this.modulateColor(tintRgba),
                 this.transformStack.current(),
                 false
         );
      }
   }

   public void drawImage(int texture, float x, float y, float w, float h, float rounding) {
      this.drawImage(texture, x, y, w, h, -1, false, rounding);
   }

   private static final Map<Identifier, Integer> imageTextureCache = new HashMap<>();

   private static final java.util.Set<Identifier> textureManagerBacked = new java.util.HashSet<>();

   private static final Map<Identifier, Integer> textureManagerConfigured = new HashMap<>();

   private int resolveTextureId(Identifier identifier) {

      if (identifier.getPath().endsWith(".png")) {
         int live = resolveFromTextureManager(identifier);
         if (live > 0) {
            textureManagerBacked.add(identifier);
            Integer prevConfigured = textureManagerConfigured.get(identifier);
            if (prevConfigured == null || prevConfigured != live) {
               configureLinearClamp(live);
               textureManagerConfigured.put(identifier, live);
            }
            return live;
         }

         if (textureManagerBacked.contains(identifier)) {
            return 0;
         }
      }

      Integer cached = imageTextureCache.get(identifier);
      if (cached != null && cached > 0 && GL11.glIsTexture(cached)) {
         return cached;
      }

      if (cached != null) {
         imageTextureCache.remove(identifier);
      }

      try {
         var resource = mc.getResourceManager().getResource(identifier);
         if (resource.isPresent()) {
            InputStream is = resource.get().getInputStream();
            int texId = uploadImageToGl(is);
            if (texId > 0) {
               imageTextureCache.put(identifier, texId);
               return texId;
            }
         }
      } catch (Exception e) {
         System.err.println("[Renderer2D] ResourceManager failed for " + identifier + ": " + e.getMessage());
      }

      try {
         String path = "assets/" + identifier.getNamespace() + "/" + identifier.getPath();
         InputStream is = Renderer2D.class.getClassLoader().getResourceAsStream(path);
         if (is != null) {
            int texId = uploadImageToGl(is);
            if (texId > 0) {
               imageTextureCache.put(identifier, texId);
               return texId;
            }
         }
      } catch (Exception e) {
         System.err.println("[Renderer2D] ClassLoader failed for " + identifier + ": " + e.getMessage());
      }

      return 0;
   }

   private int resolveFromTextureManager(Identifier identifier) {
      try {
         var tex = mc.getTextureManager().getTexture(identifier);
         if (tex != null) {
            var view = tex.getGlTextureView();
            if (view != null && view.texture() instanceof net.minecraft.client.texture.GlTexture glTex) {
               int id = glTex.getGlId();
               if (id > 0) {
                  return id;
               }
            }
         }
      } catch (Exception e) {

      }
      return 0;
   }

   private void configureLinearClamp(int id) {
      this.batcher.flush();
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
   }

   private int uploadImageToGl(InputStream is) throws Exception {
      this.batcher.flush();
      BufferedImage img = ImageIO.read(is);
      is.close();
      if (img == null) {
         System.err.println("[Renderer2D] ImageIO.read returned null");
         return 0;
      }

      int w = img.getWidth();
      int h = img.getHeight();
      System.err.println("[Renderer2D] Image size: " + w + "x" + h);

      BufferedImage converted = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
      converted.getGraphics().drawImage(img, 0, 0, null);

      int[] pixels = new int[w * h];
      converted.getRGB(0, 0, w, h, pixels, 0, w);

      ByteBuffer buf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder());
      for (int row = h - 1; row >= 0; row--) {
         for (int col = 0; col < w; col++) {
            int p = pixels[row * w + col];
            buf.put((byte)((p >> 16) & 0xFF));
            buf.put((byte)((p >> 8) & 0xFF));
            buf.put((byte)(p & 0xFF));
            buf.put((byte)((p >> 24) & 0xFF));
         }
      }
      buf.flip();

      int texId = GL11.glGenTextures();
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
      GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, 33071);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, 33071);
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

      System.err.println("[Renderer2D] Uploaded GL texture id=" + texId);
      return texId;
   }

   public void drawImage(Identifier identifier, float x, float y, float w, float h) {
      int texId = this.resolveTextureId(identifier);
      if (texId > 0) {
         this.drawImage(texId, x, y, w, h);
      } else {

         this.drawRect(x, y, w, h, 0xFF0066FF);
      }
   }

   public void drawImage(Identifier identifier, float x, float y, float w, float h, int tintRgba) {
      int texId = this.resolveTextureId(identifier);
      if (texId > 0) this.drawImage(texId, x, y, w, h, tintRgba);
   }

   public void drawImage(Identifier identifier, float x, float y, float w, float h, int tintRgba, boolean flipVertically) {
      int texId = this.resolveTextureId(identifier);
      if (texId > 0) this.drawImage(texId, x, y, w, h, tintRgba, flipVertically);
   }

   public void drawImage(Identifier identifier, float x, float y, float w, float h, int tintRgba, boolean flipVertically, float rounding) {
      int texId = this.resolveTextureId(identifier);
      if (texId > 0) this.drawImage(texId, x, y, w, h, tintRgba, flipVertically, rounding);
   }

   private void drawRgbaTextureInternal(
           int texture, float x, float y, float w, float h, int tintRgba, boolean flipVertically,
           boolean preservePremultipliedColor) {
      this.ensureFrame();
      if (texture > 0) {
         float v0 = flipVertically ? 1.0F : 0.0F;
         float v1 = flipVertically ? 0.0F : 1.0F;
         this.backend
                 .drawRgbaTexturedQuad(
                         texture, x, y, w, h, 0.0F, v0, 1.0F, v1, this.modulateColor(tintRgba),
                         this.transformStack.current(), preservePremultipliedColor);
      }
   }

   public void end() {
      if (this.frameBegun) {
         try {
            if (this.batcher != null) {
               this.batcher.flush();
            }

            if (this.backend != null) {
               this.backend.endFrame();
            }

            RenderFrameMetrics.getInstance().endFrame();
         } catch (Exception var5) {
            System.err.println("Error in Renderer2D.end(): " + var5.getMessage());
            var5.printStackTrace();
         } finally {
            this.frameBegun = false;
            this.frameWidth = 0;
            this.frameHeight = 0;
            this.clipStack.clear();
            this.transformStack.clear();
            this.resetAlphaStack();
         }
      }
   }

   public void flush() {
      this.ensureFrame();
      this.batcher.flush();
   }

   public void pushBlendMode(BlendMode mode) {
      this.ensureFrame();
      this.batcher.flush();
      (mode == null ? BlendMode.NORMAL : mode).apply();
   }

   public void popBlendMode() {
      this.ensureFrame();
      this.batcher.flush();
      BlendMode.restoreDefault();
   }

   public void drawBlendRect(float x, float y, float w, float h, BorderRadius borderRadius, int color, BlendMode mode) {
      this.drawBlendRect(x, y, w, h, borderRadius, color, 2.0F, mode);
   }

   public void drawBlendRect(float x, float y, float w, float h, BorderRadius borderRadius, int color, float squirt, BlendMode mode) {
      this.ensureFrame();
      if (mode == null || !mode.isSoft()) {

         this.drawSquircle(x, y, w, h, squirt, borderRadius, color);
         return;
      }
      this.batcher.flush();
      if (this.blendModePipeline == null) {
         this.blendModePipeline = new BlendModePipeline();
      }
      int modulatedColor = this.modulateColor(color);
      float[] m = this.transformStack.current();
      float[] gl = new float[]{ m[0], m[3], m[6], m[1], m[4], m[7], m[2], m[5], m[8] };
      float[] radii = new float[]{
            borderRadius.topLeftRadius(), borderRadius.bottomLeftRadius(),
            borderRadius.topRightRadius(), borderRadius.bottomRightRadius()
      };
      this.blendModePipeline.drawBlend(x, y, w, h, radii, modulatedColor, squirt, mode.softId(), gl, this.frameWidth, this.frameHeight);
   }

   public void pushClipRect(int x, int y, int w, int h) {
      this.pushRoundedClipRect((float) x, (float) y, (float) w, (float) h, 0.0F, 0.0F, 0.0F, 0.0F);
   }
   public void pushClipRect(float x, float y, float w, float h) {
      this.pushRoundedClipRect(x, y, w, h, 0.0F, 0.0F, 0.0F, 0.0F);
   }

   public void pushRoundedClipRect(float x, float y, float w, float h, float roundTopLeft, float roundTopRight,
                                   float roundBottomRight, float roundBottomLeft) {
      this.ensureFrame();
      Renderer2D.ClipState incoming = Renderer2D.ClipState.fromRect(
              x, y, w, h, roundTopLeft, roundTopRight, roundBottomRight, roundBottomLeft, this.transformStack.current());
      Renderer2D.ClipState applied;
      if (this.clipStack.isEmpty()) {
         applied = incoming;
      } else {
         Renderer2D.ClipState current = this.clipStack.peek();
         applied = Renderer2D.ClipState.intersect(current, incoming);
      }

      this.clipStack.push(applied);
      this.applyClipState(applied);
   }

   public void popClipRect() {
      this.ensureFrame();
      if (!this.clipStack.isEmpty()) {
         this.clipStack.pop();
         if (this.clipStack.isEmpty()) {
            this.backend.setScissorEnabled(false);
         } else {
            this.applyClipState(this.clipStack.peek());
         }
      }
   }

   private void applyClipState(Renderer2D.ClipState state) {
      if (state == null) {
         this.backend.setScissorEnabled(false);
      } else {
         this.backend.setScissorEnabled(true);
         this.backend
                 .setScissorRect(
                         state.x(), state.y(), state.w(), state.h(), state.roundTopLeft(), state.roundTopRight(),
                         state.roundBottomRight(), state.roundBottomLeft());
      }
   }

   private void beginPipelineClip() {
      Renderer2D.ClipState c = this.clipStack.peek();
      if (c == null || c.w() <= 0 || c.h() <= 0) {
         return;
      }
      int sx = c.x();
      int sy = this.frameHeight - (c.y() + c.h());
      int sw = Math.max(0, c.w());
      int sh = Math.max(0, c.h());
      GL11.glEnable(GL11.GL_SCISSOR_TEST);
      GL11.glScissor(sx, sy, sw, sh);
   }

   private void endPipelineClip() {

      GL11.glDisable(GL11.GL_SCISSOR_TEST);
   }

   public void rectOutline(float x, float y, float w, float h, int rgbaPremul, float thickness) {
      this.ensureFrame();
      float renderThickness = Math.max(thickness, 0.1F);
      float offset = renderThickness / 2.0F;
      x -= offset;
      y -= offset;
      w += renderThickness;
      h += renderThickness;
      int color = this.modulateColor(rgbaPremul);
      if (thickness < 1.0F) {
         float alphaFactor = thickness;
         int a = color >>> 24 & 0xFF;
         int na = Math.round(a * alphaFactor);
         color = na << 24 | (color & 0x00FFFFFF);
      }
      this.batcher
              .enqueueRectOutline(x, y, w, h, 0.0F, 0.0F, 0.0F, 0.0F, color,
                      Math.max(1.0F, renderThickness), this.transformStack.current());
   }

   public void rectOutline(float x, float y, float w, float h, float rounding, float squirt, int rgbaPremul, float thickness) {
      float r1 = rounding * squirt / 2.0f;
      this.rectOutline(x, y, w, h, r1, r1, r1, r1, rgbaPremul, thickness);
   }
   public void rectOutline(float x, float y, float w, float h, float rounding, int rgbaPremul, float thickness) {
      float r1 = rounding;
      this.rectOutline(x, y, w, h, r1, r1, r1, r1, rgbaPremul, thickness);
   }

   public void rectOutline(
           float x,
           float y,
           float w,
           float h,
           float roundTopLeft,
           float roundTopRight,
           float roundBottomRight,
           float roundBottomLeft,
           int rgbaPremul,
           float thickness) {
      this.ensureFrame();
      float[] radii = scratchRadii(roundTopLeft, roundTopRight, roundBottomRight, roundBottomLeft);
      float renderThickness = Math.max(thickness, 0.1F);
      float offset = renderThickness / 2.0F;
      x -= offset;
      y -= offset;
      w += renderThickness;
      h += renderThickness;
      normalizeCornerRadii(w, h, radii);
      int color = this.modulateColor(rgbaPremul);
      if (thickness < 1.0F) {
         float alphaFactor = thickness;
         int a = color >>> 24 & 0xFF;
         int na = Math.round(a * alphaFactor);
         color = na << 24 | (color & 0x00FFFFFF);
      }
      this.batcher
              .enqueueRectOutline(
                      x, y, w, h, radii[0], radii[1], radii[2], radii[3], color,
                      Math.max(1.0F, renderThickness), this.transformStack.current());
   }

   public void gradient(float x, float y, float w, float h, int c00, int c10, int c11, int c01) {
      this.gradient(x, y, w, h, 0.0F, 0.0F, 0.0F, 0.0F, c00, c10, c11, c01, false);
   }

   public void gradient(float x, float y, float w, float h, int c00, int c10, int c11, int c01, boolean anim) {
      this.gradient(x, y, w, h, 0.0F, 0.0F, 0.0F, 0.0F, c00, c10, c11, c01, anim);
   }

   /**
    * Draws a rectangular two-color gradient with a phase that travels through the bar.
    * Unlike {@link #gradient(float, float, float, float, int, int, int, int, boolean)},
    * the color movement happens along the bar itself instead of cycling corner colors.
    */
   public void animatedGradientBar(float x, float y, float w, float h,
                                   int startColor, int endColor, float speed, float phase) {
      float time = System.nanoTime() / 1_000_000_000.0f * speed + phase;
      float leftProgress = 0.5f + 0.5f * (float)Math.sin(time);
      float rightProgress = 0.5f + 0.5f * (float)Math.sin(time + 1.65f);
      int leftColor = ColorUtil.interpolateColor(startColor, endColor, leftProgress);
      int rightColor = ColorUtil.interpolateColor(startColor, endColor, rightProgress);
      this.gradient(x, y, w, h, leftColor, rightColor, rightColor, leftColor);
   }

   public void gradient(float x, float y, float w, float h, float rounding, int c00, int c10, int c11, int c01) {
      this.gradient(x, y, w, h, rounding, rounding, rounding, rounding, c00, c10, c11, c01, false);
   }

   public void gradient(float x, float y, float w, float h, float rounding, int c00, int c10, int c11, int c01, boolean anim) {
      this.gradient(x, y, w, h, rounding, rounding, rounding, rounding, c00, c10, c11, c01, anim);
   }

   public void gradient(
           float x,
           float y,
           float w,
           float h,
           float roundTopLeft,
           float roundTopRight,
           float roundBottomRight,
           float roundBottomLeft,
           int c00,
           int c10,
           int c11,
           int c01) {
      this.gradient(x, y, w, h, roundTopLeft, roundTopRight, roundBottomRight, roundBottomLeft, c00, c10, c11, c01, false);
   }

   public void gradient(
           float x,
           float y,
           float w,
           float h,
           float roundTopLeft,
           float roundTopRight,
           float roundBottomRight,
           float roundBottomLeft,
           int c00,
           int c10,
           int c11,
           int c01,
           boolean anim) {
      this.ensureFrame();
      float[] radii = scratchRadii(roundTopLeft, roundTopRight, roundBottomRight, roundBottomLeft);
      normalizeCornerRadii(w, h, radii);
      int nc00 = this.modulateColor(c00);
      int nc10 = this.modulateColor(c10);
      int nc11 = this.modulateColor(c11);
      int nc01 = this.modulateColor(c01);
      if (anim) {
         float t = (float)((System.currentTimeMillis() % 10000L) / 200.0);
         float offset = t * 0.4f;
         int idx = (int)offset;
         float frac = offset - idx;
         int[] colors = new int[]{nc00, nc10, nc11, nc01};
         nc00 = Renderer2D.ColorUtil.interpolateColor(colors[(0 + idx) % 4], colors[(0 + idx + 1) % 4], (double)frac);
         nc10 = Renderer2D.ColorUtil.interpolateColor(colors[(1 + idx) % 4], colors[(1 + idx + 1) % 4], (double)frac);
         nc11 = Renderer2D.ColorUtil.interpolateColor(colors[(2 + idx) % 4], colors[(2 + idx + 1) % 4], (double)frac);
         nc01 = Renderer2D.ColorUtil.interpolateColor(colors[(3 + idx) % 4], colors[(3 + idx + 1) % 4], (double)frac);
      }
      this.batcher
              .enqueueGradient(
                      x,
                      y,
                      w,
                      h,
                      radii[0],
                      radii[1],
                      radii[2],
                      radii[3],
                      nc00,
                      nc10,
                      nc11,
                      nc01,
                      this.transformStack.current());
   }

   public void gradientOutline(float x, float y, float w, float h, float rounding, int c00, int c10, int c11, int c01, float thickness) {
      this.gradientOutline(x, y, w, h, rounding, rounding, rounding, rounding, c00, c10, c11, c01, thickness, false);
   }

   public void gradientOutline(float x, float y, float w, float h, float rounding, int c00, int c10, int c11, int c01, float thickness, boolean anim) {
      this.gradientOutline(x, y, w, h, rounding, rounding, rounding, rounding, c00, c10, c11, c01, thickness, anim);
   }

   public void gradientOutline(
           float x,
           float y,
           float w,
           float h,
           float roundTopLeft,
           float roundTopRight,
           float roundBottomRight,
           float roundBottomLeft,
           int c00,
           int c10,
           int c11,
           int c01,
           float thickness) {
      this.gradientOutline(x, y, w, h, roundTopLeft, roundTopRight, roundBottomRight, roundBottomLeft, c00, c10, c11, c01, thickness, false);
   }

   public void gradientOutline(
           float x,
           float y,
           float w,
           float h,
           float roundTopLeft,
           float roundTopRight,
           float roundBottomRight,
           float roundBottomLeft,
           int c00,
           int c10,
           int c11,
           int c01,
           float thickness,
           boolean anim) {
      this.ensureFrame();
      float[] radii = scratchRadii(roundTopLeft, roundTopRight, roundBottomRight, roundBottomLeft);
      x--;
      y--;
      w += 2.0F;
      h += 2.0F;
      normalizeCornerRadii(w, h, radii);
      int nc00 = this.modulateColor(c00);
      int nc10 = this.modulateColor(c10);
      int nc11 = this.modulateColor(c11);
      int nc01 = this.modulateColor(c01);
      if (anim) {
         float t = (float)((System.currentTimeMillis() % 3000) / 3000.0);
         float offset = t * 4.0f;
         int idx = (int)offset;
         float frac = offset - idx;
         int[] colors = new int[]{nc00, nc10, nc11, nc01};
         nc00 = Renderer2D.ColorUtil.interpolateColor(colors[(0 + idx) % 4], colors[(0 + idx + 1) % 4], (double)frac);
         nc10 = Renderer2D.ColorUtil.interpolateColor(colors[(1 + idx) % 4], colors[(1 + idx + 1) % 4], (double)frac);
         nc11 = Renderer2D.ColorUtil.interpolateColor(colors[(2 + idx) % 4], colors[(2 + idx + 1) % 4], (double)frac);
         nc01 = Renderer2D.ColorUtil.interpolateColor(colors[(3 + idx) % 4], colors[(3 + idx + 1) % 4], (double)frac);
      }
      this.batcher
              .enqueueGradientOutline(
                      x, y, w, h, radii[0], radii[1], radii[2], radii[3],
                      nc00,
                      nc10,
                      nc11,
                      nc01,
                      Math.max(1.0F, thickness),
                      this.transformStack.current());
   }

   public void horizontalGradient(float x, float y, float w, float h, int leftColor, int rightColor) {
      this.gradient(x, y, w, h, leftColor, rightColor, rightColor, leftColor);
   }

   public void horizontalGradient(float x, float y, float w, float h, float rounding, int leftColor, int rightColor) {
      this.gradient(x, y, w, h, rounding, leftColor, rightColor, rightColor, leftColor);
   }

   public void horizontalGradient(
           float x, float y, float w, float h, float roundTopLeft, float roundTopRight, float roundBottomRight,
           float roundBottomLeft, int leftColor, int rightColor) {
      this.gradient(x, y, w, h, roundTopLeft, roundTopRight, roundBottomRight, roundBottomLeft, leftColor, rightColor,
              rightColor, leftColor);
   }

   public void verticalGradient(float x, float y, float w, float h, int topColor, int bottomColor) {
      this.gradient(x, y, w, h, topColor, topColor, bottomColor, bottomColor);
   }

   public void verticalGradient(float x, float y, float w, float h, float rounding, int topColor, int bottomColor) {
      this.gradient(x, y, w, h, rounding, topColor, topColor, bottomColor, bottomColor);
   }

   public void drawRect(float x, float y, float w, float h, int rgbaPremul) {
      this.rect(x, y, w, h, rgbaPremul);
   }

   private void drawLiquidGlassInternal(
           float x,
           float y,
           float w,
           float h,
           int color,
           float globalAlpha,
           float refractZoom,
           float distortionStrength,
           float shapeRoundness,
           float blurPixelStep,
           float tintStrength,
           float glowIntensity,
           float glowSpeed) {
      this.ensureFrame();
      this.batcher.flush();

      int modulatedColor = this.modulateColor(color);
      float modulatedGlobalAlpha = globalAlpha * this.currentAlphaMultiplier();
      this.ensureLiquidGlassInitialized();
      GlState.Snapshot state = GlState.push();

      try {
         this.liquidGlassShader.use();
         this.setLiquidGlassMatrix("uMatrix", this.transformStack.current());
         this.setLiquidGlassUniform("uViewport", (float)this.frameWidth, (float)this.frameHeight);
         this.setLiquidGlassUniform("iTime", (float)(System.currentTimeMillis() % 1000000L) / 1000.0F);
         this.setLiquidGlassUniform("Size", w, h);
         this.setLiquidGlassUniform("GlobalAlpha", modulatedGlobalAlpha);
         this.setLiquidGlassUniform("GlassRefractZoom", refractZoom);
         this.setLiquidGlassUniform("GlassDistortionStrength", distortionStrength);
         this.setLiquidGlassUniform("GlassRoundness", shapeRoundness);
         this.setLiquidGlassUniform("GlassBlurPixelStep", blurPixelStep);
         this.setLiquidGlassUniform("GlassTintStrength", tintStrength);
         this.setLiquidGlassUniform("GlassGlowIntensity", glowIntensity);
         this.setLiquidGlassUniform("GlassGlowSpeed", glowSpeed);
         GL13.glActiveTexture(GL13.GL_TEXTURE0);
         GL11.glBindTexture(GL11.GL_TEXTURE_2D, getMcFramebufferTex());
         int samplerLoc = this.liquidGlassShader.getUniformLocation("Sampler0");
         if (samplerLoc != -1) {
            GL20.glUniform1i(samplerLoc, 0);
         }

         float r = (float)(modulatedColor >> 16 & 255) / 255.0F;
         float g = (float)(modulatedColor >> 8 & 255) / 255.0F;
         float b = (float)(modulatedColor & 255) / 255.0F;
         float a = (float)(modulatedColor >> 24 & 255) / 255.0F;
         float[] vertices = new float[]{
                 x, y, 0.0F, 0.0F, 0.0F, r, g, b, a,
                 x, y + h, 0.0F, 0.0F, 1.0F, r, g, b, a,
                 x + w, y + h, 0.0F, 1.0F, 1.0F, r, g, b, a,
                 x + w, y, 0.0F, 1.0F, 0.0F, r, g, b, a
         };
         GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.liquidGlassVbo);
         GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0L, vertices);
         GL11.glEnable(GL11.GL_BLEND);
         GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
         GL11.glDisable(GL11.GL_CULL_FACE);
         GL11.glDisable(GL11.GL_DEPTH_TEST);
         GL30.glBindVertexArray(this.liquidGlassVao);
         GL11.glDrawElements(GL11.GL_TRIANGLES, 6, GL11.GL_UNSIGNED_INT, 0L);
         GL30.glBindVertexArray(0);
         GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
      } finally {
         GlState.pop(state);
      }
   }

   public void drawLiquidGlass(float x, float y, float w, float h, int color, float round,float power) {
      this.drawLiquidGlassRect(x, y, w, h, round, power * 2, color);
   }

   private void ensureSkyShaderInitialized() {
      if (this.skyShaderInitialized) return;
      this.skyShader = ShaderProgram.fromResources("assets/vesence/shaders/core/skyshader.vsh", "assets/vesence/shaders/core/skyshader.fsh");
      this.skyShaderStorm = ShaderProgram.fromResources("assets/vesence/shaders/core/skyshader.vsh", "assets/vesence/shaders/core/skyshader_storm.fsh");
      this.skyShaderSunset = ShaderProgram.fromResources("assets/vesence/shaders/core/skyshader.vsh", "assets/vesence/shaders/core/skyshader_sunset.fsh");
      this.skyShaderVao = GL30.glGenVertexArrays();
      this.skyShaderVbo = GL15.glGenBuffers();
      this.skyShaderIbo = GL15.glGenBuffers();
      GL30.glBindVertexArray(this.skyShaderVao);
      GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.skyShaderVbo);
      GL15.glBufferData(GL15.GL_ARRAY_BUFFER, 144L, GL15.GL_DYNAMIC_DRAW);
      GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, this.skyShaderIbo);
      GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, new int[]{0, 1, 2, 0, 2, 3}, GL15.GL_STATIC_DRAW);

      int posAttrib = GL20.glGetAttribLocation(this.skyShader.id(), "Position");
      if (posAttrib != -1) {
         GL20.glEnableVertexAttribArray(posAttrib);
         GL20.glVertexAttribPointer(posAttrib, 3, GL11.GL_FLOAT, false, 36, 0L);
      }

      int posAttribStorm = GL20.glGetAttribLocation(this.skyShaderStorm.id(), "Position");
      if (posAttribStorm != -1) {
         GL20.glEnableVertexAttribArray(posAttribStorm);
         GL20.glVertexAttribPointer(posAttribStorm, 3, GL11.GL_FLOAT, false, 36, 0L);
      }
      int posAttribSunset = GL20.glGetAttribLocation(this.skyShaderSunset.id(), "Position");
      if (posAttribSunset != -1) {
         GL20.glEnableVertexAttribArray(posAttribSunset);
         GL20.glVertexAttribPointer(posAttribSunset, 3, GL11.GL_FLOAT, false, 36, 0L);
      }
      GL30.glBindVertexArray(0);
      this.skyShaderInitialized = true;
   }

   public void drawSkyShader(MatrixStack matrices, Camera camera) {
      this.drawSkyShader(matrices, camera, 0);
   }

   public void drawSkyShader(MatrixStack matrices, Camera camera, int shaderIndex) {
      this.ensureSkyShaderInitialized();
      GlState.Snapshot state = GlState.push();
      try {
         ShaderProgram active = this.skyShader;
         if (shaderIndex == 1) active = this.skyShaderStorm;
         else if (shaderIndex == 2) active = this.skyShaderSunset;
         active.use();

         int width = mc.getWindow().getFramebufferWidth();
         int height = mc.getWindow().getFramebufferHeight();

         float time = (float)(System.currentTimeMillis() % 1000000L) / 1000.0F;
         float yaw = camera.getYaw();
         float pitch = camera.getPitch();
         float fov = mc.options.getFov().getValue().floatValue();
         float focal = (float) (1.0 / (2.0 * Math.tan(Math.toRadians(fov / 2.0))));

         int color = vesence.renderengine.render.Renderer2D.ColorUtil.getClientColor();
         float r = ((color >> 16) & 0xFF) / 255.0f;
         float g = ((color >> 8) & 0xFF) / 255.0f;
         float b = (color & 0xFF) / 255.0f;

         int color2 = vesence.renderengine.render.Renderer2D.ColorUtil.getClientColor2();
         float r2 = ((color2 >> 16) & 0xFF) / 255.0f;
         float g2 = ((color2 >> 8) & 0xFF) / 255.0f;
         float b2 = (color2 & 0xFF) / 255.0f;

         GL20.glUniform2f(active.getUniformLocation("resolution"), (float)width, (float)height);
         GL20.glUniform1f(active.getUniformLocation("time"), time);
         GL20.glUniform1f(active.getUniformLocation("alpha"), 1.0f);
         GL20.glUniform1f(active.getUniformLocation("yaw"), yaw);
         GL20.glUniform1f(active.getUniformLocation("pitch"), pitch);
         GL20.glUniform1f(active.getUniformLocation("focal"), focal);

         if (shaderIndex == 0) {

            GL20.glUniform3f(active.getUniformLocation("clientColor"), r, g, b);
         } else {

            GL20.glUniform3f(active.getUniformLocation("PrimaryColor"), r, g, b);
            GL20.glUniform3f(active.getUniformLocation("SecondaryColor"), r2, g2, b2);
            GL20.glUniform3f(active.getUniformLocation("AccentColor"), r, g, b);
            if (shaderIndex == 2) {

               float sunYaw = (float) Math.toRadians(-yaw + 180.0f);
               float sunPitch = (float) Math.toRadians(6.0f);
               float sx = (float)(Math.cos(sunPitch) * Math.cos(sunYaw));
               float sy = (float) Math.sin(sunPitch);
               float sz = (float)(Math.cos(sunPitch) * Math.sin(sunYaw));
               GL20.glUniform3f(active.getUniformLocation("SunDir"), sx, sy, sz);
               GL20.glUniform1f(active.getUniformLocation("Haze"), 0.6f);
            }
         }

         float[] vertices = new float[]{
                 -1.0f, -1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
                 -1.0f,  1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
                 1.0f,  1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
                 1.0f, -1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f
         };
         GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.skyShaderVbo);
         GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0L, vertices);

         GL11.glEnable(GL11.GL_BLEND);
         GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
         GL11.glDisable(GL11.GL_CULL_FACE);
         GL11.glEnable(GL11.GL_DEPTH_TEST);
         GL11.glDepthFunc(GL11.GL_LEQUAL);
         GL11.glDepthMask(false);

         GL30.glBindVertexArray(this.skyShaderVao);
         GL11.glDrawElements(GL11.GL_TRIANGLES, 6, GL11.GL_UNSIGNED_INT, 0L);
         GL30.glBindVertexArray(0);
         GL11.glDepthMask(true);
         GL11.glEnable(GL11.GL_DEPTH_TEST);
      } finally {
         GlState.pop(state);
      }
   }

   private void ensureOrbBgInitialized() {
      if (this.orbBgInitialized) return;
      this.orbBgShader = ShaderProgram.fromResources("assets/vesence/shaders/core/orb_bg.vsh", "assets/vesence/shaders/core/orb_bg.fsh");
      this.orbBgVao = GL30.glGenVertexArrays();
      this.orbBgVbo = GL15.glGenBuffers();
      this.orbBgIbo = GL15.glGenBuffers();
      GL30.glBindVertexArray(this.orbBgVao);
      GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.orbBgVbo);
      GL15.glBufferData(GL15.GL_ARRAY_BUFFER, 144L, GL15.GL_DYNAMIC_DRAW);
      GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, this.orbBgIbo);
      GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, new int[]{0, 1, 2, 0, 2, 3}, GL15.GL_STATIC_DRAW);

      int posAttrib = GL20.glGetAttribLocation(this.orbBgShader.id(), "Position");
      if (posAttrib != -1) {
         GL20.glEnableVertexAttribArray(posAttrib);
         GL20.glVertexAttribPointer(posAttrib, 3, GL11.GL_FLOAT, false, 36, 0L);
      }
      GL30.glBindVertexArray(0);

      this.orbBgFbo = GL30.glGenFramebuffers();
      this.orbBgTex = GL11.glGenTextures();
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.orbBgTex);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, 33071);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, 33071);
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

      this.orbBgInitialized = true;
   }

   private void ensureOrbBg2Initialized() {
      if (this.orbBg2Initialized) return;
      this.orbBg2Shader = ShaderProgram.fromResources("assets/vesence/shaders/core/orb_bg2.vsh", "assets/vesence/shaders/core/orb_bg2.fsh");
      this.orbBg2Initialized = true;
   }

   public void drawOrbBackground() {
      this.drawOrbBackground(0);
   }

   public void drawOrbBackground(int shaderIndex) {
      this.ensureFrame();
      this.batcher.flush();
      this.ensureOrbBgInitialized();
      if (shaderIndex == 1) this.ensureOrbBg2Initialized();

      int width = mc.getWindow().getFramebufferWidth();
      int height = mc.getWindow().getFramebufferHeight();
      int halfW = Math.max(1, width / 2);
      int halfH = Math.max(1, height / 2);

      if (this.orbBgFboW != halfW || this.orbBgFboH != halfH) {
         GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.orbBgTex);
         GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, halfW, halfH, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
         GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
         GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.orbBgFbo);
         GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, 36064, GL11.GL_TEXTURE_2D, this.orbBgTex, 0);
         GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
         this.orbBgFboW = halfW;
         this.orbBgFboH = halfH;
      }

      float time = (float)(System.currentTimeMillis() % 1000000L) / 1000.0F;

      int color = Renderer2D.ColorUtil.getClientColor();
      float r = ((color >> 16) & 0xFF) / 255.0f;
      float g = ((color >> 8) & 0xFF) / 255.0f;
      float b = (color & 0xFF) / 255.0f;

      float mx = (float)(Math.sin(time * 0.05) * 0.2 + 0.5) * halfW;
      float my = (float)(Math.cos(time * 0.03) * 0.1 + 0.5) * halfH;

      GlState.Snapshot state = GlState.push();
      try {
         GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.orbBgFbo);
         GL11.glViewport(0, 0, halfW, halfH);

         ShaderProgram activeShader = (shaderIndex == 1 && this.orbBg2Shader != null) ? this.orbBg2Shader : this.orbBgShader;
         activeShader.use();
         GL20.glUniform2f(activeShader.getUniformLocation("iResolution"), (float)halfW, (float)halfH);
         GL20.glUniform1f(activeShader.getUniformLocation("iTime"), time);
         GL20.glUniform2f(activeShader.getUniformLocation("iMouse"), mx, my);
         GL20.glUniform3f(activeShader.getUniformLocation("clientColor"), r, g, b);

         float[] vertices = new float[]{
                 -1.0f, -1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
                 -1.0f,  1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
                 1.0f,  1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
                 1.0f, -1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f
         };
         GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.orbBgVbo);
         GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0L, vertices);

         GL11.glDisable(GL11.GL_DEPTH_TEST);
         GL11.glDisable(GL11.GL_CULL_FACE);

         GL30.glBindVertexArray(this.orbBgVao);
         GL11.glDrawElements(GL11.GL_TRIANGLES, 6, GL11.GL_UNSIGNED_INT, 0L);
         GL30.glBindVertexArray(0);

         GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, this.orbBgFbo);
         GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, state.drawFramebuffer);
         GL30.glBlitFramebuffer(0, 0, halfW, halfH, 0, 0, width, height,
                 GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
         GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
         GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);
      } finally {
         GlState.pop(state);
      }
   }

   private int[] shaderPreviewFbo = new int[2];
   private int[] shaderPreviewTex = new int[2];
   private boolean[] shaderPreviewReady = new boolean[2];
   private int shaderPreviewW = 0;
   private int shaderPreviewH = 0;

   private void ensureOrbBgRoundedInitialized() {
      if (this.orbBgRoundedInitialized) return;
      this.orbBgRoundedShader = ShaderProgram.fromResources("assets/vesence/shaders/core/orb_bg_rounded.vsh", "assets/vesence/shaders/core/orb_bg_rounded.fsh");
      if (this.previewVao == 0) {
         this.previewVao = GL30.glGenVertexArrays();
         this.previewVbo = GL15.glGenBuffers();
         this.previewIbo = GL15.glGenBuffers();
         GL30.glBindVertexArray(this.previewVao);
         GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.previewVbo);
         GL15.glBufferData(GL15.GL_ARRAY_BUFFER, 144L, GL15.GL_DYNAMIC_DRAW);
         GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, this.previewIbo);
         GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, new int[]{0, 1, 2, 0, 2, 3}, GL15.GL_STATIC_DRAW);
         int posAttrib = GL20.glGetAttribLocation(this.orbBgRoundedShader.id(), "Position");
         if (posAttrib != -1) {
            GL20.glEnableVertexAttribArray(posAttrib);
            GL20.glVertexAttribPointer(posAttrib, 3, GL11.GL_FLOAT, false, 36, 0L);
         }
         GL30.glBindVertexArray(0);
      }
      this.orbBgRoundedInitialized = true;
   }

   private void ensureOrbBg2RoundedInitialized() {
      if (this.orbBg2RoundedInitialized) return;
      this.orbBg2RoundedShader = ShaderProgram.fromResources("assets/vesence/shaders/core/orb_bg2_rounded.vsh", "assets/vesence/shaders/core/orb_bg2_rounded.fsh");
      this.orbBg2RoundedInitialized = true;
   }

   public void drawShaderPreview(int shaderIndex, float x, float y, float w, float h, float rounding) {
      this.ensureFrame();
      this.batcher.flush();
      this.ensureOrbBgRoundedInitialized();
      if (shaderIndex == 1) this.ensureOrbBg2RoundedInitialized();

      float time = (float)(System.currentTimeMillis() % 1000000L) / 1000.0F;
      int color = Renderer2D.ColorUtil.getClientColor();
      float cr = ((color >> 16) & 0xFF) / 255.0f;
      float cg = ((color >> 8) & 0xFF) / 255.0f;
      float cb = (color & 0xFF) / 255.0f;
      float mx = (float)(Math.sin(time * 0.05) * 0.2 + 0.5) * w;
      float my = (float)(Math.cos(time * 0.03) * 0.1 + 0.5) * h;

      float[] mat = this.transformStack.current();
      float sx = mat[0] * x + mat[2];
      float sy = mat[4] * y + mat[5];
      float sw = mat[0] * w;
      float sh = mat[4] * h;
      int fbW = this.frameWidth;
      int fbH = this.frameHeight;

      int fboW = Math.max(512, (int)Math.ceil(sw * 4.0f));
      int fboH = Math.max(512, (int)Math.ceil(sh * 4.0f));

      fboW = Math.min(fboW, 4096);
      fboH = Math.min(fboH, 4096);

      int si = shaderIndex;
      if (this.shaderPreviewFbo[si] == 0) {
         this.shaderPreviewFbo[si] = GL30.glGenFramebuffers();
         this.shaderPreviewTex[si] = GL11.glGenTextures();
         GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.shaderPreviewTex[si]);
         GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
         GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
         GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, 33071);
         GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, 33071);
         GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
         this.shaderPreviewReady[si] = false;
      }
      if (this.shaderPreviewW != fboW || this.shaderPreviewH != fboH || !this.shaderPreviewReady[si]) {
         GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.shaderPreviewTex[si]);
         GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, fboW, fboH, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer)null);
         GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
         GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.shaderPreviewFbo[si]);
         GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, 36064, GL11.GL_TEXTURE_2D, this.shaderPreviewTex[si], 0);
         GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
         this.shaderPreviewW = fboW;
         this.shaderPreviewH = fboH;
         this.shaderPreviewReady[si] = true;
      }

      float[] vertices = new float[]{
              -1.0f, -1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
              -1.0f,  1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
              1.0f,  1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
              1.0f, -1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f
      };
      GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.previewVbo);
      GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0L, vertices);

      GlState.Snapshot state = GlState.push();
      try {

         GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.shaderPreviewFbo[si]);
         GL11.glViewport(0, 0, fboW, fboH);

         GL11.glEnable(GL11.GL_BLEND);
         GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);

         ShaderProgram shader = (shaderIndex == 1) ? this.orbBg2RoundedShader : this.orbBgRoundedShader;
         shader.use();

         GL20.glUniform2f(shader.getUniformLocation("uOffset"), 0.0f, 0.0f);
         GL20.glUniform2f(shader.getUniformLocation("uSize"), (float)fboW, (float)fboH);
         GL20.glUniform2f(shader.getUniformLocation("uViewport"), (float)fboW, (float)fboH);

         GL20.glUniform2f(shader.getUniformLocation("iResolution"), (float)fboW, (float)fboH);
         GL20.glUniform1f(shader.getUniformLocation("iTime"), time);
         GL20.glUniform2f(shader.getUniformLocation("iMouse"), mx / sw * fboW, my / sh * fboH);
         GL20.glUniform3f(shader.getUniformLocation("clientColor"), cr, cg, cb);
         GL20.glUniform1f(shader.getUniformLocation("uRounding"), mat[0] * rounding / sw * fboW);
         GL20.glUniform1f(shader.getUniformLocation("uAlpha"), this.currentAlphaMultiplier());

         GL11.glDisable(GL11.GL_DEPTH_TEST);
         GL11.glDisable(GL11.GL_CULL_FACE);

         GL30.glBindVertexArray(this.previewVao);
         GL11.glDrawElements(GL11.GL_TRIANGLES, 6, GL11.GL_UNSIGNED_INT, 0L);
         GL30.glBindVertexArray(0);

         GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, this.shaderPreviewFbo[si]);
         GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, state.drawFramebuffer);
         GL30.glBlitFramebuffer(
                 0, 0, fboW, fboH,
                 (int)sx, (int)sy, (int)(sx + sw), (int)(sy + sh),
                 GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
      } finally {
         GlState.pop(state);
      }
   }

   private int shaderPreviewMaskColor = 0;
   public void setShaderPreviewMaskColor(int color) {
      this.shaderPreviewMaskColor = color;
   }

   public void drawRainTexture(int texture, float x, float y, float w, float h, float mouseX, float mouseY) {
      this.drawRainTexture(texture, x, y, w, h, mouseX, mouseY, 1.0F);
   }

   public void drawRainTexture(int texture, float x, float y, float w, float h, float mouseX, float mouseY, float intensity) {
      float timeSeconds = (float)(System.currentTimeMillis() % 1000000L) / 1000.0F;
      this.drawRainTexture(texture, x, y, w, h, mouseX, mouseY, intensity, timeSeconds);
   }

   public void drawRainTexture(int texture, float x, float y, float w, float h, float mouseX, float mouseY, float intensity, float timeSeconds) {
      this.ensureFrame();
      this.batcher.flush();
      if (texture <= 0) {
         return;
      }

      this.ensureRainInitialized();
      GlState.Snapshot state = GlState.push();

      try {
         this.rainShader.use();
         this.setRainMatrix("uMatrix", this.transformStack.current());
         this.setRainUniform("uViewport", (float)this.frameWidth, (float)this.frameHeight);
         this.setRainUniform("Size", w, h);
         this.setRainUniform("iTime", Math.max(0.0F, timeSeconds));
         this.setRainUniform("uAlpha", this.currentAlphaMultiplier());
         this.setRainUniform("uRainAmount", clamp01(intensity));
         float localMouseX = clamp01((mouseX - x) / Math.max(w, 1.0F));
         float localMouseY = 1.0F - clamp01((mouseY - y) / Math.max(h, 1.0F));
         this.setRainUniform("uMouse", localMouseX, localMouseY);
         GL13.glActiveTexture(GL13.GL_TEXTURE0);
         GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
         int samplerLoc = this.rainShader.getUniformLocation("Sampler0");
         if (samplerLoc != -1) {
            GL20.glUniform1i(samplerLoc, 0);
         }

         float[] vertices = new float[]{
                 x, y, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F,
                 x, y + h, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F,
                 x + w, y + h, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F,
                 x + w, y, 0.0F, 1.0F, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F
         };
         GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.rainVbo);
         GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0L, vertices);
         GL11.glEnable(GL11.GL_BLEND);
         GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
         GL11.glDisable(GL11.GL_CULL_FACE);
         GL11.glDisable(GL11.GL_DEPTH_TEST);
         GL30.glBindVertexArray(this.rainVao);
         GL11.glDrawElements(GL11.GL_TRIANGLES, 6, GL11.GL_UNSIGNED_INT, 0L);
         GL30.glBindVertexArray(0);
         GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
      } finally {
         GlState.pop(state);
      }
   }

   public void drawLiquidGlassRect(float x, float y, float w, float h, float rounding, float distortionStrength, int color) {
      float alpha = ((color >> 24) & 0xFF) / 255f;
      float safeStrength = clamp01(distortionStrength);
      float globalAlpha = alpha * 0.9f;
      float refractZoom = 0.985f;
      float shapeRoundness = computeLiquidGlassRoundness(rounding, w, h);
      float glowIntensity = 2.15f;
      float glowSpeed = 2.2f;

      this.drawLiquidGlassInternal(
              x,
              y,
              w,
              h,
              color,
              globalAlpha,
              refractZoom,
              safeStrength,
              shapeRoundness,
              1.5F,
              0,
              glowIntensity,
              glowSpeed);
   }

   private static float computeLiquidGlassRoundness(float rounding, float w, float h) {
      float minSide = Math.max(1.0F, Math.min(Math.abs(w), Math.abs(h)));
      float roundRatio = clamp01(rounding / (minSide * 0.5F));
      return 6.0f + roundRatio * 6.0f;
   }

   private void ensureLiquidGlassInitialized() {
      if (this.liquidGlassInitialized) {
         return;
      }

      this.liquidGlassShader = ShaderProgram.fromResources("assets/vesence/shaders/core/liquidglass.vsh", "assets/vesence/shaders/core/liquidglass.fsh");
      this.liquidGlassVao = GL30.glGenVertexArrays();
      this.liquidGlassVbo = GL15.glGenBuffers();
      this.liquidGlassIbo = GL15.glGenBuffers();
      GL30.glBindVertexArray(this.liquidGlassVao);
      GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.liquidGlassVbo);
      GL15.glBufferData(GL15.GL_ARRAY_BUFFER, 144L, GL15.GL_DYNAMIC_DRAW);
      GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, this.liquidGlassIbo);
      GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, new int[]{0, 1, 2, 0, 2, 3}, GL15.GL_STATIC_DRAW);
      this.setupLiquidGlassAttributes();
      GL30.glBindVertexArray(0);
      this.liquidGlassInitialized = true;
   }

   private void ensureRainInitialized() {
      if (this.rainInitialized) {
         return;
      }

      this.rainShader = ShaderProgram.fromResources("assets/vesence/shaders/core/liquidglass.vsh", "assets/vesence/shaders/core/rain.fsh");
      this.rainVao = GL30.glGenVertexArrays();
      this.rainVbo = GL15.glGenBuffers();
      this.rainIbo = GL15.glGenBuffers();
      GL30.glBindVertexArray(this.rainVao);
      GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.rainVbo);
      GL15.glBufferData(GL15.GL_ARRAY_BUFFER, 144L, GL15.GL_DYNAMIC_DRAW);
      GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, this.rainIbo);
      GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, new int[]{0, 1, 2, 0, 2, 3}, GL15.GL_STATIC_DRAW);
      this.setupRainAttributes();
      GL30.glBindVertexArray(0);
      this.rainInitialized = true;
   }

   private void setupLiquidGlassAttributes() {
      int posLoc = GL20.glGetAttribLocation(this.liquidGlassShader.id(), "Position");
      if (posLoc != -1) {
         GL20.glEnableVertexAttribArray(posLoc);
         GL20.glVertexAttribPointer(posLoc, 3, GL11.GL_FLOAT, false, 36, 0L);
      }

      int uvLoc = GL20.glGetAttribLocation(this.liquidGlassShader.id(), "UV0");
      if (uvLoc != -1) {
         GL20.glEnableVertexAttribArray(uvLoc);
         GL20.glVertexAttribPointer(uvLoc, 2, GL11.GL_FLOAT, false, 36, 12L);
      }

      int colorLoc = GL20.glGetAttribLocation(this.liquidGlassShader.id(), "Color");
      if (colorLoc != -1) {
         GL20.glEnableVertexAttribArray(colorLoc);
         GL20.glVertexAttribPointer(colorLoc, 4, GL11.GL_FLOAT, false, 36, 20L);
      }
   }

   private void setupRainAttributes() {
      int posLoc = GL20.glGetAttribLocation(this.rainShader.id(), "Position");
      if (posLoc != -1) {
         GL20.glEnableVertexAttribArray(posLoc);
         GL20.glVertexAttribPointer(posLoc, 3, GL11.GL_FLOAT, false, 36, 0L);
      }

      int uvLoc = GL20.glGetAttribLocation(this.rainShader.id(), "UV0");
      if (uvLoc != -1) {
         GL20.glEnableVertexAttribArray(uvLoc);
         GL20.glVertexAttribPointer(uvLoc, 2, GL11.GL_FLOAT, false, 36, 12L);
      }

      int colorLoc = GL20.glGetAttribLocation(this.rainShader.id(), "Color");
      if (colorLoc != -1) {
         GL20.glEnableVertexAttribArray(colorLoc);
         GL20.glVertexAttribPointer(colorLoc, 4, GL11.GL_FLOAT, false, 36, 20L);
      }
   }

   private void setLiquidGlassMatrix(String name, float[] mat) {
      int loc = this.liquidGlassShader.getUniformLocation(name);
      if (loc != -1) {
         GL20.glUniformMatrix3fv(loc, false, mat);
      }
   }

   private void setRainMatrix(String name, float[] mat) {
      int loc = this.rainShader.getUniformLocation(name);
      if (loc != -1) {
         GL20.glUniformMatrix3fv(loc, false, mat);
      }
   }

   private void setLiquidGlassUniform(String name, float... values) {
      int loc = this.liquidGlassShader.getUniformLocation(name);
      if (loc == -1) {
         return;
      }

      if (values.length == 1) {
         GL20.glUniform1f(loc, values[0]);
      } else if (values.length == 2) {
         GL20.glUniform2f(loc, values[0], values[1]);
      } else if (values.length == 3) {
         GL20.glUniform3f(loc, values[0], values[1], values[2]);
      } else if (values.length == 4) {
         GL20.glUniform4f(loc, values[0], values[1], values[2], values[3]);
      }
   }

   private void setRainUniform(String name, float... values) {
      int loc = this.rainShader.getUniformLocation(name);
      if (loc == -1) {
         return;
      }

      if (values.length == 1) {
         GL20.glUniform1f(loc, values[0]);
      } else if (values.length == 2) {
         GL20.glUniform2f(loc, values[0], values[1]);
      } else if (values.length == 3) {
         GL20.glUniform3f(loc, values[0], values[1], values[2]);
      } else if (values.length == 4) {
         GL20.glUniform4f(loc, values[0], values[1], values[2], values[3]);
      }
   }

   public void drawGradient(float x, float y, float w, float h, int c00, int c10, int c11, int c01) {
      this.gradient(x, y, w, h, c00, c10, c11, c01);
   }

   public void drawBlur(float x, float y, float w, float h, float radius, float[] radii, int color) {
      this.ensureFrame();
      this.batcher.flush();
      if (this.blurPipeline == null) {
         this.blurPipeline = new BlurPipeline();
      }
      int modulatedColor = this.modulateColor(color);
      float[] m = this.transformStack.current();
      boolean hasRotation = Math.abs(m[1]) > 1.0E-6F || Math.abs(m[3]) > 1.0E-6F;
      if (hasRotation) {

         float[] gl = this.blurMatrixScratch;
         gl[0] = m[0]; gl[1] = m[3]; gl[2] = m[6];
         gl[3] = m[1]; gl[4] = m[4]; gl[5] = m[7];
         gl[6] = m[2]; gl[7] = m[5]; gl[8] = m[8];
         this.blurPipeline.drawBlur(x, y, w, h, radius, radii, modulatedColor, gl, this.frameWidth, this.frameHeight);
      } else {
         float nx = m[0] * x + m[1] * y + m[2];
         float ny = m[3] * x + m[4] * y + m[5];
         float nw = m[0] * w;
         float nh = m[4] * h;
         float[] sr = this.blurRadiiScratch;
         sr[0] = radii[0] * m[0]; sr[1] = radii[1] * m[0]; sr[2] = radii[2] * m[0]; sr[3] = radii[3] * m[0];
         this.blurPipeline.drawBlur(nx, ny, nw, nh, radius, sr, modulatedColor, IDENTITY, this.frameWidth, this.frameHeight);
      }
   }

   public void blur(float x, float y, float w, float h, float radius, float rounding, float alpha) {
      int color = java.awt.Color.TRANSLUCENT;
      int c = ((int)(alpha * 255) & 0xFF) << 24;
      float[] r = this.blurRadiiInput;
      r[0] = rounding; r[1] = rounding; r[2] = rounding; r[3] = rounding;
      this.drawBlur(x, y, w, h, radius, r, c);
   }

   public void blur(float x, float y, float w, float h, float radius, float rounding) {
      this.blur(x, y, w, h, radius, rounding, 1.0f);
   }

   public void blur(float x, float y, float w, float h, float radius, float roundTL, float roundTR, float roundBR, float roundBL, float alpha) {
      int c = ((int)(alpha * 255) & 0xFF) << 24;
      float[] r = this.blurRadiiInput;
      r[0] = roundTL; r[1] = roundTR; r[2] = roundBR; r[3] = roundBL;
      this.drawBlur(x, y, w, h, radius, r, c);
   }

   public void blur(float x, float y, float w, float h, float radius, BorderRadius borderRadius, float alpha) {
      int c = ((int)(alpha * 255) & 0xFF) << 24;
      float[] r = this.blurRadiiInput;
      r[0] = borderRadius.topLeftRadius(); r[1] = borderRadius.topRightRadius();
      r[2] = borderRadius.bottomRightRadius(); r[3] = borderRadius.bottomLeftRadius();
      this.drawBlur(x, y, w, h, radius, r, c);
   }

   public void blur(float x, float y, float w, float h, float radius, BorderRadius borderRadius) {
      this.blur(x, y, w, h, radius, borderRadius, 1.0f);
   }

   public void blur(float x, float y, float w, float h, float radius, float squirt, BorderRadius borderRadius, float alpha) {
      this.blurSquircle(x, y, w, h, radius, squirt, borderRadius, alpha);
   }

   public void blurSquircle(float x, float y, float w, float h, float blurRadius, float squirt, BorderRadius borderRadius, float alpha) {
      int c = ((int)(alpha * 255) & 0xFF) << 24;
      float[] radii = this.blurRadiiInput;
      radii[0] = borderRadius.topLeftRadius();
      radii[1] = borderRadius.bottomLeftRadius();
      radii[2] = borderRadius.topRightRadius();
      radii[3] = borderRadius.bottomRightRadius();
      this.drawBlurSquircle(x, y, w, h, blurRadius, radii, c, squirt);
   }

   public void blurSquircle(float x, float y, float w, float h, float blurRadius, float squirt, BorderRadius borderRadius) {
      this.blurSquircle(x, y, w, h, blurRadius, squirt, borderRadius, 1.0f);
   }

   public void blurSquircle(float x, float y, float w, float h, float blurRadius, float rounding, float squirt, float alpha) {
      this.blurSquircle(x, y, w, h, blurRadius, squirt, BorderRadius.all(rounding), alpha);
   }

   public void blurSquircle(float x, float y, float w, float h, float blurRadius, float rounding, float squirt) {
      this.blurSquircle(x, y, w, h, blurRadius, rounding, squirt, 1.0f);
   }

   public void blurSquircle(float x, float y, float w, float h, float blurRadius, float roundTL, float roundTR, float roundBR, float roundBL, float squirt, float alpha) {
      int c = ((int)(alpha * 255) & 0xFF) << 24;
      float[] radii = new float[]{
              roundTL,
              roundBL,
              roundTR,
              roundBR
      };
      this.drawBlurSquircle(x, y, w, h, blurRadius, radii, c, squirt);
   }

   private final float[] blurRadiiScratch = new float[4];
   private final float[] blurMatrixScratch = new float[9];
   private final float[] blurRadiiInput = new float[4];

   private void drawBlurSquircle(float x, float y, float w, float h, float blurRadius, float[] radii, int color, float squirt) {
      this.ensureFrame();
      this.batcher.flush();
      if (this.blurSquirclePipeline == null) {
         this.blurSquirclePipeline = new BlurSquirclePipeline();
      }
      int modulatedColor = this.modulateColor(color);
      float[] m = this.transformStack.current();
      boolean hasRotation = Math.abs(m[1]) > 1.0E-6F || Math.abs(m[3]) > 1.0E-6F;
      this.beginPipelineClip();
      try {
         if (hasRotation) {
            float[] gl = this.blurMatrixScratch;
            gl[0] = m[0]; gl[1] = m[3]; gl[2] = m[6];
            gl[3] = m[1]; gl[4] = m[4]; gl[5] = m[7];
            gl[6] = m[2]; gl[7] = m[5]; gl[8] = m[8];
            this.blurSquirclePipeline.drawBlurSquircle(x, y, w, h, blurRadius, radii, modulatedColor, squirt, gl, this.frameWidth, this.frameHeight);
         } else {
            float nx = m[0] * x + m[1] * y + m[2];
            float ny = m[3] * x + m[4] * y + m[5];
            float nw = m[0] * w;
            float nh = m[4] * h;
            float[] sr = this.blurRadiiScratch;
            sr[0] = radii[0] * m[0]; sr[1] = radii[1] * m[0]; sr[2] = radii[2] * m[0]; sr[3] = radii[3] * m[0];
            this.blurSquirclePipeline.drawBlurSquircle(nx, ny, nw, nh, blurRadius, sr, modulatedColor, squirt, IDENTITY, this.frameWidth, this.frameHeight);
         }
      } finally {
         this.endPipelineClip();
      }
   }

   public void drawCircle(float cx, float cy, float radius, int rgbaPremul) {
      this.circle(cx, cy, radius, 0.0F, 1.0F, rgbaPremul);
   }

   public void drawRectOutline(float x, float y, float w, float h, float thickness, int rgbaPremul) {
      this.rectOutline(x, y, w, h, rgbaPremul, thickness);
   }

   public void text(FontObject fo, String text, float x, float y, float size, int rgbaPremul) {
      this.text(fo, x, y, size, text, rgbaPremul);
   }

   public float measureTextHeight(FontObject fo, String text, float size) {
      return this.measureText(fo, text, size).height;
   }

   public void verticalGradient(
           float x, float y, float w, float h, float roundTopLeft, float roundTopRight, float roundBottomRight,
           float roundBottomLeft, int topColor, int bottomColor) {
      this.gradient(x, y, w, h, roundTopLeft, roundTopRight, roundBottomRight, roundBottomLeft, topColor, topColor,
              bottomColor, bottomColor);
   }

   public void circle(float cx, float cy, float radius, float startDeg, float pct, int rgbaPremul) {
      this.ensureFrame();
      this.batcher.enqueueCircle(cx, cy, radius, startDeg, pct, this.modulateColor(rgbaPremul),
              this.transformStack.current());
   }

   public void ring(float cx, float cy, float radius, float thickness, float startDeg, float pct, int fillColor, int bgColor, int innerColor) {
      this.circle(cx, cy, radius, 0.0F, 1.0F, bgColor);
      if (pct > 0.005F) {
         this.circle(cx, cy, radius, startDeg, pct, fillColor);
      }
      this.circle(cx, cy, radius - thickness, 0.0F, 1.0F, innerColor);
   }

   public void circleOutline(float cx, float cy, float radius, float startDeg, float pct, int rgbaPremul, float thickness) {
      this.ensureFrame();
      this.batcher.enqueueCircleOutline(cx, cy, radius, startDeg, pct, this.modulateColor(rgbaPremul), thickness,
              this.transformStack.current());
   }

   public void shadow(float x, float y, float w, float h, float rounding, float blurStrength, float spread,
                      int rgbaPremul) {
      this.shadow(x, y, w, h, rounding, rounding, rounding, rounding, blurStrength, spread, rgbaPremul);
   }

   public void shadow(
           float x,
           float y,
           float w,
           float h,
           float roundTopLeft,
           float roundTopRight,
           float roundBottomRight,
           float roundBottomLeft,
           float blurStrength,
           float spread,
           int rgbaPremul) {
      this.ensureFrame();
      if (!(w <= 0.0F) && !(h <= 0.0F)) {
         float safeBlur = Math.max(0.0F, blurStrength);
         float safeSpread = Math.max(0.0F, spread);
         if (!(safeBlur <= 0.0F) || !(safeSpread <= 0.0F)) {
            float[] radii = scratchRadii(roundTopLeft, roundTopRight, roundBottomRight, roundBottomLeft);
            normalizeCornerRadii(w, h, radii);
            this.backend
                    .drawDropShadowRect(
                            x, y, w, h, radii[0], radii[1], radii[2], radii[3], safeBlur, safeSpread,
                            this.modulateColor(rgbaPremul), this.transformStack.current());
         }
      }
   }

   public void steppedRect(float rightX, float topY, float[] widths, float[] heights,
                           int rowCount, float rounding, int color) {
      this.ensureFrame();
      if (rowCount <= 0) return;
      rowCount = Math.min(rowCount, 16);

      float maxW = 0, secondMaxW = 0;
      float totalH = 0;
      for (int i = 0; i < rowCount; i++) {
         float w = widths[i];
         if (w >= maxW) { secondMaxW = maxW; maxW = w; }
         else if (w > secondMaxW) { secondMaxW = w; }
         totalH += heights[i];
      }
      float targetW = rowCount <= 1 ? maxW : Math.max(secondMaxW, maxW * 0.65f);
      if (targetW < 0.5f || totalH < 0.5f) return;

      float x = rightX - targetW;
      int c = this.modulateColor(color);
      float[] transform = this.transformStack.current();

      this.batcher.enqueueRect(x, topY, targetW, totalH, rounding, rounding, rounding, rounding, c, transform);
   }

   public static void setupRender(boolean bloom) {
      if (bloom) {
         GlStateManager._enableBlend();
         GL11.glBlendFunc(770, 771);
         GlStateManager._disableCull();
         GlStateManager._blendFuncSeparate(770, 771, 1, 0);
         GlStateManager._colorMask(true, true, true, true);
      } else {
         GlStateManager._colorMask(true, true, true, true);
         GlStateManager._enableBlend();
      }
   }

   public static void endRender(boolean bloom) {
      if (bloom) {
         GlStateManager._colorMask(true, true, true, true);
         GlStateManager._blendFuncSeparate(770, 771, 1, 0);
         GlStateManager._enableCull();
         GlStateManager._disableBlend();
      } else {
         GlStateManager._colorMask(true, true, true, true);
         GlStateManager._enableBlend();
      }
   }

   public static void endBuilding(BufferBuilder bb) {
   }

   private static int clampToViewportFloor(float value, int viewportMax) {
      int floored = (int) Math.floor(value);
      if (floored < 0) {
         return 0;
      } else {
         return floored > viewportMax ? viewportMax : floored;
      }
   }

   private static int clampToViewportCeil(float value, int viewportMax) {
      int ceiled = (int) Math.ceil(value);
      if (ceiled < 0) {
         return 0;
      } else {
         return ceiled > viewportMax ? viewportMax : ceiled;
      }
   }

   private static Renderer2D.Bounds computeTransformedBounds(float[] matrix, float x, float y, float w, float h) {
      float x1 = x + w;
      float y1 = y + h;
      float wx0y0x = transformX(matrix, x, y);
      float wx0y0y = transformY(matrix, x, y);
      float wx1y0x = transformX(matrix, x1, y);
      float wx1y0y = transformY(matrix, x1, y);
      float wx1y1x = transformX(matrix, x1, y1);
      float wx1y1y = transformY(matrix, x1, y1);
      float wx0y1x = transformX(matrix, x, y1);
      float wx0y1y = transformY(matrix, x, y1);
      float minX = Math.min(Math.min(wx0y0x, wx1y0x), Math.min(wx1y1x, wx0y1x));
      float maxX = Math.max(Math.max(wx0y0x, wx1y0x), Math.max(wx1y1x, wx0y1x));
      float minY = Math.min(Math.min(wx0y0y, wx1y0y), Math.min(wx1y1y, wx0y1y));
      float maxY = Math.max(Math.max(wx0y0y, wx1y0y), Math.max(wx1y1y, wx0y1y));
      return new Renderer2D.Bounds(minX, minY, maxX, maxY);
   }

   private static float transformX(float[] matrix, float px, float py) {
      return matrix != null && matrix.length >= 6 ? matrix[0] * px + matrix[1] * py + matrix[2] : px;
   }

   private static float transformY(float[] matrix, float px, float py) {
      return matrix != null && matrix.length >= 6 ? matrix[3] * px + matrix[4] * py + matrix[5] : py;
   }

   public void setTransform(float[] m3) {
      this.ensureFrame();
      this.transformStack.clear();
      this.transformStack.replaceTop(m3);
   }

   public void pushRotation(float degrees) {
      this.ensureFrame();
      this.transformStack.pushRotation(degrees);
   }

   public void pushRotationAround(float degrees, float originX, float originY) {
      this.ensureFrame();
      this.transformStack.pushRotation(degrees, originX, originY);
   }

   public void popRotation() {
      this.ensureFrame();
      this.transformStack.pop();
   }

   public void pushTranslation(float tx, float ty) {
      this.ensureFrame();
      this.transformStack.pushTranslation(tx, ty);
   }

   public void popTransform() {
      this.ensureFrame();
      this.transformStack.pop();
   }

   public void pushScale(float scale) {
      this.pushScale(scale, scale);
   }

   public void pushScale(float sx, float sy) {
      this.ensureFrame();
      this.transformStack.pushScale(sx, sy, 0.0F, 0.0F);
   }

   public void pushScaleCentered(float scale) {
      this.pushScaleCentered(scale, scale);
   }

   public void pushScaleCentered(float sx, float sy) {
      this.ensureFrame();
      if (this.frameWidth > 0 && this.frameHeight > 0) {
         this.transformStack.pushScale(sx, sy, this.frameWidth * 0.5F, this.frameHeight * 0.5F);
      } else {
         throw new IllegalStateException(
                 "Cannot compute frame center before begin(width, height) is called with positive dimensions");
      }
   }

   public void pushScale(float scale, float originX, float originY) {
      this.pushScale(scale, scale, originX, originY);
   }

   public void pushScale(float sx, float sy, float originX, float originY) {
      this.ensureFrame();
      this.transformStack.pushScale(sx, sy, originX, originY);
   }

   public void popScale() {
      this.ensureFrame();
      this.transformStack.pop();
   }

   public void pushAlpha(float alpha) {
      this.ensureFrame();
      float parent = this.currentAlphaMultiplier();
      float clamped = clamp01(alpha);
      float combined = parent * clamped;
      this.alphaStack.push(combined);
      this.cachedAlpha = combined;
   }

   public void popAlpha() {
      this.ensureFrame();
      if (this.alphaStack.size() > 1) {
         this.alphaStack.pop();
      }

      Float top = this.alphaStack.peek();
      this.cachedAlpha = top == null ? 1.0F : top;
   }

   public void pushFontWeight(float weight) {
      this.ensureFrame();
      this.fontWeightStack.push(this.fontWeight);
      this.fontWeight = weight;
   }

   public void popFontWeight() {
      this.ensureFrame();
      if (!this.fontWeightStack.isEmpty()) {
         this.fontWeight = this.fontWeightStack.pop();
      } else {
         this.fontWeight = 0.0f;
      }
   }

   public float currentFontWeight() {
      return this.fontWeight;
   }

   public void registerTextRenderer(String fontId, TextRenderer tr) {
      if (tr != null) {
         this.idToTextRenderer.put(fontId, tr);
      }
   }

   public void registerTextRenderer(FontObject fo, TextRenderer tr) {
      if (tr != null) {
         this.idToTextRenderer.put(fo.id, tr);
      }
   }

   public TransformStack getTransformStack() {
      return this.transformStack;
   }

   public void text(FontObject fo, float x, float y, float size, String s, int rgbaPremul) {
      this.ensureFrame();
      if (fo == null) {
         throw new IllegalArgumentException("FontObject must not be null");
      } else if (!(size <= 0.0F)) {
         TextRenderer tr = this.idToTextRenderer.get(fo.id);
         if (tr != null) {
            tr.drawText(x, y, size / 2.0F, s, this.modulateColor(rgbaPremul), "l", this.transformStack.current(), 0f, this.fontWeight);
         }
      }
   }

   public float text(FontObject fo, float x, float y, float size, String s, int rgbaPremul, boolean animationSymbols) {
      if (!animationSymbols) {
         this.text(fo, x, y, size, s, rgbaPremul);
         return this.measureText(fo, s == null ? "" : s, size).width;
      }
      String key = "anim|" + fo.id + "|" + size + "|" + Math.round(x) + "|" + Math.round(y);
      return vesence.utils.render.text.AnimatedText.draw(this, fo, key, s, x, y, size, rgbaPremul,
            vesence.utils.render.text.AnimatedText.ALIGN_LEFT);
   }

   public float textCenter(FontObject fo, float x, float y, float size, String s, int rgbaPremul, boolean animationSymbols) {
      if (!animationSymbols) {
         this.textCenter(fo, x, y, size, s, rgbaPremul);
         return this.measureText(fo, s == null ? "" : s, size).width;
      }
      String key = "animC|" + fo.id + "|" + size + "|" + Math.round(x) + "|" + Math.round(y);
      return vesence.utils.render.text.AnimatedText.draw(this, fo, key, s, x, y, size, rgbaPremul,
            vesence.utils.render.text.AnimatedText.ALIGN_CENTER);
   }

   public float textRight(FontObject fo, float x, float y, float size, String s, int rgbaPremul, boolean animationSymbols) {
      if (!animationSymbols) {
         this.textRight(fo, x, y, size, s, rgbaPremul);
         return this.measureText(fo, s == null ? "" : s, size).width;
      }
      String key = "animR|" + fo.id + "|" + size + "|" + Math.round(x) + "|" + Math.round(y);
      return vesence.utils.render.text.AnimatedText.draw(this, fo, key, s, x, y, size, rgbaPremul,
            vesence.utils.render.text.AnimatedText.ALIGN_RIGHT);
   }

   public void textBlur(FontObject fo, float x, float y, float size, String s, int rgbaPremul, float blurRadius) {
      this.textBlur(fo, x, y, size, s, rgbaPremul, "l", 0.0F, blurRadius);
   }

   public void textBlur(FontObject fo, float x, float y, float size, String s, int rgbaPremul, float letterSpacing, float blurRadius) {
      this.textBlur(fo, x, y, size, s, rgbaPremul, "l", letterSpacing, blurRadius);
   }

   public void textBlur(FontObject fo, float x, float y, float size, String s, int rgbaPremul, String alignKey, float blurRadius) {
      this.textBlur(fo, x, y, size, s, rgbaPremul, alignKey, 0.0F, blurRadius);
   }

   public void textCenterBlur(FontObject fo, float x, float y, float size, String s, int rgbaPremul, float blurRadius) {
      this.textBlur(fo, x, y, size, s, rgbaPremul, "c", 0.0F, blurRadius);
   }

   public void textRightBlur(FontObject fo, float x, float y, float size, String s, int rgbaPremul, float blurRadius) {
      this.textBlur(fo, x, y, size, s, rgbaPremul, "r", 0.0F, blurRadius);
   }

   public void textBlur(FontObject fo, float x, float y, float size, String s, int rgbaPremul,
                        String alignKey, float letterSpacing, float blurRadius) {
      this.ensureFrame();
      if (fo == null) {
         throw new IllegalArgumentException("FontObject must not be null");
      }
      if (size <= 0.0F || s == null || s.isEmpty() || blurRadius <= 0.0F) {

         if (size > 0.0F && s != null && !s.isEmpty()) {
            this.text(fo, x, y, size, s, rgbaPremul, alignKey, letterSpacing);
         }
         return;
      }
      TextRenderer tr = this.idToTextRenderer.get(fo.id);
      if (tr == null) return;

      this.batcher.flush();
      if (this.textBlurPipeline == null) {
         this.textBlurPipeline = new TextBlurPipeline();
      }

      final int color = this.modulateColor(rgbaPremul);
      final int atlas = tr.fontAtlasTextureId();
      final float pxRange = tr.fontPxRange();
      final float atlasW = tr.fontAtlasWidth();
      final float atlasH = tr.fontAtlasHeight();
      final float[] m = this.transformStack.current();
      final float fw = this.frameWidth;
      final float fh = this.frameHeight;
      final float[] gl = new float[]{ m[0], m[3], m[6], m[1], m[4], m[7], m[2], m[5], m[8] };

      tr.forEachGlyphQuad(x, y, size / 2.0F, s, alignKey, letterSpacing, (gx, gy, gw, gh, u0, v0, u1, v1) ->
            this.textBlurPipeline.drawGlyph(atlas, pxRange, atlasW, atlasH, gx, gy, gw, gh, u0, v0, u1, v1,
                  blurRadius, color, gl, fw, fh));
   }

   public void textBlur(FontObject fo, float x, float y, float size, String s, int[] gradientColors,
                        float letterSpacing, float blurRadius) {
      this.ensureFrame();
      if (fo == null) {
         throw new IllegalArgumentException("FontObject must not be null");
      }
      if (gradientColors == null || gradientColors.length < 2) {
         int c = (gradientColors != null && gradientColors.length == 1) ? gradientColors[0] : 0xFFFFFFFF;
         this.textBlur(fo, x, y, size, s, c, "l", letterSpacing, blurRadius);
         return;
      }
      if (size <= 0.0F || s == null || s.isEmpty() || blurRadius <= 0.0F) {
         if (size > 0.0F && s != null && !s.isEmpty()) {
            this.text(fo, x, y, size, s, gradientColors, false, 3.0F, "l", letterSpacing);
         }
         return;
      }
      TextRenderer tr = this.idToTextRenderer.get(fo.id);
      if (tr == null) return;

      this.batcher.flush();
      if (this.textBlurPipeline == null) {
         this.textBlurPipeline = new TextBlurPipeline();
      }

      final int atlas = tr.fontAtlasTextureId();
      final float pxRange = tr.fontPxRange();
      final float atlasW = tr.fontAtlasWidth();
      final float atlasH = tr.fontAtlasHeight();
      final float[] m = this.transformStack.current();
      final float fw = this.frameWidth;
      final float fh = this.frameHeight;
      final float[] gl = new float[]{ m[0], m[3], m[6], m[1], m[4], m[7], m[2], m[5], m[8] };

      float measured = this.measureText(fo, s, size).width;
      final float textW = measured <= 0 ? 1f : measured;
      final float startX = x;
      final int[] grad = gradientColors;

      tr.forEachGlyphQuad(x, y, size / 2.0F, s, "l", letterSpacing, (gx, gy, gw, gh, u0, v0, u1, v1) -> {
         float tpos = Math.max(0f, Math.min(1f, (gx + gw * 0.5f - startX) / textW));
         int gc = this.modulateColor(sampleGradient(grad, tpos));
         this.textBlurPipeline.drawGlyph(atlas, pxRange, atlasW, atlasH, gx, gy, gw, gh, u0, v0, u1, v1,
               blurRadius, gc, gl, fw, fh);
      });
   }

   private static int sampleGradient(int[] colors, float t) {
      if (colors == null || colors.length == 0) return 0xFFFFFFFF;
      if (colors.length == 1) return colors[0];
      float scaled = t * (colors.length - 1);
      int idx = (int) Math.floor(scaled);
      if (idx >= colors.length - 1) return colors[colors.length - 1];
      float f = scaled - idx;
      int c0 = colors[idx], c1 = colors[idx + 1];
      int a = (int) (((c0 >>> 24) & 0xFF) + (((c1 >>> 24) & 0xFF) - ((c0 >>> 24) & 0xFF)) * f);
      int r = (int) (((c0 >> 16) & 0xFF) + (((c1 >> 16) & 0xFF) - ((c0 >> 16) & 0xFF)) * f);
      int g = (int) (((c0 >> 8) & 0xFF) + (((c1 >> 8) & 0xFF) - ((c0 >> 8) & 0xFF)) * f);
      int b = (int) ((c0 & 0xFF) + ((c1 & 0xFF) - (c0 & 0xFF)) * f);
      return (a << 24) | (r << 16) | (g << 8) | b;
   }

   public void text(FontObject fo, float x, float y, float size, String s, int rgbaPremul, float letterSpacing) {
      this.ensureFrame();
      if (fo == null) {
         throw new IllegalArgumentException("FontObject must not be null");
      } else if (!(size <= 0.0F)) {
         TextRenderer tr = this.idToTextRenderer.get(fo.id);
         if (tr != null) {
            tr.drawText(x, y, size / 2.0F, s, this.modulateColor(rgbaPremul), "l", this.transformStack.current(), letterSpacing, this.fontWeight);
         }
      }
   }

   public void textCenter(FontObject fo, float x, float y, float size, String s, int rgbaPremul) {
      this.text(fo, x, y, size, s, rgbaPremul, "c");
   }

   public void textCenter(FontObject fo, float x, float y, float size, String s, int rgbaPremul, float letterSpacing) {
      this.text(fo, x, y, size, s, rgbaPremul, "c", letterSpacing);
   }

   public void textRight(FontObject fo, float x, float y, float size, String s, int rgbaPremul) {
      this.text(fo, x, y, size, s, rgbaPremul, "r");
   }

   public void textRight(FontObject fo, float x, float y, float size, String s, int rgbaPremul, float letterSpacing) {
      this.text(fo, x, y, size, s, rgbaPremul, "r", letterSpacing);
   }

   public void textCenter(FontObject fo, float x, float y, float size, String s, int[] gradientColors) {
      this.text(fo, x, y, size, s, gradientColors, false, 3.0F, "c");
   }

   public void textCenter(FontObject fo, float x, float y, float size, String s, int[] gradientColors, float speed) {
      this.text(fo, x, y, size, s, gradientColors, true, speed, "c");
   }

   public void textRight(FontObject fo, float x, float y, float size, String s, int[] gradientColors) {
      this.text(fo, x, y, size, s, gradientColors, false, 3.0F, "r");
   }

   public void textRight(FontObject fo, float x, float y, float size, String s, int[] gradientColors, float speed) {
      this.text(fo, x, y, size, s, gradientColors, true, speed, "r");
   }

   public void text(FontObject fo, float x, float y, float size, String s, int rgbaPremul, String alignKey) {
      this.ensureFrame();
      if (fo == null) {
         throw new IllegalArgumentException("FontObject must not be null");
      } else if (!(size <= 0.0F)) {
         TextRenderer tr = this.idToTextRenderer.get(fo.id);
         if (tr != null) {
            tr.drawText(x, y, size / 2.0F, s, this.modulateColor(rgbaPremul), alignKey, this.transformStack.current(), 0f, this.fontWeight);
         }
      }
   }

   public void text(FontObject fo, float x, float y, float size, String s, int rgbaPremul, String alignKey, float letterSpacing) {
      this.ensureFrame();
      if (fo == null) {
         throw new IllegalArgumentException("FontObject must not be null");
      } else if (!(size <= 0.0F)) {
         TextRenderer tr = this.idToTextRenderer.get(fo.id);
         if (tr != null) {
            tr.drawText(x, y, size / 2.0F, s, this.modulateColor(rgbaPremul), alignKey, this.transformStack.current(), letterSpacing, this.fontWeight);
         }
      }
   }

   public void text(FontObject fo, float x, float y, float size, String s, int[] gradientColors) {
      this.text(fo, x, y, size, s, gradientColors, false, 3.0F, "l");
   }

   public void text(FontObject fo, float x, float y, float size, String s, int[] gradientColors, float speed) {
      this.text(fo, x, y, size, s, gradientColors, true, speed, "l");
   }

   public void text(FontObject fo, float x, float y, float size, String s, int[] gradientColors, String alignKey) {
      this.text(fo, x, y, size, s, gradientColors, false, 3.0F, alignKey);
   }

   public void text(FontObject fo, float x, float y, float size, String s, int[] gradientColors, boolean animated, float speed, String alignKey) {
      this.ensureFrame();
      if (fo == null) {
         throw new IllegalArgumentException("FontObject must not be null");
      } else if (size <= 0.0F) {
         return;
      }
      TextRenderer tr = this.idToTextRenderer.get(fo.id);
      if (tr != null) {
         if (gradientColors != null) {
            int[] modulated = new int[gradientColors.length];
            for (int i = 0; i < gradientColors.length; i++) {
               modulated[i] = this.modulateColor(gradientColors[i]);
            }
            tr.drawText(x, y, size / 2.0F, s, modulated, animated, speed, alignKey,
                    this.transformStack.current(), 0f, this.fontWeight);
         } else {
            tr.drawText(x, y, size / 2.0F, s, this.modulateColor(-1), alignKey,
                    this.transformStack.current(), 0f, this.fontWeight);
         }
      }
   }

   public void text(FontObject fo, float x, float y, float size, String s, int[] gradientColors, boolean animated, float speed, String alignKey, float letterSpacing) {
      this.ensureFrame();
      if (fo == null) {
         throw new IllegalArgumentException("FontObject must not be null");
      } else if (size <= 0.0F) {
         return;
      }
      TextRenderer tr = this.idToTextRenderer.get(fo.id);
      if (tr != null) {
         if (gradientColors != null) {
            int[] modulated = new int[gradientColors.length];
            for (int i = 0; i < gradientColors.length; i++) {
               modulated[i] = this.modulateColor(gradientColors[i]);
            }
            tr.drawText(x, y, size / 2.0F, s, modulated, animated, speed, alignKey,
                    this.transformStack.current(), letterSpacing, this.fontWeight);
         } else {
            tr.drawText(x, y, size / 2.0F, s, this.modulateColor(-1), alignKey,
                    this.transformStack.current(), letterSpacing, this.fontWeight);
         }
      }
   }

   public void text(FontObject fo, float x, float y, float size, String s, int[] gradientColors, boolean animated, float speed, float letterSpacing) {
      this.text(fo, x, y, size, s, gradientColors, animated, speed, "l", letterSpacing);
   }

   public void text(FontObject fo, float x, float y, float size, String s, int[] gradientColors, float speed, float letterSpacing) {
      this.text(fo, x, y, size, s, gradientColors, false, speed, "l", letterSpacing);
   }

   public TextRenderer.TextMetrics measureText(FontObject fo, String text, float size) {
      if (fo == null) {
         throw new IllegalArgumentException("FontObject must not be null");
      } else if (size <= 0.0F) {
         return new TextRenderer.TextMetrics(0.0F, 0.0F);
      } else {
         String content = text == null ? "" : text;
         TextRenderer tr = this.idToTextRenderer.get(fo.id);
         if (tr == null) {
            return new TextRenderer.TextMetrics(0.0F, 0.0F);
         } else {
            return tr.measureText(content, size / 2.0F);
         }
      }
   }

   public TextRenderer.TextMetrics measureText(FontObject fo, String text, float size, float letterSpacing) {
      if (fo == null) {
         throw new IllegalArgumentException("FontObject must not be null");
      } else if (size <= 0.0F) {
         return new TextRenderer.TextMetrics(0.0F, 0.0F);
      } else {
         String content = text == null ? "" : text;
         TextRenderer tr = this.idToTextRenderer.get(fo.id);
         if (tr == null) {
            return new TextRenderer.TextMetrics(0.0F, 0.0F);
         } else {
            return tr.measureText(content, size / 2.0F, letterSpacing);
         }
      }
   }

   private void resetAlphaStack() {
      this.alphaStack.clear();
      this.alphaStack.push(1.0F);
      this.cachedAlpha = 1.0F;
   }

   private float currentAlphaMultiplier() {

      return this.cachedAlpha;
   }

   private int modulateColor(int rgbaPremul) {
      float factor = this.currentAlphaMultiplier();
      if (factor >= 0.999F) {
         return rgbaPremul;
      } else {
         int a = rgbaPremul >>> 24 & 0xFF;
         int r = rgbaPremul >>> 16 & 0xFF;
         int g = rgbaPremul >>> 8 & 0xFF;
         int b = rgbaPremul & 0xFF;
         int na = scaleChannel(a, factor);
         int nr = scaleChannel(r, factor);
         int ng = scaleChannel(g, factor);
         int nb = scaleChannel(b, factor);
         return na << 24 | nr << 16 | ng << 8 | nb;
      }
   }

   private static int scaleChannel(int value, float factor) {
      float scaled = value * factor;
      if (scaled <= 0.0F) {
         return 0;
      } else {
         return scaled >= 255.0F ? 255 : Math.round(scaled);
      }
   }

   private static float clamp01(float value) {
      if (value < 0.0F) {
         return 0.0F;
      } else {
         return value > 1.0F ? 1.0F : value;
      }
   }

   private static final ThreadLocal<float[]> SCRATCH_RADII = ThreadLocal.withInitial(() -> new float[4]);

   private static float[] scratchRadii(float topLeft, float topRight, float bottomRight, float bottomLeft) {
      float[] r = SCRATCH_RADII.get();
      r[0] = topLeft;
      r[1] = topRight;
      r[2] = bottomRight;
      r[3] = bottomLeft;
      return r;
   }

   private static void normalizeCornerRadii(float w, float h, float[] radii) {
      if (radii != null && radii.length >= 4) {
         float absW = Math.abs(w);
         float absH = Math.abs(h);

         for (int i = 0; i < 4; i++) {
            float value = radii[i];
            if (!Float.isFinite(value)) {
               value = 0.0F;
            }

            radii[i] = Math.max(0.0F, value);
         }

         if (!(absW <= 0.0F) && !(absH <= 0.0F)) {
            float maxR = Math.min(absW, absH) * 0.5F;

            for (int i = 0; i < 4; i++) {
               radii[i] = Math.min(radii[i], maxR);
            }
         } else {
            Arrays.fill(radii, 0.0F);
         }
      } else {
         throw new IllegalArgumentException("radii");
      }
   }

   private static boolean nearlyEqual(float a, float b) {
      return Math.abs(a - b) <= 1.0E-4F;
   }

   private static boolean nearlyZero(float value) {
      return Math.abs(value) <= 1.0E-4F;
   }

   private static boolean isIdentityTransform(float[] matrix) {
      return matrix != null && matrix.length >= 9
              ? nearlyEqual(matrix[0], 1.0F)
                && nearlyZero(matrix[1])
                && nearlyZero(matrix[2])
                && nearlyZero(matrix[3])
                && nearlyEqual(matrix[4], 1.0F)
                && nearlyZero(matrix[5])
                && nearlyZero(matrix[6])
                && nearlyZero(matrix[7])
                && nearlyEqual(matrix[8], 1.0F)
              : true;
   }

   private static boolean isAxisAlignedTransform(float[] matrix) {
      return matrix != null && matrix.length >= 9
              ? nearlyZero(matrix[1]) && nearlyZero(matrix[3]) && nearlyZero(matrix[6]) && nearlyZero(matrix[7])
                && nearlyEqual(matrix[8], 1.0F)
              : true;
   }

   private static float transformPointX(float[] matrix, float x, float y) {
      return matrix != null && matrix.length >= 9 ? matrix[0] * x + matrix[1] * y + matrix[2] : x;
   }

   private static float transformPointY(float[] matrix, float x, float y) {
      return matrix != null && matrix.length >= 9 ? matrix[3] * x + matrix[4] * y + matrix[5] : y;
   }

   private static float computeRadiusScale(float[] matrix) {
      if (matrix != null && matrix.length >= 9) {
         float scaleX = Math.abs(matrix[0]);
         float scaleY = Math.abs(matrix[4]);
         float minScale = Math.min(scaleX, scaleY);
         return minScale <= 1.0E-4F ? 0.0F : minScale;
      } else {
         return 1.0F;
      }
   }

   public static void setupOrientationMatrix(MatrixStack matrix, float x, float y, float z) {
      setupOrientationMatrix(matrix, (double) x, (double) y, (double) z);
   }

   public static void setupOrientationMatrix(MatrixStack matrix, double x, double y, double z) {
      Camera camera = mc.getEntityRenderDispatcher().camera;
      Vec3d renderPos = camera.getCameraPos();
      matrix.translate(x - renderPos.x, y - renderPos.y, z - renderPos.z);
   }

   private static java.lang.reflect.Method cachedGetFovMethod;
   private static boolean getFovMethodResolved = false;

   public static Vector2d project2D(double x, double y, double z) {
      Camera camera = mc.getEntityRenderDispatcher().camera;
      if (camera == null) {
         return new Vector2d(0.0, 0.0);
      } else {
         Vec3d cameraPosition = camera.getCameraPos();
         Quaternionf cameraRotation = new Quaternionf(camera.getRotation());
         cameraRotation.conjugate();
         Vector3f relativePosition = new Vector3f(
                 (float) (cameraPosition.x - x), (float) (cameraPosition.y - y), (float) (cameraPosition.z - z));
         relativePosition.rotate(cameraRotation);
         float tickDelta = mc.getRenderTickCounter().getDynamicDeltaTicks();
         if ((Boolean) mc.options.getBobView().getValue()
                 && mc.getCameraEntity() instanceof PlayerEntity playerEntity) {
            float walkedDistance = 0.0f;
            float deltaDistance = 0.0f;
            float interpolatedDistance = -(walkedDistance + deltaDistance * tickDelta);
            float cameraYaw = camera.getYaw();
            float bobAngleX = Math.abs((float) Math.cos(interpolatedDistance * (float) Math.PI - 0.2F) * cameraYaw)
                    * 5.0F;
            Quaternionf bobQuaternionX = new Quaternionf().rotateAxis((float) Math.toRadians(bobAngleX),
                    new Vector3f(1.0F, 0.0F, 0.0F));
            bobQuaternionX.conjugate();
            relativePosition.rotate(bobQuaternionX);
            float bobAngleZ = (float) Math.sin(interpolatedDistance * (float) Math.PI) * cameraYaw * 3.0F;
            Quaternionf bobQuaternionZ = new Quaternionf().rotateAxis((float) Math.toRadians(bobAngleZ),
                    new Vector3f(0.0F, 0.0F, 1.0F));
            bobQuaternionZ.conjugate();
            relativePosition.rotate(bobQuaternionZ);
            Vector3f bobTranslation = new Vector3f(
                    (float) Math.sin(interpolatedDistance * (float) Math.PI) * cameraYaw * 0.5F,
                    -Math.abs((float) Math.cos(interpolatedDistance * (float) Math.PI) * cameraYaw),
                    0.0F);
            bobTranslation.y = -bobTranslation.y;
            relativePosition.add(bobTranslation);
         }

         double fieldOfView = 70.0;

         try {
            if (!getFovMethodResolved) {
               try {
                  cachedGetFovMethod = GameRenderer.class.getDeclaredMethod("getFov", Camera.class, float.class, boolean.class);
                  cachedGetFovMethod.setAccessible(true);
               } catch (NoSuchMethodException ignored) {}
               getFovMethodResolved = true;
            }
            if (cachedGetFovMethod != null) {
               fieldOfView = (Double) cachedGetFovMethod.invoke(mc.gameRenderer, camera, tickDelta, true);
            } else {
               fieldOfView = (double) ((Integer) mc.options.getFov().getValue()).intValue();
            }
         } catch (Exception var22) {
            double fovSetting = (double) ((Integer) mc.options.getFov().getValue()).intValue();
            fieldOfView = fovSetting;
         }

         float halfHeight = mc.getWindow().getScaledHeight() / 2.0F;
         float scaleFactor = halfHeight / (relativePosition.z() * (float) Math.tan(Math.toRadians(fieldOfView / 2.0)));
         return relativePosition.z() < 0.0F
                 ? new Vector2d(
                 -relativePosition.x() * scaleFactor + mc.getWindow().getScaledWidth() / 2,
                 mc.getWindow().getScaledHeight() / 2 - relativePosition.y() * scaleFactor)
                 : null;
      }
   }

   @Environment(EnvType.CLIENT)
   private record Bounds(float minX, float minY, float maxX, float maxY) {
   }

   @Environment(EnvType.CLIENT)
   private record ClipState(int x, int y, int w, int h, float roundTopLeft, float roundTopRight, float roundBottomRight,
                            float roundBottomLeft) {
      private static Renderer2D.ClipState fromRect(
              float x, float y, float w, float h, float roundTopLeft, float roundTopRight, float roundBottomRight,
              float roundBottomLeft) {
         return fromRect(x, y, w, h, roundTopLeft, roundTopRight, roundBottomRight, roundBottomLeft, null);
      }

      private static Renderer2D.ClipState fromRect(
              float x, float y, float w, float h, float roundTopLeft, float roundTopRight, float roundBottomRight,
              float roundBottomLeft, float[] transform) {
         if (Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(w) && Float.isFinite(h)) {
            boolean hasTransform = transform != null && transform.length >= 9
                    && !Renderer2D.isIdentityTransform(transform);
            float[] radii = Renderer2D.scratchRadii(roundTopLeft, roundTopRight, roundBottomRight, roundBottomLeft);
            Renderer2D.normalizeCornerRadii(Math.abs(w), Math.abs(h), radii);
            if (!hasTransform) {
               float left = (float) Math.floor(Math.min(x, x + w));
               float top = (float) Math.floor(Math.min(y, y + h));
               float right = (float) Math.ceil(Math.max(x, x + w));
               float bottom = (float) Math.ceil(Math.max(y, y + h));
               int ix = (int) left;
               int iy = (int) top;
               int iw = Math.max(0, (int) (right - left));
               int ih = Math.max(0, (int) (bottom - top));
               return iw > 0 && ih > 0
                       ? new Renderer2D.ClipState(ix, iy, iw, ih, radii[0], radii[1], radii[2], radii[3])
                       : new Renderer2D.ClipState(ix, iy, 0, 0, 0.0F, 0.0F, 0.0F, 0.0F);
            } else {
               float x2 = x + w;
               float y2 = y + h;
               float[] xs = new float[] { x, x2, x, x2 };
               float[] ys = new float[] { y, y, y2, y2 };
               float minX = Float.POSITIVE_INFINITY;
               float minY = Float.POSITIVE_INFINITY;
               float maxX = Float.NEGATIVE_INFINITY;
               float maxY = Float.NEGATIVE_INFINITY;

               for (int i = 0; i < 4; i++) {
                  float tx = Renderer2D.transformPointX(transform, xs[i], ys[i]);
                  float ty = Renderer2D.transformPointY(transform, xs[i], ys[i]);
                  if (!Float.isFinite(tx) || !Float.isFinite(ty)) {
                     return new Renderer2D.ClipState(0, 0, 0, 0, 0.0F, 0.0F, 0.0F, 0.0F);
                  }

                  if (tx < minX) {
                     minX = tx;
                  }

                  if (tx > maxX) {
                     maxX = tx;
                  }

                  if (ty < minY) {
                     minY = ty;
                  }

                  if (ty > maxY) {
                     maxY = ty;
                  }
               }

               float left = (float) Math.floor(Math.min(minX, maxX));
               float top = (float) Math.floor(Math.min(minY, maxY));
               float right = (float) Math.ceil(Math.max(minX, maxX));
               float bottom = (float) Math.ceil(Math.max(minY, maxY));
               int ix = (int) left;
               int iy = (int) top;
               int iw = Math.max(0, (int) (right - left));
               int ih = Math.max(0, (int) (bottom - top));
               if (iw > 0 && ih > 0) {
                  if (Renderer2D.isAxisAlignedTransform(transform)) {
                     float radiusScale = Renderer2D.computeRadiusScale(transform);
                     if (radiusScale > 0.0F) {
                        for (int i = 0; i < radii.length; i++) {
                           radii[i] *= radiusScale;
                        }
                     } else {
                        Arrays.fill(radii, 0.0F);
                     }
                  } else {
                     Arrays.fill(radii, 0.0F);
                  }

                  Renderer2D.normalizeCornerRadii(Math.abs(right - left), Math.abs(bottom - top), radii);
                  return new Renderer2D.ClipState(ix, iy, iw, ih, radii[0], radii[1], radii[2], radii[3]);
               } else {
                  return new Renderer2D.ClipState(ix, iy, 0, 0, 0.0F, 0.0F, 0.0F, 0.0F);
               }
            }
         } else {
            return new Renderer2D.ClipState(0, 0, 0, 0, 0.0F, 0.0F, 0.0F, 0.0F);
         }
      }

      private static Renderer2D.ClipState intersect(Renderer2D.ClipState a, Renderer2D.ClipState b) {
         if (a == null) {
            return b;
         } else if (b == null) {
            return a;
         } else {
            int nx = Math.max(a.x, b.x);
            int ny = Math.max(a.y, b.y);
            int nr = Math.min(a.x + a.w, b.x + b.w);
            int nb = Math.min(a.y + a.h, b.y + b.h);
            int nw = Math.max(0, nr - nx);
            int nh = Math.max(0, nb - ny);
            if (nw <= 0 || nh <= 0) {
               return new Renderer2D.ClipState(nx, ny, 0, 0, 0.0F, 0.0F, 0.0F, 0.0F);
            } else if (matchesRect(nx, ny, nw, nh, b)) {
               return new Renderer2D.ClipState(nx, ny, nw, nh, b.roundTopLeft, b.roundTopRight, b.roundBottomRight,
                       b.roundBottomLeft);
            } else {
               return matchesRect(nx, ny, nw, nh, a)
                       ? new Renderer2D.ClipState(nx, ny, nw, nh, a.roundTopLeft, a.roundTopRight, a.roundBottomRight,
                       a.roundBottomLeft)
                       : new Renderer2D.ClipState(nx, ny, nw, nh, 0.0F, 0.0F, 0.0F, 0.0F);
            }
         }
      }

      private static boolean matchesRect(int x, int y, int w, int h, Renderer2D.ClipState other) {
         return other != null && other.x == x && other.y == y && other.w == w && other.h == h;
      }
   }

   @Environment(EnvType.CLIENT)
   public static class ColorUtil {
      public static float getRedf(int color) {
         return ColorUtility.redf(color);
      }

      public static float getGreenf(int color) {
         return ColorUtility.greenf(color);
      }

      public static float getBluef(int color) {
         return ColorUtility.bluef(color);
      }

      public static float getAlphaf(int color) {
         return ColorUtility.alphaf(color);
      }

      public static Color injectAlpha(Color color, int alpha) {
         return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
      }

      public static Color TwoColoreffect(Color color, Color color2, double n) {
         float clamp = MathHelper.clamp((float) Math.sin((Math.PI * 6) * (n / 4.0 % 1.0)) / 2.0F + 0.5F, 0.0F, 1.0F);
         return new Color(
                 MathHelper.lerp(color.getRed() / 255.0F, color2.getRed() / 255.0F, clamp),
                 MathHelper.lerp(color.getGreen() / 255.0F, color2.getGreen() / 255.0F, clamp),
                 MathHelper.lerp(color.getBlue() / 255.0F, color2.getBlue() / 255.0F, clamp),
                 MathHelper.lerp(color.getAlpha() / 255.0F, color2.getAlpha() / 255.0F, clamp));
      }

      public static Color setAlpha(Color c, int alpha) {
         return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
      }

      public static int setAlpha(int color, int alpha) {
         return color & 16777215 | alpha << 24;
      }

      public static int getClientColor() {
         return getClientColor1();
      }

      public static int getClientColor1() {
         return getMainColor(10, 255);
      }

      public static int getClientColor2() {
         return getMainColor2(10, 255);
      }

      public static int getClientColorMain() {
         long time = System.currentTimeMillis();
         double angle = (time / 10) % 360;
         float ratio = (float) Math.abs(Math.sin(Math.toRadians(angle)));

         int color1 = getClientColor1();
         int color2 = getClientColor2();

         return interpolateColor(color1, color2, ratio);
      }

      private static Theme getTheme() {
         return vesence.module.impl.visuals.ThemeModule.getCurrentTheme();
      }

      // ---- Кастомный цвет клиента (вкладка Client → «Цвет и тема клиента») ----
      // 0 = Пресет (тема), 1 = Собственная (один цвет), 2 = Собственная (2) (перелив двух цветов).
      private static int customMode() {
         try {
            var m = vesence.module.impl.misc.ClickGui.colorMode;
            if (m == null) return 0;
            if (m.is("Двух-Цветный")) return 2;
            return 0;
         } catch (Throwable t) {
            return 0;
         }
      }

      private static int customC1RGB() {
         return vesence.module.impl.misc.ClickGui.customColor1.getRGB() | 0xFF000000;
      }

      private static int customC2RGB() {
         return vesence.module.impl.misc.ClickGui.customColor2.getRGB() | 0xFF000000;
      }

      // Плавный «перелив» между двумя цветами по синусоиде.
      private static int customBlend() {
         long time = System.currentTimeMillis();
         float ratio = (float) Math.abs(Math.sin(Math.toRadians((time / 10.0) % 360.0)));
         return interpolateColor(customC1RGB(), customC2RGB(), ratio);
      }

      /** true, если активен режим «Собственная (2)» (двухцветная тема). */
      public static boolean isDualColor() {
         return customMode() == 2;
      }

      private static Theme getPreTheme() {
         return vesence.module.impl.visuals.ThemeModule.getPreviousTheme();
      }

      public static int[] getClientColor(int speed, int alpha) {
         Theme theme = getTheme();
         Theme preTheme = getPreTheme();
         float transition = vesence.module.impl.visuals.ThemeModule.getThemeTransition();
         return new int[] {
                 applyOpacity(
                         gradient(speed, 0,
                                 interpolate(theme.getMain().getRGB(), preTheme.getMain().getRGB(),
                                         1.0F - transition)),
                         alpha),
                 applyOpacity(
                         gradient(speed, 90,
                                 interpolate(theme.getMain().getRGB(), preTheme.getMain().getRGB(),
                                         1.0F - transition)),
                         alpha),
                 applyOpacity(
                         gradient(speed, 180,
                                 interpolate(theme.getMain().getRGB(), preTheme.getMain().getRGB(),
                                         1.0F - transition)),
                         alpha),
                 applyOpacity(
                         gradient(speed, 270,
                                 interpolate(theme.getMain().getRGB(), preTheme.getMain().getRGB(),
                                         1.0F - transition)),
                         alpha)
         };
      }

      public static int getBackGroundColor(int speed, int index) {
         Theme theme = getTheme();
         Theme preTheme = getPreTheme();
         float transition = vesence.module.impl.visuals.ThemeModule.getThemeTransition();
         return gradient2(
                 interpolate(theme.getBg().getRGB(), preTheme.getBg().getRGB(), 1.0F - transition),
                 interpolate(theme.getBg().getRGB(), preTheme.getBg().getRGB(), 1.0F - transition),
                 speed,
                 index);
      }

      public static int getBackGroundTwoColor(int speed, int index) {
         Theme theme = getTheme();
         Theme preTheme = getPreTheme();
         float transition = vesence.module.impl.visuals.ThemeModule.getThemeTransition();
         return gradient2(
                 interpolate(theme.getBg2().getRGB(), preTheme.getBg2().getRGB(),
                         1.0F - transition),
                 interpolate(theme.getBg2().getRGB(), preTheme.getBg2().getRGB(),
                         1.0F - transition),
                 speed,
                 index);
      }

      public static int getOutLineColor(int speed, int index) {
         Theme theme = getTheme();
         Theme preTheme = getPreTheme();
         float transition = vesence.module.impl.visuals.ThemeModule.getThemeTransition();
         return gradient2(
                 interpolate(theme.getOutline().getRGB(), preTheme.getOutline().getRGB(),
                         1.0F - transition),
                 interpolate(theme.getOutline().getRGB(), preTheme.getOutline().getRGB(),
                         1.0F - transition),
                 speed,
                 index);
      }

      public static int getMainColor(int speed, int index) {
         int cm = customMode();
         if (cm == 2) return applyOpacity(customC1RGB(), index);
         Theme theme = getTheme();
         Theme preTheme = getPreTheme();
         float transition = vesence.module.impl.visuals.ThemeModule.getThemeTransition();
         return gradient2(
                 interpolate(theme.getMain().getRGB(), preTheme.getMain().getRGB(),
                         1.0F - transition),
                 interpolate(theme.getMain().getRGB(), preTheme.getMain().getRGB(),
                         1.0F - transition),
                 speed,
                 index);
      }

      public static int getMainColor2(int speed, int index) {
         int cm = customMode();
         if (cm == 2) return applyOpacity(customC2RGB(), index);
         Theme theme = getTheme();
         Theme preTheme = getPreTheme();
         float transition = vesence.module.impl.visuals.ThemeModule.getThemeTransition();
         return gradient2(
                 interpolate(theme.getMain2().getRGB(), preTheme.getMain2().getRGB(),
                         1.0F - transition),
                 interpolate(theme.getMain2().getRGB(), preTheme.getMain2().getRGB(),
                         1.0F - transition),
                 speed,
                 index);
      }

      public static int getTextColor(int speed, int index) {
         Theme theme = getTheme();
         Theme preTheme = getPreTheme();
         float transition = vesence.module.impl.visuals.ThemeModule.getThemeTransition();
         return gradient2(
                 interpolate(theme.getText().getRGB(), preTheme.getText().getRGB(),
                         1.0F - transition),
                 interpolate(theme.getText().getRGB(), preTheme.getText().getRGB(),
                         1.0F - transition),
                 speed,
                 index);
      }

      public static int getTextTwoColor(int speed, int index) {
         Theme theme = getTheme();
         Theme preTheme = getPreTheme();
         float transition = vesence.module.impl.visuals.ThemeModule.getThemeTransition();
         return gradient2(
                 interpolate(theme.getText2().getRGB(), preTheme.getText2().getRGB(),
                         1.0F - transition),
                 interpolate(theme.getText2().getRGB(), preTheme.getText2().getRGB(),
                         1.0F - transition),
                 speed,
                 index);
      }

      public Color interpolate(Color color1, Color color2, double amount) {
         amount = 1.0 - amount;
         amount = (float) MathHelper.clamp(amount, 0.0, 1.0);
         return new Color(
                 Interpolator.lerp(color1.getRed(), color2.getRed(), amount),
                 Interpolator.lerp(color1.getGreen(), color2.getGreen(), amount),
                 Interpolator.lerp(color1.getBlue(), color2.getBlue(), amount),
                 Interpolator.lerp(color1.getAlpha(), color2.getAlpha(), amount));
      }

      public static Color interpolateTwoColors(int speed, int index, Color start, Color end, boolean trueColor) {
         int angle = 0;
         if (speed == 0) {
            angle = index % 360;
         } else {
            angle = (int) ((System.currentTimeMillis() / speed + index) % 360L);
         }

         angle = (angle >= 180 ? 360 - angle : angle) * 2;
         return trueColor ? interpolateColorHue(start, end, angle / 360.0F)
                 : interpolateColorC(start, end, angle / 360.0F);
      }

      public static Color interpolateColorHue(Color color1, Color color2, float amount) {
         amount = Math.min(1.0F, Math.max(0.0F, amount));
         float[] color1HSB = Color.RGBtoHSB(color1.getRed(), color1.getGreen(), color1.getBlue(), null);
         float[] color2HSB = Color.RGBtoHSB(color2.getRed(), color2.getGreen(), color2.getBlue(), null);
         Color resultColor = Color.getHSBColor(
                 MathHelper.lerp(color1HSB[0], color2HSB[0], amount),
                 MathHelper.lerp(color1HSB[1], color2HSB[1], amount),
                 MathHelper.lerp(color1HSB[2], color2HSB[2], amount));
         return new Color(
                 resultColor.getRed(),
                 resultColor.getGreen(),
                 resultColor.getBlue(),
                 (int) MathHelper.lerp((float) color1.getAlpha(), (float) color2.getAlpha(), amount));
      }

      public static Color interpolateColorC(Color color1, Color color2, float amount) {
         return new Color(
                 MathHelper.lerp((float) color1.getRed(), (float) color2.getRed(), amount),
                 MathHelper.lerp((float) color1.getGreen(), (float) color2.getGreen(), amount),
                 MathHelper.lerp((float) color1.getBlue(), (float) color2.getBlue(), amount),
                 MathHelper.lerp((float) color1.getAlpha(), (float) color2.getAlpha(), amount));
      }

      public static int gradient2(int color1, int color2, int speed, int index) {
         Color col1 = new Color(color1);
         Color col2 = new Color(color2);
         double angle = (System.currentTimeMillis() / speed + index) % 360L;
         double var13;
         float ratio = (float) ((var13 = angle % 360.0) / 360.0);
         int red = (int) (col1.getRed() * (1.0F - ratio) + col2.getRed() * ratio);
         int green = (int) (col1.getGreen() * (1.0F - ratio) + col2.getGreen() * ratio);
         int blue = (int) (col1.getBlue() * (1.0F - ratio) + col2.getBlue() * ratio);
         Color interpolatedColor = new Color(red, green, blue);
         return interpolatedColor.getRGB();
      }

      public static int interpolate(int color1, int color2, double amount) {
         amount = (float) MathHelper.clamp(amount, 0.0, 1.0);
         return getColor(
                 Interpolator.lerp(red(color1), red(color2), amount),
                 Interpolator.lerp(green(color1), green(color2), amount),
                 Interpolator.lerp(blue(color1), blue(color2), amount),
                 Interpolator.lerp(alpha(color1), alpha(color2), amount));
      }

      public static int[] getRainbowColor(int speed) {
         int[] color1 = new int[4];
         if (speed == 0) {
            speed = 1;
         }

         color1[0] = rainbow(speed, 1, 1.0F, 1.0F, 1.0F);
         color1[1] = rainbow(speed, 90, 1.0F, 1.0F, 1.0F);
         color1[2] = rainbow(speed, 180, 1.0F, 1.0F, 1.0F);
         color1[3] = rainbow(speed, 270, 1.0F, 1.0F, 1.0F);
         return color1;
      }

      public static int rainbow(int speed, int index, float saturation, float brightness, float opacity) {
         int angle = (int) ((System.currentTimeMillis() / speed + index) % 360L);
         float hue = angle / 360.0F;
         int color = Color.HSBtoRGB(hue, saturation, brightness);
         return getColor(red(color), green(color), blue(color), Math.max(0, Math.min(255, (int) (opacity * 255.0F))));
      }

      public static int gradient(int speed, int index, int... colors) {
         int angle = (int) ((System.currentTimeMillis() / speed + index) % 360L);
         angle = (angle > 180 ? 360 - angle : angle) + 180;
         int colorIndex = (int) (angle / 360.0F * colors.length);
         if (colorIndex == colors.length) {
            colorIndex--;
         }

         int color1 = colors[colorIndex];
         int color2 = colors[colorIndex == colors.length - 1 ? 0 : colorIndex + 1];
         return interpolateColor(color1, color2, angle / 360.0F * colors.length - colorIndex);
      }

      public static final int PURPLE = rgba(157, 121, 255, 255);
      public static final int PINK = rgba(255, 121, 230, 255);
      public static final int TRANSPARENT_WHITE = rgba(255, 255, 255, 100);
      public static final int TRANSPARENT_BLACK = rgba(0, 0, 0, 100);

      public static int interpolateColor(int color1, int color2, double offset) {
         float[] rgba1 = getRGBAf(color1);
         float[] rgba2 = getRGBAf(color2);
         double r = rgba1[0] + (rgba2[0] - rgba1[0]) * offset;
         double g = rgba1[1] + (rgba2[1] - rgba1[1]) * offset;
         double b = rgba1[2] + (rgba2[2] - rgba1[2]) * offset;
         double a = rgba1[3] + (rgba2[3] - rgba1[3]) * offset;
         return rgba((int) (r * 255.0), (int) (g * 255.0), (int) (b * 255.0), (int) (a * 255.0));
      }

      public static float[] getRGBAf(int c) {
         return new float[] { red(c) / 255.0F, green(c) / 255.0F, blue(c) / 255.0F, alpha(c) / 255.0F };
      }

      public static int skyRainbow(int speed, int index) {
         double angle = (int) ((System.currentTimeMillis() / speed + index) % 360L);
         double var4;
         return Color
                 .getHSBColor((var4 = angle % 360.0) / 360.0 < 0.5 ? -((float) (var4 / 360.0)) : (float) (var4 / 360.0),
                         0.5F, 1.0F)
                 .hashCode();
      }

      public static int[] getAstolfoColor(int speed) {
         int[] color1 = new int[4];
         if (speed == 0) {
            int var2 = 1;
         }

         color1[0] = skyRainbow(25, 1);
         color1[1] = skyRainbow(25, 90);
         color1[2] = skyRainbow(25, 180);
         color1[3] = skyRainbow(25, 270);
         return color1;
      }

      public static int applyOpacity(int n, float f) {
         return rgba2(getRedInt(n), getGreenInt(n), getBlueInt(n), (int) (getAlphaInt(n) * f / 255.0F));
      }

      public static int rgba2(int n, int n2, int n3, int n4) {
         return n4 << 24 | n << 16 | n2 << 8 | n3;
      }

      public static int getRedInt(int n) {
         return n >> 16 & 0xFF;
      }

      public static int getGreenInt(int n) {
         return n >> 8 & 0xFF;
      }

      public static int getBlueInt(int n) {
         return n & 0xFF;
      }

      public static int getAlphaInt(int n) {
         return n >> 24 & 0xFF;
      }

      public static float[] getColorComps(Color color) {
         return new float[] { color.getRed() / 255.0F, color.getGreen() / 255.0F, color.getBlue() / 255.0F,
                 color.getAlpha() / 255.0F };
      }

      public static int getClientColorOne(int speed, int index) {
         Theme theme = getTheme();
         Theme preTheme = getPreTheme();
         float transition = vesence.module.impl.visuals.ThemeModule.getThemeTransition();
         return gradient(
                 interpolate(theme.getMain().getRGB(), preTheme.getMain().getRGB(),
                         1.0F - transition),
                 interpolate(theme.getMain().getRGB(), preTheme.getMain().getRGB(),
                         1.0F - transition),
                 speed,
                 index);
      }

      public static int swapAlpha(int color, float alpha) {
         int f = color >> 16 & 0xFF;
         int f1 = color >> 8 & 0xFF;
         int f2 = color & 0xFF;
         return getColor(f, f1, f2, (int) alpha);
      }

      public static Color getColor(int color) {
         int r = color >> 16 & 0xFF;
         int g = color >> 8 & 0xFF;
         int b = color & 0xFF;
         int a = color >> 24 & 0xFF;
         return new Color(r, g, b, a);
      }

      public static int replAlpha(int c, int a) {
         return getColor(red(c), green(c), blue(c), a);
      }

      public static int multDark(int c, float brpc) {
         return getColor(red(c) * brpc, green(c) * brpc, blue(c) * brpc, (float) alpha(c));
      }

      public static int red(int c) {
         return ColorUtility.red(c);
      }

      public static int green(int c) {
         return ColorUtility.green(c);
      }

      public static int blue(int c) {
         return ColorUtility.blue(c);
      }

      public static int alpha(int c) {
         return ColorUtility.alpha(c);
      }

      public static int getColor(float r, float g, float b, float a) {
         return new Color((int) r, (int) g, (int) b, (int) a).getRGB();
      }

      public static int getColor(int red, int green, int blue) {
         return getColor(red, green, blue, 255);
      }

      public static int getColor(int red, int green, int blue, int alpha) {
         int color = 0;
         color |= alpha << 24;
         color |= red << 16;
         color |= green << 8;
         return color | blue;
      }

      public static int getRed(int color) {
         return ColorUtility.red(color);
      }

      public static int getGreen(int color) {
         return ColorUtility.green(color);
      }

      public static int getBlue(int color) {
         return ColorUtility.blue(color);
      }

      public static int getAlpha(int color) {
         return ColorUtility.alpha(color);
      }

      public static float[] rgb(int color) {
         return new float[] { (color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F, (color & 0xFF) / 255.0F,
                 (color >> 24 & 0xFF) / 255.0F };
      }

      public static int rgba(int r, int g, int b, int a) {
         return a << 24 | r << 16 | g << 8 | b;
      }

      public static int colorToHex(Color color) {
         int a = color.getAlpha();
         int r = color.getRed();
         int g = color.getGreen();
         int b = color.getBlue();
         return a << 24 | r << 16 | g << 8 | b;
      }

      public static float[] rgba(int color) {
         return new float[] { (color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F, (color & 0xFF) / 255.0F,
                 (color >> 24 & 0xFF) / 255.0F };
      }
   }
}
