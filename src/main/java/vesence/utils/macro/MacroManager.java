package vesence.utils.macro;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.InputConstants;
import vesence.Vesence;

@Environment(EnvType.CLIENT)
public final class MacroManager {

   private static final MacroManager INSTANCE = new MacroManager();
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

   private final Map<Integer, String> macros = new LinkedHashMap<>();
   private File file;
   private boolean loaded = false;

   private MacroManager() {
   }

   public static MacroManager getInstance() {
      return INSTANCE;
   }

   private File file() {
      if (file == null) {
         File dir = new File(Vesence.get.root, "configs");
         dir.mkdirs();
         file = new File(dir, "macros.json");
      }
      return file;
   }

   public boolean add(String keyName, String command) {
      int key = keyToGlfw(keyName);
      if (key == 0 /* SDL_SCANCODE_UNKNOWN */ || command == null || command.isBlank()) {
         return false;
      }
      macros.put(key, command.trim());
      save();
      return true;
   }

   public boolean remove(String keyName) {
      int key = keyToGlfw(keyName);
      if (macros.remove(key) != null) {
         save();
         return true;
      }
      return false;
   }

   public void clear() {
      macros.clear();
      save();
   }

   public Map<Integer, String> getMacros() {
      ensureLoaded();
      return macros;
   }

   public void onKeyPress(int key) {
      ensureLoaded();
      String command = macros.get(key);
      if (command == null) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.player.connection == null) {
         return;
      }
      try {
         if (command.startsWith("/")) {
            mc.player.connection.sendCommand(command.substring(1));
         } else {
            mc.player.connection.sendChat(command);
         }
      } catch (Exception ignored) {
      }
   }

   public static String keyName(int key) {
      // 26.3: GLFW.glfwGetKeyName удалён, но InputConstants.Type.KEYBOARD даёт имя клавиши
      // по SDL-сканкоду — это и есть эквивалент.
      String name = InputConstants.Type.KEYBOARD.getOrCreate(key).getDisplayName().getString();
      if (name != null && !name.isEmpty()) {
         return name.toUpperCase(java.util.Locale.ROOT);
      }
      return "KEY_" + key;
   }

   public static int keyToGlfw(String s) {
      if (s == null || s.isEmpty()) {
         return 0 /* SDL_SCANCODE_UNKNOWN */;
      }
      String up = s.trim().toUpperCase(java.util.Locale.ROOT);
      if (up.length() == 1) {
         char c = up.charAt(0);
         if (c >= 'A' && c <= 'Z') {
            return InputConstants.KEY_A + (c - 'A');
         }
         if (c >= '0' && c <= '9') {
            // SDL-сканкоды цифр идут 1..9, затем 0 (KEY_1 = 30 … KEY_9 = 38, KEY_0 = 39).
            return c == '0' ? InputConstants.KEY_0 : InputConstants.KEY_1 + (c - '1');
         }
      }

      // Остальные имена (F1..F12, NUM*, ESCAPE, стрелки, модификаторы, пунктуация) —
      // из общей таблицы биндов, чтобы не дублировать сотню констант.
      Integer mapped = vesence.utils.render.utils.KeyUtil.keyMap.get(up);
      if (mapped != null) {
         return mapped;
      }

      switch (up) {
         case "SPACE": return InputConstants.KEY_SPACE;
         case "ENTER": case "RETURN": return InputConstants.KEY_RETURN;
         case "TAB": return InputConstants.KEY_TAB;
         case "LSHIFT": return InputConstants.KEY_LSHIFT;
         case "RSHIFT": return InputConstants.KEY_RSHIFT;
         case "LCTRL": case "LCONTROL": return InputConstants.KEY_LCONTROL;
         case "RCTRL": case "RCONTROL": return InputConstants.KEY_RCONTROL;
         default: return 0 /* SDL_SCANCODE_UNKNOWN */;
      }
   }

   private void ensureLoaded() {
      if (loaded) {
         return;
      }
      loaded = true;
      try {
         File f = file();
         if (f.exists()) {
            try (FileReader reader = new FileReader(f, java.nio.charset.StandardCharsets.UTF_8)) {
               Type type = new TypeToken<LinkedHashMap<String, String>>() {}.getType();
               Map<String, String> raw = GSON.fromJson(reader, type);
               if (raw != null) {
                  macros.clear();
                  for (Map.Entry<String, String> e : raw.entrySet()) {
                     try {
                        macros.put(Integer.parseInt(e.getKey()), e.getValue());
                     } catch (NumberFormatException ignored) {
                     }
                  }
               }
            }
         }
      } catch (Exception ignored) {
      }
   }

   private void save() {
      try {
         Map<String, String> raw = new LinkedHashMap<>();
         for (Map.Entry<Integer, String> e : macros.entrySet()) {
            raw.put(String.valueOf(e.getKey()), e.getValue());
         }
         try (FileWriter writer = new FileWriter(file())) {
            GSON.toJson(raw, writer);
         }
      } catch (Exception ignored) {
      }
   }
}
