package vesence.utils.render.math.animation;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class Translate {
   private float x;
   private float y;
   private long lastMS = System.currentTimeMillis();

   public Translate(float x, float y) {
      this.x = x;
      this.y = y;
   }

   public void interpolate(float targetX, float targetY, float smoothing) {
      long currentMS = System.currentTimeMillis();
      long delta = currentMS - this.lastMS;
      this.lastMS = currentMS;
      double deltaFactor = (double)delta * (smoothing / 16.0);
      this.x = (float)this.calculate(this.x, targetX, deltaFactor);
      this.y = (float)this.calculate(this.y, targetY, deltaFactor);
   }

   public void interpolate(float targetX, float targetY, float xSmoothing, float ySmoothing) {
      long currentMS = System.currentTimeMillis();
      long delta = currentMS - this.lastMS;
      this.lastMS = currentMS;
      this.x = (float)this.calculate(this.x, targetX, (double)delta * (xSmoothing / 16.0));
      this.y = (float)this.calculate(this.y, targetY, (double)delta * (ySmoothing / 16.0));
   }

   private double calculate(float current, float target, double speed) {
      float delta = target - current;
      if (speed > 1.0) {
         speed = 1.0;
      } else if (speed < 0.0) {
         speed = 0.0;
      }

      return (double)current + (double)delta * speed;
   }

   public float getX() {
      return this.x;
   }

   public void setX(float x) {
      this.x = x;
   }

   public float getY() {
      return this.y;
   }

   public void setY(float y) {
      this.y = y;
   }
}
