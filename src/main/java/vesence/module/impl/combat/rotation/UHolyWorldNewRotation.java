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
 * UHolyWorldNewRotation — HolyWorld.
 *
 * <p>ТЗ:
 * <pre>
 *   cap = 180°
 *   speed = attack ? rand(0.86..0.96) : rand(0.1..0.4)
 *   next  = lerp(rand(speed..speed+0.2), current, current + clampΔ)
 *   джиттера нет
 * </pre>
 *
 * <p>Суть: быстрый плавный доворот в атаке, очень медленный дрейф в простое.
 */
@Environment(EnvType.CLIENT)
public class UHolyWorldNewRotation {

    private static final float CAP = 180.0F;

    private static final float SPEED_ATTACK_MIN = 0.86F;
    private static final float SPEED_ATTACK_MAX = 0.96F;
    private static final float SPEED_IDLE_MIN = 0.1F;
    private static final float SPEED_IDLE_MAX = 0.4F;
    /** ТЗ: lerp(rand(speed..speed+0.2), ...). */
    private static final float LERP_SPREAD = 0.2F;

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

        float speed = attack
                ? URotationMath.stableRand("hw.speedAtk", SPEED_ATTACK_MIN, SPEED_ATTACK_MAX, 100L)
                : URotationMath.stableRand("hw.speedIdle", SPEED_IDLE_MIN, SPEED_IDLE_MAX, 200L);
        float lerpK = speed + URotationMath.stableRand("hw.lerpSpread", 0.0F, LERP_SPREAD, 100L);

        float capYaw = URotationMath.axisCap(yawDelta, total, CAP);
        float capPitch = URotationMath.axisCap(pitchDelta, total, CAP);
        float stepYaw = URotationMath.clampDelta(yawDelta, capYaw);
        float stepPitch = URotationMath.clampDelta(pitchDelta, capPitch);

        float newYaw = URotationMath.lerpTowards(currentYaw, currentYaw + stepYaw, lerpK);
        float newPitch = MathHelper.clamp(
                URotationMath.lerpTowards(currentPitch, currentPitch + stepPitch, lerpK), -90.0F, 90.0F);

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
            URotationMath.submit(20.0F, 18.0F, 20.0F, 18.0F, 0, 15, toYaw, toPitch);
        }
        return done;
    }

    public static void state() {
    }
}