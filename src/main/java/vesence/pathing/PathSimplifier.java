package vesence.pathing;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * «Струнная стяжка» маршрута: убирает промежуточные узлы сетки с прямых участков.
 *
 * <p>Зачем. A* ходит по клеткам и на ровном месте рисует ломаную: северо-восток,
 * восток, северо-восток, восток… Каждый такой микроизлом — поворот курса на
 * 20–45°, и даже сглаженная «морковка» на гребенке диагоналей виляет. Со
 * стороны это ровно то, на что жалуются: «ходит зигзагами».
 *
 * <p>Живой игрок так не ходит: он видит, что от угла до угла свободно, и идёт
 * по прямой. Здесь то же самое — подряд идущие узлы обычного шага на одной
 * высоте сливаются в один отрезок, если коридор между краями проходим. Узлы
 * действий (прыжок, спуск, падение, плавание, лазание, раскоп) сохраняются
 * ВСЕГДА: в них закодировано, что именно должен сделать исполнитель, и терять
 * их нельзя.
 *
 * <p>Стяжка выполняется один раз на готовый путь, поэтому sampling коридора
 * (четверть блока на пробу) стоит копейки и не касается горячего пути поиска.
 */
@Environment(EnvType.CLIENT)
final class PathSimplifier {

    /** Как далеко вперед пробуем тянуть одну струну, узлов. */
    private static final int MAX_MERGE = 24;

    private PathSimplifier() {
    }

    /**
     * Упрощает путь, если есть что упрощать.
     *
     * @return тот же путь, если стягивать нечего (или мир недоступен), иначе
     *         новый путь с теми же целью и ценой, но более редкими узлами
     */
    static Path simplify(Path path) {
        if (path == null) {
            return null;
        }
        List<PathNode> nodes = path.nodes;
        if (nodes.size() < 3) {
            return path;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) {
            return path;
        }

        List<PathNode> kept = new ArrayList<>(nodes.size());
        kept.add(nodes.get(0));

        int i = 1;
        int n = nodes.size();
        while (i < n) {
            PathNode anchor = kept.get(kept.size() - 1);
            PathNode node = nodes.get(i);

            // Узел действия или смена высоты — всегда оставляем как есть.
            if (node.type != MovementType.WALK || node.y != anchor.y) {
                kept.add(node);
                i++;
                continue;
            }

            // Тянем струну вперёд, пока прямой коридор проходим.
            int farthest = i;
            for (int j = i + 1; j < n && j - i < MAX_MERGE; j++) {
                PathNode candidate = nodes.get(j);
                if (candidate.type != MovementType.WALK || candidate.y != anchor.y) {
                    break;
                }
                if (!corridorClear(mc.level, anchor, candidate)) {
                    break;
                }
                farthest = j;
            }
            kept.add(nodes.get(farthest));
            i = farthest + 1;
        }

        if (kept.size() == nodes.size()) {
            return path;
        }
        return new Path(kept, path.goal, path.cost);
    }

    /**
     * Свободен ли прямой коридор между центрами двух узлов.
     *
     * <p>Проверяется на уровне ног: клетки ног и головы свободны, под ногами
     * опора на всём протяжении. Проба каждые четверть блока — шаг крупнее
     * позволял бы «проткнуть» угловой блок по диагонали.
     */
    private static boolean corridorClear(Level level, PathNode a, PathNode b) {
        double ax = a.centerX();
        double az = a.centerZ();
        double dx = b.centerX() - ax;
        double dz = b.centerZ() - az;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-6) {
            return true;
        }
        int y = a.y;
        int samples = (int) Math.ceil(len * 4.0);
        for (int s = 1; s <= samples; s++) {
            double t = (double) s / samples;
            int x = (int) Math.floor(ax + dx * t);
            int z = (int) Math.floor(az + dz * t);
            if (!clear(level, x, y, z) || !clear(level, x, y + 1, z)) {
                return false;
            }
            if (!solid(level, x, y - 1, z)) {
                return false;
            }
        }
        return true;
    }

    private static boolean clear(Level level, int x, int y, int z) {
        var pos = new net.minecraft.core.BlockPos(x, y, z);
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    private static boolean solid(Level level, int x, int y, int z) {
        var pos = new net.minecraft.core.BlockPos(x, y, z);
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }
}
