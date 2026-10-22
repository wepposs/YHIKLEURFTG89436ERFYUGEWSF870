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
   static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();
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
         // Убираем недописанные .tmp от прерванных сохранений — они больше не нужны,
         // потому что рабочий файл подменяется атомарно.
         File[] stale = configDirectory.listFiles(fx -> fx.isFile() && fx.getName().endsWith(".tmp"));
         if (stale != null) {
            for (File s : stale) {
               s.delete();
            }
         }

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
               // Испорченный файл откладываем в .corrupt, чтобы его можно было
               // восстановить вручную, а не молча затереть при следующем автосейве.
               System.err.println("[weppos] Failed to parse config '" + configName + "': " + var10.getMessage());
               try {
                  java.nio.file.Files.move(config.getFile().toPath(),
                          new File(config.getFile().getParentFile(), config.getFile().getName() + ".corrupt").toPath(),
                          java.nio.file.StandardCopyOption.REPLACE_EXISTING);
               } catch (Exception ignored) {
               }
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
         // Атомарная запись: сначала во временный файл, затем подмена.
         // Раньше писали прямо в целевой файл на фоновом потоке автоСейва, и если
         // JVM завершалась посреди записи (быстрый выход, краш), конфиг оставался
         // обрезанным — при следующем запуске он не парсился и все настройки/бинды
         // терялись. Теперь недописанным может остаться только .tmp, а рабочий
         // файл всегда содержит либо старую, либо новую полную версию.
         java.io.File target = config.getFile();
         // Уникальное имя временного файла: автоСейв идёт в отдельном потоке, и два
         // одновременных сохранения не должны писать в один и тот же .tmp.
         java.io.File tmp = new java.io.File(target.getParentFile(),
                 target.getName() + "." + System.nanoTime() + ".tmp");

         try {
            try (FileWriter writer = new FileWriter(tmp, java.nio.charset.StandardCharsets.UTF_8)) {
               writer.write(contentPrettyPrint);
               writer.flush();
            }

            try {
               java.nio.file.Files.move(
                       tmp.toPath(), target.toPath(),
                       java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                       java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException notAtomic) {
               java.nio.file.Files.move(
                       tmp.toPath(), target.toPath(),
                       java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
         } catch (IOException var9) {
            tmp.delete();
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
