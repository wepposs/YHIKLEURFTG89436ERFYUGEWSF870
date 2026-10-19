package vesence.utils.config;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import vesence.Vesence;
import vesence.module.Theme;
import vesence.module.api.Category;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;

@Environment(EnvType.CLIENT)
public class GuiManager {
   public static MinecraftClient mc = MinecraftClient.getInstance();
   private File file;
   private Theme currentTheme = Theme.Blue;
   private Category currentCategory = Category.VISUALS;
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

   public Theme getCurrentTheme() {
      return this.currentTheme;
   }

   public Category getCurrentCategory() {
      return this.currentCategory;
   }

   public void setCategoryScroll(Category category, float scroll) {
      if (category == null) {
         return;
      }

      this.categoryScrolls.put(category, scroll);
      this.saveSettings();
   }

   public float getCategoryScroll(Category category) {
      if (category == null) {
         return 0.0F;
      }

      return this.categoryScrolls.getOrDefault(category, 0.0F);
   }

   private void saveSettings() {
      try (FileWriter writer = new FileWriter(this.file)) {
         Properties props = new Properties();
         props.setProperty("theme", this.currentTheme.name());
         props.setProperty("category", this.currentCategory.name());
         for (Category category : Category.values()) {
            props.setProperty("scroll." + category.name(), Float.toString(this.getCategoryScroll(category)));
         }
         props.store(writer, "GUI Settings");
      } catch (IOException var6) {
         var6.printStackTrace();
      }
   }

   private void readSettings() {
      try (FileReader reader = new FileReader(this.file)) {
         Properties props = new Properties();
         props.load(reader);
         this.currentTheme = Theme.valueOf(props.getProperty("theme", Theme.Blue.name()));
         this.currentCategory = Category.valueOf(props.getProperty("category", Category.VISUALS.name()));
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
