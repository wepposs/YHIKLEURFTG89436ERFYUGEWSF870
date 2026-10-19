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
 * USlothRotation — Sloth (лёгкий / легит).
 *
 * <p>ТЗ:
 * <pre>
 *   yawStep   = clamp(Δ, ±rand(7..12)°)
 *   pitchStep = clamp(Δ, ±rand(4..7)°)
 *   микро-джиттер ±0.3°
 *   GCD-снап
 * </pre>
 *
 * <p>Медленно, но почти не отличимо от руки: жёсткий мелкий шаг за тик,
 * микро-джиттер и снап на сетку мыши.
 */
@Environment(EnvType.CLIENT)
public class USlothRotation {

    private static final float YAW_STEP_MIN = 7.0F;
    private static final float YAW_STEP_MAX = 12.0F;
    private static final float PITCH_STEP_MIN = 4.0F;
    private static final float PITCH_STEP_MAX = 7.0F;
    private static final float MICRO_JITTER = 0.3F;

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

        float yawStep = URotationMath.clampDelta(yawDelta,
                URotationMath.stableRand("sl.yawStep", YAW_STEP_MIN, YAW_STEP_MAX, 90L));
        float pitchStep = URotationMath.clampDelta(pitchDelta,
                URotationMath.stableRand("sl.pitchStep", PITCH_STEP_MIN, PITCH_STEP_MAX, 90L));

        float newYaw = currentYaw + yawStep;
        float newPitch = MathHelper.clamp(currentPitch + pitchStep, -90.0F, 90.0F);

        // микро-джиттер — только вне окна атаки (в окне атаки ротация чистая).
        if (!attack) {
            newYaw += URotationMath.stableRand("sl.jitY", -MICRO_JITTER, MICRO_JITTER, 70L);
            newPitch += URotationMath.stableRand("sl.jitP", -MICRO_JITTER, MICRO_JITTER, 70L);
        }

        URotationMath.submit(YAW_STEP_MAX, PITCH_STEP_MAX, 20.0F, 18.0F, 0, 15, newYaw, newPitch);
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
            URotationMath.submit(14.0F, 12.0F, 14.0F, 12.0F, 0, 15, toYaw, toPitch);
        }
        return done;
    }

    public static void state() {
    }
}