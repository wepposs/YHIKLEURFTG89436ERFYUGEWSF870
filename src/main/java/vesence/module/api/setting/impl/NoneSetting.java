package vesence.module.api.setting.impl;

import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.module.api.setting.Setting;

@Environment(EnvType.CLIENT)
public class NoneSetting extends Setting<Float> {
   public float up;

   public NoneSetting(float up) {
      this.up = up;
   }

   public NoneSetting() {
      this.up = 15.0F;
   }

   public NoneSetting hidden(Supplier<Boolean> hidden) {
      this.hidden = hidden;
      return this;
   }

   @Override
   public Float get() {
      return this.up;
   }
}
