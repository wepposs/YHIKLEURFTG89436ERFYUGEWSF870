package vesence.module.impl.visuals;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import com.mojang.blaze3d.platform.InputConstants;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BindSettings;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.api.setting.impl.SliderSetting;

@IModule(name = "Living Hands", description = "Анимации живых рук от первого лица (Hold My Items)", category = Category.VISUALS, bind = -1)
@Environment(EnvType.CLIENT)
public class LivingHands extends Module {

   public static LivingHands INSTANCE;

   /**
    * Флаг «последний удар был критическим». Выставляется из
    * {@link vesence.hmi.mixin.MinecraftClientMixin} в момент атаки (HEAD doAttack),
    * где ещё виден полный кулдаун и цель, и читается Lua-скриптом через {@link #getCrit()}.
    */
   private static volatile boolean lastAttackCrit = false;

   private final ModeSetting animationType = new ModeSetting("Тип анимации", "Дефолт", "Дефолт", "Новый");

   private final SliderSetting mainX = new SliderSetting("Основная рука X", 0.0, -1.0, 1.0, 0.01);
   private final SliderSetting mainY = new SliderSetting("Основная рука Y", 0.0, -1.0, 1.0, 0.01);
   private final SliderSetting mainZ = new SliderSetting("Основная рука Z", 0.0, -1.0, 1.0, 0.01);
   private final SliderSetting offX = new SliderSetting("Второстепенная рука X", 0.0, -1.0, 1.0, 0.01);
   private final SliderSetting offY = new SliderSetting("Второстепенная рука Y", 0.0, -1.0, 1.0, 0.01);
   private final SliderSetting offZ = new SliderSetting("Второстепенная рука Z", 0.0, -1.0, 1.0, 0.01);

   private final BindSettings inspectKey = new BindSettings("Клавиша осмотра", InputConstants.KEY_C);

   public LivingHands() {
      INSTANCE = this;
      this.addSettings(new Setting[]{animationType, mainX, mainY, mainZ, offX, offY, offZ, inspectKey});
   }

   public static boolean isEnabled() {
      return INSTANCE != null && INSTANCE.enable;
   }

   public static int getSwingStyle() {
      return 0;
   }

   /**
    * Режим анимации для Lua-скрипта: 0 — «Дефолт» (ванильный порт HMI),
    * 1 — «Новый» (проработанная боевая анимация ударов и критов).
    */
   public static int getAnimationMode() {
      return (INSTANCE != null && INSTANCE.animationType.is("Новый")) ? 1 : 0;
   }

   /** Сообщить движку, что только что выполненная атака была критом (вызывается из миксина). */
   public static void notifyAttackCrit(boolean crit) {
      lastAttackCrit = crit;
   }

   /** Сила крита для Lua (0.0 — обычный удар, 1.0 — крит). */
   public static double getCrit() {
      return lastAttackCrit ? 1.0 : 0.0;
   }

   public static double getMainX() { return INSTANCE == null ? 0.0 : INSTANCE.mainX.get(); }
   public static double getMainY() { return INSTANCE == null ? 0.0 : INSTANCE.mainY.get(); }
   public static double getMainZ() { return INSTANCE == null ? 0.0 : INSTANCE.mainZ.get(); }
   public static double getOffX() { return INSTANCE == null ? 0.0 : INSTANCE.offX.get(); }
   public static double getOffY() { return INSTANCE == null ? 0.0 : INSTANCE.offY.get(); }
   public static double getOffZ() { return INSTANCE == null ? 0.0 : INSTANCE.offZ.get(); }

   public static int getInspectKey() {
      return INSTANCE == null ? InputConstants.KEY_C : INSTANCE.inspectKey.key;
   }
}
