package vesence.module.api.setting.impl;

import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.module.api.setting.Setting;
import vesence.utils.render.math.animation.anim.util.Animation2;

@Environment(EnvType.CLIENT)
public class StringSetting extends Setting {
   public String input;
   public String description;
   public boolean active;

   public final Animation2 activeAnim = new Animation2();

   public final Animation2 charAnim = new Animation2();

   public int lastRenderedLength;

   public StringSetting(String name, String input) {
      this.name = input;
      this.input = input;
      this.lastRenderedLength = input != null ? input.length() : 0;
   }

   public String get() {
      return this.input;
   }

   public void set(String input) {
      this.input = input;
   }

   public StringSetting hidden(Supplier<Boolean> hidden) {
      this.hidden = hidden;
      return this;
   }
}
