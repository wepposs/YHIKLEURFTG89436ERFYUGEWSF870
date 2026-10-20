package vesence.module.impl.combat.auraComponent;

import lombok.Generated;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.FreeLookUtil;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.Rotation;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.URotations;
import vesence.utils.other.IMinecraft;
import vesence.utils.other.Mathf;
import vesence.utils.other.TimerUtil;
import vesence.utils.render.math.animation.anim2.Interpolator;

import java.security.SecureRandom;
import java.util.concurrent.ThreadLocalRandom;

@Environment(EnvType.CLIENT)
public final class Rotate implements IMinecraft {
   static int tick;
   static TimerUtil time1 = new TimerUtil();
   static TimerUtil time2 = new TimerUtil();

   public static float randomLerp(float min, float max) {
      return Interpolator.lerp(max, min, new SecureRandom().nextFloat());
   }

   public static void onMatrixRotation(LivingEntity target, boolean attack) {
      float addyVacY = target.getBbHeight() / 2.0F * (float)Math.cos(System.currentTimeMillis() / 2200.0);
      float addyVacZ = 0.16F * (float)Math.cos(System.currentTimeMillis() / 1250.0);
      float addyVacX = 0.22F * (float)Math.sin(System.currentTimeMillis() / 1700.0);
      Vec3 vec = UBoxPoints.getBestVector3dOnEntityBox(target, false)
         .add(addyVacX, addyVacY, addyVacZ)
         .subtract(mc.player.getEyePosition());
      float yaw = (float)Math.toDegrees(Math.atan2(-vec.x, vec.z));
      float pitch = (float)Mth.clamp(-Math.toDegrees(Math.atan2(vec.y, Math.hypot(vec.x, vec.z))), -90.0, 90.0);
      float spy = Mathf.random(7.0F, 9.0F);
      float spx = Mathf.randomInt(60, 80);
      Rotation newRotation = new Rotation(yaw + Mathf.randomInt(-1, 2), pitch);
      URotations.update(newRotation, spx, spy, Mathf.randomInt(20, 30), Mathf.randomInt(20, 30), 1, 15, false);
   }

   public static void onPolarRotation(LivingEntity target, boolean attack) {
   }

   public static void onSnapRotation(LivingEntity target, boolean attack, String type) {
      float addyVacY = 0.25F * (float)Math.cos(System.currentTimeMillis() / 1500L);
      float addyVacZ = 0.2F * (float)Math.cos(System.currentTimeMillis() / 700L);
      float addyVacX = 0.2F * (float)Math.cos(System.currentTimeMillis() / 900L);
      Vec3 playerEyePos = mc.player.getEyePosition();
      Vec3 vec = new Vec3(target.getX(), target.getY(), target.getZ())
         .add(addyVacX, Mth.clamp(playerEyePos.y - target.getY(), 0.0, 0.8) - addyVacY, addyVacZ)
         .subtract(playerEyePos)
         .normalize();
      if (type.contains("Fast")) {
         float yaw = FreeLookUtil.freeYaw;
         float pitch = FreeLookUtil.freePitch;
         float speed = Mathf.random(190.0F, 245.0F);
         if (attack) {
            yaw = (float)Math.toDegrees(Math.atan2(-vec.x, vec.z));
            pitch = (float)Mth.clamp(-Math.toDegrees(Math.atan2(vec.y, Math.hypot(vec.x, vec.z))), -90.0, 90.0);
         }

         float rx = 0.0F;
         float ry = 0.0F;
         URotations.update(new Rotation(yaw + rx, pitch + ry), speed, speed, 40.0F, 40.0F, 1, 7, false);
      } else if (type.contains("Smooth")) {
         float yaw = FreeLookUtil.freeYaw;
         float pitch = FreeLookUtil.freePitch;
         float speed = 24.0F;
         if (attack) {
            tick = 3;
            speed = 88.0F;
         }

         if (tick > 0) {
            yaw = (float)Math.toDegrees(Math.atan2(-vec.x, vec.z));
            pitch = (float)Mth.clamp(-Math.toDegrees(Math.atan2(vec.y, Math.hypot(vec.x, vec.z))), -90.0, 90.0);
            tick--;
         }

         float rx = 0.0F;
         float ry = 0.0F;
         URotations.update(new Rotation(yaw + rx, pitch + ry), speed, speed, 40.0F, 40.0F, 1, 7, false);
      } else if (type.contains("Random")) {
         float yawx = FreeLookUtil.freeYaw;
         float pitchx = FreeLookUtil.freePitch;
         float speedx = Mathf.random(30.0F, 35.0F);
         if (attack) {
            tick = Mathf.randomInt(2, 4);
         }

         if (tick > 0) {
            speedx = Mathf.random(140.0F, 220.0F);
            yawx = (float)Math.toDegrees(Math.atan2(-vec.x, vec.z));
            pitchx = (float)Mth.clamp(-Math.toDegrees(Math.atan2(vec.y, Math.hypot(vec.x, vec.z))), -90.0, 90.0);
            tick--;
         }

         float randomXY = ThreadLocalRandom.current().nextFloat(-3.0F, 3.0F)
            + (float)(Mathf.random(4.0F, 5.0F) * Math.cos(System.currentTimeMillis() / 150.0))
            + (float)(Mathf.random(4.0F, 5.0F) * Math.sin(System.currentTimeMillis() / 50.0))
            + (float)(Mathf.random(5.0F, 8.0F) * Math.sin(System.currentTimeMillis() / 130.0))
               * (float)(Mathf.random(4.0F, 7.0F) * Math.cos(System.currentTimeMillis() / 650.0))
            + (float)(Mathf.random(12.0F, 18.0F) * Math.sin(System.currentTimeMillis() / 80.0))
               * (float)(Mathf.random(2.0F, 3.0F) * Math.cos(System.currentTimeMillis() / 2650.0));
         float randomX = ThreadLocalRandom.current().nextFloat(-1.0F, 1.0F)
            + (float)(Mathf.random(2.0F, 3.0F) * Math.cos(System.currentTimeMillis() / 170.0))
            + (float)(Mathf.random(3.0F, 4.0F) * Math.sin(System.currentTimeMillis() / 70.0))
            + (float)(Mathf.random(1.0F, 2.0F) * Math.sin(System.currentTimeMillis() / 110.0))
               * (float)(Mathf.random(1.0F, 2.0F) * Math.cos(System.currentTimeMillis() / 350.0));
         URotations.update(new Rotation(yawx + randomXY / 4.0F, pitchx + randomX), speedx, speedx, 40.0F, 40.0F, 1, 7, false);
      }
   }

   @Generated
   private Rotate() {
      throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
   }
}
