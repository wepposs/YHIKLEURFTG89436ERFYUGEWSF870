package vesence.pathing;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.pathing.goal.Goal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Готовый путь от старта до цели.
 *
 * <p>Узлы идут в порядке прохождения. Исполнитель хранит свой курсор {@link #index}
 * и продвигает его по мере достижения узлов, поэтому путь не нужно пересчитывать
 * на каждом тике.
 */
@Environment(EnvType.CLIENT)
public final class Path {

    public final List<PathNode> nodes;
    public final Goal goal;
    public final double cost;

    /** Курсор исполнителя: индекс узла, к которому сейчас идём. */
    public int index;

    public Path(List<PathNode> nodes, Goal goal, double cost) {
        this.nodes = nodes;
        this.goal = goal;
        this.cost = cost;
        this.index = 0;
    }

    public boolean isEmpty() {
        return this.nodes.isEmpty();
    }

    public int size() {
        return this.nodes.size();
    }

    /** Текущий узел-цель, либо последний, если курсор уже добежал до конца. */
    public PathNode current() {
        if (this.nodes.isEmpty()) {
            return null;
        }
        if (this.index >= this.nodes.size()) {
            return this.nodes.get(this.nodes.size() - 1);
        }
        return this.nodes.get(this.index);
    }

    public PathNode last() {
        return this.nodes.isEmpty() ? null : this.nodes.get(this.nodes.size() - 1);
    }

    public boolean finished() {
        return this.nodes.isEmpty() || this.index >= this.nodes.size();
    }

    /** Оставшаяся часть пути — для отрисовки линии. */
    public List<PathNode> remaining() {
        if (this.nodes.isEmpty()) {
            return Collections.emptyList();
        }
        int from = Math.min(this.index, this.nodes.size() - 1);
        return new ArrayList<>(this.nodes.subList(from, this.nodes.size()));
    }

    @Override
    public String toString() {
        return "Path[" + this.nodes.size() + " узлов, цена " + String.format("%.1f", this.cost) + "]";
    }
}
