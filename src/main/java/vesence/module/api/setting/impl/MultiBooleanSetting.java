package vesence.module.api.setting.impl;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.module.api.setting.Setting;

@Environment(EnvType.CLIENT)
public class MultiBooleanSetting extends Setting<List<BooleanSetting>> {
   public List<BooleanSetting> settings;
   public boolean opened;

   public MultiBooleanSetting(String name, BooleanSetting... settings) {
      this.name = name;
      this.settings = Arrays.asList(settings);
   }

   public MultiBooleanSetting(String name, Map<String, Boolean> defaults) {
      this.name = name;
      this.settings = new ArrayList<>();
      for (Map.Entry<String, Boolean> entry : defaults.entrySet()) {
         this.settings.add(new BooleanSetting(entry.getKey(), entry.getValue()));
      }
   }

   public boolean get(String name) {
      for (BooleanSetting setting : this.settings) {
         if (setting.name.equals(name)) {
            return setting.get();
         }
      }

      return false;
   }

   @Override
   public List<BooleanSetting> get() {
      return this.settings;
   }

   public MultiBooleanSetting hidden(Supplier<Boolean> hidden) {
      this.hidden = hidden;
      return this;
   }
}
