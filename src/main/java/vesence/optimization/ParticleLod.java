package vesence.optimization;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.Particle;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Прореживание дальних частиц.
 *
 * <p>Частицы — самый частый источник мелких draw-вызовов, а на расстоянии отдельная
 * дымовая точка сливается в общую дымку. Дальше 16 блоков рисуется каждая вторая
 * частица, дальше 32 — каждая четвёртая; для отброшенных не выполняется и подготовка
 * квада.
 *
 * <p>Маска берётся из {@code identityHashCode} частицы, поэтому она постоянна на всё
 * время её жизни и не мерцает между кадрами.
 */
@Environment(EnvType.CLIENT)
public final class ParticleLod {

   private static final double NEAR_SQ = 16.0 * 16.0;
   private static final double MID_SQ = 32.0 * 32.0;

   private ParticleLod() {
   }

   /** Рисовать ли частицу: {@code false} — пропустить подготовку квада целиком. */
   public static boolean keep(Particle particle, Camera camera) {
      if (!Opt.on() || particle == null || camera == null) {
         return true;
      }
      AABB box = particle.getBoundingBox();
      Vec3 cam = camera.position();
      double dx = (box.minX + box.maxX) * 0.5 - cam.x;
      double dy = (box.minY + box.maxY) * 0.5 - cam.y;
      double dz = (box.minZ + box.maxZ) * 0.5 - cam.z;
      double distSq = dx * dx + dy * dy + dz * dz;
      if (distSq < NEAR_SQ) {
         return true;
      }
      int mask = System.identityHashCode(particle) >>> 4;
      if (distSq < MID_SQ) {
         return (mask & 1) == 0;
      }
      return (mask & 3) == 0;
   }
}
