package vesence.pathing;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Mth;

/**
 * Плавный доворот головы к нужному направлению.
 *
 * <p>Зачем отдельный класс. Тик идёт 20 раз в секунду, а кадров бывает 144.
 * Если менять угол раз в тик, голова поворачивается ступеньками по 15–20° —
 * это и читается как «робот, который дёргает головой». Здесь угол считается по
 * реальному времени кадра, с разгоном и торможением, как у живой шеи.
 *
 * <p>Скорость ограничена в градусах в секунду, поэтому поведение не зависит от
 * FPS: на слабом и на мощном компьютере голова поворачивается за одно и то же
 * время.
 */
@Environment(EnvType.CLIENT)
public final class RotationSmoother {

    /**
     * Максимальная скорость поворота по рысканью, градусов в секунду.
     *
     * <p>900 — это примерно пол-оборота за 0.2 с: так человек и разворачивается,
     * когда ведёт мышь на повороте. Медленнее (220) голова «думает» над каждым
     * поворотом по полсекунды, и это выглядит как задумчивость.
     */
    private static final float MAX_YAW_SPEED = 900.0f;

    /** Максимальная скорость по тангажу — шея вверх-вниз двигается медленнее. */
    private static final float MAX_PITCH_SPEED = 500.0f;

    /** Разгон: сколько градусов в секунду прибавляется за секунду. */
    private static final float YAW_ACCEL = 6_000.0f;
    private static final float PITCH_ACCEL = 3_500.0f;

    /** Насколько резко гасим скорость у цели — иначе голова «кивает» вокруг угла. */
    private static final float DECEL_RATIO = 0.25f;

    /**
     * Предел поворота за один кадр, градусов.
     *
     * <p>Страховка от длинных кадров: 18° за кадр — это уже заметный, но всё ещё
     * плавный доворот, а не щелчок. Нужен потому, что при подгрузке чанков кадр
     * может длиться сотню миллисекунд, и «градусы в секунду» дали бы рывок.
     */
    private static final float MAX_STEP_PER_FRAME = 18.0f;

    private float yaw;
    private float pitch;
    private float targetYaw;
    private float targetPitch;
    private float yawVelocity;
    private float pitchVelocity;

    private long lastUpdateNanos;

    public RotationSmoother(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
        this.targetYaw = yaw;
        this.targetPitch = pitch;
        this.lastUpdateNanos = System.nanoTime();
    }

    public void setTarget(float yaw, float pitch) {
        this.targetYaw = yaw;
        this.targetPitch = Mth.clamp(pitch, -90.0f, 90.0f);
    }

    /** Мгновенно поставить угол, без доворота — при старте пути. */
    public void snapTo(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
        this.targetYaw = yaw;
        this.targetPitch = pitch;
        this.yawVelocity = 0.0f;
        this.pitchVelocity = 0.0f;
        this.lastUpdateNanos = System.nanoTime();
    }

    /**
     * Прижать текущий угол к фактическому, сохранив скорость.
     *
     * <p>Отличие от {@link #snapTo} принципиальное: скорость НЕ обнуляется.
     * Угол может быть записан снаружи (Baritone подрезает наш шаг своим
     * лимитером) — пружина обязана продолжать разгон оттуда, где голова
     * находится на самом деле, а не оттуда, где она «думала».
     */
    public void syncTo(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public float yaw() {
        return this.yaw;
    }

    public float pitch() {
        return this.pitch;
    }

    public float targetYaw() {
        return this.targetYaw;
    }

    /**
     * Продвинуть угол по реальному времени с прошлого вызова.
     *
     * <p>Вызывается и на тике, и на кадре: кто первый — тот и двигает время,
     * повторный вызов с тем же {@code nanoTime} просто ничего не делает.
     */
    public void update() {
        long now = System.nanoTime();
        float dt = (now - this.lastUpdateNanos) / 1_000_000_000.0f;
        this.lastUpdateNanos = now;

        // Ограничение сверху: после лага или паузы окна не должно быть рывка на
        // пол-оборота — живой игрок тоже не телепортирует голову.
        if (dt <= 0.0f) {
            return;
        }
        dt = Math.min(dt, 0.1f);

        this.yaw = advance(this.yaw, this.targetYaw, true, dt);
        this.pitch = advance(this.pitch, this.targetPitch, false, dt);
    }

    private float advance(float current, float target, boolean yawAxis, float dt) {
        float delta = Mth.wrapDegrees(target - current);
        float maxSpeed = yawAxis ? MAX_YAW_SPEED : MAX_PITCH_SPEED;
        float accel = yawAxis ? YAW_ACCEL : PITCH_ACCEL;

        float velocity = yawAxis ? this.yawVelocity : this.pitchVelocity;

        // Хотим остановиться: до цели осталось меньше, чем мы пройдём за кадр
        // при текущей скорости. Тогда тормозим, а не проскакиваем.
        float stoppingDistance = velocity * velocity / (2.0f * accel);
        if (Math.abs(delta) <= stoppingDistance || Math.abs(delta) < 0.05f) {
            velocity -= Math.signum(velocity) * accel * DECEL_RATIO * dt;
            if (Math.abs(velocity) < 1.0f) {
                velocity = 0.0f;
            }
        } else {
            velocity += Math.signum(delta) * accel * dt;
        }

        velocity = Mth.clamp(velocity, -maxSpeed, maxSpeed);

        float step = velocity * dt;

        // Жёсткий предел на кадр. Скорость в градусах в секунду хороша тем, что
        // не зависит от FPS, но у неё есть обратная сторона: при длинном кадре
        // (подгрузка чанков, своп) шаг получается в десятки градусов — и голова
        // всё-таки «щёлкает». Этот предел гарантирует, что за один кадр глаз не
        // увидит рывка, каким бы ни был dt.
        float maxStep = MAX_STEP_PER_FRAME;
        if (step > maxStep) {
            step = maxStep;
        } else if (step < -maxStep) {
            step = -maxStep;
        }

        if (Math.abs(step) >= Math.abs(delta)) {
            current = target;
            velocity = 0.0f;
        } else {
            current += step;
        }

        if (yawAxis) {
            this.yawVelocity = velocity;
            return Mth.wrapDegrees(current);
        }
        this.pitchVelocity = velocity;
        return Mth.clamp(current, -90.0f, 90.0f);
    }
}
