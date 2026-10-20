package vesence.module.impl.visuals.pet;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.joml.Vector2f;
import net.minecraft.world.phys.Vec3;

/**
 * Общие утилиты питомцев (порт из harmony):
 *  - isBlockSolid — PlayerUtils.isBlockSolid (адаптировано под 1.21.11: наличие
 *    коллизии блока вместо Material.isSolid);
 *  - rotations    — RotateUtility.get(from, target) с квантованием по
 *    чувствительности мыши (yaw/pitch к точке).
 */
public final class PetUtil {

   private PetUtil() {
   }

   public static boolean isBlockSolid(double x, double y, double z) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return false;
      }
      BlockPos pos = BlockPos.containing(x, y, z);
      BlockState state = mc.level.getBlockState(pos);
      try {
         return !state.getCollisionShape(mc.level, pos).isEmpty();
      } catch (Throwable ignored) {
         return state.isSolidRender();
      }
   }

   public static Vector2f rotations(Vec3 from, Vec3 target) {
      double posX = target.x - from.x;
      double posY = target.y - from.y;
      double posZ = target.z - from.z;
      double sqrt = Math.sqrt(posX * posX + posZ * posZ);
      float yaw = (float) (Math.atan2(posZ, posX) * 180.0 / Math.PI) - 90.0F;
      float pitch = (float) (-(Math.atan2(posY, sqrt) * 180.0 / Math.PI));
      double sensitivity = Minecraft.getInstance().options.sensitivity().get();
      float sens = (float) (Math.pow(sensitivity, 1.5) * 0.05F + 0.1F);
      float pow = sens * sens * sens * 1.2F;
      yaw -= yaw % pow;
      pitch -= pitch % (pow * sens);
      return new Vector2f(yaw, pitch);
   }
}
