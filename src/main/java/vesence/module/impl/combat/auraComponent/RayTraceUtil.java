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
import net.minecraft.world.ClipContext.FluidHandling;
import net.minecraft.world.ClipContext.ShapeType;
import net.minecraft.world.level.Level;
import vesence.utils.other.IMinecraft;

import java.util.Optional;
import java.util.function.Predicate;

@Environment(EnvType.CLIENT)
public final class RayTraceUtil implements IMinecraft {
   public static EntityHitResult traceEntities(
         Entity shooter, Vec3 startVector, Vec3 endVector, AABB boundingBox, Predicate<Entity> filter,
         double distance) {
      Level world = Minecraft.getInstance().world;
      double closestDistance = distance;
      Entity closestEntity = null;
      Vec3 closestHitVector = null;

      for (Entity entity : world.getOtherEntities(shooter, boundingBox, filter)) {
         AABB entityBoundingBox = entity.getBoundingBox().expand(entity.getTargetingMargin());
         Optional<Vec3> optional = entityBoundingBox.raycast(startVector, endVector);
         if (entityBoundingBox.contains(startVector) || optional.isPresent()) {
            double distanceToHit = optional.<Double>map(startVector::squaredDistanceTo).orElse(0.0);
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
      float tickDelta = mc.getRenderTickCounter().getTickProgress(true);
      Vec3 startVector = mc.player.getCameraPosVec(tickDelta);
      Vec3 directionVector = getVectorForRotation(pitch, yaw);
      Vec3 endVector = startVector.add(directionVector.multiply(distance));
      HitResult blockResult = traceBlock(startVector, endVector, ShapeType.COLLIDER, FluidHandling.NONE);
      double entityDistance = blockResult.getPos().squaredDistanceTo(startVector);
      AABB entityBoundingBox = entity.getBoundingBox().stretch(directionVector.multiply(distance)).expand(1.0);
      EntityHitResult entityHitResult = ProjectileUtil.raycast(
            entity, startVector, endVector, entityBoundingBox, x -> !x.isSpectator() && x.isAlive() && x.canHit(),
            distance * distance);
      return (HitResult) (entityHitResult == null
            || !ignoreBlocks && !(entityHitResult.getPos().squaredDistanceTo(startVector) < entityDistance)
                  ? blockResult
                  : entityHitResult);
   }

   public static boolean rayTraceEntity(float yaw, float pitch, double distance, Entity entity) {
      float tickDelta = mc.getRenderTickCounter().getTickProgress(true);
      Vec3 eyeVec = mc.player.getCameraPosVec(tickDelta);
      Vec3 lookVec = getVectorForRotation(pitch, yaw);
      Vec3 endVec = eyeVec.add(lookVec.multiply(distance));
      AABB entityBox = entity.getBoundingBox();
      return entityBox.contains(eyeVec) || entityBox.raycast(eyeVec, endVec).isPresent();
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
      Vec3 extendedVec = eyeVec.add(lookVec.multiply(distance));
      AABB AABB2 = entity.getBoundingBox();
      return AABB2.contains(eyeVec) || AABB2.raycast(eyeVec, extendedVec).isPresent();
   }

   public static boolean rayTraceSmallHitBox(float yaw, float pitch, double distance, Entity entity) {
      Vec3 eyeVec = mc.player.getEyePosition();
      Vec3 lookVec = RayTraceUtil.getVectorForRotation(pitch, yaw);
      Vec3 extendedVec = eyeVec.add(lookVec.multiply(distance));
      AABB originalBB = entity.getBoundingBox();
      double centerX = (originalBB.minX + originalBB.maxX) / 2.0;
      float value = 1.6f;
      double halfX = (originalBB.maxX - originalBB.minX) / 2.0 / (double)value;
      double centerY = (originalBB.minY + originalBB.maxY) / 2.0;
      double halfY = (originalBB.maxY - originalBB.minY) / 2.0 / (double)1.2f;
      double centerZ = (originalBB.minZ + originalBB.maxZ) / 2.0;
      double halfZ = (originalBB.maxZ - originalBB.minZ) / 2.0 / (double)value;
      AABB smallBB = new AABB(centerX - halfX, centerY - halfY, centerZ - halfZ, centerX + halfX, centerY + halfY, centerZ + halfZ);
      return smallBB.contains(eyeVec) || smallBB.raycast(eyeVec, extendedVec).isPresent();
   }
   
   public static HitResult traceBlock(Vec3 startVec, Vec3 endVec, ShapeType blockMode, FluidHandling fluidMode) {
      return mc.level.raycast(new ClipContext(startVec, endVec, blockMode, fluidMode, mc.player));
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
