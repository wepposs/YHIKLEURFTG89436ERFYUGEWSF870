package vesence.utils.cfg;

import com.google.gson.JsonObject;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.Vesence;
import vesence.module.api.Module;
import vesence.renderengine.utils.animation.Animation;
import vesence.renderengine.utils.animation.impl.EaseInOutQuad;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

@Environment(EnvType.CLIENT)
public final class Config implements ConfigUpdater {
   private final String name;
   private final File file;
   public Animation animation1 = new EaseInOutQuad(500, 1.0);
   public Animation animation2 = new EaseInOutQuad(300, 1.0);
   public Animation animation3 = new EaseInOutQuad(300, 1.0);
   public Animation animation4 = new EaseInOutQuad(300, 1.0);
   public Animation animation5 = new EaseInOutQuad(500, 1.0);

   public Config(String name) {
      this.name = name;
      this.file = new File(ConfigManager.configDirectory, name + ".json");
      if (!this.file.exists()) {
         try {
            this.file.createNewFile();
         } catch (Exception var3) {
         }
      }
   }

   public File getFile() {
      return this.file;
   }

   public String getName() {
      return this.name;
   }

   @Override
   public JsonObject save() {
      JsonObject jsonObject = new JsonObject();
      JsonObject modulesObject = new JsonObject();

      for (Module module : Vesence.get.manager.module) {
         modulesObject.add(module.name, module.save());
      }

      jsonObject.add("Features", modulesObject);
      JsonObject draggablePositions = new JsonObject();
      Map<String, JsonObject> elementStates = DraggableManager.getInstance().snapshotElementStates();

      for (Entry<String, JsonObject> entry : elementStates.entrySet()) {
         draggablePositions.add(entry.getKey(), entry.getValue());
      }

      jsonObject.add("DraggablePositions", draggablePositions);
      // 26.3: помечаем формат биндов, чтобы старые конфиги (GLFW-коды) мигрировали
      // на SDL-сканкоды ровно один раз — см. KeyCodeMigration.
      jsonObject.addProperty(vesence.utils.other.KeyCodeMigration.FORMAT_FIELD,
              vesence.utils.other.KeyCodeMigration.FORMAT_SDL);
      return jsonObject;
   }

   @Override
   public void load(JsonObject object) {
      System.out.println("[Config] Loading config: " + this.name);
      // Старый конфиг (до порта на 26.3) хранил GLFW-коды клавиш; новые — SDL-сканкоды.
      // Конвертируем только если файл ЯВНО помечен как glfw: конфиги без маркера писались
      // уже портированным клиентом, т.е. содержат SDL-коды, и трогать их нельзя.
      String format = object.has(vesence.utils.other.KeyCodeMigration.FORMAT_FIELD)
              ? object.get(vesence.utils.other.KeyCodeMigration.FORMAT_FIELD).getAsString()
              : vesence.utils.other.KeyCodeMigration.FORMAT_SDL;
      boolean legacy = vesence.utils.other.KeyCodeMigration.FORMAT_GLFW.equalsIgnoreCase(format);
      vesence.utils.other.KeyCodeMigration.beginLoad(legacy);
      try {
         loadModules(object);
      } finally {
         vesence.utils.other.KeyCodeMigration.endLoad();
      }

      if (legacy) {
         // Пересохраняем сразу, чтобы миграция не повторялась при следующем запуске.
         System.out.println("[Config] Migrating legacy GLFW keycodes to SDL scancodes: " + this.name);
         try {
            java.io.File tmp = new java.io.File(this.file.getParentFile(),
                    this.file.getName() + "." + System.nanoTime() + ".tmp");
            try (FileWriter writer = new FileWriter(tmp)) {
               writer.write(ConfigManager.PRETTY_GSON.toJson(this.save()));
               writer.flush();
            }
            java.nio.file.Files.move(tmp.toPath(), this.file.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
         } catch (IOException e) {
            System.out.println("[Config] Could not rewrite migrated config: " + e.getMessage());
         }
      }
   }

   private void loadModules(JsonObject object) {
      if (object.has("Features")) {
         JsonObject modulesObject = object.getAsJsonObject("Features");
         int enabledCount = 0;

         for (Module module : Vesence.get.manager.module) {
            if (module.enable) {
               module.setState(false);
            }

            if (modulesObject.has(module.name)) {
               module.load(modulesObject.getAsJsonObject(module.name));
               if (module.enable) {
                  enabledCount++;
                  System.out.println("[Config] Module enabled: " + module.name);
               }
            } else if (module.defaultEnabled) {
               module.setState(true);
               enabledCount++;
               System.out.println("[Config] Module enabled by default: " + module.name);
            }
         }

         System.out.println("[Config] Total modules enabled: " + enabledCount);
      }

      if (object.has("DraggablePositions")) {
         JsonObject draggablePositions = object.getAsJsonObject("DraggablePositions");
         Map<String, JsonObject> elementStates = new HashMap<>();

         for (String key : draggablePositions.keySet()) {
            JsonObject posObject = draggablePositions.getAsJsonObject(key);
            elementStates.put(key, posObject);
         }

         DraggableManager.getInstance().loadElementStates(elementStates);
         System.out.println("[Config] Loaded " + elementStates.size() + " element states");
      }
   }
}
