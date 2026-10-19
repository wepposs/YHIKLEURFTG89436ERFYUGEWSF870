package vesence.utils.cfg;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.apache.commons.io.FilenameUtils;
import vesence.Vesence;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

@Environment(EnvType.CLIENT)
public final class ConfigManager extends Manager<Config> {
   private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final String OS = System.getProperty("os.name").toLowerCase();
   private static final boolean IS_WINDOWS = OS.contains("win");
   private static final boolean IS_LINUX = OS.contains("nix") || OS.contains("nux") || OS.contains("aix");
   public static final File configDirectory = getConfigDirectory();
   private static final ArrayList<Config> loadedConfigs = new ArrayList<>();

   private static File getConfigDirectory() {
      if (IS_LINUX) {
         String homeDir = System.getProperty("user.home");
         return new File(homeDir, ".vesence" + File.separator + "configs" + File.separator + "cfg");
      } else {
         return new File(Vesence.get.root, "configs" + File.separator + "cfg");
      }
   }

   public ConfigManager() {
      this.setContents(loadConfigs());
      configDirectory.mkdirs();
   }

   private static ArrayList<Config> loadConfigs() {
      File[] files = configDirectory.listFiles();
      if (files != null) {
         for (File file : files) {
            if (FilenameUtils.getExtension(file.getName()).equals("json")) {
               loadedConfigs.add(new Config(FilenameUtils.removeExtension(file.getName())));
            }
         }
      }

      return loadedConfigs;
   }

   public static ArrayList<Config> getLoadedConfigs() {
      return loadedConfigs;
   }

   public void load() {
      if (!configDirectory.exists()) {
         configDirectory.mkdirs();
      }

      if (configDirectory != null) {
         File[] files = configDirectory.listFiles(fx -> !fx.isDirectory() && FilenameUtils.getExtension(fx.getName()).equals("json"));

         for (File f : files) {
            String configName = FilenameUtils.removeExtension(f.getName()).replace(" ", "");

            if (findConfig(configName) == null) {
               Config config = new Config(configName);
               loadedConfigs.add(config);
            }
         }
      }
   }

   public boolean loadConfig(String configName) {
      if (configName == null) {
         return false;
      } else {
         Config config = this.findConfig(configName);
         if (config == null) {
            return false;
         } else {
            try {
               boolean var6;
               try (FileReader reader = new FileReader(config.getFile())) {
                  JsonParser parser = new JsonParser();
                  JsonObject object = (JsonObject)parser.parse(reader);
                  config.load(object);
                  var6 = true;
               }

               return var6;
            } catch (IOException var9) {
               return false;
            } catch (Exception var10) {
               // Повреждённый/обрезанный JSON конфига (например, прерванная запись)
               // не должен крашить клиент — просто пропускаем загрузку этого конфига.
               System.err.println("[weppos] Failed to parse config '" + configName + "': " + var10.getMessage());
               return false;
            }
         }
      }
   }

   public boolean saveConfig(String configName) {
      if (configName == null) {
         return false;
      } else {
         Config config;
         if ((config = this.findConfig(configName)) == null) {
            Config newConfig = config = new Config(configName);
            this.getContents().add(newConfig);
         }

         String contentPrettyPrint = PRETTY_GSON.toJson(config.save());

         try {
            boolean var5;
            try (FileWriter writer = new FileWriter(config.getFile())) {
               writer.write(contentPrettyPrint);
               var5 = true;
            }

            return var5;
         } catch (IOException var9) {
            return false;
         }
      }
   }

   public Config findConfig(String configName) {
      if (configName == null) {
         return null;
      } else {
         for (Config config : this.getContents()) {
            if (config.getName().equalsIgnoreCase(configName)) {
               return config;
            }
         }

         return new File(configDirectory, configName + ".json").exists() ? new Config(configName) : null;
      }
   }

   public boolean deleteConfig(String configName) {
      if (configName == null) {
         return false;
      } else {
         Config config;
         if ((config = this.findConfig(configName)) == null) {
            return false;
         } else {
            File f = config.getFile();
            this.getContents().remove(config);
            return f.exists() && f.delete();
         }
      }
   }

   // Флаг активного сохранения — не запускаем новый поток пока предыдущий ещё идёт.
   private volatile boolean saving = false;

   public void autoSave() {
      if (Vesence.get.configManager == null) return;
      if (saving) return;  // сохранение уже идёт, пропускаем лишний вызов
      saving = true;
      // Сохраняем в фоновом потоке, чтобы не блокировать главный поток
      // (иначе при смене мира/смерти FileWriter вешает экран «Загрузка территории»).
      Thread saveThread = new Thread(() -> {
         try {
            this.saveConfig("default");
         } catch (Throwable ignored) {
         } finally {
            saving = false;
         }
      }, "vesence-autosave");
      saveThread.setDaemon(true);
      saveThread.start();
   }
}
