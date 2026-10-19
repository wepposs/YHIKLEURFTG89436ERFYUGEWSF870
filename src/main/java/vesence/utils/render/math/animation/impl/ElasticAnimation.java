package vesence.utils.render.math.animation.impl;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.utils.render.math.animation.Animation;
import vesence.utils.render.math.animation.Direction;

@Environment(EnvType.CLIENT)
public class ElasticAnimation extends Animation {
   private final float easeAmount;

   public ElasticAnimation(int ms, double endPoint, float easeAmount) {
      super(ms, endPoint);
      this.easeAmount = easeAmount;
   }

   public ElasticAnimation(int ms, double endPoint, float easeAmount, Direction direction) {
      super(ms, endPoint, direction);
      this.easeAmount = easeAmount;
   }

   @Override
   protected double getEquation(double x) {
      double x1 = x / (double)this.duration;
      return Math.pow(2.0, -10.0 * x1) * Math.sin((x1 * 10.0 - (double)this.easeAmount) * (Math.PI * 2.0 / 3.0)) + 1.0;
   }
}
