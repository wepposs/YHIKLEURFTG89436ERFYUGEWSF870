package vesence.utils.render.text;

import java.util.Objects;
import java.util.regex.Matcher;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.renderengine.providers.GlBackend;

@Environment(EnvType.CLIENT)
public final class TextRenderer {
   private static final float[] IDENTITY_TRANSFORM = new float[]{1.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 1.0F};
   private final GlBackend backend;
   private final MsdfFont font;

   private static String[] splitLines(String s) {
      int nl = s.indexOf('\n');
      if (nl < 0) {
         return new String[] { s };
      }
      java.util.ArrayList<String> parts = new java.util.ArrayList<>(4);
      int start = 0;
      int idx = nl;
      while (idx >= 0) {
         parts.add(s.substring(start, idx));
         start = idx + 1;
         idx = s.indexOf('\n', start);
      }
      parts.add(s.substring(start));
      return parts.toArray(new String[0]);
   }

   private static final int MEASURE_CACHE_MAX = 512;
   private final java.util.LinkedHashMap<String, TextMetrics> measureCache =
         new java.util.LinkedHashMap<>(128, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(java.util.Map.Entry<String, TextMetrics> eldest) {
               return size() > MEASURE_CACHE_MAX;
            }
         };

   public TextRenderer(GlBackend backend, MsdfFont font) {
      this.backend = Objects.requireNonNull(backend, "backend");
      this.font = Objects.requireNonNull(font, "font");
   }

   @FunctionalInterface
   public interface GlyphConsumer {
      void glyph(float x, float y, float w, float h, float u0, float v0, float u1, float v1);
   }

   public int fontAtlasTextureId() { return this.font.textureId(); }
   public float fontPxRange() { return this.font.distanceRange(); }
   public float fontAtlasWidth() { return this.font.textureWidth(); }
   public float fontAtlasHeight() { return this.font.textureHeight(); }

   public void forEachGlyphQuad(float x, float y, float size, String text, String alignKey,
                                float letterSpacing, GlyphConsumer consumer) {
      if (size <= 0.0F || text == null || text.isEmpty() || consumer == null) return;
      float scale = size / Math.max(1.0E-6F, this.font.emSize());
      float lineHeight = this.font.lineHeight() * scale;
      float baselineY = y;
      String align = alignKey == null ? "l" : alignKey.toLowerCase();
      String[] lines = splitLines(text);

      for (String rawLine : lines) {
         String line = rawLine.indexOf("${") >= 0 ? ColorFormat.strip(rawLine) : rawLine;
         float width = this.measureLineWidth(line, scale, letterSpacing);
         float startX = x;
         if ("c".equals(align)) {
            startX = x - width * 0.5F;
         } else if ("r".equals(align)) {
            startX = x - width;
         }

         float penX = startX;
         int prevCp = -1;
         int i = 0;
         while (i < line.length()) {
            int cp = line.codePointAt(i);
            i += Character.charCount(cp);
            MsdfFont.Glyph glyph = resolveGlyph(cp);
            int glyphCode = glyph != null ? cp : 63;
            if (glyph == null) continue;
            if (prevCp != -1) {
               penX += this.font.kerning(prevCp, glyphCode) * scale;
            }
            if (glyph.renderable) {
               float x0 = penX + glyph.planeLeft * scale;
               float y0 = baselineY - glyph.planeTop * scale;
               float x1 = penX + glyph.planeRight * scale;
               float y1 = baselineY - glyph.planeBottom * scale;
               float w = x1 - x0;
               float h = y1 - y0;
               if (w > 0.0F && h > 0.0F) {
                  consumer.glyph(x0, y0, w, h, glyph.u0, glyph.v1, glyph.u1, glyph.v0);
               }
            }
            penX += glyph.advance * scale + letterSpacing;
            prevCp = glyphCode;
         }
         baselineY += lineHeight;
      }
   }

   public void drawText(float x, float y, float size, String text, int rgbaPremul) {
      this.drawText(x, y, size, text, rgbaPremul, "l", IDENTITY_TRANSFORM, 0f, 0.0f);
   }

   public void drawText(float x, float y, float size, String text, int rgbaPremul, float[] transform) {
      this.drawText(x, y, size, text, rgbaPremul, "l", transform, 0f, 0.0f);
   }

   public void drawText(float x, float y, float size, String text, int rgbaPremul, String alignKey) {
      this.drawText(x, y, size, text, rgbaPremul, alignKey, IDENTITY_TRANSFORM, 0f, 0.0f);
   }

   public void drawText(float x, float y, float size, String text, int rgbaPremul, String alignKey, float[] transform) {
      this.drawText(x, y, size, text, rgbaPremul, alignKey, transform, 0f, 0.0f);
   }

   public void drawText(float x, float y, float size, String text, int rgbaPremul, float[] transform, float letterSpacing) {
      this.drawText(x, y, size, text, rgbaPremul, "l", transform, letterSpacing, 0.0f);
   }

   public void drawText(float x, float y, float size, String text, int rgbaPremul, String alignKey, float letterSpacing) {
      this.drawText(x, y, size, text, rgbaPremul, alignKey, IDENTITY_TRANSFORM, letterSpacing, 0.0f);
   }

   public void drawText(float x, float y, float size, String text, int rgbaPremul, String alignKey, float letterSpacing, float fontWeight) {
      this.drawText(x, y, size, text, rgbaPremul, alignKey, IDENTITY_TRANSFORM, letterSpacing, fontWeight);
   }

   public void drawText(float x, float y, float size, String text, int rgbaPremul, String alignKey, float[] transform, float letterSpacing, float fontWeight) {
      if (!(size <= 0.0F)) {
         String content = text == null ? "" : text;
         if (!content.isEmpty()) {
            float[] matrix = transform != null && transform.length >= 6 ? transform : IDENTITY_TRANSFORM;
            float scale = size / Math.max(1.0E-6F, this.font.emSize());
            float lineHeight = this.font.lineHeight() * scale;
            float baselineY = y;
            String align = alignKey == null ? "l" : alignKey.toLowerCase();
            int color = rgbaPremul;
            int texture = this.font.textureId();
            float pxRange = this.font.distanceRange();
            String[] lines = splitLines(content);

            for (String line : lines) {
               float width = this.measureLineWidth(line, scale, letterSpacing);
               float startX = x;
               if ("c".equals(align)) {
                  startX = x - width * 0.5F;
               } else if ("r".equals(align)) {
                  startX = x - width;
               }

               this.drawTextLine(startX, baselineY, scale, line, color, matrix, texture, pxRange, letterSpacing, fontWeight);
               baselineY += lineHeight;
            }
         }
      }
   }

   public void drawText(float x, float y, float size, String text, int rgbaPremul, String alignKey, float[] transform, float letterSpacing) {
      this.drawText(x, y, size, text, rgbaPremul, alignKey, transform, letterSpacing, 0.0f);
   }

   public void drawText(float x, float y, float size, String text, int[] colors,
                        boolean animated, float speed, String alignKey,
                        float[] transform, float letterSpacing, float fontWeight) {
      if (size <= 0.0F) return;
      String content = text == null ? "" : text;
      if (content.isEmpty()) return;

      if (colors == null || colors.length < 2) {
         int fallbackColor = (colors != null && colors.length == 1) ? colors[0] : 0xFFFFFFFF;
         this.drawText(x, y, size, content, fallbackColor, alignKey, transform, letterSpacing, fontWeight);
         return;
      }

      float[] matrix = transform != null && transform.length >= 6 ? transform : IDENTITY_TRANSFORM;
      float scale = size / Math.max(1.0E-6F, this.font.emSize());
      float lineHeight = this.font.lineHeight() * scale;
      float baselineY = y;
      String align = alignKey == null ? "l" : alignKey.toLowerCase();
      int texture = this.font.textureId();
      float pxRange = this.font.distanceRange();
      String[] lines = splitLines(content);

      float timeOffset = animated ? (System.currentTimeMillis() % 6000L) / 6000.0F * speed : 0.0F;

      for (String line : lines) {
         String cleanLine = line.indexOf("${") >= 0 ? ColorFormat.strip(line) : line;
         float width = this.measureLineWidth(cleanLine, scale, letterSpacing);
         float startX = x;
         if ("c".equals(align)) {
            startX = x - width * 0.5F;
         } else if ("r".equals(align)) {
            startX = x - width;
         }

         if (width > 0.0F) {
            this.drawGradientLine(startX, baselineY, scale, line, cleanLine, colors,
               startX, startX + width, matrix, texture, pxRange,
               letterSpacing, fontWeight, timeOffset, animated);
         }
         baselineY += lineHeight;
      }
   }

   public void drawText(float x, float y, float size, String text, int[] colors) {
      this.drawText(x, y, size, text, colors, false, 3.0F, "l", IDENTITY_TRANSFORM, 0f, 0.0f);
   }

   public void drawText(float x, float y, float size, String text, int[] colors, float speed) {
      this.drawText(x, y, size, text, colors, true, speed, "l", IDENTITY_TRANSFORM, 0f, 0.0f);
   }

    private void drawGradientLine(float x, float baseline, float scale,
                                   String rawLine, String cleanLine, int[] colors,
                                   float textStartX, float textEndX,
                                   float[] matrix, int texture, float pxRange,
                                   float letterSpacing, float fontWeight,
                                   float timeOffset, boolean animated) {
       if (rawLine.indexOf("${") < 0) {
          drawGradientSegment(x, baseline, scale, cleanLine, colors,
             textStartX, textEndX, matrix, texture, pxRange,
             letterSpacing, fontWeight, timeOffset, animated);
          return;
       }

       int defaultA = (colors[0] >>> 24) & 0xFF;
       Matcher m = ColorFormat.PATTERN.matcher(rawLine);
       float penX = x;
       int prevCp = -1;
       int overrideColor = 0;
       boolean useOverride = false;

       boolean hasFirst = m.find(0);
       int nextMarker = hasFirst ? m.start() : rawLine.length();
       int nextMarkerEnd = hasFirst ? m.end() : rawLine.length();

       int i = 0;
       while (i < rawLine.length()) {
          if (i == nextMarker) {
             i = nextMarkerEnd;

             String type = m.group(1);
             if (type != null) {
                int r = Integer.parseInt(m.group(2));
                int g = Integer.parseInt(m.group(3));
                int b = Integer.parseInt(m.group(4));
                int a = (type.equalsIgnoreCase("rgba") && m.group(5) != null)
                        ? Integer.parseInt(m.group(5)) : defaultA;

                a = (a * defaultA) / 255;
                overrideColor = (a << 24) | (r << 16) | (g << 8) | b;
                useOverride = true;
             } else {
                useOverride = false;
             }

             if (m.find()) {
                nextMarker = m.start();
                nextMarkerEnd = m.end();
             } else {
                nextMarker = rawLine.length();
             }
             continue;
          }

          if (rawLine.charAt(i) == '\\' && i + 9 < rawLine.length() && rawLine.charAt(i + 1) == 'c') {
             i += 10;
             continue;
          }

          int cp = rawLine.codePointAt(i);
          int cpLen = Character.charCount(cp);
          i += cpLen;
          MsdfFont.Glyph glyph = resolveGlyph(cp);
          int glyphCode = glyph != null ? cp : 63;
          if (glyph == null) {
             continue;
          }

          if (prevCp != -1) {
             penX += this.font.kerning(prevCp, glyphCode) * scale;
          }

          if (glyph.renderable) {
             float x0 = penX + glyph.planeLeft * scale;
             float y0 = baseline - glyph.planeTop * scale;
             float x1 = penX + glyph.planeRight * scale;
             float y1 = baseline - glyph.planeBottom * scale;
             float w = x1 - x0;
             float h = y1 - y0;
             if (w > 0.0F && h > 0.0F) {
                if (useOverride) {
                   this.backend.enqueueMsdfGlyph(texture, pxRange, x0, y0, w, h,
                      glyph.u0, glyph.v1, glyph.u1, glyph.v0, overrideColor, matrix, fontWeight);
                } else {
                   float textWidth = textEndX - textStartX;
                   float t0 = textWidth > 0.0F ? (x0 - textStartX) / textWidth : 0.0F;
                   float t1 = textWidth > 0.0F ? (x1 - textStartX) / textWidth : 1.0F;
                   t0 = Math.max(0.0F, Math.min(1.0F, t0));
                   t1 = Math.max(0.0F, Math.min(1.0F, t1));
                   int colorLeft = animated ? sampleGradientLooping(colors, t0 + timeOffset) : sampleGradientStatic(colors, t0);
                   int colorRight = animated ? sampleGradientLooping(colors, t1 + timeOffset) : sampleGradientStatic(colors, t1);
                   this.backend.enqueueMsdfGlyphMultiColor(texture, pxRange, x0, y0, w, h,
                      glyph.u0, glyph.v1, glyph.u1, glyph.v0,
                      colorLeft, colorRight, colorRight, colorLeft, matrix, fontWeight);
                }
             }
          }

          penX += glyph.advance * scale + letterSpacing;
          prevCp = glyphCode;
       }
    }

   private void drawGradientSegment(float x, float baseline, float scale, String line, int[] colors,
                                     float textStartX, float textEndX,
                                     float[] matrix, int texture, float pxRange,
                                     float letterSpacing, float fontWeight,
                                     float timeOffset, boolean animated) {
      drawGradientSegment(x, baseline, scale, line, colors, textStartX, textEndX,
         matrix, texture, pxRange, letterSpacing, fontWeight, timeOffset, animated, -1);
   }

   private void drawGradientSegment(float penX, float baseline, float scale, String line, int[] colors,
                                     float textStartX, float textEndX,
                                     float[] matrix, int texture, float pxRange,
                                     float letterSpacing, float fontWeight,
                                     float timeOffset, boolean animated, int prevCodepoint) {
      int prev = prevCodepoint;
      float x = penX;
      int i = 0;

      while (i < line.length()) {
         char ch = line.charAt(i);
         if (ch == '\\' && i + 9 < line.length() && line.charAt(i + 1) == 'c') {
            i += 10;
         } else {
            int cp = line.codePointAt(i);
            int cpLen = Character.charCount(cp);
            i += cpLen;
            MsdfFont.Glyph glyph = resolveGlyph(cp);
            int glyphCode = glyph != null ? cp : 63;
            if (glyph == null) {
               continue;
            }

            if (prev != -1) {
               x += this.font.kerning(prev, glyphCode) * scale;
            }

            if (glyph.renderable) {
               float x0 = x + glyph.planeLeft * scale;
               float y0 = baseline - glyph.planeTop * scale;
               float x1 = x + glyph.planeRight * scale;
               float y1 = baseline - glyph.planeBottom * scale;
               float w = x1 - x0;
               float h = y1 - y0;
               if (w > 0.0F && h > 0.0F) {
                  float textWidth = textEndX - textStartX;
                  float t0 = textWidth > 0.0F ? (x0 - textStartX) / textWidth : 0.0F;
                  float t1 = textWidth > 0.0F ? (x1 - textStartX) / textWidth : 1.0F;
                  t0 = Math.max(0.0F, Math.min(1.0F, t0));
                  t1 = Math.max(0.0F, Math.min(1.0F, t1));
                  int colorLeft = animated ? sampleGradientLooping(colors, t0 + timeOffset) : sampleGradientStatic(colors, t0);
                  int colorRight = animated ? sampleGradientLooping(colors, t1 + timeOffset) : sampleGradientStatic(colors, t1);
                  this.backend.enqueueMsdfGlyphMultiColor(texture, pxRange, x0, y0, w, h, glyph.u0, glyph.v1, glyph.u1, glyph.v0, colorLeft, colorRight, colorRight, colorLeft, matrix, fontWeight);
               }
            }

            x += glyph.advance * scale + letterSpacing;
            prev = glyphCode;
         }
      }
   }

   private static int sampleGradientStatic(int[] colors, float t) {
      if (colors.length == 1) return colors[0];
      t = Math.max(0.0F, Math.min(1.0F, t));
      float segment = 1.0F / (colors.length - 1);
      int idx = Math.min((int)(t * (colors.length - 1)), colors.length - 2);
      float localT = (t - idx * segment) / segment;
      localT = Math.max(0.0F, Math.min(1.0F, localT));
      int c0 = colors[idx];
      int c1 = colors[idx + 1];
      int a0 = (c0 >>> 24) & 0xFF, r0 = (c0 >>> 16) & 0xFF, g0 = (c0 >>> 8) & 0xFF, b0 = c0 & 0xFF;
      int a1 = (c1 >>> 24) & 0xFF, r1 = (c1 >>> 16) & 0xFF, g1 = (c1 >>> 8) & 0xFF, b1 = c1 & 0xFF;
      int a = Math.round(a0 + (a1 - a0) * localT);
      int r = Math.round(r0 + (r1 - r0) * localT);
      int g = Math.round(g0 + (g1 - g0) * localT);
      int b = Math.round(b0 + (b1 - b0) * localT);
      return (a << 24) | (r << 16) | (g << 8) | b;
   }

   private static int sampleGradientLooping(int[] colors, float t) {
      if (colors.length == 1) return colors[0];
      t = t - (float) Math.floor(t);
      int n = colors.length;
      float segment = 1.0F / n;
      int idx = Math.min((int)(t * n), n - 1);
      float localT = (t - idx * segment) / segment;
      localT = Math.max(0.0F, Math.min(1.0F, localT));
      int c0 = colors[idx];
      int c1 = colors[(idx + 1) % n];
      int a0 = (c0 >>> 24) & 0xFF, r0 = (c0 >>> 16) & 0xFF, g0 = (c0 >>> 8) & 0xFF, b0 = c0 & 0xFF;
      int a1 = (c1 >>> 24) & 0xFF, r1 = (c1 >>> 16) & 0xFF, g1 = (c1 >>> 8) & 0xFF, b1 = c1 & 0xFF;
      int a = Math.round(a0 + (a1 - a0) * localT);
      int r = Math.round(r0 + (r1 - r0) * localT);
      int g = Math.round(g0 + (g1 - g0) * localT);
      int b = Math.round(b0 + (b1 - b0) * localT);
      return (a << 24) | (r << 16) | (g << 8) | b;
   }

   public TextMetrics measureText(String text, float size) {
      return this.measureText(text, size, 0f);
   }

   public TextMetrics measureText(String text, float size, float letterSpacing) {
      if (size <= 0.0F) {
         return new TextMetrics(0.0F, 0.0F);
      } else {
         String content = text == null ? "" : text;
         if (content.isEmpty()) {
            return new TextMetrics(0.0F, 0.0F);
         }

         String key = null;
         if (content.length() <= 256) {
            key = size + "|" + letterSpacing + "|" + content;
            TextMetrics cached = this.measureCache.get(key);
            if (cached != null) {
               return cached;
            }
         }

         float scale = size / Math.max(1.0E-6F, this.font.emSize());
         float lineHeight = this.font.lineHeight() * scale;
         String[] lines = splitLines(content);
         float maxWidth = 0.0F;

         for (String line : lines) {
            maxWidth = Math.max(maxWidth, this.measureLineWidth(line, scale, letterSpacing));
         }

         float height = Math.max(lineHeight * lines.length, lineHeight);
         TextMetrics result = new TextMetrics(maxWidth, height);
         if (key != null) {
            this.measureCache.put(key, result);
         }
         return result;
      }
   }

   private void drawTextLine(float x, float baseline, float scale, String line, int defaultColor,
                              float[] matrix, int texture, float pxRange, float letterSpacing, float fontWeight) {
      if (!line.isEmpty()) {
         if (line.indexOf("${") >= 0 && ColorFormat.PATTERN.matcher(line).find()) {
            drawFormattedTextLine(x, baseline, scale, line, defaultColor, matrix, texture, pxRange, letterSpacing, fontWeight);
            return;
         }
         drawPlainTextLine(x, baseline, scale, line, defaultColor, matrix, texture, pxRange, letterSpacing, fontWeight);
      }
   }

   private void drawFormattedTextLine(float x, float baseline, float scale, String line, int defaultColor,
                                       float[] matrix, int texture, float pxRange, float letterSpacing, float fontWeight) {
      int defaultR = (defaultColor >>> 16) & 0xFF;
      int defaultG = (defaultColor >>> 8) & 0xFF;
      int defaultB = defaultColor & 0xFF;
      int defaultA = (defaultColor >>> 24) & 0xFF;
      int curR = defaultR, curG = defaultG, curB = defaultB, curA = defaultA;

      float penX = x;
      int prevCodepoint = -1;

      Matcher matcher = ColorFormat.PATTERN.matcher(line);
      int lastEnd = 0;

      while (matcher.find()) {
         String segment = line.substring(lastEnd, matcher.start());
         if (!segment.isEmpty()) {
            int segColor = (curA << 24) | (curR << 16) | (curG << 8) | curB;
            float[] result = drawPlainTextSegment(penX, baseline, scale, segment, segColor, matrix, texture, pxRange, prevCodepoint, letterSpacing, fontWeight);
            penX = result[0];
            prevCodepoint = (int) result[1];
         }

         String type = matcher.group(1);
         if (type != null) {
            curR = Integer.parseInt(matcher.group(2));
            curG = Integer.parseInt(matcher.group(3));
            curB = Integer.parseInt(matcher.group(4));
            if (type.equalsIgnoreCase("rgba") && matcher.group(5) != null) {

               int inlineA = Integer.parseInt(matcher.group(5));
               curA = (inlineA * defaultA) / 255;
            } else {
               curA = defaultA;
            }
         } else {
            curR = defaultR; curG = defaultG; curB = defaultB; curA = defaultA;
         }

         lastEnd = matcher.end();
      }

      String tail = line.substring(lastEnd);
      if (!tail.isEmpty()) {
         int segColor = (curA << 24) | (curR << 16) | (curG << 8) | curB;
         drawPlainTextSegment(penX, baseline, scale, tail, segColor, matrix, texture, pxRange, prevCodepoint, letterSpacing, fontWeight);
      }
   }

   private float[] drawPlainTextSegment(float penX, float baseline, float scale, String segment, int color,
                                         float[] matrix, int texture, float pxRange, int prevCodepoint,
                                         float letterSpacing, float fontWeight) {
      int prev = prevCodepoint;
      float x = penX;
      for (int i = 0; i < segment.length(); ) {
         char ch = segment.charAt(i);
         if (ch == '\\' && i + 9 < segment.length() && segment.charAt(i + 1) == 'c') {
            i += 10;
         } else {
            int cp = segment.codePointAt(i);
            int cpLen = Character.charCount(cp);
            i += cpLen;
            MsdfFont.Glyph glyph = resolveGlyph(cp);
            int glyphCode = glyph != null ? cp : 63;
            if (glyph == null) {
               continue;
            }
            if (prev != -1) {
               x += this.font.kerning(prev, glyphCode) * scale;
            }
            if (glyph.renderable) {
               float x0 = x + glyph.planeLeft * scale;
               float y0 = baseline - glyph.planeTop * scale;
               float x1 = x + glyph.planeRight * scale;
               float y1 = baseline - glyph.planeBottom * scale;
               float w = x1 - x0;
               float h = y1 - y0;
               if (w > 0.0F && h > 0.0F) {
                  this.backend.enqueueMsdfGlyph(texture, pxRange, x0, y0, w, h, glyph.u0, glyph.v1, glyph.u1, glyph.v0, color, matrix, fontWeight);
               }
            }
            x += glyph.advance * scale + letterSpacing;
            prev = glyphCode;
         }
      }
      return new float[]{x, prev};
   }

   private void drawPlainTextLine(float x, float baseline, float scale, String line, int color,
                                   float[] matrix, int texture, float pxRange, float letterSpacing, float fontWeight) {
      float penX = x;
      int prevCodepoint = -1;
      int i = 0;

      while (i < line.length()) {
         char ch = line.charAt(i);
         if (ch == '\\' && i + 9 < line.length() && line.charAt(i + 1) == 'c') {
            i += 10;
         } else {
            int cp = line.codePointAt(i);
            int cpLen = Character.charCount(cp);
            i += cpLen;
            MsdfFont.Glyph glyph = resolveGlyph(cp);
            int glyphCode = glyph != null ? cp : 63;
            if (glyph == null) {
               continue;
            }

            if (prevCodepoint != -1) {
               penX += this.font.kerning(prevCodepoint, glyphCode) * scale;
            }

            if (glyph.renderable) {
               float x0 = penX + glyph.planeLeft * scale;
               float y0 = baseline - glyph.planeTop * scale;
               float x1 = penX + glyph.planeRight * scale;
               float y1 = baseline - glyph.planeBottom * scale;
               float w = x1 - x0;
               float h = y1 - y0;
               if (w > 0.0F && h > 0.0F) {
                  this.backend.enqueueMsdfGlyph(texture, pxRange, x0, y0, w, h, glyph.u0, glyph.v1, glyph.u1, glyph.v0, color, matrix, fontWeight);
               }
            }

            penX += glyph.advance * scale + letterSpacing;
            prevCodepoint = glyphCode;
         }
      }
   }

   private MsdfFont.Glyph resolveGlyph(int cp) {
      MsdfFont.Glyph glyph = this.font.glyph(cp);
      if (glyph != null) {
         return glyph;
      }
      if (cp < 128) {
         return this.font.glyph(63);
      }
      return null;
   }

   private float measureLineWidth(String line, float scale, float letterSpacing) {
      if (line.isEmpty()) return 0.0F;

      String stripped = line.indexOf("${") >= 0 ? ColorFormat.strip(line) : line;
      float penX = 0.0F;
      int prevCodepoint = -1;
      int charCount = 0;
      int i = 0;

      while (i < stripped.length()) {
         char ch = stripped.charAt(i);
         if (ch == '\\' && i + 9 < stripped.length() && stripped.charAt(i + 1) == 'c') {
            i += 10;
         } else {
            int cp = stripped.codePointAt(i);
            int cpLen = Character.charCount(cp);
            i += cpLen;
            MsdfFont.Glyph glyph = resolveGlyph(cp);
            int glyphCode = glyph != null ? cp : 63;
            if (glyph == null) {
               continue;
            }

            if (prevCodepoint != -1) {
               penX += this.font.kerning(prevCodepoint, glyphCode) * scale;
            }

            penX += glyph.advance * scale + letterSpacing;
            prevCodepoint = glyphCode;
            charCount++;
         }
      }

      if (charCount > 0 && letterSpacing != 0f) {
         penX -= letterSpacing;
      }

      return penX;
   }

   @Environment(EnvType.CLIENT)
   public static final class TextMetrics {
      public final float width;
      public final float height;

      public TextMetrics(float width, float height) {
         this.width = width;
         this.height = height;
      }
   }
}
