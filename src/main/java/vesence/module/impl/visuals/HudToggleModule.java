package vesence.module.impl.visuals;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.Vesence;
import vesence.module.api.Category;
import vesence.module.api.Module;

@Environment(EnvType.CLIENT)
public class HudToggleModule extends Module {

   private final String elementName;
   private boolean settingsAttached = false;

   public HudToggleModule(String displayName, String elementName) {
      super(displayName, Category.DISPLAY);
      this.elementName = elementName;

      this.enable = true;
      attachElementSettings();
   }

   private Hud hud() {
      if (Vesence.get == null || Vesence.get.manager == null) {
         return null;
      }
      return Vesence.get.manager.get(Hud.class);
   }

   private HudElement element() {
      Hud hud = hud();
      if (hud == null) {
         return null;
      }
      for (HudElement element : hud.getHudElements()) {
         if (element.name.equals(elementName)) {
            return element;
         }
      }
      return null;
   }

   public void attachElementSettings() {
      if (settingsAttached) {
         return;
      }
      HudElement element = element();
      if (element == null) {
         return;
      }

      addSetting(element.scaleSetting);

      for (var s : element.settings) {
         addSetting(s);
      }
      for (var s : element.modeSettings) {
         addSetting(s);
      }
      for (var s : element.multiBooleanSettings) {
         addSetting(s);
      }
      for (var s : element.bottomSettings) {
         addSetting(s);
      }
      for (var s : element.bottomModeSettings) {
         addSetting(s);
      }
      for (var s : element.bottomMultiBooleanSettings) {
         addSetting(s);
      }

      settingsAttached = true;
   }

   public void syncFromHud() {
      Hud hud = hud();
      if (hud != null) {
         this.enable = hud.isElementEnabled(elementName);
      }
   }

   @Override
   public void onEnable() {
      super.onEnable();
      Hud hud = hud();
      if (hud != null) {
         hud.setElementEnabled(elementName, true);

         if (!hud.enable) {
            hud.toggle();
         }
      }
   }

   @Override
   public void onDisable() {
      super.onDisable();
      Hud hud = hud();
      if (hud != null) {
         hud.setElementEnabled(elementName, false);
      }
   }
}
