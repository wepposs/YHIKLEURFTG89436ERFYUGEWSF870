package vesence.utils.render.math.animation;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class Counter {
   private long time = System.currentTimeMillis();

   public void reset() {
      this.time = System.currentTimeMillis();
   }

   public long getTime() {
      return this.time;
   }

   public void setTime(long time) {
      this.time = time;
   }

   public boolean hasReached(float ms) {
      return (float)(System.currentTimeMillis() - this.time) >= ms;
   }

   public boolean hasReached(double ms) {
      return (double)(System.currentTimeMillis() - this.time) >= ms;
   }

   public long getTimePassed() {
      return System.currentTimeMillis() - this.time;
   }
}
