package vesence.mixin;

import baritone.api.utils.Rotation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import vesence.pathing.RotationSmoother;
import vesence.pathing.VesenceBaritone;

/**
 * Живая голова внутри настоящего Baritone.
 *
 * <p>Baritone решает, КУДА смотреть: нужный угол каждому тику диктуют его
 * движения — копание, установка блоков, прыжки. Дальше угол проходит через
 * {@code AbstractAimProcessor.nextRotation(target)} — единственную точку, где
 * желаемый поворот превращается в применённый: Baritone квантует его под
 * «мышиные» шаги и записывает как текущее положение головы.
 *
 * <p>Этот миксин подменяет ЖЕЛАЕМУЮ точку до того, как Baritone сделает свой
 * шаг: пружина Vesence ({@link RotationSmoother}) разгоняется и тормозит к
 * цели, как живая шея, а лимитер Baritone стоит последним и гарантирует, что
 * внутренний счётчик углов, пакеты серверу и готовность ломать блок останутся
 * согласованными. Мы меняем форму кривой, а не точку назначения — поэтому
 * ничего в мозге Baritone не разъезжается.
 *
 * <p>Пружина своя на каждый экземпляр процессора: живой процессор раз в тик
 * делает один шаг, вилки ({@code fork()}), которыми Baritone заглядывает в
 * будущее, через {@code nextRotation} не ходят — планирование остаётся на
 * родной кривой Baritone и чуть оптимистичнее факта; на точность движений это
 * не влияет, потому что критичные проверки (достал ли блок) Baritone делает по
 * фактическому положению игрока.
 *
 * <p>Цель — строкой ({@code targets}): вложенный класс Baritone может быть
 * непубличным, строковая цель от доступа не зависит. Выключается из чата:
 * {@code #set look off}.
 */
@Mixin(targets = "baritone.behavior.LookBehavior$AbstractAimProcessor")
public abstract class BaritoneAimProcessorMixin {

    /** Фактически применённый на прошлом шаге угол. */
    @Shadow
    protected abstract Rotation getPrevRotation();

    /** Пружина Vesence: своя на экземпляр процессора, создаётся лениво. */
    @Unique
    private RotationSmoother vesenceSmoother;

    /** Лог активации уже напечатан — одна строка на процессор, без спама. */
    @Unique
    private boolean vesenceAnnounced;

    @ModifyVariable(method = "nextRotation", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Rotation vesenceSpringTarget(Rotation target) {
        if (target == null || !VesenceBaritone.get().isLookSmoothing()) {
            return target;
        }
        Rotation prev = this.getPrevRotation();
        if (prev == null) {
            return target;
        }
        if (this.vesenceSmoother == null) {
            this.vesenceSmoother = new RotationSmoother(prev.getYaw(), prev.getPitch());
        }
        if (!this.vesenceAnnounced) {
            this.vesenceAnnounced = true;
            System.out.println("[vesence] живая голова: пружина Vesence ведёт взгляд Baritone");
        }
        // Прижимаем пружину к реально применённому углу, не сбрасывая скорость:
        // даже если лимитер Baritone подрезал прошлый шаг, разгон продолжается
        // оттуда, где голова находится на самом деле.
        this.vesenceSmoother.syncTo(prev.getYaw(), prev.getPitch());
        this.vesenceSmoother.setTarget(target.getYaw(), target.getPitch());
        this.vesenceSmoother.update();
        return new Rotation(this.vesenceSmoother.yaw(), this.vesenceSmoother.pitch());
    }
}
