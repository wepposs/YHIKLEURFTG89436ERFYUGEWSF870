package vesence.utils.render.math.animation.impl;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.utils.render.math.animation.Animation;
import vesence.utils.render.math.animation.Direction;

@Environment(EnvType.CLIENT)
public class DecelerateAnimation extends Animation {
   public DecelerateAnimation(int ms, double endPoint) {
      super(ms, endPoint);
   }

   public DecelerateAnimation(int ms, double endPoint, Direction direction) {
      super(ms, endPoint, direction);
   }

   @Override
   protected double getEquation(double x) {
      double x1 = x / (double)this.duration;
      return 1.0 - (x1 - 1.0) * (x1 - 1.0);
   }
}
