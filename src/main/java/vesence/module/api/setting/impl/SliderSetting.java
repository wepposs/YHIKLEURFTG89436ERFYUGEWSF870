package vesence.module.api.setting.impl;

import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.module.api.setting.Setting;

@Environment(EnvType.CLIENT)
public class SliderSetting extends Setting<Double> {
   public double current;
   public double minimum;
   public double maximum;
   public double increment;
   public final double defaultValue;
   public float sliderWidth;
   public boolean sliding;
   public boolean percent;
   public String description;

   public boolean editing;

   public String editBuffer = "";

   public SliderSetting(String name, double current, double minimum, double maximum, double increment, boolean percent) {
      this.name = name;
      this.minimum = minimum;
      this.current = current;
      this.maximum = maximum;
      this.increment = increment;
      this.percent = percent;
      this.defaultValue = current;
   }

   public SliderSetting(String name, double current, double minimum, double maximum, double increment) {
      this(name, current, minimum, maximum, increment, false);
   }

   @Override
   public Double get() {
      return this.current;
   }

   public double getDefault() {
      return this.defaultValue;
   }

   public double getMin() {
      return this.minimum;
   }

   public double getMax() {
      return this.maximum;
   }

   public double getStep() {
      return this.increment;
   }

   public void setValue(double value) {
      this.current = value;
   }

   public SliderSetting hidden(Supplier<Boolean> hidden) {
      this.hidden = hidden;
      return this;
   }
}