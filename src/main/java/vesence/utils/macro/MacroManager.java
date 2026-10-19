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
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
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
      if (key == GLFW.GLFW_KEY_UNKNOWN || command == null || command.isBlank()) {
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
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player == null || mc.player.networkHandler == null) {
         return;
      }
      try {
         if (command.startsWith("/")) {
            mc.player.networkHandler.sendChatCommand(command.substring(1));
         } else {
            mc.player.networkHandler.sendChatMessage(command);
         }
      } catch (Exception ignored) {
      }
   }

   public static String keyName(int key) {
      String name = GLFW.glfwGetKeyName(key, 0);
      if (name != null && !name.isEmpty()) {
         return name.toUpperCase(java.util.Locale.ROOT);
      }
      return "KEY_" + key;
   }

   public static int keyToGlfw(String s) {
      if (s == null || s.isEmpty()) {
         return GLFW.GLFW_KEY_UNKNOWN;
      }
      String up = s.trim().toUpperCase(java.util.Locale.ROOT);
      if (up.length() == 1) {
         char c = up.charAt(0);
         if (c >= 'A' && c <= 'Z') {
            return GLFW.GLFW_KEY_A + (c - 'A');
         }
         if (c >= '0' && c <= '9') {
            return GLFW.GLFW_KEY_0 + (c - '0');
         }
      }

      switch (up) {
         case "SPACE": return GLFW.GLFW_KEY_SPACE;
         case "ENTER": case "RETURN": return GLFW.GLFW_KEY_ENTER;
         case "TAB": return GLFW.GLFW_KEY_TAB;
         case "LSHIFT": return GLFW.GLFW_KEY_LEFT_SHIFT;
         case "RSHIFT": return GLFW.GLFW_KEY_RIGHT_SHIFT;
         case "LCTRL": case "LCONTROL": return GLFW.GLFW_KEY_LEFT_CONTROL;
         case "RCTRL": case "RCONTROL": return GLFW.GLFW_KEY_RIGHT_CONTROL;
         default: return GLFW.GLFW_KEY_UNKNOWN;
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
            try (FileReader reader = new FileReader(f)) {
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
