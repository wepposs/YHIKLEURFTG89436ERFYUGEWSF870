package vesence.module.impl.combat.rotation;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import vesence.module.impl.combat.auraComponent.GCDUtil;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.FreeLookUtil;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.Rotation;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.URotations;

import java.util.concurrent.ThreadLocalRandom;

import static vesence.utils.other.IMinecraft.mc;

@Environment(EnvType.CLIENT)
public class UShardRotation {

    private static final float MAX_YAW_SPEED = 54.0F;
    private static final float MAX_PITCH_SPEED = 38.0F;
    private static final float MIN_ACCEL = 4.0F;
    private static final float MAX_ACCEL = 12.0F;
    private static final float SETTLE_THRESHOLD = 8.5F;

    private static int targetId = Integer.MIN_VALUE;
    private static float currentYawVelocity = 0.0F;
    private static float currentPitchVelocity = 0.0F;
    private static float lastYawDelta = 0.0F;
    private static float lastPitchDelta = 0.0F;
    private static float residual = Float.MAX_VALUE;

    private static double offsetSeedX = 0.0;
    private static double offsetSeedY = 0.0;
    private static double offsetSeedZ = 0.0;
    private static long lastHitTime = 0L;
    private static long targetStartTime = 0L;
    private static int consecutiveHits = 0;

    private UShardRotation() {
    }

    public static boolean isSettled() {
        return residual <= SETTLE_THRESHOLD;
    }

    public static float residual() {
        return residual;
    }

    public static void rotation(LivingEntity target, boolean attack, float attackDist, float rotateDist, Vec3 point) {
        if (mc.player == null || target == null) {
            reset();
            return;
        }

        long now = System.currentTimeMillis();
        if (target.getId() != targetId) {
            targetId = target.getId();
            targetStartTime = now;
            currentYawVelocity = 0.0F;
            currentPitchVelocity = 0.0F;
            lastYawDelta = 0.0F;
            lastPitchDelta = 0.0F;
            consecutiveHits = 0;
            offsetSeedX = ThreadLocalRandom.current().nextDouble(-0.04, 0.04);
            offsetSeedY = ThreadLocalRandom.current().nextDouble(-0.06, 0.06);
            offsetSeedZ = ThreadLocalRandom.current().nextDouble(-0.04, 0.04);
        }

        Vec3 targetAim = calculateAimPoint(target);
        Rotation targetRot = calculateAngle(targetAim);

        float curYaw = mc.player.getYRot();
        float curPitch = mc.player.getXRot();

        float deltaYaw = Mth.wrapDegrees(targetRot.yaw - curYaw);
        float deltaPitch = targetRot.pitch - curPitch;
        float totalDist = (float) Math.hypot(deltaYaw, deltaPitch);

        if (totalDist < 1.0E-4F) {
            residual = 0.0F;
            return;
        }

        if (now - targetStartTime < 35L) {
            residual = totalDist;
            return;
        }

        boolean postHit = now - lastHitTime < 65L;
        float speedScale = postHit ? 0.65F : 1.0F;

        float targetYawVel = computeVelocity(deltaYaw, totalDist, MAX_YAW_SPEED * speedScale, lastYawDelta);
        float targetPitchVel = computeVelocity(deltaPitch, totalDist, MAX_PITCH_SPEED * speedScale, lastPitchDelta);

        float accelLimit = ThreadLocalRandom.current().nextFloat() * (MAX_ACCEL - MIN_ACCEL) + MIN_ACCEL;
        currentYawVelocity = approachVelocity(currentYawVelocity, targetYawVel, accelLimit);
        currentPitchVelocity = approachVelocity(currentPitchVelocity, targetPitchVel, accelLimit * 0.85F);

        float stepYaw = currentYawVelocity;
        float stepPitch = currentPitchVelocity;

        if (!attack && !postHit) {
            double time = (double) now * 0.005;
            float microJitterYaw = (float) (Math.sin(time * 3.7) * 0.06 + Math.cos(time * 1.3) * 0.04);
            float microJitterPitch = (float) (Math.cos(time * 2.9) * 0.05 + Math.sin(time * 0.9) * 0.03);
            stepYaw += microJitterYaw;
            stepPitch += microJitterPitch;
        }

        float desiredYaw = curYaw + stepYaw;
        float desiredPitch = Mth.clamp(curPitch + stepPitch, -90.0F, 90.0F);

        float snappedYaw = GCDUtil.snapToCameraGCD(curYaw, desiredYaw);
        float snappedPitch = GCDUtil.snapPitchToCameraGCD(curPitch, desiredPitch);

        URotations.update(new Rotation(snappedYaw, snappedPitch), 360.0F, 360.0F, 24.0F, 20.0F, 0, 15, false);

        lastYawDelta = deltaYaw;
        lastPitchDelta = deltaPitch;

        residual = (float) Math.hypot(
                Mth.wrapDegrees(targetRot.yaw - snappedYaw),
                targetRot.pitch - snappedPitch
        );

        if (attack) {
            lastHitTime = now;
            consecutiveHits++;
            offsetSeedX += ThreadLocalRandom.current().nextDouble(-0.03, 0.03);
            offsetSeedY += ThreadLocalRandom.current().nextDouble(-0.02, 0.02);
            offsetSeedZ += ThreadLocalRandom.current().nextDouble(-0.03, 0.03);
        }
    }

    private static float computeVelocity(float delta, float total, float maxSpeed, float lastDelta) {
        float dir = Math.signum(delta);
        float absDelta = Math.abs(delta);

        float dynamicMax = (absDelta / total) * maxSpeed;
        float desiredSpeed;

        if (absDelta > 20.0F) {
            desiredSpeed = dynamicMax;
        } else if (absDelta > 5.0F) {
            float t = (absDelta - 5.0F) / 15.0F;
            desiredSpeed = dynamicMax * (0.5F + 0.5F * smoothStep(t));
        } else {
            float t = absDelta / 5.0F;
            desiredSpeed = Math.max(0.7F, dynamicMax * (0.25F + 0.45F * t));
        }

        if (lastDelta != 0.0F && Math.signum(delta) != Math.signum(lastDelta) && absDelta < 10.0F) {
            desiredSpeed *= 0.8F;
        }

        return dir * desiredSpeed;
    }

    private static float approachVelocity(float current, float target, float maxDelta) {
        float diff = target - current;
        if (Math.abs(diff) <= maxDelta) {
            return target;
        }
        return current + Math.signum(diff) * maxDelta;
    }

    private static float smoothStep(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static Vec3 calculateAimPoint(LivingEntity target) {
        AABB bb = target.getBoundingBox();
        Vec3 center = bb.getCenter();
        double targetY = bb.minY + bb.getYsize() * 0.72;
        center = new Vec3(center.x, targetY, center.z);

        double dynamicWobble = Math.sin((double) System.currentTimeMillis() * 0.0024) * 0.025;
        return center.add(offsetSeedX + dynamicWobble, offsetSeedY, offsetSeedZ);
    }

    private static Rotation calculateAngle(Vec3 point) {
        Vec3 eyes = mc.player.getEyePosition();
        Vec3 diff = point.subtract(eyes);
        float yaw = (float) Math.toDegrees(Math.atan2(-diff.x, diff.z));
        float pitch = (float) Mth.clamp(-Math.toDegrees(Math.atan2(diff.y, Math.hypot(diff.x, diff.z))), -90.0F, 90.0F);
        return new Rotation(yaw, pitch);
    }

    public static boolean smoothBack() {
        if (mc.player == null) {
            return true;
        }
        float toYaw = FreeLookUtil.freeYaw;
        float toPitch = FreeLookUtil.freePitch;

        float deltaYaw = Mth.wrapDegrees(toYaw - mc.player.getYRot());
        float deltaPitch = toPitch - mc.player.getXRot();

        if (Math.abs(deltaYaw) < 1.0F && Math.abs(deltaPitch) < 1.0F) {
            return true;
        }

        float stepY = Math.signum(deltaYaw) * Math.min(Math.abs(deltaYaw) * 0.35F, 18.0F);
        float stepP = Math.signum(deltaPitch) * Math.min(Math.abs(deltaPitch) * 0.35F, 14.0F);

        float targetY = mc.player.getYRot() + stepY;
        float targetP = Mth.clamp(mc.player.getXRot() + stepP, -90.0F, 90.0F);

        float snappedY = GCDUtil.snapToCameraGCD(mc.player.getYRot(), targetY);
        float snappedP = GCDUtil.snapPitchToCameraGCD(mc.player.getXRot(), targetP);

        URotations.update(new Rotation(snappedY, snappedP), 24.0F, 20.0F, 24.0F, 20.0F, 0, 15, false);
        return false;
    }

    public static void state() {
        reset();
    }

    private static void reset() {
        targetId = Integer.MIN_VALUE;
        currentYawVelocity = 0.0F;
        currentPitchVelocity = 0.0F;
        lastYawDelta = 0.0F;
        lastPitchDelta = 0.0F;
        residual = Float.MAX_VALUE;
        offsetSeedX = 0.0;
        offsetSeedY = 0.0;
        offsetSeedZ = 0.0;
        lastHitTime = 0L;
        targetStartTime = 0L;
        consecutiveHits = 0;
    }
}
