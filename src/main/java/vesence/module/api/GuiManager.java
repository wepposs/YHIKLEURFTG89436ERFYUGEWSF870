package vesence.module.api;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import vesence.Vesence;
import vesence.module.Theme;

@Environment(EnvType.CLIENT)
public class GuiManager {
   public static Minecraft mc = Minecraft.getInstance();
   private File file;
   private Theme currentTheme = Theme.Blue;
   private Category currentCategory = Category.VISUALS;
   private String currentModule = "";
   private final Map<Category, Float> categoryScrolls = new EnumMap<>(Category.class);

   public void init() {
      this.file = new File(Vesence.get.root + "\\configs", "gui.cfg");

      try {
         if (!this.file.getParentFile().exists()) {
            this.file.getParentFile().mkdirs();
         }

         if (!this.file.exists()) {
            this.file.createNewFile();
            this.saveSettings();
         } else {
            this.readSettings();
         }
      } catch (Exception var2) {
         var2.printStackTrace();
      }
   }

   public void setGuiTheme(Theme theme) {
      this.currentTheme = theme;
      this.saveSettings();
   }

   public void setGuiCategory(Category category) {
      this.currentCategory = category;
      this.saveSettings();
   }

   public void saveGuiState(Category category, Map<Category, Float> scrolls) {
      if (category != null) {
         this.currentCategory = category;
      }

      if (scrolls != null) {
         this.categoryScrolls.clear();
         this.categoryScrolls.putAll(scrolls);
      }

      this.saveSettings();
   }

   /**
    * Полное сохранение состояния ClickGui: категория, последний открытый
    * модуль и скроллы. Вызывается при закрытии GUI.
    */
   public void saveGuiState(Category category, String moduleName, Map<Category, Float> scrolls) {
      if (moduleName != null) {
         this.currentModule = moduleName;
      } else {
         this.currentModule = "";
      }
      this.saveGuiState(category, scrolls);
   }

   public void setCurrentModule(String moduleName) {
      this.currentModule = moduleName != null ? moduleName : "";
      this.saveSettings();
   }

   public String getCurrentModule() {
      return this.currentModule;
   }

   public Theme getCurrentTheme() {
      return this.currentTheme;
   }

   public Category getCurrentCategory() {
      return this.currentCategory;
   }

   public float getCategoryScroll(Category category) {
      if (category == null) {
         return 0.0F;
      }

      return this.categoryScrolls.getOrDefault(category, 0.0F);
   }

   private void saveSettings() {
      try (FileWriter writer = new FileWriter(this.file, java.nio.charset.StandardCharsets.UTF_8)) {
         Properties props = new Properties();
         props.setProperty("theme", this.currentTheme.name());
         props.setProperty("category", this.currentCategory.name());
         props.setProperty("module", this.currentModule != null ? this.currentModule : "");
         for (Category category : Category.values()) {
            props.setProperty("scroll." + category.name(), Float.toString(this.getCategoryScroll(category)));
         }
         props.store(writer, "GUI Settings");
      } catch (IOException var6) {
         var6.printStackTrace();
      }
   }

   private void readSettings() {
      try (FileReader reader = new FileReader(this.file, java.nio.charset.StandardCharsets.UTF_8)) {
         Properties props = new Properties();
         props.load(reader);
         this.currentTheme = Theme.valueOf(props.getProperty("theme", Theme.Blue.name()));
         this.currentCategory = Category.valueOf(props.getProperty("category", Category.VISUALS.name()));
         this.currentModule = props.getProperty("module", "");
         this.categoryScrolls.clear();
         for (Category category : Category.values()) {
            String scrollValue = props.getProperty("scroll." + category.name());
            if (scrollValue != null) {
               this.categoryScrolls.put(category, Float.parseFloat(scrollValue));
            }
         }
      } catch (IllegalArgumentException | IOException var6) {
         var6.printStackTrace();
      }
   }
}
