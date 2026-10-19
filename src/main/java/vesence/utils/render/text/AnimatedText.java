package vesence.utils.render.text;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;

import java.util.HashMap;
import java.util.Map;

@Environment(EnvType.CLIENT)
public final class AnimatedText {

   public static final int ALIGN_LEFT = 0;
   public static final int ALIGN_CENTER = 1;
   public static final int ALIGN_RIGHT = 2;

   private static final long STAGGER_MS = 45L;
   private static final long DURATION_MS = 180L;
   private static final long PRUNE_AFTER_MS = 15_000L;

   private static final float DEFAULT_MIN_ALPHA = 0.55f;

   public static final float WAYPOINT_MIN_ALPHA = 0.9f;

   private static final Map<Object, State> STATES = new HashMap<>();
   private static long lastPrune = 0L;

   private AnimatedText() {
   }

   private static final class State {
      String text = "";
      long[] starts = new long[0];
      final Animation2 widthAnim = new Animation2();
      boolean widthInit = false;
      long lastTouch = System.currentTimeMillis();
   }

   private static State state(Object key) {
      State s = STATES.get(key);
      if (s == null) {
         s = new State();
         STATES.put(key, s);
      }
      s.lastTouch = System.currentTimeMillis();
      maybePrune();
      return s;
   }

   private static void maybePrune() {
      long now = System.currentTimeMillis();
      if (now - lastPrune < 4000L) return;
      lastPrune = now;
      STATES.entrySet().removeIf(e -> now - e.getValue().lastTouch > PRUNE_AFTER_MS);
   }

   private static float easeOutCubic(float t) {
      if (t <= 0f) return 0f;
      if (t >= 1f) return 1f;
      float inv = 1f - t;
      return 1f - inv * inv * inv;
   }

   private static int withAlphaMul(int color, float mul) {
      int base = (color >>> 24) & 0xFF;
      int a = (int) (base * Math.max(0f, Math.min(1f, mul)));
      return (a << 24) | (color & 0x00FFFFFF);
   }

   public static float width(Object key) {
      State s = STATES.get(key);
      return s == null ? 0f : (float) s.widthAnim.getValue();
   }

   private static void updateState(Renderer2D r, FontObject font, State s, String text, float size) {
      long now = System.currentTimeMillis();
      if (!text.equals(s.text)) {
         String old = s.text;
         long[] oldStarts = s.starts;
         long[] starts = new long[text.length()];
         int changedOrder = 0;
         for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean same = i < old.length() && old.charAt(i) == c;
            boolean animatable = Character.isDigit(c);
            if (same && oldStarts != null && i < oldStarts.length) {
               starts[i] = oldStarts[i];
            } else if (animatable) {

               starts[i] = now + changedOrder * STAGGER_MS;
               changedOrder++;
            } else {

               starts[i] = now - DURATION_MS * 2L;
            }
         }
         s.starts = starts;
         s.text = text;
      }

      float targetWidth = r.measureText(font, text, size).width;
      if (!s.widthInit) {
         s.widthAnim.set(targetWidth);
         s.widthInit = true;
      } else {
         s.widthAnim.update();
         s.widthAnim.run(targetWidth, 0.22, Easings.CUBIC_OUT);
      }
   }

   public static float draw(Renderer2D r, FontObject font, Object key, String text,
                            float x, float y, float size, int color, int align) {
      return drawInternal(r, font, key, text, x, y, size, color, null, align, DEFAULT_MIN_ALPHA);
   }

   public static float draw(Renderer2D r, FontObject font, Object key, String text,
                            float x, float y, float size, int color, int align, float minAlpha) {
      return drawInternal(r, font, key, text, x, y, size, color, null, align, minAlpha);
   }

   public static float draw(Renderer2D r, FontObject font, Object key, String text,
                            float x, float y, float size, int[] gradient, int align) {
      int single = (gradient != null && gradient.length > 0) ? gradient[0] : 0xFFFFFFFF;
      return drawInternal(r, font, key, text, x, y, size, single, gradient, align, DEFAULT_MIN_ALPHA);
   }

   private static float drawInternal(Renderer2D r, FontObject font, Object key, String text,
                                     float x, float y, float size, int color, int[] gradient, int align,
                                     float minAlpha) {
      if (text == null) text = "";
      State s = state(key);
      updateState(r, font, s, text, size);

      float animWidth = (float) s.widthAnim.getValue();
      float fullWidth = r.measureText(font, text, size).width;

      float startX;
      switch (align) {
         case ALIGN_CENTER:
            startX = x - animWidth / 2f;
            break;
         case ALIGN_RIGHT:
            startX = x - animWidth;
            break;
         default:
            startX = x;
            break;
      }

      long now = System.currentTimeMillis();
      float rise = size * 0.16f;

      for (int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);
         float advance = r.measureText(font, text.substring(0, i), size).width;

         boolean animatable = Character.isDigit(c);
         long start = i < s.starts.length ? s.starts[i] : now;
         float prog = animatable ? easeOutCubic((now - start) / (float) DURATION_MS) : 1f;

         float yOff = animatable ? (1f - prog) * rise : 0f;
         float aMul = animatable ? (minAlpha + (1f - minAlpha) * prog) : 1f;
         int col;
         if (gradient != null && gradient.length >= 2) {
            float tpos = fullWidth <= 0f ? 0f : (advance + 0.5f) / fullWidth;
            col = sampleGradient(gradient, tpos);
         } else {
            col = color;
         }
         col = withAlphaMul(col, aMul);

         if (((col >>> 24) & 0xFF) > 0) {
            r.text(font, startX + advance, y + yOff, size, String.valueOf(c), col);
         }
      }

      return animWidth;
   }

   private static int sampleGradient(int[] colors, float t) {
      if (colors == null || colors.length == 0) return 0xFFFFFFFF;
      if (colors.length == 1) return colors[0];
      t = Math.max(0f, Math.min(1f, t));
      float scaled = t * (colors.length - 1);
      int idx = (int) Math.floor(scaled);
      if (idx >= colors.length - 1) return colors[colors.length - 1];
      float f = scaled - idx;
      int c0 = colors[idx], c1 = colors[idx + 1];
      int a = (int) (((c0 >>> 24) & 0xFF) + (((c1 >>> 24) & 0xFF) - ((c0 >>> 24) & 0xFF)) * f);
      int rr = (int) (((c0 >> 16) & 0xFF) + (((c1 >> 16) & 0xFF) - ((c0 >> 16) & 0xFF)) * f);
      int g = (int) (((c0 >> 8) & 0xFF) + (((c1 >> 8) & 0xFF) - ((c0 >> 8) & 0xFF)) * f);
      int b = (int) ((c0 & 0xFF) + ((c1 & 0xFF) - (c0 & 0xFF)) * f);
      return (a << 24) | (rr << 16) | (g << 8) | b;
   }
}
