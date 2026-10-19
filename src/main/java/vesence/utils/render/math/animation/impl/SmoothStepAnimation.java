package vesence.utils.render.math.animation.impl;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.utils.render.math.animation.Animation;
import vesence.utils.render.math.animation.Direction;

@Environment(EnvType.CLIENT)
public class SmoothStepAnimation extends Animation {
   public SmoothStepAnimation(int ms, double endPoint) {
      super(ms, endPoint);
   }

   public SmoothStepAnimation(int ms, double endPoint, Direction direction) {
      super(ms, endPoint, direction);
   }

   @Override
   protected double getEquation(double x) {
      double x1 = x / (double)this.duration;
      return x1 * x1 * (3.0 - 2.0 * x1);
   }
}
