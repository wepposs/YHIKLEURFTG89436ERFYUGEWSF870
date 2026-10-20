package vesence.utils.render.utils;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * Утилита текстовых полей ClickGui: доступ к системному буферу обмена и общий
 * фильтр вставки. Используется поиском (compact/classic), StringSetting и
 * редактированием числового буфера слайдера, чтобы Ctrl+A / Ctrl+C / Ctrl+V /
 * Ctrl+X работали единообразно во всех полях.
 */
@Environment(EnvType.CLIENT)
public final class ClipboardUtil {

   /** Разрешить любой печатный символ (>= 32, кроме DEL). */
   public static final int MODE_PRINTABLE = 0;
   /** Только буквы латиницы, цифры и пробел (фильтр classic-поиска). */
   public static final int MODE_ALNUM_SPACE = 1;
   /** Числовой ввод: цифры, точка, запятая, минус (редактирование слайдера). */
   public static final int MODE_NUMERIC = 2;
   /** Печатный ASCII 32..126 (поля ника/сервера/адреса — как в исходном фильтре). */
   public static final int MODE_ASCII = 3;

   private ClipboardUtil() {
   }

   /** Зажат ли Ctrl (или Cmd на macOS) — по битовой маске модификаторов события. */
   public static boolean isCtrl(int modifiers) {
      return (modifiers & InputConstants.MOD_CONTROL) != 0 || (modifiers & InputConstants.MOD_SUPER) != 0;
   }

   /** Текущее содержимое буфера обмена (никогда не null; при ошибке — пустая строка). */
   public static String get() {
      try {
         Minecraft mc = Minecraft.getInstance();
         if (mc != null && mc.keyboardHandler != null) {
            String s = mc.keyboardHandler.getClipboard();
            return s == null ? "" : s;
         }
      } catch (Throwable ignored) {
      }
      return "";
   }

   /** Положить текст в буфер обмена (безопасно, ошибки проглатываются). */
   public static void set(String text) {
      try {
         Minecraft mc = Minecraft.getInstance();
         if (mc != null && mc.keyboardHandler != null) {
            mc.keyboardHandler.setClipboard(text == null ? "" : text);
         }
      } catch (Throwable ignored) {
      }
   }

   /**
    * Дописать к {@code base} отфильтрованное содержимое {@code add}, не превышая
    * {@code maxLen} символов. Символы вне выбранного {@code mode} пропускаются
    * (перевод строки/таб и прочее управление отбрасывается).
    */
   public static String appendFiltered(String base, String add, int maxLen, int mode) {
      StringBuilder sb = new StringBuilder(base == null ? "" : base);
      if (add == null || add.isEmpty()) {
         return sb.toString();
      }
      for (int i = 0; i < add.length() && sb.length() < maxLen; i++) {
         char c = add.charAt(i);
         if (isAllowed(c, mode)) {
            sb.append(c);
         }
      }
      return sb.toString();
   }

   private static boolean isAllowed(char c, int mode) {
      if (c < 32 || c == 127) {
         return false;
      }
      switch (mode) {
         case MODE_ALNUM_SPACE:
            return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == ' ';
         case MODE_NUMERIC:
            return (c >= '0' && c <= '9') || c == '.' || c == ',' || c == '-';
         case MODE_ASCII:
            return c < 127;
         case MODE_PRINTABLE:
         default:
            return true;
      }
   }
}
