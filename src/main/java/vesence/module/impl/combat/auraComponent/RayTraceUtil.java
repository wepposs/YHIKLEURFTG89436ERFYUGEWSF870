package vesence.module.impl.combat.auraComponent;

import lombok.Generated;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.Level;
import vesence.utils.other.IMinecraft;

import java.util.Optional;
import java.util.function.Predicate;

@Environment(EnvType.CLIENT)
public final class RayTraceUtil implements IMinecraft {
   public static EntityHitResult traceEntities(
         Entity shooter, Vec3 startVector, Vec3 endVector, AABB boundingBox, Predicate<Entity> filter,
         double distance) {
      Level world = Minecraft.getInstance().level;
      double closestDistance = distance;
      Entity closestEntity = null;
      Vec3 closestHitVector = null;

      for (Entity entity : world.getEntities(shooter, boundingBox, filter)) {
         AABB entityBoundingBox = entity.getBoundingBox().inflate(entity.getPickRadius());
         Optional<Vec3> optional = entityBoundingBox.clip(startVector, endVector);
         if (entityBoundingBox.contains(startVector) || optional.isPresent()) {
            double distanceToHit = optional.<Double>map(startVector::distanceToSqr).orElse(0.0);
            distanceToHit = Math.sqrt(distanceToHit);
            if ((distanceToHit < closestDistance || closestDistance == 0.0)
                  && entity.getRootVehicle() != shooter.getRootVehicle()) {
               closestEntity = entity;
               closestHitVector = optional.orElse(startVector);
               closestDistance = distanceToHit;
            }
         }
      }

      return closestEntity == null ? null : new EntityHitResult(closestEntity, closestHitVector);
   }

   public static HitResult calculateRayTrace(double distance, float yaw, float pitch, Entity entity,
         boolean ignoreBlocks) {
      float tickDelta = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
      Vec3 startVector = mc.player.getEyePosition(tickDelta);
      Vec3 directionVector = getVectorForRotation(pitch, yaw);
      Vec3 endVector = startVector.add(directionVector.scale(distance));
      HitResult blockResult = traceBlock(startVector, endVector, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE);
      double entityDistance = blockResult.getLocation().distanceToSqr(startVector);
      AABB entityBoundingBox = entity.getBoundingBox().expandTowards(directionVector.scale(distance)).inflate(1.0);
      EntityHitResult entityHitResult = ProjectileUtil.getEntityHitResult(
            entity, startVector, endVector, entityBoundingBox, x -> !x.isSpectator() && x.isAlive() && x.isPickable(),
            distance * distance);
      return (HitResult) (entityHitResult == null
            || !ignoreBlocks && !(entityHitResult.getLocation().distanceToSqr(startVector) < entityDistance)
                  ? blockResult
                  : entityHitResult);
   }

   public static boolean rayTraceEntity(float yaw, float pitch, double distance, Entity entity) {
      float tickDelta = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
      Vec3 eyeVec = mc.player.getEyePosition(tickDelta);
      Vec3 lookVec = getVectorForRotation(pitch, yaw);
      Vec3 endVec = eyeVec.add(lookVec.scale(distance));
      AABB entityBox = entity.getBoundingBox();
      return entityBox.contains(eyeVec) || entityBox.clip(eyeVec, endVec).isPresent();
   }

   public static Vec3 getVectorForRotation(float pitch, float yaw) {
      float yawRadians = -yaw * (float) (Math.PI / 180.0) - (float) Math.PI;
      float pitchRadians = -pitch * (float) (Math.PI / 180.0);
      float cosYaw = (float) Math.cos(yawRadians);
      float sinYaw = (float) Math.sin(yawRadians);
      float cosPitch = -(float) Math.cos(pitchRadians);
      float sinPitch = (float) Math.sin(pitchRadians);
      return new Vec3(sinYaw * cosPitch, sinPitch, cosYaw * cosPitch);
   }
   
   public static boolean rayTraceSingleEntity(float yaw, float pitch, double distance, Entity entity) {
      Vec3 eyeVec = mc.player.getEyePosition();
      Vec3 lookVec = RayTraceUtil.getVectorForRotation(pitch, yaw);
      Vec3 extendedVec = eyeVec.add(lookVec.scale(distance));
      AABB AABB2 = entity.getBoundingBox();
      return AABB2.contains(eyeVec) || AABB2.clip(eyeVec, extendedVec).isPresent();
   }

   public static boolean rayTraceSmallHitBox(float yaw, float pitch, double distance, Entity entity) {
      Vec3 eyeVec = mc.player.getEyePosition();
      Vec3 lookVec = RayTraceUtil.getVectorForRotation(pitch, yaw);
      Vec3 extendedVec = eyeVec.add(lookVec.scale(distance));
      AABB originalBB = entity.getBoundingBox();
      double centerX = (originalBB.minX + originalBB.maxX) / 2.0;
      float value = 1.6f;
      double halfX = (originalBB.maxX - originalBB.minX) / 2.0 / (double)value;
      double centerY = (originalBB.minY + originalBB.maxY) / 2.0;
      double halfY = (originalBB.maxY - originalBB.minY) / 2.0 / (double)1.2f;
      double centerZ = (originalBB.minZ + originalBB.maxZ) / 2.0;
      double halfZ = (originalBB.maxZ - originalBB.minZ) / 2.0 / (double)value;
      AABB smallBB = new AABB(centerX - halfX, centerY - halfY, centerZ - halfZ, centerX + halfX, centerY + halfY, centerZ + halfZ);
      return smallBB.contains(eyeVec) || smallBB.clip(eyeVec, extendedVec).isPresent();
   }
   
   public static HitResult traceBlock(Vec3 startVec, Vec3 endVec, ClipContext.Block blockMode, ClipContext.Fluid fluidMode) {
      return mc.level.clip(new ClipContext(startVec, endVec, blockMode, fluidMode, mc.player));
   }

   public static Vec3 calculateViewVector(float yaw, float pitch) {
      float pitchRad = (float) (pitch * (Math.PI / 180.0));
      float yawRad = (float) (-yaw * (Math.PI / 180.0));
      float cosYaw = (float) Math.cos(yawRad);
      float sinYaw = (float) Math.sin(yawRad);
      float cosPitch = (float) Math.cos(pitchRad);
      float sinPitch = (float) Math.sin(pitchRad);
      return new Vec3(sinYaw * cosPitch, -sinPitch, cosYaw * cosPitch);
   }

   @Generated
   private RayTraceUtil() {
      throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
   }
}
