package vesence.module.api;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.utils.render.math.animation.anim.util.Animation2;

@Environment(EnvType.CLIENT)
public enum Category {
   COMBAT("Combat", "Битва", "b"),
   MOVEMENT("Movement", "Движение", "c"),
   VISUALS("Visuals", "Визуал", "f"),
   PLAYER("Player", "Игрок", "e"),
   MISC("Misc", "Утилиты", "w"),
   DISPLAY("Display", "Дисплей", "p");

   private final String name;
   private final String russian_name;
   private final String icon;
   public Animation2 anim33 = new Animation2();
   public Animation2 anim44 = new Animation2();

   private Category(String name, String russian_name, String icon) {
      this.name = name;
      this.russian_name = russian_name;
      this.icon = icon;
   }

   public boolean isMain() {
      return this != DISPLAY;
   }

   public String getIcon() {
      return this.icon;
   }

   public String getName() {
      return this.name;
   }
   public String getRussianName() {
      return this.russian_name;
   }
}
