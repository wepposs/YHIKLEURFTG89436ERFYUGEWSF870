package vesence.utils.render.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;

@Environment(EnvType.CLIENT)
public class KeyUtil {
   public static final Map<String, Integer> keyMap = new HashMap<>();
   public static final Map<Integer, String> reverseKeyMap = new HashMap<>();
   public static MinecraftClient mc = MinecraftClient.getInstance();

   public static boolean isKeyDown(int keyCode) {
      return InputUtil.isKeyPressed(mc.getWindow(), keyCode);
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

      keyMap.put("A", 65);
      keyMap.put("B", 66);
      keyMap.put("C", 67);
      keyMap.put("D", 68);
      keyMap.put("E", 69);
      keyMap.put("F", 70);
      keyMap.put("G", 71);
      keyMap.put("H", 72);
      keyMap.put("I", 73);
      keyMap.put("J", 74);
      keyMap.put("K", 75);
      keyMap.put("L", 76);
      keyMap.put("M", 77);
      keyMap.put("N", 78);
      keyMap.put("O", 79);
      keyMap.put("P", 80);
      keyMap.put("Q", 81);
      keyMap.put("R", 82);
      keyMap.put("S", 83);
      keyMap.put("T", 84);
      keyMap.put("U", 85);
      keyMap.put("V", 86);
      keyMap.put("W", 87);
      keyMap.put("X", 88);
      keyMap.put("Y", 89);
      keyMap.put("Z", 90);

      keyMap.put("0", 48);
      keyMap.put("1", 49);
      keyMap.put("2", 50);
      keyMap.put("3", 51);
      keyMap.put("4", 52);
      keyMap.put("5", 53);
      keyMap.put("6", 54);
      keyMap.put("7", 55);
      keyMap.put("8", 56);
      keyMap.put("9", 57);

      keyMap.put("F1", 290);
      keyMap.put("F2", 291);
      keyMap.put("F3", 292);
      keyMap.put("F4", 293);
      keyMap.put("F5", 294);
      keyMap.put("F6", 295);
      keyMap.put("F7", 296);
      keyMap.put("F8", 297);
      keyMap.put("F9", 298);
      keyMap.put("F10", 299);
      keyMap.put("F11", 300);
      keyMap.put("F12", 301);

      keyMap.put("NUM0", 320);
      keyMap.put("NUM1", 321);
      keyMap.put("NUM2", 322);
      keyMap.put("NUM3", 323);
      keyMap.put("NUM4", 324);
      keyMap.put("NUM5", 325);
      keyMap.put("NUM6", 326);
      keyMap.put("NUM7", 327);
      keyMap.put("NUM8", 328);
      keyMap.put("NUM9", 329);
      keyMap.put("NUMDECIMAL", 330);
      keyMap.put("NUMDIVIDE", 331);
      keyMap.put("NUMMULTIPLY", 332);
      keyMap.put("NUMSUBTRACT", 333);
      keyMap.put("NUMADD", 334);
      keyMap.put("NUMENTER", 335);
      keyMap.put("NUMEQUAL", 336);

      keyMap.put("SPACE", 32);
      keyMap.put("ENTER", 257);
      keyMap.put("ESCAPE", 256);
      keyMap.put("TAB", 258);
      keyMap.put("BACKSPACE", 259);
      keyMap.put("INSERT", 260);
      keyMap.put("DELETE", 261);
      keyMap.put("HOME", 268);
      keyMap.put("END", 269);
      keyMap.put("PAGEUP", 266);
      keyMap.put("PAGEDOWN", 267);
      keyMap.put("RIGHT", 262);
      keyMap.put("LEFT", 263);
      keyMap.put("DOWN", 264);
      keyMap.put("UP", 265);

      keyMap.put("LSHIFT", 340);
      keyMap.put("LCTRL", 341);
      keyMap.put("LALT", 342);
      keyMap.put("LSUPER", 343);
      keyMap.put("RSHIFT", 344);
      keyMap.put("RCTRL", 345);
      keyMap.put("RALT", 346);
      keyMap.put("RSUPER", 347);
      keyMap.put("MENU", 348);

      keyMap.put("CAPSLOCK", 280);
      keyMap.put("NUMLOCK", 282);
      keyMap.put("SCROLLLOCK", 281);
      keyMap.put("PRINTSCREEN", 283);
      keyMap.put("PAUSE", 284);

      keyMap.put("APOSTROPHE", 39);
      keyMap.put("COMMA", 44);
      keyMap.put("MINUS", 45);
      keyMap.put("PERIOD", 46);
      keyMap.put("SLASH", 47);
      keyMap.put("SEMICOLON", 59);
      keyMap.put("EQUAL", 61);
      keyMap.put("LBRACKET", 91);
      keyMap.put("BACKSLASH", 92);
      keyMap.put("RBRACKET", 93);
      keyMap.put("GRAVE", 96);

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
