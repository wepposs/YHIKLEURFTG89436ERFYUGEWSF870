package vesence.utils.player;

import lombok.Generated;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import vesence.event.player.EventInput;
import vesence.module.impl.combat.AttackAura;
import vesence.utils.other.IMinecraft;

@Environment(EnvType.CLIENT)
public final class MoveUtil implements IMinecraft {

   public static double direction(float rotationYaw, float moveForward, float moveStrafing) {
      if (moveForward < 0.0F) {
         rotationYaw += 180.0F;
      }

      float forward = 1.0F;
      if (moveForward < 0.0F) {
         forward = -0.5F;
      } else if (moveForward > 0.0F) {
         forward = 0.5F;
      }

      if (moveStrafing > 0.0F) {
         rotationYaw -= 90.0F * forward;
      }

      if (moveStrafing < 0.0F) {
         rotationYaw += 90.0F * forward;
      }

      return Math.toRadians(rotationYaw);
   }

   public static boolean isMoving() {
      if (mc.player == null || mc.player.input == null) return false;
      net.minecraft.util.math.Vector2f move = mc.player.input.getMovementInput();
      return move.x != 0.0F || move.y != 0.0F;
   }

   public static double[] calculateDirection(double distance) {
      float[] movement = getMovementFromKeys();
      return calculateDirection(movement[0], movement[1], distance);
   }

   public static double[] calculateDirection(float forward, float sideways, double distance) {
      float yaw = mc.player.getYRot();
      if (forward != 0.0F) {
         if (sideways > 0.0F) {
            yaw += forward > 0.0F ? -45.0F : 45.0F;
         } else if (sideways < 0.0F) {
            yaw += forward > 0.0F ? 45.0F : -45.0F;
         }

         sideways = 0.0F;
         forward = forward > 0.0F ? 1.0F : -1.0F;
      }

      double sinYaw = Math.sin(Math.toRadians(yaw + 90.0F));
      double cosYaw = Math.cos(Math.toRadians(yaw + 90.0F));
      double xMovement = forward * distance * cosYaw + sideways * distance * sinYaw;
      double zMovement = forward * distance * sinYaw - sideways * distance * cosYaw;
      return new double[]{xMovement, zMovement};
   }

   public static void fixMovement(EventInput event, float cameraYaw) {
      float forward = event.getForward();
      float strafe = event.getStrafe();

      if (forward == 0.0F && strafe == 0.0F) {
         return;
      }

      float effectiveYaw = mc.player.isGliding() ? mc.player.getYRot() : cameraYaw;
      double angle = Mth.wrapDegrees(Math.toDegrees(direction(effectiveYaw, forward, strafe)));

      float closestForward = 0.0F;
      float closestStrafe = 0.0F;
      float closestDifference = Float.MAX_VALUE;

      for (float predictedForward = -1.0F; predictedForward <= 1.0F; predictedForward += 1.0F) {
         for (float predictedStrafe = -1.0F; predictedStrafe <= 1.0F; predictedStrafe += 1.0F) {
            if (predictedStrafe == 0.0F && predictedForward == 0.0F) continue;

            double predictedAngle = Mth.wrapDegrees(
                    Math.toDegrees(direction(mc.player.getYRot(), predictedForward, predictedStrafe))
            );

            double difference = Math.abs(angle - predictedAngle);
            if (difference < closestDifference) {
               closestDifference = (float) difference;
               closestForward = predictedForward;
               closestStrafe = predictedStrafe;
            }
         }
      }

      event.setForward(closestForward);
      event.setStrafe(closestStrafe);
   }

   /**
    * Двух-yaw remap (полный порт Relevant RotationStorage.fixMovement).
    * Ремапит WASD так, чтобы мировое направление движения, вычисленное от
    * {@code intentYaw} (куда игрок СМОТРЕЛ САМ — FreeLook), получилось при том,
    * что MC использует {@code applyYaw} (= player.getYRot(), который крутит аура).
    * НЕ меняет величину/наличие движения — только раскладку forward/strafe.
    * Используется "Свободной" коррекцией движения в AttackAura.onMoveKeys.
    */
   public static void fixMovement(EventInput event, float intentYaw, float applyYaw) {
      float forward = event.getForward();
      float strafe = event.getStrafe();
      if (forward == 0.0F && strafe == 0.0F) {
         return;
      }

      double targetAngle = Mth.wrapDegrees(Math.toDegrees(direction(intentYaw, forward, strafe)));
      float bestForward = 0.0F;
      float bestStrafe = 0.0F;
      float smallestDifference = Float.MAX_VALUE;

      for (float testForward = -1.0F; testForward <= 1.0F; testForward++) {
         for (float testStrafe = -1.0F; testStrafe <= 1.0F; testStrafe++) {
            if (testForward != 0.0F || testStrafe != 0.0F) {
               double testAngle = Mth.wrapDegrees(Math.toDegrees(direction(applyYaw, testForward, testStrafe)));
               float difference = Math.abs(Mth.wrapDegrees((float) (targetAngle - testAngle)));
               if (difference < smallestDifference) {
                  smallestDifference = difference;
                  bestForward = testForward;
                  bestStrafe = testStrafe;
               }
            }
         }
      }

      event.setForward(bestForward);
      event.setStrafe(bestStrafe);
   }

   public static void fixMovement(float cameraYaw) {
      float[] movement = getMovementFromKeys();
      float forward = movement[0];
      float strafe = movement[1];

      if (forward == 0.0F && strafe == 0.0F) {
         return;
      }

      float effectiveYaw = mc.player.isGliding() ? mc.player.getYRot() : cameraYaw;
      double angle = Mth.wrapDegrees(
              Math.toDegrees(direction(effectiveYaw, forward, strafe))
      );

      float closestForward = 0.0F;
      float closestStrafe = 0.0F;
      float closestDifference = Float.MAX_VALUE;

      for (float predictedForward = -1.0F; predictedForward <= 1.0F; predictedForward += 1.0F) {
         for (float predictedStrafe = -1.0F; predictedStrafe <= 1.0F; predictedStrafe += 1.0F) {
            if (predictedStrafe == 0.0F && predictedForward == 0.0F) continue;

            double predictedAngle = Mth.wrapDegrees(
                    Math.toDegrees(direction(mc.player.getYRot(), predictedForward, predictedStrafe))
            );

            double difference = Math.abs(angle - predictedAngle);
            if (difference < closestDifference) {
               closestDifference = (float) difference;
               closestForward = predictedForward;
               closestStrafe = predictedStrafe;
            }
         }
      }

      mc.options.forwardKey.setPressed(closestForward > 0.0F);
      mc.options.backKey.setPressed(closestForward < 0.0F);
      mc.options.leftKey.setPressed(closestStrafe > 0.0F);
      mc.options.rightKey.setPressed(closestStrafe < 0.0F);
   }

   public static float[] getMovementFromKeys() {
      float forward = 0.0F;
      float strafe = 0.0F;
      if (mc.options.forwardKey.isPressed()) {
         forward++;
      }
      if (mc.options.backKey.isPressed()) {
         forward--;
      }
      if (mc.options.leftKey.isPressed()) {
         strafe++;
      }
      if (mc.options.rightKey.isPressed()) {
         strafe--;
      }
      return new float[]{forward, strafe};
   }

   /**
    * Автопилот полёта на элитре (порт MoveFixUtil из Meow).
    * Работает даже когда игрок не жмёт клавиши: принудительно forward=1 и
    * раскладка так, чтобы мировое движение смотрело на aim.
    */
   public static boolean flyAtPosition(EventInput event, Vec3 aim) {
      if (mc.player == null || aim == null) return false;

      float forward = event.getForward();
      float strafe = event.getStrafe();
      if (forward == 0.0F && strafe == 0.0F) {
         forward = 1.0F;
         strafe = 0.0F;
      }

      double dx = aim.x - mc.player.getX();
      double dz = aim.z - mc.player.getZ();
      if (dx * dx + dz * dz < 1.0E-6D) return false;

      float motionYaw = (float) Mth.wrapDegrees(
              Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);

      double intended = Mth.wrapDegrees(
              Math.toDegrees(direction(motionYaw, forward, strafe)));

      float applyYaw = mc.player.getYRot();

      float bestForward = 0.0F;
      float bestStrafe = 0.0F;
      float bestDifference = Float.MAX_VALUE;

      for (float testForward = -1.0F; testForward <= 1.0F; testForward += 1.0F) {
         for (float testStrafe = -1.0F; testStrafe <= 1.0F; testStrafe += 1.0F) {
            if (testForward == 0.0F && testStrafe == 0.0F) continue;
            double testAngle = Mth.wrapDegrees(
                    Math.toDegrees(direction(applyYaw, testForward, testStrafe)));
            float difference = Math.abs(Mth.wrapDegrees((float) (intended - testAngle)));
            if (difference < bestDifference) {
               bestDifference = difference;
               bestForward = testForward;
               bestStrafe = testStrafe;
            }
         }
      }

      event.setForward(bestForward);
      event.setStrafe(bestStrafe);
      return true;
   }
   public static void targetMovement(float cameraYaw, Vec3 position) {
      float[] movement = getMovementFromKeys();
      float forward = movement[0];
      float strafe = movement[1];
      if (forward != 0.0F || strafe != 0.0F) {
         AABB box = AttackAura.target.getBoundingBox();
         double randX = Mth.lerp(Math.random(), box.minX, box.maxX);
         double randY = Mth.lerp(Math.random(), box.minY, box.maxY);
         double randZ = Mth.lerp(Math.random(), box.minZ, box.maxZ);
         randY = Mth.clamp(randY, AttackAura.target.getY() + 0.2, AttackAura.target.getY() + AttackAura.target.getHeight() - 0.2);
         Vec3 randomHitVec = new Vec3(randX, randY, randZ);
         Vec3 direction = randomHitVec.subtract(mc.player.getEyePosition()).normalize();
         float targetYaw = (float)Mth.wrapDegrees(Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90.0);
         double angle = Mth.wrapDegrees(
                 Math.toDegrees(direction(mc.player.isGliding() ? mc.player.getYRot() : targetYaw, forward, strafe))
         );
         float closestForward = 0.0F;
         float closestStrafe = 0.0F;
         float closestDifference = Float.MAX_VALUE;

         for (float predictedForward = -1.0F; predictedForward <= 1.0F; predictedForward += 1.0F) {
            for (float predictedStrafe = -1.0F; predictedStrafe <= 1.0F; predictedStrafe += 1.0F) {
               if (predictedStrafe == 0.0F && predictedForward == 0.0F) continue;
               double predictedAngle = Mth.wrapDegrees(
                       Math.toDegrees(direction(mc.player.getYRot(), predictedForward, predictedStrafe))
               );
               double difference = Math.abs(angle - predictedAngle);
               if (difference < closestDifference) {
                  closestDifference = (float)difference;
                  closestForward = predictedForward;
                  closestStrafe = predictedStrafe;
               }
            }
         }

         mc.options.forwardKey.setPressed(closestForward > 0.0F);
         mc.options.backKey.setPressed(closestForward < 0.0F);
         mc.options.leftKey.setPressed(closestStrafe > 0.0F);
         mc.options.rightKey.setPressed(closestStrafe < 0.0F);
      }
   }

   public static float getdir() {
      float[] movement = getMovementFromKeys();
      float forward = movement[0];
      float strafe = movement[1];
      if (forward == 0.0F && strafe == 0.0F) return -1.0F;
      return (float) Math.toDegrees(direction(mc.player.getYRot(), forward, strafe));
   }

   @Generated
   private MoveUtil() {
      throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
   }
}
