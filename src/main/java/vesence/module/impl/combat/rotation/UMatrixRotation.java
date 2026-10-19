package vesence.module.impl.combat.rotation;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.FreeLookUtil;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.Rotation;

import static vesence.module.impl.combat.rotation.URotationMath.rand;
import static vesence.utils.other.IMinecraft.mc;

/**
 * UMatrixRotation — Matrix.
 *
 * <p>ТЗ:
 * <pre>
 *   yawCap   = attack ? 360° : 100°
 *   pitchCap = 180°
 *   speed    = attack ? 1 : rand(0..0.5)
 *   idle jitter с рандомной частотой:
 *       yaw   += rand(0..6) * sin(ms / rand(15..145))
 *       pitch += rand(1..3) * sin(ms / rand(15..145))
 * </pre>
 *
 * <p>Рандомная частота ломает спектральный анализ — это ключевая часть профиля,
 * поэтому делитель перевыбирается на каждом тике, а не фиксируется.
 */
@Environment(EnvType.CLIENT)
public class UMatrixRotation {

    private static final float YAW_CAP_ATTACK = 360.0F;
    private static final float YAW_CAP_IDLE = 100.0F;
    private static final float PITCH_CAP = 180.0F;

    private static final float SPEED_ATTACK = 1.0F;
    private static final float SPEED_IDLE_MAX = 0.5F;

    private static final float IDLE_YAW_AMP_MIN = 0.0F;
    private static final float IDLE_YAW_AMP_MAX = 6.0F;
    private static final float IDLE_PITCH_AMP_MIN = 1.0F;
    private static final float IDLE_PITCH_AMP_MAX = 3.0F;
    private static final double FREQ_DIV_MIN = 15.0;
    private static final double FREQ_DIV_MAX = 145.0;

    public static void rotation(LivingEntity target, boolean attack, float attackDist, float rotateDist) {
        if (mc.player == null || target == null) {
            return;
        }

        Vec3 aim = URotationMath.visibleAimPoint(target, attackDist + rotateDist, false);
        if (aim == null) {
            aim = target.getEyePosition();
        }
        Rotation goal = URotationMath.lookAt(aim);

        float currentYaw = mc.player.getYRot();
        float currentPitch = mc.player.getXRot();

        float yawDelta = URotationMath.wrap(goal.yaw - currentYaw);
        float pitchDelta = goal.pitch - currentPitch;
        float total = URotationMath.total(yawDelta, pitchDelta);

        float yawCap = attack ? YAW_CAP_ATTACK : YAW_CAP_IDLE;
        float speed = attack ? SPEED_ATTACK : URotationMath.stableRand("mx.speed", 0.0F, SPEED_IDLE_MAX, 200L);

        float capYaw = URotationMath.axisCap(yawDelta, total, yawCap);
        float capPitch = URotationMath.axisCap(pitchDelta, total, PITCH_CAP);
        float stepYaw = URotationMath.clampDelta(yawDelta, capYaw);
        float stepPitch = URotationMath.clampDelta(pitchDelta, capPitch);

        float newYaw = URotationMath.lerp(speed, currentYaw, currentYaw + stepYaw);
        float newPitch = URotationMath.lerp(speed, currentPitch, currentPitch + stepPitch);

        // idle jitter — только вне окна атаки; делитель рандомный на каждом тике.
        if (!attack) {
            newYaw += URotationMath.stableRand("mx.idleY", IDLE_YAW_AMP_MIN, IDLE_YAW_AMP_MAX, 150L)
                    * URotationMath.sinJitter(1.0F, URotationMath.stableRand("mx.freqY", (float) FREQ_DIV_MIN, (float) FREQ_DIV_MAX, 400L));
            newPitch += URotationMath.stableRand("mx.idleP", IDLE_PITCH_AMP_MIN, IDLE_PITCH_AMP_MAX, 150L)
                    * URotationMath.sinJitter(1.0F, URotationMath.stableRand("mx.freqP", (float) FREQ_DIV_MIN, (float) FREQ_DIV_MAX, 400L));
        }

        newPitch = Mth.clamp(newPitch, -90.0F, 90.0F);

        URotationMath.submit(360.0F, 360.0F, 30.0F, 30.0F, 0, 15, newYaw, newPitch);
    }

    public static boolean smoothBack() {
        if (mc.player == null) {
            return true;
        }
        float toYaw = FreeLookUtil.freeYaw;
        float toPitch = FreeLookUtil.freePitch;
        boolean done = Math.abs(URotationMath.wrap(toYaw - mc.player.getYRot())) < 1.0F
                && Math.abs(toPitch - mc.player.getXRot()) < 1.0F;
        if (!done) {
            URotationMath.submit(60.0F, 55.0F, 60.0F, 55.0F, 0, 15, toYaw, toPitch);
        }
        return done;
    }

    public static void state() {
    }
}