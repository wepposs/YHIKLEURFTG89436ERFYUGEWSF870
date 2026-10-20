package vesence.utils.render.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * Таблица имён клавиш для биндов и команды {@code .bind}.
 *
 * <p>Порт 26.3: GLFW удалён, {@code KeyEvent#key()} — это <b>SDL-сканкод</b>, и именно он
 * хранится в {@code Module#bind}. Раньше таблица содержала GLFW-коды (A = 65, ENTER = 257,
 * ESCAPE = 256, F1 = 290, NUMPAD0 = 320 …), поэтому в 26.3:
 * <ul>
 *   <li>{@link #getKey(int)} не находил бинд в {@code reverseKeyMap} и показывал «KEY4»
 *       вместо «A» — отсюда ощущение, что все кнопки перепутаны;</li>
 *   <li>{@code .bind add <module> <key>} записывал GLFW-код, который никогда не совпадёт
 *       с SDL-сканкодом в {@code KeyboardMixin}.</li>
 * </ul>
 * Теперь обе таблицы строятся из {@code InputConstants.KEY_*}, т.е. из реальных SDL-сканкодов.
 */
@Environment(EnvType.CLIENT)
public class KeyUtil {
   public static final Map<String, Integer> keyMap = new HashMap<>();
   public static final Map<Integer, String> reverseKeyMap = new HashMap<>();
   public static Minecraft mc = Minecraft.getInstance();

   /** SDL-сканкоды, для которых в {@link InputConstants} нет именованной константы. */
   private static final int SDL_SCANCODE_KP_DIVIDE = 84;
   private static final int SDL_SCANCODE_KP_MINUS = 86;
   private static final int SDL_SCANCODE_KP_DECIMAL = 99;
   private static final int SDL_SCANCODE_MENU = 101;

   public static boolean isKeyDown(int keyCode) {
      return InputConstants.isKeyDown(keyCode);
   }

   public static String getKey(int key) {
      if (key == -1) {
         return "NONE";
      } else if (key <= -100) {
         int button = -key - 100;
         return switch (button) {
            case 0 -> "LMB";
            case 1 -> "RMB";
            case 2 -> "MB3";
            case 3 -> "MB4";
            case 4 -> "MB5";
            default -> "MB" + (button + 1);
         };
      } else if (reverseKeyMap.containsKey(key)) {
         return reverseKeyMap.get(key);
      } else {
         return "KEY" + key;
      }
   }

   private static void getBindCMD() {
      // Буквы A-Z: SDL-сканкоды идут подряд, начиная с KEY_A.
      for (char c = 'A'; c <= 'Z'; c++) {
         keyMap.put(String.valueOf(c), InputConstants.KEY_A + (c - 'A'));
      }

      // Цифры 1-9 идут подряд, 0 стоит отдельно (SDL-порядок: 1..9, затем 0).
      for (char c = '1'; c <= '9'; c++) {
         keyMap.put(String.valueOf(c), InputConstants.KEY_1 + (c - '1'));
      }
      keyMap.put("0", InputConstants.KEY_0);

      for (int i = 1; i <= 12; i++) {
         keyMap.put("F" + i, InputConstants.KEY_F1 + (i - 1));
      }

      for (int i = 1; i <= 9; i++) {
         keyMap.put("NUM" + i, InputConstants.KEY_NUMPAD1 + (i - 1));
      }
      keyMap.put("NUM0", InputConstants.KEY_NUMPAD0);
      keyMap.put("NUMDECIMAL", SDL_SCANCODE_KP_DECIMAL);
      keyMap.put("NUMDIVIDE", SDL_SCANCODE_KP_DIVIDE);
      keyMap.put("NUMMULTIPLY", InputConstants.KEY_MULTIPLY);
      keyMap.put("NUMSUBTRACT", SDL_SCANCODE_KP_MINUS);
      keyMap.put("NUMADD", InputConstants.KEY_ADD);
      keyMap.put("NUMENTER", InputConstants.KEY_NUMPADENTER);
      keyMap.put("NUMEQUAL", InputConstants.KEY_NUMPADEQUALS);

      keyMap.put("SPACE", InputConstants.KEY_SPACE);
      keyMap.put("ENTER", InputConstants.KEY_RETURN);
      keyMap.put("ESCAPE", InputConstants.KEY_ESCAPE);
      keyMap.put("TAB", InputConstants.KEY_TAB);
      keyMap.put("BACKSPACE", InputConstants.KEY_BACKSPACE);
      keyMap.put("INSERT", InputConstants.KEY_INSERT);
      keyMap.put("DELETE", InputConstants.KEY_DELETE);
      keyMap.put("HOME", InputConstants.KEY_HOME);
      keyMap.put("END", InputConstants.KEY_END);
      keyMap.put("PAGEUP", InputConstants.KEY_PAGEUP);
      keyMap.put("PAGEDOWN", InputConstants.KEY_PAGEDOWN);
      keyMap.put("RIGHT", InputConstants.KEY_RIGHT);
      keyMap.put("LEFT", InputConstants.KEY_LEFT);
      keyMap.put("DOWN", InputConstants.KEY_DOWN);
      keyMap.put("UP", InputConstants.KEY_UP);

      keyMap.put("LSHIFT", InputConstants.KEY_LSHIFT);
      keyMap.put("LCTRL", InputConstants.KEY_LCONTROL);
      keyMap.put("LALT", InputConstants.KEY_LALT);
      keyMap.put("LSUPER", InputConstants.KEY_LGUI);
      keyMap.put("RSHIFT", InputConstants.KEY_RSHIFT);
      keyMap.put("RCTRL", InputConstants.KEY_RCONTROL);
      keyMap.put("RALT", InputConstants.KEY_RALT);
      keyMap.put("RSUPER", InputConstants.KEY_RGUI);
      keyMap.put("MENU", SDL_SCANCODE_MENU);

      keyMap.put("CAPSLOCK", InputConstants.KEY_CAPSLOCK);
      keyMap.put("NUMLOCK", InputConstants.KEY_NUMLOCK);
      keyMap.put("SCROLLLOCK", InputConstants.KEY_SCROLLLOCK);
      keyMap.put("PRINTSCREEN", InputConstants.KEY_PRINTSCREEN);
      keyMap.put("PAUSE", InputConstants.KEY_PAUSE);

      keyMap.put("APOSTROPHE", InputConstants.KEY_APOSTROPHE);
      keyMap.put("COMMA", InputConstants.KEY_COMMA);
      keyMap.put("MINUS", InputConstants.KEY_MINUS);
      keyMap.put("PERIOD", InputConstants.KEY_PERIOD);
      keyMap.put("SLASH", InputConstants.KEY_SLASH);
      keyMap.put("SEMICOLON", InputConstants.KEY_SEMICOLON);
      keyMap.put("EQUAL", InputConstants.KEY_EQUALS);
      keyMap.put("LBRACKET", InputConstants.KEY_LBRACKET);
      keyMap.put("BACKSLASH", InputConstants.KEY_BACKSLASH);
      keyMap.put("RBRACKET", InputConstants.KEY_RBRACKET);
      keyMap.put("GRAVE", InputConstants.KEY_GRAVE);

      // Мышиные кнопки: vesence-кодировка -100 - индекс (0 = ЛКМ, 1 = ПКМ, 2 = СКМ).
      keyMap.put("LMB", -100);
      keyMap.put("RMB", -101);
      keyMap.put("MB3", -102);
      keyMap.put("MB4", -103);
      keyMap.put("MB5", -104);
   }

   private static void reverseMappings() {
      for (Entry<String, Integer> entry : keyMap.entrySet()) {
         reverseKeyMap.put(entry.getValue(), entry.getKey());
      }
   }

   static {
      getBindCMD();
      reverseMappings();
   }
}
