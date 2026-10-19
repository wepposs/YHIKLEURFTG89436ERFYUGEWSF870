package vesence.module.impl.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.ItemStack;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;

@IModule(
   name = "NoEntityTrace",
   description = "Позволяет взаимодействовать с контейнерами сквозь сущности",
   category = Category.PLAYER,
   bind = -1
)
@Environment(EnvType.CLIENT)
public class NoEntityTrace extends Module {

   private final BooleanSetting noSword = new BooleanSetting("Выключать с мечом", true);
   private static NoEntityTrace instance;

   public NoEntityTrace() {
      this.addSettings(new Setting[]{noSword});
      instance = this;
   }

   public static NoEntityTrace getInstance() {
      return instance;
   }

   public boolean shouldIgnoreEntityTrace() {
      if (!this.enable || mc.player == null) return false;
      if (!noSword.get()) return true;

      ItemStack stack = mc.player.getMainHandStack();
      String key = stack.getItem().getTranslationKey().toLowerCase();
      return !key.contains("sword");
   }
}
