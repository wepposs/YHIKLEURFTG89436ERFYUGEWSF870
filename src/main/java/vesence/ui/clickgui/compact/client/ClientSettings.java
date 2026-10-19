package vesence.ui.clickgui.compact.client;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.module.api.setting.Setting;
import vesence.module.impl.misc.ClickGui;

@Environment(EnvType.CLIENT)
public final class ClientSettings {

   private static final List<Setting<?>> SETTINGS = new ArrayList<>();
   private static boolean initialized = false;

   private ClientSettings() {
   }

   public static <T extends Setting<?>> T add(T setting) {
      SETTINGS.add(setting);
      return setting;
   }

   public static List<Setting<?>> getSettings() {
      ensureInit();
      return SETTINGS;
   }

   private static void ensureInit() {
      if (initialized) return;
      initialized = true;

      add(ClickGui.basic);
      add(ClickGui.blurHud);
      add(ClickGui.blurStrengthHud);
      add(ClickGui.cornerHud);
      add(ClickGui.hudAlpha);
      add(ClickGui.hudVariation);
      add(ClickGui.squircleHud);

      add(ClickGui.gui);
      add(ClickGui.blurGui);
      add(ClickGui.blurStrengthGui);
      add(ClickGui.cornerGui);
      add(ClickGui.squircleGui);
      add(ClickGui.sort);
      add(ClickGui.guiScale);
      add(ClickGui.guiAlpha);
      add(ClickGui.colorTitle);
      add(ClickGui.colorMode);
      add(ClickGui.customColor1);
      add(ClickGui.customColor2);
      add(ClickGui.moduleSoundTitle);
      add(ClickGui.moduleEnableSound);
      add(ClickGui.moduleSoundVolume);
   }
}
