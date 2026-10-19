package vesence.utils.render.utils;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.module.api.setting.impl.TitleSetting;

@Environment(EnvType.CLIENT)
public final class ClientSound {
   private static final String NONE = "Без звука";
   private static final String DEFAULT = "1";
   private static final String MODULE = "2";
   private static final String CHECKBOX = "3";

   public static final TitleSetting moduleSoundTitle = new TitleSetting("Звуки модулей");
   public static final ModeSetting moduleSound = new ModeSetting(
         "Звук",
         DEFAULT,
         DEFAULT,
         MODULE,
         CHECKBOX,
         NONE
   );

   public static final SliderSetting moduleSoundVolume = new SliderSetting(
         "Громкость звука",
         1.0,
         0.0,
         1.0,
         0.05
   ).hidden(() -> moduleSound.is(NONE));

   private ClientSound() {
   }

   public static void playModuleToggle(boolean enabled) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client == null || client.player == null) {
         return;
      }

      String sound = enabled ? resolveEnableSound() : resolveDisableSound();
      if (sound == null) {
         return;
      }

      SoundUtil.playUi(sound, moduleSoundVolume.get().floatValue(), 20L);
   }

   private static String resolveEnableSound() {
      if (moduleSound.is(DEFAULT)) {
         return "on";
      }
      if (moduleSound.is(MODULE)) {
         return "module_enable";
      }
      if (moduleSound.is(CHECKBOX)) {
         return "cb_on";
      }
      return null;
   }

   private static String resolveDisableSound() {
      if (moduleSound.is(DEFAULT)) {
         return "off";
      }
      if (moduleSound.is(MODULE)) {
         return "module_disable";
      }
      if (moduleSound.is(CHECKBOX)) {
         return "cb_off";
      }
      return null;
   }
}
