package vesence.module.impl.combat.rotation;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.FreeLookUtil;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.Rotation;

import static vesence.utils.other.IMinecraft.mc;

/**
 * UHvHRotation — UHvHRotation-сервера.
 *
 * <p>ТЗ:
 * <pre>
 *   cap = 360° обе оси
 *   speed ≈ 1 (почти инстант — на UHvHRotation ротационных проверок нет/слабые)
 *   idle jitter: 5*sin(ms/45) по обеим осям
 * </pre>
 *
 * <p>Для UHvHRotation главное — скорость и реоч, а не «легит». Профиль сознательно не
 * маскируется: он для серверов без ротационных проверок.
 */
@Environment(EnvType.CLIENT)
public class UHvHRotation {

    private static final float CAP = 360.0F;
    private static final float SPEED = 1.0F;

    private static final double IDLE_DIVISOR = 45.0;
    private static final float IDLE_AMP = 5.0F;

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

        float capYaw = URotationMath.axisCap(yawDelta, total, CAP);
        float capPitch = URotationMath.axisCap(pitchDelta, total, CAP);

        float newYaw = currentYaw + URotationMath.clampDelta(yawDelta, capYaw);
        float newPitch = currentPitch + URotationMath.clampDelta(pitchDelta, capPitch);

        // idle jitter по обеим осям.
        if (!attack) {
            newYaw += URotationMath.sinJitter(IDLE_AMP, IDLE_DIVISOR);
            newPitch += URotationMath.sinJitter(IDLE_AMP, IDLE_DIVISOR);
        }

        newPitch = MathHelper.clamp(newPitch, -90.0F, 90.0F);

        URotationMath.submit(CAP, CAP, 40.0F, 40.0F, 0, 15, newYaw, newPitch);
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
            URotationMath.submit(90.0F, 90.0F, 90.0F, 90.0F, 0, 15, toYaw, toPitch);
        }
        return done;
    }

    public static void state() {
    }
}