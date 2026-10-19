package vesence.module.impl.visuals.pet;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import vesence.event.EventInit;
import vesence.event.impl.EventUpdate;
import vesence.module.impl.combat.AttackAura;

/**
 * Порт xd.harm.modules.models.spike.SpikeBrain — наземное следование как у
 * коровки, но при таргете Спайк отходит ОТ цели и смотрит прямо на неё.
 */
public class SpikeBrain {

   private static final double TELEPORT_DISTANCE = 12.0D;
   private static final double FOLLOW_DISTANCE = 1.55D;
   private static final double TARGET_DISTANCE = 0.95D;
   private static final double STOP_DISTANCE = 0.3D;
   private static final double MAX_WALK_SPEED = 0.52D;
   private static final double MAX_RUN_SPEED = 0.7D;
   private static final double ACCELERATION = 0.55D;
   private static final double DAMPING = 0.62D;
   private static final double GRAVITY = 0.12D;
   private static final double STEP_UP_MOTION = 0.34D;
   private static final double WATER_SURFACE_OFFSET = 1.02D;
   private static final double LILY_PAD_SURFACE_OFFSET = 0.12D;

   private final MinecraftClient mc = MinecraftClient.getInstance();

   private Vec3d pos;
   private Vec3d motion = Vec3d.ZERO;
   private Vec3d lastRenderPos;
   private float body;
   private float yaw;
   private float headYaw;
   private float headPitch;
   private int speed = 50;

   private final PetAnimation x = new PetAnimation();
   private final PetAnimation y = new PetAnimation();
   private final PetAnimation z = new PetAnimation();
   private final PetAnimation bodyAnim = new PetAnimation();
   private final PetAnimation yawAnim = new PetAnimation();
   private final PetAnimation pitchAnim = new PetAnimation();
   private long stayingMs = System.currentTimeMillis();

   private boolean idle = true;
   public float prevLimbSwingAmount;
   public float limbSwingAmount;
   public float limbSwing;
   private float moveSpeed;

   private PlayerEntity entity;

   public void setEntity(PlayerEntity entity) {
      this.entity = entity;
   }

   @EventInit
   public void onUpdate(EventUpdate event) {
      this.entity = mc.player;
      if (entity == null) {
         return;
      }

      Vec3d playerPos = entity.getEntityPos();
      LivingEntity target = AttackAura.target;
      Vec3d desiredPos = resolveDesiredPosition(playerPos, target);
      if (pos == null || pos.distanceTo(playerPos) > TELEPORT_DISTANCE) {
         pos = desiredPos;
         x.animate((float) pos.x, 1);
         y.animate((float) pos.y, 1);
         z.animate((float) pos.z, 1);
      }

      motion = motion.add(0.0D, isWaterOrLilySupport(pos.x, pos.y, pos.z) ? -GRAVITY * 0.18D : -GRAVITY, 0.0D);
      Vec3d newPos = pos.add(motion);

      if (PetUtil.isBlockSolid(newPos.x, newPos.y, newPos.z)) {
         int blockY = MathHelper.floor(newPos.y);
         newPos = new Vec3d(newPos.x, blockY + 1.1D, newPos.z);
         motion = new Vec3d(motion.x, 0.0D, motion.z);
      }

      motion = updateHorizontalMotion(newPos, desiredPos, target);
      newPos = pos.add(motion);
      newPos = resolveGroundCollision(newPos);
      newPos = resolveFluidAndLilyCollision(newPos);
      handleRotation(target, desiredPos, playerPos);
      pos = newPos;

      speed = 70;
      x.animate((float) pos.x, speed);
      y.animate((float) pos.y, speed);
      z.animate((float) pos.z, speed);
      Vec3d renderPos = new Vec3d(x.get(), y.get(), z.get());
      if (lastRenderPos == null) {
         lastRenderPos = renderPos;
      }
      double renderDeltaX = renderPos.x - lastRenderPos.x;
      double renderDeltaZ = renderPos.z - lastRenderPos.z;
      moveSpeed = (float) Math.sqrt(renderDeltaX * renderDeltaX + renderDeltaZ * renderDeltaZ);
      lastRenderPos = renderPos;
      motion = new Vec3d(motion.x * DAMPING, motion.y * 0.92D, motion.z * DAMPING);

      limbTick();

      idle = desiredPos.distanceTo(pos) <= 0.2D && limbSwingAmount < 0.08F;
      if (!idle) {
         stayingMs = System.currentTimeMillis();
      }
   }

   private Vec3d resolveDesiredPosition(Vec3d playerPos, LivingEntity target) {
      if (target != null && entity instanceof ClientPlayerEntity) {
         Vec3d targetPos = target.getEntityPos();
         Vec3d awayFromTarget = pos == null ? playerPos.subtract(targetPos) : pos.subtract(targetPos);
         awayFromTarget = new Vec3d(awayFromTarget.x, 0.0D, awayFromTarget.z);
         if (awayFromTarget.lengthSquared() < 1.0E-4D) {
            awayFromTarget = playerPos.subtract(targetPos);
            awayFromTarget = new Vec3d(awayFromTarget.x, 0.0D, awayFromTarget.z);
         }
         if (awayFromTarget.lengthSquared() < 1.0E-4D) {
            awayFromTarget = new Vec3d(0.0D, 0.0D, 1.0D);
         } else {
            awayFromTarget = awayFromTarget.normalize();
         }
         return findGroundedPosition(targetPos.add(awayFromTarget.x * TARGET_DISTANCE, 0.0D, awayFromTarget.z * TARGET_DISTANCE));
      }

      Vec3d offset = pos == null ? new Vec3d(1.15D, 0.0D, 0.0D) : pos.subtract(playerPos);
      double horizontalLength = Math.sqrt(offset.x * offset.x + offset.z * offset.z);
      if (horizontalLength < 1.0E-4D) {
         offset = new Vec3d(1.15D, 0.0D, 0.0D);
         horizontalLength = 1.15D;
      }

      double scale = FOLLOW_DISTANCE / horizontalLength;
      Vec3d anchor = playerPos.add(offset.x * scale, 0.0D, offset.z * scale);
      return findGroundedPosition(anchor);
   }

   private Vec3d updateHorizontalMotion(Vec3d currentPos, Vec3d desiredPos, LivingEntity target) {
      double deltaX = desiredPos.x - currentPos.x;
      double deltaZ = desiredPos.z - currentPos.z;
      double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

      double targetSpeed = 0.0D;
      if (horizontalDistance > STOP_DISTANCE) {
         double maxSpeed = target != null ? MAX_RUN_SPEED : MAX_WALK_SPEED;
         targetSpeed = Math.min(maxSpeed, (horizontalDistance - STOP_DISTANCE) * 0.42D + 0.12D);
      }

      double desiredMotionX = 0.0D;
      double desiredMotionZ = 0.0D;
      if (horizontalDistance > 1.0E-4D) {
         desiredMotionX = deltaX / horizontalDistance * targetSpeed;
         desiredMotionZ = deltaZ / horizontalDistance * targetSpeed;
      }

      double nextMotionX = MathHelper.lerp(ACCELERATION, motion.x, desiredMotionX);
      double nextMotionZ = MathHelper.lerp(ACCELERATION, motion.z, desiredMotionZ);

      if (shouldStepUp(currentPos, nextMotionX, nextMotionZ)) {
         motion = new Vec3d(nextMotionX, STEP_UP_MOTION, nextMotionZ);
      } else if (PetUtil.isBlockSolid(currentPos.x, currentPos.y - 0.12D, currentPos.z)
            || isWaterOrLilySupport(currentPos.x, currentPos.y, currentPos.z)) {
         motion = new Vec3d(nextMotionX, 0.0D, nextMotionZ);
      } else {
         motion = new Vec3d(nextMotionX, motion.y, nextMotionZ);
      }

      if (target != null && entity instanceof ClientPlayerEntity) {
         Box box = new Box(getPos().subtract(0.6D, 0.0D, 0.6D), getPos().add(0.6D, 0.8D, 0.6D));
         Box targetBox = target.getBoundingBox().expand(-0.1D, 0.0D, -0.1D);
         if (box.maxX > targetBox.minX
               && box.maxY > targetBox.minY
               && box.maxZ > targetBox.minZ
               && box.minX < targetBox.maxX
               && box.minY < targetBox.maxY
               && box.minZ < targetBox.maxZ) {
            Vec3d away = getPos().subtract(target.getEntityPos());
            away = new Vec3d(away.x, 0.0D, away.z);
            if (away.lengthSquared() > 1.0E-4D) {
               away = away.normalize().multiply(0.18D);
               motion = new Vec3d(away.x, motion.y, away.z);
            } else {
               motion = motion.multiply(0.0D, 1.0D, 0.0D);
            }
         }
      }
      return motion;
   }

   private boolean shouldStepUp(Vec3d currentPos, double motionX, double motionZ) {
      if (Math.abs(motionX) < 1.0E-4D && Math.abs(motionZ) < 1.0E-4D) {
         return false;
      }

      Vec3d ahead = currentPos.add(motionX * 2.0D, 0.0D, motionZ * 2.0D);
      return PetUtil.isBlockSolid(ahead.x, ahead.y, ahead.z)
            && !PetUtil.isBlockSolid(ahead.x, ahead.y + 1.0D, ahead.z)
            && (PetUtil.isBlockSolid(currentPos.x, currentPos.y - 0.12D, currentPos.z)
            || isWaterOrLilySupport(currentPos.x, currentPos.y, currentPos.z));
   }

   private Vec3d resolveGroundCollision(Vec3d currentPos) {
      if (PetUtil.isBlockSolid(currentPos.x, currentPos.y, currentPos.z)) {
         int blockY = MathHelper.floor(currentPos.y);
         return new Vec3d(currentPos.x, blockY + 1.1D, currentPos.z);
      }
      return currentPos;
   }

   private Vec3d resolveFluidAndLilyCollision(Vec3d currentPos) {
      double surfaceY = getFluidOrLilySurfaceY(currentPos.x, currentPos.y, currentPos.z);
      if (!Double.isNaN(surfaceY) && currentPos.y < surfaceY) {
         motion = new Vec3d(motion.x, 0.0D, motion.z);
         return new Vec3d(currentPos.x, surfaceY, currentPos.z);
      }
      return currentPos;
   }

   private boolean isWaterOrLilySupport(double x, double y, double z) {
      return !Double.isNaN(getFluidOrLilySurfaceY(x, y, z));
   }

   private double getFluidOrLilySurfaceY(double x, double y, double z) {
      if (mc.world == null) {
         return Double.NaN;
      }

      int blockX = MathHelper.floor(x);
      int baseY = MathHelper.floor(y);
      int blockZ = MathHelper.floor(z);
      BlockPos.Mutable mutable = new BlockPos.Mutable();
      for (int offset = 1; offset >= -2; offset--) {
         int checkY = baseY + offset;
         mutable.set(blockX, checkY, blockZ);
         BlockState state = mc.world.getBlockState(mutable);
         if (state.isOf(Blocks.LILY_PAD)) {
            return checkY + LILY_PAD_SURFACE_OFFSET;
         }
         if (state.isOf(Blocks.WATER)) {
            BlockState above = mc.world.getBlockState(mutable.up());
            if (above.isOf(Blocks.LILY_PAD)) {
               return checkY + 1.0D + LILY_PAD_SURFACE_OFFSET;
            }
            return checkY + WATER_SURFACE_OFFSET;
         }
      }
      return Double.NaN;
   }

   private Vec3d findGroundedPosition(Vec3d anchor) {
      Vec3d best = anchor;
      for (int i = 0; i < 5; i++) {
         Vec3d test = anchor.add(0.0D, i - 2.0D, 0.0D);
         double fluidSurfaceY = getFluidOrLilySurfaceY(test.x, test.y, test.z);
         if (!Double.isNaN(fluidSurfaceY)) {
            best = new Vec3d(test.x, fluidSurfaceY, test.z);
            break;
         }
         if (!PetUtil.isBlockSolid(test.x, test.y, test.z) && PetUtil.isBlockSolid(test.x, test.y - 0.15D, test.z)) {
            best = new Vec3d(test.x, Math.floor(test.y) + 0.1D, test.z);
            break;
         }
      }
      return best;
   }

   private void handleRotation(LivingEntity target, Vec3d desiredPos, Vec3d playerPos) {
      if (yaw == 0.0F) {
         yaw = body;
      }
      if (motion.x * motion.x + motion.z * motion.z > 1.0E-4D) {
         yaw = normalizeDegrees((float) Math.toDegrees(Math.atan2(motion.z, motion.x)) - 90.0F);
      }

      Vec3d lookTarget = entity.getEntityPos().add(0.0D, 1.0D, 0.0D);
      Vec2f lookRotation = PetUtil.rotations(pos, lookTarget);
      if (target != null && entity instanceof ClientPlayerEntity) {
         lookTarget = target.getEntityPos().add(0.0D, target.getHeight() * 0.65D, 0.0D);
         lookRotation = PetUtil.rotations(pos, lookTarget);
         yaw = normalizeDegrees(lookRotation.x);

         bodyAnim.animate(body + wrapDegrees(yaw - body), 130);
         body = body + wrapDegrees(yaw - body);

         headYaw = 0.0F;
         double deltaY = lookTarget.y - (pos.y + 1.0D);
         double horizontalDistance = Math.sqrt(
               (lookTarget.x - pos.x) * (lookTarget.x - pos.x)
                     + (lookTarget.z - pos.z) * (lookTarget.z - pos.z));
         headPitch = MathHelper.clamp((float) Math.toDegrees(Math.atan2(deltaY, Math.max(horizontalDistance, 0.001D))), -35.0F, 35.0F);
         yawAnim.animate(headYaw, 120);
         pitchAnim.animate(headPitch, 110);
         return;
      }

      float lookDelta = wrapDegrees(lookRotation.x - yaw);
      double lookDistanceSq = target == null ? playerPos.subtract(pos).lengthSquared() : 9.0D;
      float bodyTurnLimit = target == null ? 155.0F : 135.0F;
      if (Math.abs(lookDelta) > bodyTurnLimit && lookDistanceSq > 4.0D) {
         yaw = normalizeDegrees(yaw + lookDelta);
         lookDelta = wrapDegrees(lookRotation.x - yaw);
      }

      bodyAnim.animate(body + wrapDegrees(yaw - body), idle ? 180 : 95);
      body = body + wrapDegrees(yaw - body);

      headYaw = MathHelper.clamp(lookDelta, -115.0F, 115.0F);
      double heightDelta = entity.getY() - pos.y;
      double deltaY = Math.abs(heightDelta) > 0.75D ? lookTarget.y - (pos.y + 1.0D) : 0.0D;
      if (Math.abs(deltaY) < 0.2D) {
         deltaY = 0.0D;
      }
      double horizontalDistance = Math.sqrt(
            (lookTarget.x - pos.x) * (lookTarget.x - pos.x)
                  + (lookTarget.z - pos.z) * (lookTarget.z - pos.z));
      headPitch = MathHelper.clamp((float) Math.toDegrees(Math.atan2(deltaY, Math.max(horizontalDistance, 0.001D))), -55.0F, 55.0F);
      yawAnim.animate(headYaw, 85);
      pitchAnim.animate(headPitch, 90);
   }

   public void limbTick() {
      prevLimbSwingAmount = limbSwingAmount;
      double horizontalMotion = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
      float f = (float) (horizontalMotion * 11.5D);
      if (f > 1.0F) {
         f = 1.0F;
      }
      limbSwingAmount += (f - limbSwingAmount) * 0.22F;
      limbSwing += 0.12F + limbSwingAmount * 0.95F;
   }

   private float wrapDegrees(float angle) {
      angle %= 360.0F;
      if (angle >= 180.0F) {
         angle -= 360.0F;
      }
      if (angle < -180.0F) {
         angle += 360.0F;
      }
      return angle;
   }

   private float normalizeDegrees(float angle) {
      angle %= 360.0F;
      if (angle < 0.0F) {
         angle += 360.0F;
      }
      return angle;
   }

   public float getBody() {
      return bodyAnim.get();
   }

   public float getHeadYaw() {
      return yawAnim.get();
   }

   public float getHeadPitch() {
      return pitchAnim.get();
   }

   public boolean isIdle() {
      return idle;
   }

   public float getMoveSpeed() {
      return moveSpeed;
   }

   public Vec3d getPos() {
      return new Vec3d(x.get(), y.get(), z.get());
   }
}
