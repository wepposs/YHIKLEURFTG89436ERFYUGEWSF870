package vesence.module.impl.visuals.pet;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.math.Vector2f;
import net.minecraft.world.phys.Vec3;
import vesence.event.EventInit;
import vesence.event.impl.EventUpdate;
import vesence.module.impl.combat.AttackAura;

/**
 * Порт xd.harm.modules.models.dragon.DragonBrain — летающий питомец (парит над
 * игроком/у цели, обходит препятствия через findSafePosition). Vector3d -> Vec3,
 * HitAura.getTarget() -> AttackAura.target, PlayerUtils/RotateUtility -> PetUtil.
 */
public class DragonBrain {

   private final Minecraft mc = Minecraft.getInstance();

   private Vec3 pos;
   private Vec3 motion = Vec3.ZERO;
   private float direction = (float) (Math.random() * 360.0);
   private float yaw;
   private float body;
   private float pitch;
   private int speed = 50;

   private final PetAnimation x = new PetAnimation();
   private final PetAnimation y = new PetAnimation();
   private final PetAnimation z = new PetAnimation();

   private final PetAnimation bodyAnim = new PetAnimation();
   private final PetAnimation yawAnim = new PetAnimation();
   private final PetAnimation pitchAnim = new PetAnimation();

   public float wingFlap = 0.0F;
   public float prevWingFlap = 0.0F;
   public float tailSway = 0.0F;
   public float prevTailSway = 0.0F;

   private long circleTimer = System.currentTimeMillis();
   private float flyHeight = 1.5F;

   private Player entity;

   public void setEntity(Player entity) {
      this.entity = entity;
   }

   @EventInit
   public void onUpdate(EventUpdate e) {
      this.entity = mc.player;
      if (entity == null) {
         return;
      }

      Vec3 playerPos = entity.getEntityPos();

      if (pos == null || pos.distanceTo(playerPos) > 15) {
         Vec3 startPos = new Vec3(playerPos.x, playerPos.y + flyHeight, playerPos.z);
         pos = findSafePosition(startPos, playerPos);
         x.animate((float) pos.x, 1);
         y.animate((float) pos.y, 1);
         z.animate((float) pos.z, 1);
      }

      LivingEntity target = AttackAura.target;

      Vec3 newTargetPos;
      if (target != null && entity instanceof LocalPlayer) {
         Vec3 targetPos = target.getEntityPos();
         double targetX = targetPos.x + 1.5;
         double targetY = targetPos.y + flyHeight;
         double targetZ = targetPos.z + 1.5;

         newTargetPos = new Vec3(targetX, targetY, targetZ);
      } else {
         double targetX = playerPos.x + 2.0;
         double targetY = playerPos.y + flyHeight + Math.sin(mc.player.tickCount * 0.1F) * 0.3;
         double targetZ = playerPos.z;

         newTargetPos = new Vec3(targetX, targetY, targetZ);
      }

      Vec3 safeTargetPos = findSafePosition(newTargetPos, playerPos);

      Vec3 diff = safeTargetPos.subtract(pos);
      double speed = target != null ? 0.2 : 0.15;
      motion = diff.multiply(speed);

      Vec3 newPos = pos.add(motion);
      if (!PetUtil.isBlockSolid(newPos.x, newPos.y, newPos.z)
            && !PetUtil.isBlockSolid(newPos.x, newPos.y + 0.5, newPos.z)) {
         pos = newPos;
      } else {
         Vec3 horizontalPos = pos.add(motion.x * 0.35, 0.0, motion.z * 0.35);
         if (!PetUtil.isBlockSolid(horizontalPos.x, horizontalPos.y, horizontalPos.z)
               && !PetUtil.isBlockSolid(horizontalPos.x, horizontalPos.y + 0.5, horizontalPos.z)) {
            pos = horizontalPos;
         }
      }

      handleRotation();

      speed = 100;
      x.animate((float) pos.x, (int) speed);
      y.animate((float) pos.y, (int) speed);
      z.animate((float) pos.z, (int) speed);

      updateDragonAnimations();
   }

   private void updateDragonAnimations() {
      prevWingFlap = wingFlap;
      prevTailSway = tailSway;

      wingFlap += 0.6F;
      if (wingFlap > Math.PI * 2) {
         wingFlap = 0;
      }
      tailSway += 0.15F;
      if (tailSway > Math.PI * 2) {
         tailSway = 0;
      }
   }

   private void handleRotation() {
      LivingEntity target = AttackAura.target;

      if (target != null && entity instanceof LocalPlayer) {
         Vector2f targetRotation = PetUtil.rotations(pos, target.getEntityPos());
         yaw = targetRotation.x;
      } else {
         if (motion.x != 0 || motion.z != 0) {
            double angle = Math.atan2(motion.z, motion.x);
            yaw = (float) Math.toDegrees(angle) - 90;
            yaw %= 360;
            if (yaw < 0) {
               yaw += 360;
            }
         }
      }

      bodyAnim.animate(yaw, 80);
      yawAnim.animate(yaw, 100);
      pitchAnim.animate(0, 100);

      body = bodyAnim.get();
   }

   public float getBody() {
      return bodyAnim.get();
   }

   public float getYaw() {
      return yawAnim.get();
   }

   public float getPitch() {
      return pitchAnim.get();
   }

   public Vec3 getPos() {
      return new Vec3(x.get(), y.get(), z.get());
   }

   public float getWingFlap() {
      return wingFlap;
   }

   public float getTailSway() {
      return tailSway;
   }

   public boolean isFlying() {
      return true;
   }

   public boolean isPlayerMoving() {
      if (entity == null) {
         return false;
      }
      return entity.getDeltaMovement().lengthSqr() > 0.01;
   }

   private Vec3 findSafePosition(Vec3 targetPos, Vec3 playerPos) {
      if (!PetUtil.isBlockSolid(targetPos.x, targetPos.y, targetPos.z)
            && !PetUtil.isBlockSolid(targetPos.x, targetPos.y + 0.5, targetPos.z)) {
         return targetPos;
      }

      double[] radii = new double[]{0.5, 1.0, 1.5, 2.0};
      for (double radius : radii) {
         for (int i = 0; i < 8; i++) {
            double angle = Math.toRadians(i * 45.0);
            Vec3 testPos = new Vec3(
                  targetPos.x + Math.cos(angle) * radius,
                  targetPos.y,
                  targetPos.z + Math.sin(angle) * radius);
            if (!PetUtil.isBlockSolid(testPos.x, testPos.y, testPos.z)
                  && !PetUtil.isBlockSolid(testPos.x, testPos.y + 0.5, testPos.z)) {
               return testPos;
            }
         }
      }

      return new Vec3(playerPos.x, playerPos.y + flyHeight, playerPos.z);
   }
}
