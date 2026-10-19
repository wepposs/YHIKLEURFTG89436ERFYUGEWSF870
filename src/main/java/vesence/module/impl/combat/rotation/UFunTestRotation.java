package vesence.module.impl.combat.rotation;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import vesence.module.impl.combat.auraComponent.RayTraceUtil;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.FreeLookUtil;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.Rotation;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.URotations;
import vesence.utils.other.Mathf;

import java.util.concurrent.ThreadLocalRandom;

import static vesence.utils.other.IMinecraft.mc;

/**
 * FunTest — независимая копия ротации Spooky Duel (порт {@link USpookyTimeRotation})
 * с двумя добавками:
 * <ul>
 *   <li><b>Дрожание головы во время ударов</b> — постоянный лёгкий тремор + всплеск
 *       сразу после удара (шумит серверную ротацию, ломает aim-consistency);</li>
 *   <li><b>Пост-хит отворот</b> — рандомно после удара голова уводится на ~20° yaw
 *       на несколько тиков, чтобы сбросить статистику «бьёт ровно в цель».</li>
 * </ul>
 *
 * <p>Как и Spooky Duel, это «тихая» аура через FreeLook (clientRotation=false):
 * FOV-гейта нет, довод камеры гуманизирован (GCD, шум скорости, easing, smoothBack).
 * Состояние трекинга сбрасывается при заходе/уходе с цели (а не каждый тик), поэтому
 * гуманизация и оба новых эффекта работают корректно.</p>
 */
@Environment(EnvType.CLIENT)
public class UFunTestRotation {
    private static final long SMOOTHBACK_DURATION_MS = 100L;
    private static long smoothbackShakeStartMs = -1L;
    private static float releaseFromYaw = 0f;
    private static float releaseFromPitch = 0f;
    private static boolean hasReleaseFrom = false;
    private static float releaseToYaw = 0f;
    private static float releaseToPitch = 0f;
    private static boolean hasReleaseTo = false;
    private static LivingEntity trackedTarget = null;
    private static float startYaw = 0f;
    private static float startPitch = 0f;
    private static float targetStartYaw = 0f;
    private static float targetStartPitch = 0f;
    private static float initialRotationDistance = 0f;
    private static boolean hasRotationStart = false;
    private static float basePitch = 0.0f;

    // === Humanization fields ===
    private static Vec3d aimOffset = Vec3d.ZERO;
    private static float speedNoiseYaw = 1.0f;
    private static float speedNoisePitch = 1.0f;
    private static int noiseTick = 0;

    private static final float AIM_OFFSET_RANGE = 0.08f;
    private static final float SPEED_NOISE_RANGE = 0.10f;
    private static final int NOISE_UPDATE_INTERVAL = 3;

    // === FunTest: дрожание головы во время ударов ===
    private static float tremorPhase = 0f;
    private static float tremorIntensity = 0f;             // 0..1, всплеск на удар, затем затухает
    private static final float TREMOR_BASE_AMP = 0.6f;      // градусы — постоянное лёгкое дрожание при наведении
    private static final float TREMOR_HIT_AMP = 2.5f;       // доп. амплитуда сразу после удара
    private static final float TREMOR_PHASE_STEP = 1.9f;    // скорость дрожания (рад/тик)
    private static final float TREMOR_DECAY = 0.06f;        // затухание интенсивности за тик

    // === FunTest: пост-хит отворот головы (сброс статистики ударов) ===
    private static int flickTicks = 0;
    private static float flickYawOffset = 0f;
    private static final float POST_HIT_FLICK_DEG = 20f;     // ~20° yaw
    private static final float POST_HIT_FLICK_CHANCE = 0.6f; // рандомно ~60% ударов
    private static final int POST_HIT_FLICK_TICKS = 3;       // держим отворот столько тиков

    public static void rotation(LivingEntity target, float attackDist, float rotateDist) {
        rotation(target, attackDist, rotateDist, target != null ? target.getEyePos() : null);
    }

    public static void rotation(LivingEntity target, float attackDist, float rotateDist, Vec3d point) {
        if (mc.player == null || target == null) return;

        // ТЗ: аим-поинт — ближайшая ВИДИМАЯ точка хитбокса, не центр коробки.
        Vec3d resolvedPoint = URotationMath.visibleAimPoint(target, rotateDist, false);
        if (resolvedPoint == null) {
            resolvedPoint = point != null ? point : target.getBoundingBox().getCenter();
        }
        point = resolvedPoint;

        smoothbackShakeStartMs = -1L;
        hasReleaseFrom = false;
        hasReleaseTo = false;

        noiseTick++;
        if (noiseTick % NOISE_UPDATE_INTERVAL == 0) {
            speedNoiseYaw = 1.0f + Mathf.randomValue(-SPEED_NOISE_RANGE, SPEED_NOISE_RANGE);
            speedNoisePitch = 1.0f + Mathf.randomValue(-SPEED_NOISE_RANGE, SPEED_NOISE_RANGE);
        }

        Vec3d directionVec = point.subtract(mc.player.getEyePos());
        float targetYaw = (float) Math.toDegrees(Math.atan2(-directionVec.x, directionVec.z));
        float targetPitch = (float) MathHelper.clamp(
                -Math.toDegrees(Math.atan2(directionVec.y, Math.hypot(directionVec.x, directionVec.z))),
                -90.0, 90.0
        );

        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();

        boolean isNewRotation = false;
        if (trackedTarget != target) {
            isNewRotation = true;
        } else if (!hasRotationStart) {
            isNewRotation = true;
        } else {
            float yawShift = Math.abs(MathHelper.wrapDegrees(targetYaw - targetStartYaw));
            float pitchShift = Math.abs(targetPitch - targetStartPitch);
            if (yawShift > 8f || pitchShift > 5f) {
                isNewRotation = true;
            }
        }

        if (isNewRotation) {
            trackedTarget = target;
            startYaw = currentYaw;
            startPitch = currentPitch;
            targetStartYaw = targetYaw;
            targetStartPitch = targetPitch;
            hasRotationStart = true;

            float startDeltaYaw = MathHelper.wrapDegrees(targetYaw - startYaw);
            float startDeltaPitch = targetPitch - startPitch;
            initialRotationDistance = (float) Math.max(
                    Math.hypot(Math.abs(startDeltaYaw), Math.abs(startDeltaPitch)), 1e-4
            );

            aimOffset = new Vec3d(
                    Mathf.randomValue(-AIM_OFFSET_RANGE, AIM_OFFSET_RANGE),
                    Mathf.randomValue(-AIM_OFFSET_RANGE * 0.5f, AIM_OFFSET_RANGE * 0.5f),
                    Mathf.randomValue(-AIM_OFFSET_RANGE, AIM_OFFSET_RANGE)
            );
        }

        Vec3d adjustedPoint = point.add(aimOffset);

        Vec3d adjDirectionVec = adjustedPoint.subtract(mc.player.getEyePos());
        float adjTargetYaw = (float) Math.toDegrees(Math.atan2(-adjDirectionVec.x, adjDirectionVec.z));
        float adjTargetPitch = (float) MathHelper.clamp(
                -Math.toDegrees(Math.atan2(adjDirectionVec.y, Math.hypot(adjDirectionVec.x, adjDirectionVec.z))),
                -90.0, 90.0
        );

        // FunTest: пост-хит отворот — целимся мимо цели на ~20° по yaw несколько тиков,
        // чтобы сбить корреляцию «бьёт ровно в цель» (статистика ударов анти-чита).
        if (flickTicks > 0) {
            adjTargetYaw = MathHelper.wrapDegrees(adjTargetYaw + flickYawOffset);
            flickTicks--;
        }

        float currentDeltaYaw = MathHelper.wrapDegrees(adjTargetYaw - currentYaw);
        float currentDeltaPitch = adjTargetPitch - currentPitch;
        float currentDistance = (float) Math.max(
                Math.hypot(Math.abs(currentDeltaYaw), Math.abs(currentDeltaPitch)), 1e-4
        );

        float rotationProgress = (initialRotationDistance > 1e-4)
                ? MathHelper.clamp(1f - (currentDistance / initialRotationDistance), 0f, 1f)
                : 1f;

        float distanceToTarget = (float) mc.player.getEyePos().distanceTo(point);
        float maxDist = attackDist + rotateDist + 2f;
        float distanceFactor = 1f - MathHelper.clamp(distanceToTarget / maxDist, 0f, 1f);

        float combinedProgress = rotationProgress * 0.65f + distanceFactor * 0.35f;
        combinedProgress = MathHelper.clamp(combinedProgress, 0f, 1f);

        float easedProgress = combinedProgress * combinedProgress * (3.0f - 2.0f * combinedProgress);

        float deltaYaw = MathHelper.wrapDegrees(adjTargetYaw - currentYaw);
        float deltaPitch = adjTargetPitch - currentPitch;

        float hypot = (float) Math.max(Math.hypot(Math.abs(deltaYaw), Math.abs(deltaPitch)), 1e-4);

        boolean onRay = RayTraceUtil.rayTraceSingleEntity(currentYaw, currentPitch, attackDist + rotateDist + 0.1f, target);
        boolean inAir = !mc.player.isOnGround();

        float baseYawSpeed;
        float basePitchSpeed;

        if (inAir) {
            baseYawSpeed   = Mathf.randomValue(18f, 40f);
            basePitchSpeed = Mathf.randomValue(18f, 30f);
        } else {
            baseYawSpeed   = Mathf.randomValue(20f, 38f);
            basePitchSpeed = Mathf.randomValue(18f, 26f);
        }

        float speedMultiplier = 1.0f + 1.2f * easedProgress;

        float yawMaxSpeed   = baseYawSpeed * speedMultiplier;
        float pitchMaxSpeed = basePitchSpeed * speedMultiplier;

        if (!onRay) {
            yawMaxSpeed   = Math.max(yawMaxSpeed, 35.0f);
            pitchMaxSpeed = Math.max(pitchMaxSpeed, 30.0f);
        }

        yawMaxSpeed *= speedNoiseYaw;
        pitchMaxSpeed *= speedNoisePitch;

        if (rotationProgress > 0.88f) {
            float precisionFactor = 1.0f - (rotationProgress - 0.88f) / 0.12f * 0.35f;
            yawMaxSpeed *= Math.max(precisionFactor, 0.65f);
            pitchMaxSpeed *= Math.max(precisionFactor, 0.65f);
        }

        float maxYaw   = Math.abs(deltaYaw   / hypot) * yawMaxSpeed;
        float maxPitch = Math.abs(deltaPitch / hypot) * pitchMaxSpeed;

        // FunTest: дрожание головы. Постоянный лёгкий тремор + всплеск после удара
        // (tremorIntensity спайкается в onAttack и затухает). Добавляется к финальным
        // углам, GCD-квантование в URotations сгладит до шага мыши.
        tremorPhase += TREMOR_PHASE_STEP;
        if (tremorIntensity > 0f) {
            tremorIntensity = Math.max(0f, tremorIntensity - TREMOR_DECAY);
        }
        float tremAmpYaw = TREMOR_BASE_AMP + TREMOR_HIT_AMP * tremorIntensity;
        float tremAmpPitch = TREMOR_BASE_AMP * 0.5f + TREMOR_HIT_AMP * 0.45f * tremorIntensity;
        // ТЗ: в окне атаки ротация чистая — тремор не накладываем.
        if (URotationMath.inAttackWindow()) {
            tremAmpYaw = 0.0f;
            tremAmpPitch = 0.0f;
        }
        float tremorYaw = (float) Math.sin(tremorPhase) * tremAmpYaw;
        float tremorPitch = (float) Math.cos(tremorPhase * 1.37f) * tremAmpPitch;

        float newYaw = currentYaw + MathHelper.clamp(deltaYaw, -maxYaw, maxYaw) + tremorYaw;
        basePitch = MathHelper.clamp(
                currentPitch + MathHelper.clamp(deltaPitch, -maxPitch, maxPitch) + tremorPitch,
                -90f, 90f
        );

        Rotation rot = new Rotation(newYaw, basePitch);
        URotations.update(rot, yawMaxSpeed, pitchMaxSpeed, 20f, 18f, 0, 15, false);
    }

    /**
     * Вызывать после успешного удара (из AttackAura.attackEntity). Даёт всплеск
     * дрожания и рандомно ставит пост-хит отворот головы на ~20° yaw.
     */
    public static void onAttack() {
        tremorIntensity = 1.0f;
        if (ThreadLocalRandom.current().nextFloat() < POST_HIT_FLICK_CHANCE) {
            float sign = ThreadLocalRandom.current().nextBoolean() ? 1f : -1f;
            flickYawOffset = sign * (POST_HIT_FLICK_DEG + Mathf.randomValue(-3f, 3f));
            flickTicks = POST_HIT_FLICK_TICKS;
        }
    }

    public static boolean smoothBack() {
        if (mc.player == null) return true;

        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();

        if (smoothbackShakeStartMs < 0L) {
            smoothbackShakeStartMs = System.currentTimeMillis();
            releaseFromYaw   = currentYaw;
            releaseFromPitch = currentPitch;
            hasReleaseFrom   = true;
            releaseToYaw   = FreeLookUtil.freeYaw;
            releaseToPitch = FreeLookUtil.freePitch;
            hasReleaseTo   = true;
        } else if (hasReleaseTo) {
            releaseToYaw   = FreeLookUtil.freeYaw;
            releaseToPitch = FreeLookUtil.freePitch;
        }

        float toYaw   = hasReleaseTo ? releaseToYaw   : FreeLookUtil.freeYaw;
        float toPitch = hasReleaseTo ? releaseToPitch : FreeLookUtil.freePitch;

        float t = smoothbackShakeStartMs >= 0L
                ? MathHelper.clamp((float)(System.currentTimeMillis() - smoothbackShakeStartMs) / SMOOTHBACK_DURATION_MS, 0f, 1f)
                : 1.0f;

        float tEased = 1.0f - (float) Math.pow(1.0f - t, 3.0);

        float deltaYaw   = MathHelper.wrapDegrees(toYaw   - currentYaw);
        float deltaPitch = toPitch - currentPitch;
        float hypot = (float) Math.max(Math.hypot(Math.abs(deltaYaw), Math.abs(deltaPitch)), 1e-4);

        float yawSpeed   = MathHelper.lerp(tEased, 18.0f, 35.0f);
        float pitchSpeed = MathHelper.lerp(tEased, 16.0f, 28.0f);

        float maxYaw   = Math.abs(deltaYaw   / hypot) * yawSpeed;
        float maxPitch = Math.abs(deltaPitch / hypot) * pitchSpeed;

        float lerpK = MathHelper.lerp(tEased, 0.28f, 0.35f);

        float newYaw = MathHelper.lerp(lerpK,
                currentYaw,
                currentYaw + MathHelper.clamp(deltaYaw, -maxYaw, maxYaw)
        );
        float newPitch = MathHelper.clamp(
                MathHelper.lerp(lerpK,
                        currentPitch,
                        currentPitch + MathHelper.clamp(deltaPitch, -maxPitch, maxPitch)
                ), -90f, 90f
        );

        if (smoothbackShakeStartMs >= 0L && hasReleaseFrom) {
            newYaw   = MathHelper.lerpAngleDegrees(tEased, releaseFromYaw,   newYaw);
            newPitch = MathHelper.lerp(tEased, releaseFromPitch, newPitch);

            if (t >= 1.0f) {
                hasReleaseFrom = false;
                hasReleaseTo   = false;
            }
        }

        boolean done = t >= 1.0f && Math.abs(deltaYaw) < 1.0f && Math.abs(deltaPitch) < 1.0f;

        if (!done) {
            Rotation rot = new Rotation(newYaw, MathHelper.clamp(newPitch, -90f, 90f));
            URotations.update(rot, maxYaw, maxPitch, 25, 28.0f, 0, 15, false);
        } else {
            // Уход с цели завершён — чистим трекинг и пост-хит эффекты, чтобы следующее
            // наведение стартовало «с нуля».
            hasRotationStart = false;
            trackedTarget = null;
            flickTicks = 0;
            tremorIntensity = 0f;
        }

        return done;
    }

    public static void state() {
        basePitch = mc.player.getPitch();
        smoothbackShakeStartMs = -1L;
        hasReleaseFrom = false;
        hasReleaseTo   = false;
        hasRotationStart = false;
        trackedTarget = null;
        aimOffset = Vec3d.ZERO;
        speedNoiseYaw = 1.0f;
        speedNoisePitch = 1.0f;
        noiseTick = 0;
        tremorPhase = 0f;
        tremorIntensity = 0f;
        flickTicks = 0;
        flickYawOffset = 0f;
    }
}
