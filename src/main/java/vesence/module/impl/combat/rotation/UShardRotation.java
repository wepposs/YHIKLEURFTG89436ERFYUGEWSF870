package vesence.module.impl.combat.rotation;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.FreeLookUtil;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.Rotation;

import static vesence.utils.other.IMinecraft.mc;

/**
 * UShardRotation — профиль под античит Shard.
 *
 * <p>Чем он отличается от остальных профилей. Большинство профилей в этом пакете
 * ограничивают только ВЕЛИЧИНУ шага за тик: {@code step = clamp(Δ, ±MAX)}. Для
 * Shard этого мало, потому что проверка смотрит не на размер шага, а на его
 * производную — на то, как шаг меняется от тика к тику. Доворот на 180° за один
 * тик и доворот на 180° за восемь тиков имеют одинаковый итог, но у руки есть
 * разгон, а у {@code clamp} его нет. Отсюда четыре правила профиля:
 *
 * <ol>
 *   <li><b>Ограничен разгон.</b> Шаг не может вырасти больше чем на
 *       {@link #ACCEL_YAW}° за тик. Мгновенный доворот физически невозможен:
 *       даже цель за спиной набирается несколько тиков. Торможение при этом не
 *       ограничено — рука останавливается резко, и это позволяет гасить шаг у
 *       цели без перелёта.</li>
 *   <li><b>Разворот через ноль.</b> Смена знака шага за один тик запрещена:
 *       рука проходит через нулевую скорость. Один тик простоя дешевле, чем
 *       скачок на двойную амплитуду.</li>
 *   <li><b>Пауза после удара.</b> В первые {@link #POST_HIT_HOLD_MS} мс после
 *       удара шаг ограничен {@link #POST_HIT_YAW_CAP}°. Доворот сразу после
 *       пакета атаки — отдельная проверка: у живого игрока рука после клика не
 *       уезжает.</li>
 *   <li><b>Сначала прицел, потом удар.</b> Профиль публикует остаток
 *       {@link #isSettled()}, и аура не бьёт, пока прицел не сошёлся. Иначе
 *       пакет уходит с углом «мимо цели», и это ловится проверкой направления
 *       удара — самая дешёвая для античита и самая частая причина бана.</li>
 * </ol>
 *
 * <p>Общие для всех профилей правила тоже соблюдены: per-axis straight-line cap
 * (иначе траектория идёт дугой), GCD-снап ровно один раз на applied-delta (сетка
 * берётся из реальной сенсы игрока) и джиттер только вне окна атаки.
 *
 * <p>Константы — не измеренные пороги Shard, а разумная отправная точка:
 * скорость человеческого флика и предел разгона. Их имеет смысл подкручивать под
 * конкретный сервер, начиная с {@link #ACCEL_YAW}: это самый чувствительный
 * параметр — он один определяет, выглядит ли доворот как рывок.
 */
@Environment(EnvType.CLIENT)
public class UShardRotation {

    // ── Скорость ──────────────────────────────────────────────────────────────

    /**
     * Предел шага по рысканью за тик в обычном режиме.
     *
     * <p>Восемнадцать градусов за тик — это 360°/с. Быстрее человек доводит
     * прицел только флик-движением, а флик — это отдельная сигнатура, которую
     * античиты ищут по разгону. Здесь разгон и так ограничен, поэтому сама
     * величина шага может быть умеренно высокой.
     */
    private static final float YAW_STEP_MAX = 18.0F;

    /** Предел шага по тангажу: шея медленнее кисти. */
    private static final float PITCH_STEP_MAX = 11.0F;

    /** Предел шага в тик удара — последние градусы перед пакетом. */
    private static final float ATTACK_YAW_STEP_MAX = 26.0F;
    private static final float ATTACK_PITCH_STEP_MAX = 15.0F;

    /**
     * Предел разгона: насколько шаг может вырасти за один тик.
     *
     * <p>Девять градусов — примерно половина обычного шага, то есть полная
     * скорость набирается за два тика. Меньше делать нет смысла: цель, ушедшая
     * вбок, перестанет догоняться. Больше — и разгон перестаёт отличаться от
     * мгновенного доворота, ради чего профиль и написан.
     */
    private static final float ACCEL_YAW = 9.0F;
    private static final float ACCEL_PITCH = 5.5F;

    // ── Доводка у цели ────────────────────────────────────────────────────────

    /** С какого остатка начинается торможение, в градусах. */
    private static final float SLOW_RADIUS_YAW = 7.0F;
    private static final float SLOW_RADIUS_PITCH = 5.0F;

    /**
     * Минимальный шаг доводки.
     *
     * <p>Без него квадратичный спад у самой цели даёт шаг, который GCD-снап
     * округляет в ноль, и прицел замирает в полутора градусах от цели. Ровно
     * столько, чтобы удар уходил мимо хитбокса при внешне «наведённой» камере.
     */
    private static final float MIN_STEP = 0.25F;

    // ── Пауза после удара ─────────────────────────────────────────────────────

    /** Сколько держим прицел после пакета атаки. */
    private static final long POST_HIT_HOLD_MS = 90L;

    /** Предел шага во время этой паузы. */
    private static final float POST_HIT_YAW_CAP = 3.0F;
    private static final float POST_HIT_PITCH_CAP = 2.0F;

    // ── Джиттер ───────────────────────────────────────────────────────────────

    /** Амплитуда покачивания вне боя. */
    private static final float JITTER_MIN = 0.10F;
    private static final float JITTER_MAX = 0.35F;

    // ── Готовность к удару ────────────────────────────────────────────────────

    /**
     * Остаток, при котором прицел считается наведённым.
     *
     * <p>Три градуса на дистанции удара — это доли блока, то есть заведомо
     * внутри хитбокса. Строже делать нельзя: цель движется, и профиль честно
     * догонял бы её бесконечно, не нанося ударов.
     */
    private static final float SETTLE_DEG = 3.0F;

    /** Пределы, отдаваемые в общий сток ротации. Заведомо больше шага профиля. */
    private static final float SUBMIT_YAW_CAP = 40.0F;
    private static final float SUBMIT_PITCH_CAP = 30.0F;

    // ── Состояние ─────────────────────────────────────────────────────────────

    /** Шаг прошлого тика — база для предела разгона. */
    private static float lastYawStep;
    private static float lastPitchStep;

    private static long lastHitMs;
    private static boolean hasHit;

    /** Остаток до цели после последнего шага; читается аурой через {@link #isSettled()}. */
    private static float residual = Float.MAX_VALUE;

    private UShardRotation() {
    }

    /**
     * Наведён ли прицел на цель.
     *
     * <p>Аура обязана спрашивать это перед ударом: пакет атаки должен уходить с
     * углом, который уже смотрит на цель. Проверять «попал ли луч» бесполезно —
     * удар всё равно засчитается, а вот расхождение угла и направления на
     * цель античит видит сразу.
     */
    public static boolean isSettled() {
        return residual <= SETTLE_DEG;
    }

    /** Остаток до цели в градусах — для диагностики и настроек. */
    public static float residual() {
        return residual;
    }

    public static void rotation(LivingEntity target, boolean attack, float attackDist, float rotateDist) {
        if (mc.player == null || target == null) {
            residual = Float.MAX_VALUE;
            return;
        }

        // Аим-поинт — ближайшая ВИДИМАЯ точка хитбокса, а не центр коробки:
        // наведение в центр даёт угол, который на дистанции удара уходит ниже
        // головы, и проверка направления на это реагирует.
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

        // 1. Прямая к цели, а не дуга: каждая ось получает долю предела,
        //    пропорциональную своему остатку.
        float speedYaw = attack ? ATTACK_YAW_STEP_MAX : YAW_STEP_MAX;
        float speedPitch = attack ? ATTACK_PITCH_STEP_MAX : PITCH_STEP_MAX;
        float capYaw = URotationMath.axisCap(yawDelta, total, speedYaw);
        float capPitch = URotationMath.axisCap(pitchDelta, total, speedPitch);

        float stepYaw = URotationMath.clampDelta(yawDelta, capYaw);
        float stepPitch = URotationMath.clampDelta(pitchDelta, capPitch);

        // 2. Торможение у цели: шаг падает пропорционально остатку, иначе
        //    полный шаг проскакивает цель и следующий тик тянет обратно —
        //    это и есть видимое дрожание камеры.
        stepYaw = decelerate(stepYaw, yawDelta, SLOW_RADIUS_YAW);
        stepPitch = decelerate(stepPitch, pitchDelta, SLOW_RADIUS_PITCH);

        // 3. Пауза после удара.
        boolean hold = inPostHitHold();
        if (hold) {
            stepYaw = URotationMath.clampDelta(stepYaw, POST_HIT_YAW_CAP);
            stepPitch = URotationMath.clampDelta(stepPitch, POST_HIT_PITCH_CAP);
        }

        // 4. Разгон и запрет разворота за один тик.
        stepYaw = limitAccel(stepYaw, lastYawStep, ACCEL_YAW);
        stepPitch = limitAccel(stepPitch, lastPitchStep, ACCEL_PITCH);

        float newYaw = currentYaw + stepYaw;
        float newPitch = Mth.clamp(currentPitch + stepPitch, -90.0F, 90.0F);

        // 5. Покачивание — только вне окна атаки и вне паузы после удара.
        //    Амплитуда и частота держатся во времени: если пересчитывать их
        //    каждый тик, синус превращается в белый шум.
        if (!attack && !hold) {
            newYaw += URotationMath.sinJitter(
                    URotationMath.stableRand("sh.ampY", JITTER_MIN, JITTER_MAX, 130L),
                    URotationMath.stableRand("sh.divY", 11.0F, 95.0F, 380L));
            newPitch += URotationMath.sinJitter(
                    URotationMath.stableRand("sh.ampP", JITTER_MIN, JITTER_MAX, 130L),
                    URotationMath.stableRand("sh.divP", 11.0F, 95.0F, 380L));
        }

        // 6. Отдаём в сток: он снапнет на GCD-сетку игрока ровно один раз.
        URotationMath.submit(SUBMIT_YAW_CAP, SUBMIT_PITCH_CAP, 22.0F, 18.0F, 0, 15, newYaw, newPitch);

        lastYawStep = stepYaw;
        lastPitchStep = stepPitch;

        // Остаток считается ПОСЛЕ шага: аура читает его на следующем тике, когда
        // камера уже сдвинулась, и должна видеть то, что будет на экране.
        residual = (float) Math.hypot(
                Math.abs(yawDelta - stepYaw),
                Math.abs(pitchDelta - stepPitch));

        if (attack) {
            lastHitMs = System.currentTimeMillis();
            hasHit = true;
        }
    }

    /**
     * Квадратичный спад шага в последних градусах до цели.
     *
     * <p>Квадрат, а не линейка: линейный спад даёт заметное «прилипание» —
     * камера ползёт к цели с постоянной скоростью и останавливается рывком.
     */
    private static float decelerate(float step, float delta, float radius) {
        float remaining = Math.abs(delta);
        if (remaining >= radius) {
            return step;
        }
        float ratio = remaining / radius;
        float scaled = step * ratio * ratio;
        if (Math.abs(scaled) >= MIN_STEP) {
            return scaled;
        }
        // У самой цели — ровно остаток, чтобы дойти до нуля, но не меньше
        // одного кванта, иначе GCD-снап округлит шаг в ноль.
        return Math.copySign(Math.min(MIN_STEP, remaining), step);
    }

    /**
     * Предел разгона и запрет разворота за тик.
     *
     * <p>Ограничивается только рост шага. Торможение свободно: рука
     * останавливается резко, и именно поэтому профиль успевает погасить шаг у
     * цели и не перелетает её.
     */
    private static float limitAccel(float step, float previous, float accel) {
        if (previous != 0.0F && step != 0.0F && Math.signum(step) != Math.signum(previous)) {
            // Разворот за один тик невозможен: рука проходит через ноль.
            return 0.0F;
        }
        if (Math.abs(step) <= Math.abs(previous) + accel) {
            return step;
        }
        return Math.copySign(Math.abs(previous) + accel, step);
    }

    private static boolean inPostHitHold() {
        return hasHit && System.currentTimeMillis() - lastHitMs < POST_HIT_HOLD_MS;
    }

    /**
     * Плавный отвод камеры, когда цели больше нет.
     *
     * <p>Возврат идёт тем же ограниченным разгоном, что и наведение: иначе после
     * боя камера одним тиком прыгает туда, куда игрок смотрел до боя.
     */
    public static boolean smoothBack() {
        if (mc.player == null) {
            return true;
        }
        float toYaw = FreeLookUtil.freeYaw;
        float toPitch = FreeLookUtil.freePitch;
        boolean done = Math.abs(URotationMath.wrap(toYaw - mc.player.getYRot())) < 1.0F
                && Math.abs(toPitch - mc.player.getXRot()) < 1.0F;
        if (!done) {
            URotationMath.submit(16.0F, 13.0F, 16.0F, 13.0F, 0, 15, toYaw, toPitch);
        }
        return done;
    }

    /**
     * Сброс состояния захода.
     *
     * <p>Обязателен: остаток разгона от прошлой цели иначе переносится на новую,
     * и первый тик новой цели начинается с чужого шага — то есть с рывка.
     */
    public static void state() {
        lastYawStep = 0.0F;
        lastPitchStep = 0.0F;
        hasHit = false;
        lastHitMs = 0L;
        residual = Float.MAX_VALUE;
    }
}
