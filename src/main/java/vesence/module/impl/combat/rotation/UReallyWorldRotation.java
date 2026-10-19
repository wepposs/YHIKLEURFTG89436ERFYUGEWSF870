package vesence.module.impl.combat.rotation;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.FreeLookUtil;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.Rotation;
import vesence.module.impl.combat.rotation.URotationMath.AttackEdge;
import vesence.module.impl.combat.rotation.URotationMath.HitWindow;

import static vesence.module.impl.combat.rotation.URotationMath.rand;
import static vesence.utils.other.IMinecraft.mc;

/**
 * UReallyWorldRotation — ReallyWorld.
 *
 * <p>ТЗ:
 * <pre>
 *   cap = |axisΔ/total| * 180°
 *   speed ≈ 1 -> next = lerp(rand(1..1.2), current, current + clampΔ)
 *   idle jitter: yaw += -6*cos(ms/90), pitch += 6*sin(ms/90)
 *   каждый 50-й хит в окне 200ms -> pitch = lerp(0.55, current, current - 90)
 *                                   (резкий флик вниз)
 * </pre>
 */
@Environment(EnvType.CLIENT)
public class UReallyWorldRotation {

    private static final float CAP = 180.0F;

    private static final double IDLE_DIVISOR = 90.0;
    private static final float IDLE_AMP = 6.0F;

    /** ТЗ: каждый 50-й хит в окне 200ms. */
    private static final long FLICK_WINDOW_MS = 200L;
    private static final int FLICK_EVERY = 50;
    /** ТЗ: pitch = lerp(0.55, current, current - 90). */
    private static final float FLICK_LERP = 0.55F;
    private static final float FLICK_TARGET_DELTA = -90.0F;

    private static final HitWindow flickWindow = new HitWindow(FLICK_WINDOW_MS, FLICK_EVERY);
    private static final AttackEdge attackEdge = new AttackEdge();

    private static boolean flicking;
    private static long flickEndMs;
    /** Сколько тиков держим флик-питч (резкий флик вниз = 1 тик + удержание). */
    private static final long FLICK_DURATION_MS = 120L;

    public static void rotation(LivingEntity target, boolean attack, float attackDist, float rotateDist, Vec3 point) {
        if (mc.player == null || target == null) {
            return;
        }

        Vec3 aim = URotationMath.visibleAimPoint(target, attackDist + rotateDist, false);
        if (aim == null) {
            aim = point != null ? point : target.getEyePosition();
        }
        Rotation goal = URotationMath.lookAt(aim);

        float currentYaw = mc.player.getYRot();
        float currentPitch = mc.player.getXRot();

        float yawDelta = URotationMath.wrap(goal.yaw - currentYaw);
        float pitchDelta = goal.pitch - currentPitch;
        float total = URotationMath.total(yawDelta, pitchDelta);

        boolean risingEdge = attackEdge.update(attack);
        if (risingEdge && flickWindow.onHit()) {
            flicking = true;
            flickEndMs = System.currentTimeMillis() + FLICK_DURATION_MS;
        }

        float capYaw = URotationMath.axisCap(yawDelta, total, CAP);
        float capPitch = URotationMath.axisCap(pitchDelta, total, CAP);
        float stepYaw = URotationMath.clampDelta(yawDelta, capYaw);
        float stepPitch = URotationMath.clampDelta(pitchDelta, capPitch);

        float lerpK = URotationMath.stableRand("rw.lerpK", 1.0F, 1.2F, 120L);
        // lerpTowards, а не lerp: при k > 1 обычный lerp проскакивает цель и
        // следующий тик возвращает угол назад — это дёрганье.
        float newYaw = URotationMath.lerpTowards(currentYaw, currentYaw + stepYaw, lerpK);
        float newPitch = URotationMath.lerpTowards(currentPitch, currentPitch + stepPitch, lerpK);

        if (flicking) {
            if (System.currentTimeMillis() >= flickEndMs) {
                flicking = false;
            } else {
                // ТЗ: pitch = lerp(0.55, current, current - 90).
                newPitch = URotationMath.lerp(FLICK_LERP, currentPitch, currentPitch + FLICK_TARGET_DELTA);
            }
        } else {
            // idle jitter — только вне окна атаки (в окне атаки ротация чистая).
            // ТЗ: yaw += -6*cos(ms/90), pitch += 6*sin(ms/90).
            if (!attack) {
                newYaw += -IDLE_AMP * URotationMath.cosJitter(1.0F, IDLE_DIVISOR);
                newPitch += URotationMath.sinJitter(IDLE_AMP, IDLE_DIVISOR);
            }
        }

        newPitch = Mth.clamp(newPitch, -90.0F, 90.0F);

        URotationMath.submit(CAP, CAP, 30.0F, 30.0F, 0, 15, newYaw, newPitch);
    }

    public static boolean isFlicking() {
        return flicking;
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
            URotationMath.submit(26.0F, 24.0F, 26.0F, 24.0F, 0, 15, toYaw, toPitch);
        }
        return done;
    }

    public static void state() {
        flickWindow.reset();
        attackEdge.reset();
        flicking = false;
        flickEndMs = 0L;
    }
}