package vesence.module.impl.combat.rotation;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import vesence.module.impl.combat.auraComponent.GCDUtil;
import vesence.module.impl.combat.auraComponent.RayTraceUtil;
import vesence.module.impl.combat.auraComponent.UBoxPoints;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.Rotation;

import java.util.concurrent.ThreadLocalRandom;

import static vesence.utils.other.IMinecraft.mc;

/**
 * Общий слой ротационных профилей (UFunTimeRotation / UReallyWorldRotation / UCakeWorldRotation /
 * UHolyWorldNewRotation / UMatrixRotation / UHvHRotation / USlothRotation).
 *
 * <p>Правила, одинаковые для ВСЕХ профилей:
 * <ul>
 *   <li>{@code delta = wrapDegrees(target - current)} по каждой оси,
 *       {@code total = hypot(yawΔ, pitchΔ)};</li>
 *   <li>per-axis straight-line cap: {@code cap = |axisΔ/total| * MAX} — AC палят
 *       кривые дуги, траектория должна идти по прямой к цели;</li>
 *   <li>финал всегда GCD-снапить, и снап делается ОДИН раз на applied-delta;</li>
 *   <li>джиттер (sin/cos) добавлять только когда {@code !attack} — в окне атаки
 *       ротация чистая;</li>
 *   <li>аим-поинт — ближайшая ВИДИМАЯ точка хитбокса (raycast), не центр.</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public final class URotationMath {

    private URotationMath() {
    }

    // ── Окно атаки ───────────────────────────────────────────────────────────

    /**
     * Тик, в котором аура реально бьёт. Ставится ОДИН раз за тик в
     * {@code AttackAura.onEvent} из {@code resolveAttackGate()} и читается всеми
     * профилями через {@link #inAttackWindow()}.
     *
     * <p>Профиль не имеет права угадывать окно атаки сам ({@code canSee + distance
     * + cooldown}): гейт удара живёт в ауре, и любой рассинхрон кладёт джиттер
     * ровно на тик удара — это ровно та сигнатура, по которой AC отсекает чит.
     */
    private static boolean attackWindow;

    public static void setAttackWindow(boolean attack) {
        attackWindow = attack;
    }

    /** Идёт ли окно атаки на этом тике. */
    public static boolean inAttackWindow() {
        return attackWindow;
    }

    // ── Базовые углы ─────────────────────────────────────────────────────────

    public static float wrap(float delta) {
        return Mth.wrapDegrees(delta);
    }

    /** total = hypot(yawΔ, pitchΔ), никогда не 0. */
    public static float total(float yawDelta, float pitchDelta) {
        return (float) Math.max(Math.hypot(Math.abs(yawDelta), Math.abs(pitchDelta)), 1.0E-4F);
    }

    /** per-axis straight-line cap: {@code |axisΔ/total| * maxDeg}. */
    public static float axisCap(float axisDelta, float total, float maxDeg) {
        return Math.abs(axisDelta / total) * maxDeg;
    }

    public static float clampDelta(float axisDelta, float cap) {
        return Mth.clamp(axisDelta, -cap, cap);
    }

    /** lerp(t, a, b) = a + t*(b-a). */
    public static float lerp(float t, float a, float b) {
        return a + t * (b - a);
    }

    /**
     * Lerp, который не проезжает цель.
     *
     * <p>В ТЗ-константах есть {@code lerpK > 1} (UReallyWorldRotation 1.0–1.2, UHolyWorldNewRotation до 1.16).
     * С амплитудным клампом {@code step = полная дельта} такой коэффициент
     * выносит угол ЗА цель, и следующий тик тянет его обратно — это и есть
     * видимое дёрганье: камера пилит туда-сюда вокруг цели.
     */
    public static float lerpTowards(float current, float target, float t) {
        float k = Mth.clamp(t, 0.0F, 1.0F);
        return current + (target - current) * k;
    }

    /** ease(t) = t * (0.5 + 0.5t) — ТЗ SpookyTime. */
    public static float ease(float t) {
        return t * (0.5F + 0.5F * t);
    }

    public static float rand(float min, float max) {
        return min + ThreadLocalRandom.current().nextFloat() * (max - min);
    }

    /**
     * Случайное значение, которое живёт во времени, а не пересчитывается каждый
     * тик. Ключ обязателен: у каждого профиля своя амплитуда и свой момент
     * обновления.
     *
     * <p>Без этого {@code rand(20..26) * sin(ms/25)} даёт НОВУЮ амплитуду на
     * каждом тике — синус превращается в белый шум размахом в разы больше самого
     * джиттера, и камера начинает мелко дрожать вместо плавного покачивания.
     */
    public static float stableRand(String key, float min, float max, long refreshMs) {
        long now = System.currentTimeMillis();
        StableValue slot = STABLE_VALUES.get(key);
        if (slot == null || now >= slot.nextRefresh) {
            slot = new StableValue(rand(min, max), now + refreshMs);
            STABLE_VALUES.put(key, slot);
        }
        return slot.value;
    }

    /** Целочисленный вариант {@link #stableRand} — для делителей частоты. */
    public static float stableRandInt(String key, int min, int max, long refreshMs) {
        long now = System.currentTimeMillis();
        StableValue slot = STABLE_VALUES.get(key);
        if (slot == null || now >= slot.nextRefresh) {
            slot = new StableValue(ThreadLocalRandom.current().nextInt(min, max + 1), now + refreshMs);
            STABLE_VALUES.put(key, slot);
        }
        return slot.value;
    }

    private static final java.util.Map<String, StableValue> STABLE_VALUES = new java.util.concurrent.ConcurrentHashMap<>();

    private static final class StableValue {
        private final float value;
        private final long nextRefresh;

        private StableValue(float value, long nextRefresh) {
            this.value = value;
            this.nextRefresh = nextRefresh;
        }
    }

    public static float gaussian() {
        return (float) ThreadLocalRandom.current().nextGaussian() * 0.5F;
    }

    /** sin/cos джиттер от wall-clock, как во всех профилях ТЗ. */
    public static float sinJitter(float amp, double divisor) {
        return (float) (amp * Math.sin(System.currentTimeMillis() / divisor));
    }

    public static float cosJitter(float amp, double divisor) {
        return (float) (amp * Math.cos(System.currentTimeMillis() / divisor));
    }

    // ── GCD-снап ─────────────────────────────────────────────────────────────

    /**
     * Финал профиля: снап applied-delta на GCD-сетку игрока ОДИН раз.
     * Возвращает {yaw, pitch}.
     */
    public static float[] apply(float currentYaw, float currentPitch, float newYaw, float newPitch) {
        float gcdDeltaYaw = newYaw - currentYaw;
        float gcdDeltaPitch = newPitch - currentPitch;
        float snappedYaw = currentYaw + GCDUtil.getSensitivity(gcdDeltaYaw);
        float snappedPitch = currentPitch + GCDUtil.getSensitivity(gcdDeltaPitch);
        return new float[]{
                currentYaw + enforceMinStep(snappedYaw - currentYaw, gcdDeltaYaw),
                Mth.clamp(currentPitch + enforceMinStep(snappedPitch - currentPitch, gcdDeltaPitch), -90.0F, 90.0F)
        };
    }

    /** Не даём GCD-снапу обнулить ненулевой шаг. */
    private static float enforceMinStep(float snapped, float requested) {
        if (snapped != 0.0F || requested == 0.0F) {
            return snapped;
        }
        float gcd = GCDUtil.getGCDValue();
        return requested > 0.0F ? gcd : -gcd;
    }

    /** Отдать посчитанную ротацию в общий sink, уже снапнутую. */
    public static void submit(float yawSpeed, float pitchSpeed, float returnYawSpeed, float returnPitchSpeed,
                              int timeout, int priority, float newYaw, float newPitch) {
        if (mc.player == null) {
            return;
        }
        float[] snapped = apply(mc.player.getYRot(), mc.player.getXRot(), newYaw, newPitch);
        vesence.module.impl.combat.auraComponent.rotationComponent.impl.URotations.update(
                new Rotation(snapped[0], snapped[1]),
                yawSpeed, pitchSpeed, returnYawSpeed, returnPitchSpeed, timeout, priority, false);
    }

    // ── Аим-поинт ────────────────────────────────────────────────────────────

    /** Сетка точек хитбокса для выбора видимой (только верх/центр — ноги не нужны). */
    private static final double[][] HITBOX_GRID = {
            {0.0, 0.94, 0.0}, {0.0, 0.86, 0.0}, {0.0, 0.78, 0.0}, {0.0, 0.70, 0.0},
            {0.0, 0.62, 0.0}, {0.0, 0.54, 0.0}, {0.0, 0.46, 0.0},
            {0.24, 0.86, 0.0}, {-0.24, 0.86, 0.0}, {0.0, 0.86, 0.24}, {0.0, 0.86, -0.24},
            {0.24, 0.70, 0.24}, {-0.24, 0.70, -0.24}, {0.36, 0.62, 0.0}, {-0.36, 0.62, 0.0}
    };

    /**
     * Ближайшая ВИДИМАЯ точка хитбокса (raycast от глаза). Не центр — иначе
     * reach/aim flag. Если ни одна точка не видна — падаем в
     * {@link UBoxPoints#getBestVector3dOnEntityBox(Entity)}.
     *
     * <p>Результат стабилизируется: без этого выбор скачет между головой и
     * грудью каждый тик, и на `cap = 360` профилях камера мотается на 10–20°
     * туда-сюда — это выглядит как дёрганье, хотя интерполяция работает.
     */
    public static Vec3 visibleAimPoint(LivingEntity target, double range, boolean ignoreWalls) {
        if (target == null || mc.player == null) {
            return null;
        }
        return stabilize(target, computeAimPoint(target, range, ignoreWalls), range, ignoreWalls);
    }

    private static Vec3 computeAimPoint(LivingEntity target, double range, boolean ignoreWalls) {
        if (ignoreWalls) {
            return UBoxPoints.getBestVector3dOnEntityBox(target);
        }

        AABB box = target.getBoundingBox();
        Vec3 eye = mc.player.getEyePosition();
        Vec3 center = box.getCenter();
        double halfX = box.getXsize() * 0.5;
        double halfZ = box.getZsize() * 0.5;
        double height = box.getYsize();

        Vec3 best = null;
        double bestDist = Double.MAX_VALUE;

        for (double[] p : HITBOX_GRID) {
            Vec3 point = new Vec3(
                    center.x + halfX * p[0],
                    box.minY + height * p[1],
                    center.z + halfZ * p[2]);
            double dist = eye.distanceTo(point);
            if (dist > range || dist >= bestDist) {
                continue;
            }
            if (isVisible(point, dist)) {
                best = point;
                bestDist = dist;
            }
        }

        if (best != null) {
            return best;
        }
        Vec3 fallback = UBoxPoints.getBestVector3dOnEntityBox(target);
        return fallback != null && isVisible(fallback, eye.distanceTo(fallback)) ? fallback : null;
    }

    // ── Стабилизация аим-поинта ──────────────────────────────────────────────

    /** Сколько тиков держим выбранную точку, даже если нашлась чуть ближе. */
    private static final int AIM_HOLD_TICKS = 12;
    /** Насколько новая точка должна быть ближе, чтобы оправдать переключение (блоки). */
    private static final double AIM_SWITCH_MARGIN = 0.35D;

    private static int aimTargetId = Integer.MIN_VALUE;
    private static Vec3 aimPoint;
    private static int aimPointAge = Integer.MIN_VALUE;

    /**
     * Гистерезис выбора точки: пока старая точка видна и не сильно хуже новой —
     * держим её. Иначе каждый тик цель прыгает по сетке хитбокса и ротация
     * мотается между двумя углами.
     */
    private static Vec3 stabilize(LivingEntity target, Vec3 computed, double range, boolean ignoreWalls) {
        return stabilizeAim(target.getId(), computed, range, ignoreWalls);
    }

    /**
     * Публичная версия гистерезиса — тем же правилом пользуется аура, когда
     * сама выбирает точку атаки по своему пресету.
     */
    public static Vec3 stabilizeAim(int targetId, Vec3 computed, double range, boolean ignoreWalls) {
        int age = mc.player != null ? mc.player.tickCount : 0;
        boolean sameTarget = aimTargetId == targetId;

        if (sameTarget && aimPoint != null && aimPointAge != Integer.MIN_VALUE
                && age - aimPointAge < AIM_HOLD_TICKS) {
            Vec3 eye = mc.player.getEyePosition();
            double heldDist = eye.distanceTo(aimPoint);
            boolean stillValid = heldDist <= range
                    && (ignoreWalls || isVisible(aimPoint, heldDist));
            if (stillValid) {
                boolean clearlyBetter = computed != null
                        && eye.distanceTo(computed) + AIM_SWITCH_MARGIN < heldDist;
                if (!clearlyBetter) {
                    return aimPoint;
                }
            }
        }

        aimTargetId = targetId;
        aimPoint = computed;
        aimPointAge = age;
        return computed;
    }

    /** Есть ли линия от глаза к точке без блоков между ними. */
    private static boolean isVisible(Vec3 point, double dist) {
        if (dist <= 1.0E-4) {
            return true;
        }
        Vec3 eye = mc.player.getEyePosition();
        return RayTraceUtil.traceBlock(eye, point, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE)
                .getLocation().distanceToSqr(eye) >= dist * dist - 1.0E-4;
    }

    /** Ротация на точку из позиции глаз. */
    public static Rotation lookAt(Vec3 point) {
        Vec3 dir = point.subtract(mc.player.getEyePosition());
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        float pitch = (float) Mth.clamp(
                -Math.toDegrees(Math.atan2(dir.y, Math.hypot(dir.x, dir.z))), -90.0F, 90.0F);
        return new Rotation(yaw, pitch);
    }

    // ── Гейты для флик-«тэллов» ──────────────────────────────────────────────

    /**
     * Детект rising-edge {@code canAttack}: счётчик хитов должен расти только
     * на переходе false→true, иначе один и тот же тик удара посчитается
     * несколько раз за окно.
     */
    public static final class AttackEdge {
        private boolean last;

        public boolean update(boolean attack) {
            boolean rising = attack && !this.last;
            this.last = attack;
            return rising;
        }

        public void reset() {
            this.last = false;
        }
    }

    /**
     * Счётчик хитов в скользящем окне. ТЗ: «каждый 86-й хит в окне 250ms»
     * (FunTime) и «каждый 50-й хит в окне 200ms» (ReallyWorld).
     */
    public static final class HitWindow {
        private final long windowMs;
        private final int every;
        private long windowStart;
        private int hits;

        public HitWindow(long windowMs, int every) {
            this.windowMs = windowMs;
            this.every = every;
        }

        /** @return true, если этот хит — триггерный в текущем окне */
        public boolean onHit() {
            long now = System.currentTimeMillis();
            if (now - this.windowStart > this.windowMs) {
                this.windowStart = now;
                this.hits = 0;
            }
            this.hits++;
            return this.hits % this.every == 0;
        }

        public void reset() {
            this.windowStart = 0L;
            this.hits = 0;
        }
    }

    /**
     * ТЗ: лимит доворота {@code 0°} пока не прошло N мс после хита, потом M°.
     * Возвращает актуальный лимит доворота по yaw.
     */
    public static final class PostHitLimit {
        private final long holdMs;
        private final float afterDeg;
        private long lastHit;
        private boolean hasHit;

        public PostHitLimit(long holdMs, float afterDeg) {
            this.holdMs = holdMs;
            this.afterDeg = afterDeg;
        }

        public void onHit() {
            this.lastHit = System.currentTimeMillis();
            this.hasHit = true;
        }

        public float limit() {
            if (!this.hasHit) {
                return 0.0F;
            }
            return System.currentTimeMillis() - this.lastHit >= this.holdMs ? this.afterDeg : 0.0F;
        }

        public void reset() {
            this.lastHit = 0L;
            this.hasHit = false;
        }
    }
}