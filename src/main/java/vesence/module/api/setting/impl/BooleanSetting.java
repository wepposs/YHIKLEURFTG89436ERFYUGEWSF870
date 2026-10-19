package vesence.module.api.setting.impl;

import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.module.api.setting.Setting;
import vesence.utils.render.math.animation.anim.util.Animation2;

@Environment(EnvType.CLIENT)
public class BooleanSetting extends Setting<Boolean> {
   private boolean enabled;
   private final boolean defaultValue;
   public Animation2 anim = new Animation2();

   public BooleanSetting(String name, boolean enabled) {
      this.name = name;
      this.enabled = enabled;
      this.defaultValue = enabled;
   }

   @Override
   public Boolean get() {
      return this.enabled;
   }

   public boolean getDefault() {
      return this.defaultValue;
   }

   public void set(boolean state) {
      this.enabled = state;
   }

   public BooleanSetting hidden(Supplier<Boolean> hidden) {
      this.hidden = hidden;
      return this;
   }
}
