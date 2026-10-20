package vesence.utils.render.ttf;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.Identifier;
import vesence.renderengine.render.Renderer2D;

import java.awt.Font;
import java.util.HashMap;
import java.util.Map;

@Environment(EnvType.CLIENT)
public final class TtfFontFamily {

   private static final String NAMESPACE = "vesence";

   private final String fileName;
   private final Map<Float, TtfFont> sizeCache = new HashMap<>();

   public TtfFontFamily(String fileName) {
      this.fileName = fileName;
   }

   public String getFileName() {
      return fileName;
   }

   public TtfFont size(float size) {
      TtfFont cached = sizeCache.get(size);
      if (cached != null) return cached;
      Font awt = TtfFontUtil.load(Identifier.fromNamespaceAndPath(NAMESPACE, "fonts/ttf/" + fileName), size);
      if (awt == null) {
         awt = new Font("Arial", Font.PLAIN, Math.max(1, Math.round(size)));
      }
      TtfFont font = new TtfFont(awt);
      sizeCache.put(size, font);
      return font;
   }

   public float drawString(Renderer2D r, String text, float x, float y, float size, int color) {
      return size(size).drawString(r, text, x, y, color);
   }

   /** Draws the glyphs with a soft, pulsing theme-colored glow halo behind them. */
   public float drawStringWithGlow(Renderer2D r, String text, float x, float y, float size, int color) {
      return size(size).drawStringWithGlow(r, text, x, y, color);
   }

   /** Same as above but with an opacity multiplier for the glow (higher = brighter). */
   public float drawStringWithGlow(Renderer2D r, String text, float x, float y, float size, int color, float intensity) {
      return size(size).drawStringWithGlow(r, text, x, y, color, intensity);
   }

   /** Draws only the additive glow halo for the glyphs (no crisp glyph on top). */
   public void drawGlow(Renderer2D r, String text, float x, float y, float size, int color) {
      size(size).drawGlow(r, text, x, y, color);
   }

   public float drawCenteredString(Renderer2D r, String text, float cx, float y, float size, int color) {
      return size(size).drawCenteredString(r, text, cx, y, color);
   }

   public float drawCenteredStringWithGlow(Renderer2D r, String text, float cx, float y, float size, int color) {
      return size(size).drawCenteredStringWithGlow(r, text, cx, y, color);
   }

   public float drawRightString(Renderer2D r, String text, float cx, float y, float size, int color) {
      return size(size).drawRightString(r, text, cx, y, color);
   }

   public float getStringWidth(String text, float size) {
      return size(size).getStringWidth(text);
   }

   public float getHeight(float size) {
      return size(size).getHeight();
   }
}
