package vesence.module.impl.visuals.pet;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

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
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.world == null) {
         return false;
      }
      BlockPos pos = BlockPos.ofFloored(x, y, z);
      BlockState state = mc.world.getBlockState(pos);
      try {
         return !state.getCollisionShape(mc.world, pos).isEmpty();
      } catch (Throwable ignored) {
         return state.isSolidBlock(mc.world, pos);
      }
   }

   public static Vec2f rotations(Vec3d from, Vec3d target) {
      double posX = target.x - from.x;
      double posY = target.y - from.y;
      double posZ = target.z - from.z;
      double sqrt = Math.sqrt(posX * posX + posZ * posZ);
      float yaw = (float) (Math.atan2(posZ, posX) * 180.0 / Math.PI) - 90.0F;
      float pitch = (float) (-(Math.atan2(posY, sqrt) * 180.0 / Math.PI));
      double sensitivity = MinecraftClient.getInstance().options.getMouseSensitivity().getValue();
      float sens = (float) (Math.pow(sensitivity, 1.5) * 0.05F + 0.1F);
      float pow = sens * sens * sens * 1.2F;
      yaw -= yaw % pow;
      pitch -= pitch % (pow * sens);
      return new Vec2f(yaw, pitch);
   }
}
