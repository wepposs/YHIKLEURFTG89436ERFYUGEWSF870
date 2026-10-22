package vesence.pathing.goal;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;

/**
 * Цель поиска пути.
 *
 * <p>Интерфейс намеренно повторяет форму Baritone API ({@code isInGoal} + {@code heuristic}),
 * но это собственная реализация: движок, узлы и эвристика полностью наши.
 *
 * <p>{@code heuristic} обязан быть допустимым (admissible): он не должен переоценивать
 * реальную стоимость. Иначе A* найдёт не кратчайший путь. Для диагональной ходьбы
 * с ценой {@code sqrt(2)} евклидово расстояние подходит, манхэттен — нет.
 */
@Environment(EnvType.CLIENT)
public interface Goal {

    boolean isInGoal(int x, int y, int z);

    double heuristic(int x, int y, int z);

    default boolean isInGoal(BlockPos pos) {
        return isInGoal(pos.getX(), pos.getY(), pos.getZ());
    }

    default double heuristic(BlockPos pos) {
        return heuristic(pos.getX(), pos.getY(), pos.getZ());
    }

    default String describe() {
        return getClass().getSimpleName();
    }

    /**
     * Более мягкий вариант этой же цели — или {@code null}, если мягче уже некуда.
     *
     * <p>Зачем это нужно. Игрок просит «иди в блок X». Поиск берёт точку
     * буквально: конкретные x, y, z. Но рельеф почти никогда не совпадает —
     * земля уходит на блок вверх, цель оказывается внутри холма или висит в
     * воздухе. Тогда достижимых блоков нет вообще, A* честно обшаривает всю
     * округу и возвращает неудачу, а бот встаёт на месте.
     *
     * <p>Живой игрок в такой ситуации не сдаётся: он идёт «примерно туда».
     * Это и выражает {@code relaxed()}: сначала точный блок, потом окрестность,
     * потом просто нужные координаты по горизонтали.
     */
    default Goal relaxed() {
        return null;
    }

    /**
     * Точка, к которой ведёт эта цель — или {@code null}, если такой нет.
     *
     * <p>Нужна для дальних маршрутов. Клиент видит мир только вокруг игрока;
     * за пределами загруженных чанков блоков просто нет, и A* там честно
     * разводит руками. Игрок в такой ситуации не сдаётся — он идёт в сторону
     * цели, пока не подгрузится следующий участок. Чтобы так поступить, движку
     * нужно знать, куда, собственно, сторона.
     */
    default BlockPos anchor() {
        return null;
    }
}
