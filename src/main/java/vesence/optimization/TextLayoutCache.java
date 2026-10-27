package vesence.optimization;

import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.util.FormattedCharSequence;

/**
 * Кэш подготовленного текста: layout строки ({@code Font#prepareText}) пересобирается
 * только когда строка, позиция, размер и цвет действительно изменились.
 *
 * <p>Имена, scoreboard и HUD-подписи передаются одной и той же строкой каждый кадр,
 * а {@code prepareText} заново раскладывает глифы. Ключ включает саму строку, поэтому
 * при смене текста промах происходит ровно один раз.
 */
@Environment(EnvType.CLIENT)
public final class TextLayoutCache {

   private static final int LIMIT = 2048;
   private static final Map<Key, Font.PreparedText> CACHE = new LruMap<>(LIMIT);

   private TextLayoutCache() {
   }

   public static boolean enabled() {
      return Opt.on();
   }

   public static Font.PreparedText get(FormattedCharSequence text, float x, float y, int color,
                                       boolean dropShadow, boolean seeThrough, int backgroundColor) {
      if (text == null) {
         return null;
      }
      return CACHE.get(new Key(text, x, y, color, dropShadow, seeThrough, backgroundColor));
   }

   public static void put(FormattedCharSequence text, float x, float y, int color,
                          boolean dropShadow, boolean seeThrough, int backgroundColor,
                          Font.PreparedText prepared) {
      if (text == null || prepared == null) {
         return;
      }
      CACHE.put(new Key(text, x, y, color, dropShadow, seeThrough, backgroundColor), prepared);
   }

   public static void clear() {
      CACHE.clear();
   }

   private record Key(FormattedCharSequence text, float x, float y, int color,
                      boolean dropShadow, boolean seeThrough, int backgroundColor) {
   }

   private static final class LruMap<K, V> extends java.util.LinkedHashMap<K, V> {
      private final int limit;

      private LruMap(int limit) {
         super(128, 0.75f, true);
         this.limit = limit;
      }

      @Override
      protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
         return size() > this.limit;
      }
   }
}
