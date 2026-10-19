package vesence.utils.render.math.animation.impl;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.utils.render.math.animation.Animation;
import vesence.utils.render.math.animation.Direction;

@Environment(EnvType.CLIENT)
public class EaseBackIn extends Animation {
   private final float easeAmount;

   public EaseBackIn(int ms, double endPoint, float easeAmount) {
      super(ms, endPoint);
      this.easeAmount = easeAmount;
   }

   public EaseBackIn(int ms, double endPoint, float easeAmount, Direction direction) {
      super(ms, endPoint, direction);
      this.easeAmount = easeAmount;
   }

   @Override
   protected double getEquation(double x) {
      double x1 = x / (double)this.duration;
      float shrink = this.easeAmount + 1.0F;
      return (double)shrink * Math.pow(x1, 3.0) - (double)this.easeAmount * Math.pow(x1, 2.0);
   }
}
