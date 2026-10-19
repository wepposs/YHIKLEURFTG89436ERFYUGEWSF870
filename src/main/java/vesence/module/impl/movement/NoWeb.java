package vesence.module.impl.movement;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.Vec3d;
import vesence.event.EventInit;
import vesence.event.impl.EventUpdate;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.utils.other.Mathf;
import vesence.utils.player.MoveUtil;
import vesence.utils.player.PlayerUtil;

@IModule(
   name = "NoWeb",
   description = "Позволяет быстро передвигаться в паутине",
   category = Category.MOVEMENT,
   bind = -1
)
@Environment(EnvType.CLIENT)
public class NoWeb extends Module {

   private final ModeSetting mode = new ModeSetting("Режим", "ReallyWorld", "ReallyWorld");
   private final BooleanSetting ignoreBreaking = new BooleanSetting("Игнор. ломание", false);

   public NoWeb() {
      this.addSettings(new Setting[]{mode, ignoreBreaking});
   }

   @EventInit
   public void onUpdate(EventUpdate e) {
      if (mc.player == null || mc.world == null) return;
      if (!PlayerUtil.isPlayerInWeb()) return;

      if (mode.is("ReallyWorld")) {
         double y = 0.0;
         if (mc.options.jumpKey.isPressed()) { y = 1.2; }
         else if (mc.options.sneakKey.isPressed()) { y = -2.0; }
         double[] speed = MoveUtil.calculateDirection(Mathf.random(0.62F, 0.64F));
         mc.player.setVelocity(speed[0], y, speed[1]);
      }
   }

   @Override
   public void onEnable() {
      super.onEnable();
   }

   @Override
   public void onDisable() {
      super.onDisable();
   }

   public static NoWeb getInstance() {
      return (NoWeb) vesence.Vesence.get.manager.getModule(NoWeb.class);
   }
}
