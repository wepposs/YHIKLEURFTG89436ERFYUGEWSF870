package vesence.pathing;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;

/**
 * Узел пути: позиция блока, способ прихода в неё и стоимости A*.
 *
 * <p>Поля {@code g}, {@code h}, {@code parent} изменяются прямо в поиске — так мы
 * избегаем создания копий узла на каждое улучшение оценки (это горячий путь:
 * один запрос пути перебирает десятки тысяч узлов).
 */
@Environment(EnvType.CLIENT)
public final class PathNode {

    public final int x;
    public final int y;
    public final int z;

    /**
     * Способ прихода в узел.
     *
     * <p>Поле изменяемое намеренно: A* может найти более дешёвый путь в тот же
     * блок (например, сначала через прыжок, а потом шагом). Если тип не
     * обновлять, исполнитель получит узел с устаревшим «прыжок» и будет прыгать
     * на ровном месте всю дорогу.
     */
    public MovementType type;

    /**
     * Блок, который надо прорубить, чтобы войти в этот узел.
     *
     * <p>Заполняется только для {@link MovementType#DIG}. Хранится именно блок,
     * а не «направление копания»: исполнителю нужно конкретное место, чтобы
     * навести взгляд, подобрать инструмент и держать удар.
     *
     * <p>Поле изменяемое по той же причине, что и {@link #type}: при улучшении
     * оценки узел может перестать быть «прорубленным» и стать обычным шагом.
     */
    public BlockPos dig;

    /** Стоимость от старта. */
    public double g;
    /** Эвристика до цели. */
    public double h;
    /** Родитель в дереве пути. */
    public PathNode parent;

    public PathNode(int x, int y, int z, MovementType type) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.type = type;
    }

    public double f() {
        return this.g + this.h;
    }

    public BlockPos pos() {
        return new BlockPos(this.x, this.y, this.z);
    }

    /** Ключ для карт: упаковка координат в long, как это делает сам BlockPos. */
    public long key() {
        return BlockPos.asLong(this.x, this.y, this.z);
    }

    /** Центр блока — то, куда целится исполнитель. */
    public double centerX() {
        return this.x + 0.5;
    }

    public double centerY() {
        return this.y;
    }

    public double centerZ() {
        return this.z + 0.5;
    }

    @Override
    public String toString() {
        return "(" + this.x + ", " + this.y + ", " + this.z + ") " + this.type;
    }
}
