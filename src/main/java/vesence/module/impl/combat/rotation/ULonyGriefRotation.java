package vesence.module.impl.combat.rotation;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.Rotation;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.URotations;
import vesence.utils.other.Mathf;


import static vesence.utils.other.IMinecraft.mc;

/**
 * LonyGrief - smooth rotation toward the target with slight jitter.
 * <p>
 * No direct Energy port exists (class absent in the original), so this is a
 * standalone implementation. Similar to ReallyWorld but slightly slower and
 * with a small random offset so the aim is not perfectly centered.
 */
@Environment(EnvType.CLIENT)
public class ULonyGriefRotation {

    private static final float AIM_OFFSET_RANGE = 0.05f;
    private static final float SPEED_NOISE_RANGE = 0.10f;
    private static final int NOISE_UPDATE_INTERVAL = 3;

    private static LivingEntity trackedTarget = null;
    private static Vec3 aimOffset = Vec3.ZERO;
    private static float speedNoiseYaw = 1.0f;
    private static float speedNoisePitch = 1.0f;
    private static int noiseTick = 0;

    public static void rotation(LivingEntity target, float attackDist, float rotateDist) {
        rotation(target, attackDist, rotateDist, target != null ? target.getBoundingBox().getCenter() : null);
    }

    public static void rotation(LivingEntity target, float attackDist, float rotateDist, Vec3 point) {
        if (mc.player == null || target == null) return;

        // ТЗ: аим-поинт — ближайшая ВИДИМАЯ точка хитбокса, не центр коробки.
        Vec3 resolved = URotationMath.visibleAimPoint(target, rotateDist, false);
        if (resolved == null) resolved = point != null ? point : target.getBoundingBox().getCenter();
        point = resolved;

        noiseTick++;
        if (noiseTick % NOISE_UPDATE_INTERVAL == 0) {
            speedNoiseYaw = 1.0f + Mathf.randomValue(-SPEED_NOISE_RANGE, SPEED_NOISE_RANGE);
            speedNoisePitch = 1.0f + Mathf.randomValue(-SPEED_NOISE_RANGE, SPEED_NOISE_RANGE);
        }

        if (trackedTarget != target) {
            trackedTarget = target;
            aimOffset = new Vec3(
                    Mathf.randomValue(-AIM_OFFSET_RANGE, AIM_OFFSET_RANGE),
                    Mathf.randomValue(-AIM_OFFSET_RANGE * 0.5f, AIM_OFFSET_RANGE * 0.5f),
                    Mathf.randomValue(-AIM_OFFSET_RANGE, AIM_OFFSET_RANGE)
            );
        }

        // ТЗ: в окне атаки аим-точка чистая — случайный offset вбок/вниз
        // сдвигает прицел ровно на тике удара.
        Vec3 effectiveOffset = URotationMath.inAttackWindow() ? Vec3.ZERO : aimOffset;
        Vec3 dir = point.add(effectiveOffset).subtract(mc.player.getEyePosition());
        float targetYaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        float targetPitch = (float) Mth.clamp(
                -Math.toDegrees(Math.atan2(dir.y, Math.hypot(dir.x, dir.z))),
                -90.0, 90.0
        );

        float currentYaw = mc.player.getYRot();
        float currentPitch = mc.player.getXRot();

        float deltaYaw = Mth.wrapDegrees(targetYaw - currentYaw);
        float deltaPitch = targetPitch - currentPitch;

        float distance = (float) mc.player.getEyePosition().distanceTo(point);
        float speedScale = 1.0f + Mth.clamp(distance / 4.0f, 0.0f, 1.2f);

        float yawSpeed = 20.0f * speedScale * (URotationMath.inAttackWindow() ? 1.0f : speedNoiseYaw);
        float pitchSpeed = 18.0f * speedScale * (URotationMath.inAttackWindow() ? 1.0f : speedNoisePitch);

        float newYaw = currentYaw + Mth.clamp(deltaYaw, -yawSpeed, yawSpeed);
        float newPitch = Mth.clamp(
                currentPitch + Mth.clamp(deltaPitch, -pitchSpeed, pitchSpeed),
                -90f, 90f
        );

        Rotation rot = new Rotation(newYaw, newPitch);
        URotations.update(rot, yawSpeed, pitchSpeed, 18f, 16f, 3, 10, false);
    }

    public static void state() {
        trackedTarget = null;
        aimOffset = Vec3.ZERO;
        speedNoiseYaw = 1.0f;
        speedNoisePitch = 1.0f;
        noiseTick = 0;
    }
}