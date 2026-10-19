package vesence.module.impl.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.MultiBooleanSetting;

@IModule(
   name = "Criticals",
   description = "Модификация критических ударов",
   category = Category.COMBAT,
   bind = -1
)
@Environment(EnvType.CLIENT)
public class Criticals extends Module {

   public final MultiBooleanSetting modes = new MultiBooleanSetting("Режимы",
      new BooleanSetting("Плавно падение", true),
      new BooleanSetting("Паутина", false)
   );

   public Criticals() {
      this.addSettings(new Setting[]{modes});
   }

   public static Criticals getInstance() {
      return (Criticals) vesence.Vesence.get.manager.getModule(Criticals.class);
   }

   public boolean isSmoothFall() {
      return modes.get("Плавно падение");
   }

   public boolean isWeb() {
      return modes.get("Паутина");
   }
}
