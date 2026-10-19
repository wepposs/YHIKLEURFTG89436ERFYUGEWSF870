package vesence.module.api.setting;

import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;

@Environment(EnvType.CLIENT)
public class Setting<T> extends Config {
   public String name;
   public Supplier<Boolean> hidden = () -> false;
   public Animation2 visibilityAnim = new Animation2();

   public Setting() {
      visibilityAnim.set(1.0);
   }

   public Setting(String name) {
      this.name = name;
      visibilityAnim.set(1.0);
   }

   public T get() {
      return null;
   }

   public float getVisibility() {
      return hidden.get() ? 0.0f : 1.0f;
   }
}
