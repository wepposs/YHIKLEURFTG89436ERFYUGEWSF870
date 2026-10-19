package vesence.module.impl.visuals.pet;

/**
 * Порт xd.harm.utils.animations.InfinityAnimation (+ вложенная Animation с
 * линейным изингом) из harmony. Линейный тайм-твин к движущейся цели:
 * animate(dest, ms) перебазирует внутреннюю анимацию, get() возвращает текущее
 * сглаженное значение. Поведение 1:1 с оригиналом (Easing.LINEAR, forward=false).
 */
public class PetAnimation {

   private float output;
   private float endpoint;
   private Anim animation = new Anim(0.0, 0);

   public float animate(float destination, int ms) {
      ms = Math.max(1, ms);
      this.output = this.endpoint - this.animation.get();
      this.endpoint = destination;
      if (this.output != this.endpoint - destination) {
         this.animation = new Anim(this.endpoint - this.output, ms);
      }
      return this.output;
   }

   public boolean finished() {
      return this.output == this.endpoint || this.animation.finished();
   }

   public float get() {
      this.output = this.endpoint - this.animation.get();
      return this.output;
   }

   // Соответствует harmony Animation с forward=false и Easing.LINEAR.
   private static final class Anim {
      private final double size;
      private final int speed;
      private final long start = System.currentTimeMillis();

      private Anim(double size, int speed) {
         this.size = size;
         this.speed = speed;
      }

      private boolean finished() {
         return this.speed <= 0 || System.currentTimeMillis() - this.start > this.speed;
      }

      private float get() {
         long time = System.currentTimeMillis() - this.start;
         if (this.speed <= 0 || time >= this.speed) {
            return 0.0F;
         }
         return (float) ((1.0 - time / (double) this.speed) * this.size);
      }
   }
}
