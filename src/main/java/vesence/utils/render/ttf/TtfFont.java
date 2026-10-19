package vesence.utils.render.ttf;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import com.mojang.renderpearl.backend.opengl.GlTexture;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.ResourceLocation;
import vesence.renderengine.render.Renderer2D;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@Environment(EnvType.CLIENT)
public final class TtfFont {

   private static final AtomicInteger COUNTER = new AtomicInteger(0);
   private static final int GLYPH_PADDING = 2;

   private final Font font;
   private final FontMetrics metrics;
   private final Map<Character, Glyph> glyphs = new HashMap<>();
   private final int ascent;
   private final int height;

   public TtfFont(Font font) {
      this.font = font;
      BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = probe.createGraphics();
      g.setFont(font);
      this.metrics = g.getFontMetrics();
      this.ascent = metrics.getAscent();
      this.height = metrics.getHeight();
      g.dispose();
   }

   public Font getFont() {
      return font;
   }

   public int getHeight() {
      return height;
   }

   public int getStringWidth(String text) {
      if (text == null || text.isEmpty()) return 0;
      int w = 0;
      for (int i = 0; i < text.length(); i++) {
         Glyph g = glyph(text.charAt(i));
         w += g.advance;
      }
      return w;
   }

   private Glyph glyph(char c) {
      Glyph cached = glyphs.get(c);
      if (cached != null) return cached;
      Glyph g = renderGlyph(c);
      glyphs.put(c, g);
      return g;
   }

   private Glyph renderGlyph(char c) {
      int advance = metrics.charWidth(c);
      int boxW = Math.max(1, advance + GLYPH_PADDING * 2);
      int boxH = Math.max(1, height + GLYPH_PADDING * 2);

      BufferedImage img = new BufferedImage(boxW, boxH, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = img.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
      g.setFont(font);
      g.setColor(new Color(255, 255, 255, 0));
      g.fillRect(0, 0, boxW, boxH);
      g.setColor(Color.WHITE);
      g.drawString(String.valueOf(c), GLYPH_PADDING, GLYPH_PADDING + ascent);
      g.dispose();

      NativeImage native_ = new NativeImage(boxW, boxH, false);
      for (int y = 0; y < boxH; y++) {
         for (int x = 0; x < boxW; x++) {
            native_.setColorArgb(x, y, img.getRGB(x, y));
         }
      }

      String name = "ttf_glyph_" + COUNTER.incrementAndGet();
      final String nameFinal = name;
      Supplier<String> nameSupplier = () -> nameFinal;
      NativeImageBackedTexture tex = new NativeImageBackedTexture(nameSupplier, native_);
      tex.upload();
      ResourceLocation id = ResourceLocation.of("vesence", "ttf_glyph/" + name);
      Minecraft.getInstance().getTextureManager().registerTexture(id, tex);

      int glId = 0;
      if (tex.getGlTexture() instanceof GlTexture glTex) {
         glId = glTex.getGlId();
      }

      Glyph glyph = new Glyph();
      glyph.glId = glId;
      glyph.identifier = id;
      glyph.width = boxW;
      glyph.height = boxH;
      glyph.advance = advance;
      glyph.offsetX = -GLYPH_PADDING;
      glyph.offsetY = -GLYPH_PADDING;

      buildGlowTexture(glyph, img, boxW, boxH);
      return glyph;
   }

   private static final int GLOW_MAX_PAD = 30;

   /**
    * Bakes a high quality glow texture for a glyph once, at creation time. The glyph's alpha
    * coverage is placed onto a padded canvas and run through a separable Gaussian blur, giving
    * a genuinely smooth radial falloff. At draw time this single texture is tinted and blended
    * behind the crisp glyph, which produces a clean, artifact-free colored glow (no per-frame
    * stamping / noise). The interior of the halo is hidden by the sharp glyph drawn on top, so
    * only the soft outer light is visible.
    */
   private void buildGlowTexture(Glyph glyph, BufferedImage img, int boxW, int boxH) {
      int pad = Math.max(5, Math.min(GLOW_MAX_PAD, Math.round(this.height * 0.5f)));
      int gw = boxW + pad * 2;
      int gh = boxH + pad * 2;

      float[] src = new float[gw * gh];
      for (int yy = 0; yy < boxH; yy++) {
         for (int xx = 0; xx < boxW; xx++) {
            int alpha = (img.getRGB(xx, yy) >>> 24) & 0xFF;
            if (alpha != 0) {
               src[(yy + pad) * gw + (xx + pad)] = alpha / 255f;
            }
         }
      }

      float sigma = pad * 0.5f;
      float[] blur = gaussianBlur(src, gw, gh, sigma);

      NativeImage glowImg = new NativeImage(gw, gh, false);
      for (int yy = 0; yy < gh; yy++) {
         for (int xx = 0; xx < gw; xx++) {
            float v = blur[yy * gw + xx];
            if (v > 1f) v = 1f;
            // Gamma lift makes the halo fuller and softer rather than a thin faint outline.
            v = (float) Math.pow(v, 0.75);
            int av = (int) (v * 255f + 0.5f);
            if (av < 0) av = 0; else if (av > 255) av = 255;
            glowImg.setColorArgb(xx, yy, (av << 24) | 0x00FFFFFF);
         }
      }

      String name = "ttf_glow_" + COUNTER.incrementAndGet();
      final String nameFinal = name;
      Supplier<String> nameSupplier = () -> nameFinal;
      NativeImageBackedTexture tex = new NativeImageBackedTexture(nameSupplier, glowImg);
      tex.upload();
      ResourceLocation id = ResourceLocation.of("vesence", "ttf_glow/" + name);
      Minecraft.getInstance().getTextureManager().registerTexture(id, tex);

      int glowGlId = 0;
      if (tex.getGlTexture() instanceof GlTexture glTex) {
         glowGlId = glTex.getGlId();
      }

      glyph.glowGlId = glowGlId;
      glyph.glowIdentifier = id;
      glyph.glowWidth = gw;
      glyph.glowHeight = gh;
      glyph.glowPad = pad;
   }

   /** Separable Gaussian blur over a single (alpha) channel. Clamps at the edges. */
   private static float[] gaussianBlur(float[] src, int w, int h, float sigma) {
      int radius = Math.max(1, (int) Math.ceil(sigma * 3f));
      float[] kernel = new float[radius * 2 + 1];
      float twoSigma2 = 2f * sigma * sigma;
      float sum = 0f;
      for (int i = -radius; i <= radius; i++) {
         float v = (float) Math.exp(-(i * i) / twoSigma2);
         kernel[i + radius] = v;
         sum += v;
      }
      for (int i = 0; i < kernel.length; i++) kernel[i] /= sum;

      float[] tmp = new float[w * h];
      for (int y = 0; y < h; y++) {
         int row = y * w;
         for (int x = 0; x < w; x++) {
            float acc = 0f;
            for (int k = -radius; k <= radius; k++) {
               int sx = x + k;
               if (sx < 0) sx = 0; else if (sx >= w) sx = w - 1;
               acc += src[row + sx] * kernel[k + radius];
            }
            tmp[row + x] = acc;
         }
      }
      float[] out = new float[w * h];
      for (int y = 0; y < h; y++) {
         for (int x = 0; x < w; x++) {
            float acc = 0f;
            for (int k = -radius; k <= radius; k++) {
               int sy = y + k;
               if (sy < 0) sy = 0; else if (sy >= h) sy = h - 1;
               acc += tmp[sy * w + x] * kernel[k + radius];
            }
            out[y * w + x] = acc;
         }
      }
      return out;
   }

   public float drawString(Renderer2D renderer, String text, float x, float y, int color) {
      if (text == null || text.isEmpty()) return 0f;
      float curX = x;
      for (int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);
         Glyph g = glyph(c);
         if (g.glId != 0) {
            renderer.drawImage(g.glId, curX + g.offsetX, y + g.offsetY, g.width, g.height, color);
         }
         curX += g.advance;
      }
      return curX - x;
   }

   /**
    * Draws a soft, smoothly pulsing glow behind the glyphs using {@code color}, then renders
    * the crisp glyphs on top completely unchanged. The glow sits strictly behind the icon
    * like a colored drop shadow and never brightens or alters the glyph itself.
    */
   public float drawStringWithGlow(Renderer2D renderer, String text, float x, float y, int color) {
      return this.drawStringWithGlow(renderer, text, x, y, color, 1.0f);
   }

   public float drawStringWithGlow(Renderer2D renderer, String text, float x, float y, int color, float intensity) {
      this.drawGlow(renderer, text, x, y, color, intensity);
      return this.drawString(renderer, text, x, y, color);
   }

   /**
    * Renders only the glow that sits BEHIND the glyphs using the pre-baked, Gaussian-blurred
    * glow texture. Two layers are composited: a wide, faint bloom and a tighter, brighter halo,
    * both tinted with {@code color} and gently pulsing over time. Because it is a real blurred
    * texture (not per-frame stamping), the result is perfectly smooth with no noise or banding,
    * and it can never spoil the crisp glyph drawn on top.
    */
   public void drawGlow(Renderer2D renderer, String text, float x, float y, int color) {
      this.drawGlow(renderer, text, x, y, color, 1.0f);
   }

   /**
    * @param intensity multiplier for the glow opacity (1.0 = default; higher = brighter glow).
    */
   public void drawGlow(Renderer2D renderer, String text, float x, float y, int color, float intensity) {
      if (text == null || text.isEmpty()) return;
      int rgb = color & 0x00FFFFFF;
      int baseAlpha = (color >>> 24) & 0xFF;
      if (baseAlpha <= 0 || intensity <= 0f) return;

      double time = System.currentTimeMillis() / 1000.0;
      float pulse = 0.82f + 0.18f * (float) (0.5 + 0.5 * Math.sin(time * (Math.PI * 2.0 / 2.6)));

      float base = baseAlpha * intensity * pulse;
      int aInner = clampByte(base * 0.24f);
      int aOuter = clampByte(base * 0.1f);
      if (aInner <= 0 && aOuter <= 0) return;

      int tintInner = (aInner << 24) | rgb;
      int tintOuter = (aOuter << 24) | rgb;

      float curX = x;
      for (int i = 0; i < text.length(); i++) {
         Glyph g = glyph(text.charAt(i));
         if (g.glowGlId != 0) {
            // Wide soft bloom first, then a tighter brighter halo on top of it.
            if (aOuter > 0) drawGlowQuad(renderer, g, curX, y, 1.85f, tintOuter);
            if (aInner > 0) drawGlowQuad(renderer, g, curX, y, 1.0f, tintInner);
         }
         curX += g.advance;
      }
   }

   private void drawGlowQuad(Renderer2D renderer, Glyph g, float baseX, float baseY, float scale, int tint) {
      float gx = baseX + g.offsetX - g.glowPad;
      float gy = baseY + g.offsetY - g.glowPad;
      float cx = gx + g.glowWidth / 2f;
      float cy = gy + g.glowHeight / 2f;
      float w = g.glowWidth * scale;
      float h = g.glowHeight * scale;
      renderer.drawImage(g.glowGlId, cx - w / 2f, cy - h / 2f, w, h, tint);
   }

   private static int clampByte(float v) {
      int i = (int) (v + 0.5f);
      if (i < 0) return 0;
      return i > 255 ? 255 : i;
   }

   public float drawCenteredString(Renderer2D renderer, String text, float cx, float y, int color) {
      float w = getStringWidth(text);
      return drawString(renderer, text, cx - w / 2f, y, color);
   }

   public float drawCenteredStringWithGlow(Renderer2D renderer, String text, float cx, float y, int color) {
      float w = getStringWidth(text);
      return drawStringWithGlow(renderer, text, cx - w / 2f, y, color);
   }

   public float drawRightString(Renderer2D renderer, String text, float cx, float y, int color) {
      float w = getStringWidth(text);
      return drawString(renderer, text, cx - (w), y, color);
   }

   private static final class Glyph {
      int glId;
      ResourceLocation identifier;
      int width;
      int height;
      int advance;
      int offsetX;
      int offsetY;
      // Pre-baked, Gaussian-blurred glow texture (white with soft alpha halo).
      int glowGlId;
      ResourceLocation glowIdentifier;
      int glowWidth;
      int glowHeight;
      int glowPad;
   }
}
