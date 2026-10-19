package vesence.module.api.setting.impl;

import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.module.api.setting.Setting;

@Environment(EnvType.CLIENT)
public class RangeSliderSetting extends Setting<double[]> {
   public double valueFrom;
   public double valueTo;
   public double minimum;
   public double maximum;
   public double increment;
   public boolean percent;
   public String description;

   public int draggingThumb = 0;

   public RangeSliderSetting(String name, double valueFrom, double valueTo, double minimum, double maximum, double increment, boolean percent) {
      this.name = name;
      this.minimum = minimum;
      this.maximum = maximum;
      this.increment = increment;
      this.percent = percent;
      this.valueFrom = clampSnap(valueFrom);
      this.valueTo = clampSnap(valueTo);
      if (this.valueFrom > this.valueTo) {
         double tmp = this.valueFrom;
         this.valueFrom = this.valueTo;
         this.valueTo = tmp;
      }
   }

   public RangeSliderSetting(String name, double valueFrom, double valueTo, double minimum, double maximum, double increment) {
      this(name, valueFrom, valueTo, minimum, maximum, increment, false);
   }

   @Override
   public double[] get() {
      return new double[]{this.valueFrom, this.valueTo};
   }

   public double getFrom() {
      return this.valueFrom;
   }

   public double getTo() {
      return this.valueTo;
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

   public void setFrom(double value) {
      this.valueFrom = clampSnap(value);
      if (this.valueFrom > this.valueTo) this.valueFrom = this.valueTo;
   }

   public void setTo(double value) {
      this.valueTo = clampSnap(value);
      if (this.valueTo < this.valueFrom) this.valueTo = this.valueFrom;
   }

   public double clampSnap(double value) {
      double snapped = Math.round((value - minimum) / increment) * increment + minimum;
      return Math.max(minimum, Math.min(maximum, snapped));
   }

   public RangeSliderSetting hidden(Supplier<Boolean> hidden) {
      this.hidden = hidden;
      return this;
   }
}
