package vesence.module.impl.combat.rotation;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.FreeLookUtil;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.Rotation;

import static vesence.module.impl.combat.rotation.URotationMath.rand;
import static vesence.utils.other.IMinecraft.mc;

/**
 * UCakeWorldRotation — UCakeWorldRotation / LonyGrief (одно и то же).
 *
 * <p>ТЗ:
 * <pre>
 *   cap = 180°
 *   baseSpeed = attack ? 0.93 : 0.56
 *   close-range (&lt; 0.66 блока) -> доп. замедление
 *   idle jitter агрессивный:
 *       yaw   += rand(20..26) * sin(ms/25)
 *       pitch += rand(8..23)  * sin(ms/27)
 * </pre>
 */
@Environment(EnvType.CLIENT)
public class UCakeWorldRotation {

    private static final float CAP = 180.0F;

    private static final float SPEED_ATTACK = 0.93F;
    private static final float SPEED_IDLE = 0.56F;

    /** ТЗ: close-range порог. */
    private static final double CLOSE_RANGE = 0.66;
    /** Доп. замедление в упор. */
    private static final float CLOSE_RANGE_SLOWDOWN = 0.35F;

    private static final float IDLE_YAW_MIN = 20.0F;
    private static final float IDLE_YAW_MAX = 26.0F;
    private static final double IDLE_YAW_DIVISOR = 25.0;
    private static final float IDLE_PITCH_MIN = 8.0F;
    private static final float IDLE_PITCH_MAX = 23.0F;
    private static final double IDLE_PITCH_DIVISOR = 27.0;

    public static void rotation(LivingEntity target, boolean attack, float attackDist, float rotateDist) {
        if (mc.player == null || target == null) {
            return;
        }

        Vec3d aim = URotationMath.visibleAimPoint(target, attackDist + rotateDist, false);
        if (aim == null) {
            aim = target.getEyePos();
        }
        Rotation goal = URotationMath.lookAt(aim);

        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();

        float yawDelta = URotationMath.wrap(goal.yaw - currentYaw);
        float pitchDelta = goal.pitch - currentPitch;
        float total = URotationMath.total(yawDelta, pitchDelta);

        float speed = attack ? SPEED_ATTACK : SPEED_IDLE;

        // close-range -> доп. замедление.
        if (mc.player.distanceTo(target) < CLOSE_RANGE) {
            speed *= CLOSE_RANGE_SLOWDOWN;
        }

        float capYaw = URotationMath.axisCap(yawDelta, total, CAP);
        float capPitch = URotationMath.axisCap(pitchDelta, total, CAP);
        float stepYaw = URotationMath.clampDelta(yawDelta, capYaw);
        float stepPitch = URotationMath.clampDelta(pitchDelta, capPitch);

        float newYaw = URotationMath.lerp(speed, currentYaw, currentYaw + stepYaw);
        float newPitch = URotationMath.lerp(speed, currentPitch, currentPitch + stepPitch);

        // idle jitter — только вне окна атаки.
        if (!attack) {
            newYaw += URotationMath.stableRand("lg.idleY", IDLE_YAW_MIN, IDLE_YAW_MAX, 140L)
                    * URotationMath.sinJitter(1.0F, IDLE_YAW_DIVISOR);
            newPitch += URotationMath.stableRand("lg.idleP", IDLE_PITCH_MIN, IDLE_PITCH_MAX, 140L)
                    * URotationMath.sinJitter(1.0F, IDLE_PITCH_DIVISOR);
        }

        newPitch = MathHelper.clamp(newPitch, -90.0F, 90.0F);

        URotationMath.submit(CAP, CAP, 30.0F, 30.0F, 0, 15, newYaw, newPitch);
    }

    public static boolean smoothBack() {
        if (mc.player == null) {
            return true;
        }
        float toYaw = FreeLookUtil.freeYaw;
        float toPitch = FreeLookUtil.freePitch;
        boolean done = Math.abs(URotationMath.wrap(toYaw - mc.player.getYaw())) < 1.0F
                && Math.abs(toPitch - mc.player.getPitch()) < 1.0F;
        if (!done) {
            URotationMath.submit(22.0F, 20.0F, 22.0F, 20.0F, 0, 15, toYaw, toPitch);
        }
        return done;
    }

    public static void state() {
    }
}