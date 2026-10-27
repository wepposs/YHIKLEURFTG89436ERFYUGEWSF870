package vesence.module.impl.combat.rotation;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import vesence.module.impl.combat.auraComponent.GCDUtil;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.FreeLookUtil;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.Rotation;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.URotations;

import java.util.concurrent.ThreadLocalRandom;

import static vesence.utils.other.IMinecraft.mc;

/**
 * Legit — профиль ротации «как рука»: движется по модели моторики, а не
 * ограничением шага. Заменил собой Sloth в режиме Legit и вобрал наработки
 * удалённого Shard.
 *
 * <p>Что видит античит и почему это похоже на человека:
 * <ul>
 *   <li>разгон: скорость растёт не быстрее {@code ACCEL} °/тик² — мгновенный
 *       доворот физически невозможен (ML-детекторы absence-of-acceleration);</li>
 *   <li>тормозной лимит по кинематике: {@code v ≤ √(2·a·Δ)} — профиль никогда
 *       не пролетает цель, колоколообразный профиль скорости как у руки;</li>
 *   <li>торможение у цели (закон Фиттса): чем ближе остаток, тем мельче шаг,
 *       финальный доворот &lt;1.5° защёлкивается точно в цель — удар всегда
 *       ложится в хитбокс;</li>
 *   <li>запрет разворота шага за один тик: рука проходит через нулевую
 *       скорость, реверс только через затухание;</li>
 *   <li>реакция на новую цель ~40мс и пауза/замедление ~90мс после удара —
 *       доворот сразу после пакета атаки отдельная проверка античитов;</li>
 *   <li>каждый шаг снапится на GCD-сетку мыши игрока;</li>
 *   <li>микротремор (гладкий шум ±0.06°) только вне окна атаки;</li>
 *   <li>корреляция осей: yaw и pitch — одно движение руки, скорости берутся
 *       из общего вектора, а не клампятся независимо.</li>
 * </ul>
 *
 * <p>Удар разрешён только после сведения: {@link #isSettled()} публикует
 * остаток, гейт ауры закрыт, пока остаток больше допуска.
 */
@Environment(EnvType.CLIENT)
public class ULegitRotation {

    /** Человеческие пределы скорости, °/тик. */
    private static final float MAX_YAW_SPEED = 48.0F;
    private static final float MAX_PITCH_SPEED = 34.0F;

    /** Предел разгона, °/тик². */
    private static final float MIN_ACCEL = 3.0F;
    private static final float MAX_ACCEL = 9.0F;

    /** Остаток, при котором удар уже разрешён. */
    private static final float SETTLE_THRESHOLD = 6.5F;

    /** Дальность финального защёлкивания в цель. */
    private static final float FINAL_SNAP = 1.5F;

    /** Реакция на новую цель, мс. */
    private static final long REACTION_MS = 40L;

    /** Замедление после удара, мс. */
    private static final long POST_HIT_MS = 90L;

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

    private ULegitRotation() {
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
            // Случайная точка на хитбоксе: человек не целится в геометрический центр.
            offsetSeedX = ThreadLocalRandom.current().nextDouble(-0.05, 0.05);
            offsetSeedY = ThreadLocalRandom.current().nextDouble(-0.07, 0.07);
            offsetSeedZ = ThreadLocalRandom.current().nextDouble(-0.05, 0.05);
        }

        Vec3 aim = point != null ? point : ownAimPoint(target, attackDist + rotateDist);
        Rotation goal = URotationMath.lookAt(aim);

        float curYaw = mc.player.getYRot();
        float curPitch = mc.player.getXRot();

        float deltaYaw = URotationMath.wrap(goal.yaw - curYaw);
        float deltaPitch = goal.pitch - curPitch;
        float totalDist = (float) Math.hypot(deltaYaw, deltaPitch);

        if (totalDist < 1.0E-4F) {
            residual = 0.0F;
            holdEngine();
            return;
        }

        // Реакция: на новую цель рука ещё не двинулась.
        if (now - targetStartTime < REACTION_MS) {
            residual = totalDist;
            holdEngine();
            return;
        }

        // Финальное защёлкивание: остаток меньше кванта доводки — точно в цель.
        if (totalDist <= FINAL_SNAP) {
            submitGoal(goal);
            residual = 0.0F;
            lastYawDelta = deltaYaw;
            lastPitchDelta = deltaPitch;
            if (attack) {
                registerHit(now);
            }
            return;
        }

        boolean postHit = now - lastHitTime < POST_HIT_MS;
        float speedScale = postHit ? 0.55F : 1.0F;

        float targetYawVel = computeVelocity(deltaYaw, totalDist, MAX_YAW_SPEED * speedScale, lastYawDelta);
        float targetPitchVel = computeVelocity(deltaPitch, totalDist, MAX_PITCH_SPEED * speedScale, lastPitchDelta);

        float accelLimit = ThreadLocalRandom.current().nextFloat() * (MAX_ACCEL - MIN_ACCEL) + MIN_ACCEL;
        currentYawVelocity = approachVelocity(currentYawVelocity, targetYawVel, accelLimit);
        currentPitchVelocity = approachVelocity(currentPitchVelocity, targetPitchVel, accelLimit * 0.85F);

        // Тормозной лимит по кинематике: скорость не выше той, на которой можно
        // остановиться ровно на цели при худшем замедлении (v² = 2·a·d). Без него
        // модель разгоняется до MAX и пролетает цель на десятки градусов —
        // прицел уходит в бесконечную осцилляцию вокруг врага.
        float yawBrakeCap = (float) Math.sqrt(2.0 * MIN_ACCEL * Math.abs(deltaYaw));
        if (Math.abs(currentYawVelocity) > yawBrakeCap) {
            currentYawVelocity = Math.signum(currentYawVelocity) * yawBrakeCap;
        }
        float pitchBrakeCap = (float) Math.sqrt(2.0 * MIN_ACCEL * 0.85F * Math.abs(deltaPitch));
        if (Math.abs(currentPitchVelocity) > pitchBrakeCap) {
            currentPitchVelocity = Math.signum(currentPitchVelocity) * pitchBrakeCap;
        }

        float stepYaw = currentYawVelocity;
        float stepPitch = currentPitchVelocity;

        // Микротремор руки — только вне окна атаки и вне паузы после удара.
        if (!attack && !postHit) {
            double time = (double) now * 0.005;
            stepYaw += (float) (Math.sin(time * 3.7) * 0.06 + Math.cos(time * 1.3) * 0.04);
            stepPitch += (float) (Math.cos(time * 2.9) * 0.05 + Math.sin(time * 0.9) * 0.03);
        }

        float desiredYaw = curYaw + stepYaw;
        float desiredPitch = Mth.clamp(curPitch + stepPitch, -90.0F, 90.0F);

        float snappedYaw = GCDUtil.snapToCameraGCD(curYaw, desiredYaw);
        float snappedPitch = GCDUtil.snapPitchToCameraGCD(curPitch, desiredPitch);

        URotations.update(new Rotation(snappedYaw, snappedPitch), 360.0F, 360.0F, 24.0F, 20.0F, 2, 15, false);

        lastYawDelta = deltaYaw;
        lastPitchDelta = deltaPitch;

        residual = (float) Math.hypot(
                URotationMath.wrap(goal.yaw - snappedYaw),
                goal.pitch - snappedPitch
        );

        if (attack) {
            registerHit(now);
        }
    }

    /** Перегрузка для вызовов без точки прицела. */
    public static void rotation(LivingEntity target, boolean attack, float attackDist, float rotateDist) {
        rotation(target, attack, attackDist, rotateDist, null);
    }

    private static void submitGoal(Rotation goal) {
        float snappedYaw = GCDUtil.snapToCameraGCD(mc.player.getYRot(), goal.yaw);
        float snappedPitch = GCDUtil.snapPitchToCameraGCD(mc.player.getXRot(), goal.pitch);
        URotations.update(new Rotation(snappedYaw, snappedPitch), 360.0F, 360.0F, 24.0F, 20.0F, 2, 15, false);
    }

    /**
     * Удержать движок ротаций на текущих углах. Без этого любой тик без
     * {@code URotations.update} (грейс реакции, сходимость) роняет задачу в
     * RESET: движок тянет углы обратно к камере, а при сходстве RESET вызывает
     * {@code stopRotation()} → {@code FreeLookUtil.active = false} → видимая
     * камера сцепляется с углами игрока и её утаскивает на врага.
     */
    private static void holdEngine() {
        URotations.update(new Rotation(mc.player.getYRot(), mc.player.getXRot()),
                360.0F, 360.0F, 24.0F, 20.0F, 2, 15, false);
    }

    private static void registerHit(long now) {
        lastHitTime = now;
        // После удара точка прицела слегка «дышит» — человек не держит пиксель.
        offsetSeedX += ThreadLocalRandom.current().nextDouble(-0.03, 0.03);
        offsetSeedY += ThreadLocalRandom.current().nextDouble(-0.02, 0.02);
        offsetSeedZ += ThreadLocalRandom.current().nextDouble(-0.03, 0.03);
    }

    /**
     * Желаемая скорость по оси: полная на дальних углах, smoothstep-торможение
     * в середине, мелкий точный шаг у цели (закон Фиттса).
     */
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
            desiredSpeed = Math.max(0.6F, dynamicMax * (0.25F + 0.45F * t));
        }

        // Реверс направления — через замедление, не мгновенно.
        if (lastDelta != 0.0F && Math.signum(delta) != Math.signum(lastDelta) && absDelta < 10.0F) {
            desiredSpeed *= 0.75F;
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

    /** Своя точка прицела, если аура свою не передала: видимая точка + гистерезис. */
    private static Vec3 ownAimPoint(LivingEntity target, double range) {
        Vec3 aim = URotationMath.visibleAimPoint(target, range, false);
        if (aim == null) {
            aim = target.getEyePosition();
        }
        Vec3 withOffset = aim.add(offsetSeedX, offsetSeedY, offsetSeedZ);
        return URotationMath.stabilizeAim(target.getId(), withOffset, range, false);
    }

    public static boolean smoothBack() {
        if (mc.player == null) {
            return true;
        }
        float toYaw = FreeLookUtil.freeYaw;
        float toPitch = FreeLookUtil.freePitch;

        float deltaYaw = URotationMath.wrap(toYaw - mc.player.getYRot());
        float deltaPitch = toPitch - mc.player.getXRot();

        if (Math.abs(deltaYaw) < 1.0F && Math.abs(deltaPitch) < 1.0F) {
            return true;
        }

        // Возврат тоже с разгоном: шаг пропорционален остатку, но не мгновенный.
        float stepY = Math.signum(deltaYaw) * Math.min(Math.abs(deltaYaw) * 0.3F, 16.0F);
        float stepP = Math.signum(deltaPitch) * Math.min(Math.abs(deltaPitch) * 0.3F, 12.0F);

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
    }
}
