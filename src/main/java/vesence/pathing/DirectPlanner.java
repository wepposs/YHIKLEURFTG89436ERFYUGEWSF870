package vesence.pathing;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Прямой ход к цели — когда поиск не нашёл ничего.
 *
 * <p>Зачем это нужно. Поиск пути честно отвечает «не нашёл» в двух случаях:
 * цель за границей загруженных чанков (там просто нет блоков, по которым можно
 * строить граф) и цель в месте, куда нет проходимого маршрута. Для игрока оба
 * ответа выглядят одинаково бесполезно: он попросил идти, а бот встал и
 * написал «путь не найден».
 *
 * <p>Живой игрок так не делает. Он идёт в нужную сторону: обходит, перепрыгивает,
 * переплывает, а если совсем стена — прорубается. Направление ему известно
 * заранее, и никакого перебора для этого не нужно.
 *
 * <p>Отсюда и форма: не поиск, а построение. Один проход по прямой к цели,
 * по одному блоку за шаг, по одной оси (как и {@link DigPlanner} — диагональный
 * ход физически непроходим). Всё, что мешает, помечается на слом; вода не
 * обходится, а переплывается; обрыв не обходится, а пережидается падением.
 *
 * <p>Путь не претендует на оптимальность. Его задача — двигать игрока к цели,
 * пока не подгрузятся чанки и обычный поиск снова не сможет построить маршрут.
 */
@Environment(EnvType.CLIENT)
public final class DirectPlanner {

    /** Длина одного прямого хода. Дальше всё равно пересчёт. */
    private static final int MAX_STEPS = 64;

    /** Безопасный допуск по умолчанию, как у обычного поиска. */
    private static final int DEFAULT_MAX_DROP = 3;

    private DirectPlanner() {
    }

    /**
     * Строит прямой ход от игрока к цели.
     *
     * @return узлы хода (первый — {@link MovementType#START}) либо {@code null},
     *         если даже в сторону цели шагнуть некуда
     */
    public static List<PathNode> plan(BlockWorld world, BlockPos start, BlockPos goal) {
        return plan(world, start, goal, DEFAULT_MAX_DROP);
    }

    public static List<PathNode> plan(BlockWorld world, BlockPos start, BlockPos goal, int maxFall) {
        maxFall = Math.max(0, Math.min(8, maxFall));
        Set<BlockPos> visited = new HashSet<>();
        visited.add(new BlockPos(start.getX(), start.getY(), start.getZ()));
        List<PathNode> nodes = new ArrayList<>();
        PathNode current = new PathNode(start.getX(), start.getY(), start.getZ(),
                MovementType.START);
        nodes.add(current);

        int x = start.getX();
        int y = start.getY();
        int z = start.getZ();

        for (int step = 0; step < MAX_STEPS; step++) {
            int dx = goal.getX() - x;
            int dz = goal.getZ() - z;
            int dy = goal.getY() - y;

            // Пришли по горизонтали и по высоте — дальше пусть работает поиск.
            if (dx == 0 && dz == 0 && Math.abs(dy) <= 1) {
                break;
            }

            // Ось выбираем ту, где осталось больше: так ход сам чередуется и
            // выходит к цели, а не упирается в одну стену.
            int dirX = 0;
            int dirZ = 0;
            if (Math.abs(dx) >= Math.abs(dz) && dx != 0) {
                dirX = Integer.signum(dx);
            } else if (dz != 0) {
                dirZ = Integer.signum(dz);
            } else if (dx != 0) {
                dirX = Integer.signum(dx);
            }

            Step next = stepToward(world, x, y, z, dirX, dirZ, dy, maxFall);
            if (next != null && visited.contains(new BlockPos(next.x, next.y, next.z))) {
                next = null;
            }
            if (next == null) {
                // Выбранная ось закрыта. Пробуем все четыре, в порядке близости к
                // направлению на цель. Сдаваться после одной неудачи нельзя:
                // именно так рождалось «путь не найден» на ровном месте — цель за
                // углом скалы, а мы пробовали только одну ось.
                next = tryOtherAxes(world, x, y, z, dirX, dirZ, dy, maxFall, visited);
            }
            if (next == null) {
                // Нет безопасного непосещённого шага — возвращаем только пройденный участок.
                return nodes.size() > 1 ? nodes : null;
            }

            PathNode node = new PathNode(next.x, next.y, next.z, next.type);
            node.dig = next.dig;
            node.parent = current;
            nodes.add(node);
            visited.add(node.pos());
            current = node;

            x = next.x;
            y = next.y;
            z = next.z;
        }

        return nodes.size() > 1 ? nodes : null;
    }

    /**
     * Перебор остальных осей, когда выбранная закрыта.
     *
     * <p>Порядок — по «полезности»: сначала та же ось в другую сторону, потом
     * поперечная. Так бот обходит препятствие, а не упирается в него.
     */
    private static Step tryOtherAxes(BlockWorld world, int x, int y, int z,
                                     int dirX, int dirZ, int dy, int maxFall,
                                     Set<BlockPos> visited) {
        int[][] axes = {
                {dirX, dirZ}, {-dirX, -dirZ},
                {dirZ, -dirX}, {-dirZ, dirX},
                {1, 0}, {-1, 0}, {0, 1}, {0, -1}
        };
        for (int[] axis : axes) {
            if (axis[0] == 0 && axis[1] == 0) {
                continue;
            }
            Step step = stepToward(world, x, y, z, axis[0], axis[1], dy, maxFall);
            if (step != null && !visited.contains(new BlockPos(step.x, step.y, step.z))) {
                return step;
            }
        }
        return null;
    }

    /** Один шаг прямого хода. */
    private static Step stepToward(BlockWorld world, int x, int y, int z,
                                   int dirX, int dirZ, int dy, int maxFall) {
        if (dirX == 0 && dirZ == 0) {
            return null;
        }
        int nx = x + dirX;
        int nz = z + dirZ;

        // Высота шага. К цели по высоте идём по одному блоку за шаг: и вверх, и
        // вниз. Больше одного блока за раз не проходится без лестницы.
        int ny = y;
        if (dy > 1) {
            ny = y + 1;
        } else if (dy < -1) {
            ny = y - 1;
        }

        if (world.dangerous(nx, ny, nz) || world.dangerous(nx, ny + 1, nz)
                || world.dangerous(nx, ny - 1, nz)) {
            return null;
        }

        // Вода не отменяет проверку свободного объёма игрока.
        if (world.water(nx, ny, nz) || world.water(nx, ny + 1, nz)) {
            if (!world.passable(nx, ny, nz) || !world.passable(nx, ny + 1, nz)) {
                return null;
            }
            return new Step(nx, ny, nz, MovementType.SWIM, null);
        }

        // Нет опоры под ногами — не копаем в пустоту, а шагаем и падаем.
        if (!world.hasFloor(nx, ny, nz) && !world.climbable(nx, ny, nz)) {
            // Считаем полную высоту падения от исходных ног, не от ny:
            // при спуске ny уже на блок ниже и не даёт лишний блок допуска.
            int landing = landing(world, nx, y, nz, maxFall);
            if (landing == Integer.MIN_VALUE) {
                return null; // Нельзя превращать отсутствие опоры в WALK/DIG.
            }
            MovementType type = world.inWater(nx, landing, nz) ? MovementType.SWIM
                    : (landing < y ? MovementType.FALL : MovementType.WALK);
            return new Step(nx, landing, nz, type, null);
        }

        // Обе клетки роста должны быть либо свободны, либо разрушаемы.
        boolean feetFree = world.passable(nx, ny, nz);
        boolean headFree = world.passable(nx, ny + 1, nz);
        boolean feetDig = world.diggable(nx, ny, nz);
        boolean headDig = world.diggable(nx, ny + 1, nz);

        if (world.dangerous(nx, ny, nz) || world.dangerous(nx, ny + 1, nz)) {
            return null;
        }
        if (!(feetFree || feetDig) || !(headFree || headDig)) {
            return null;
        }

        if (feetFree && headFree) {
            MovementType type = ny > y ? MovementType.ASCEND
                    : (ny < y ? MovementType.DESCEND : MovementType.WALK);
            return new Step(nx, ny, nz, type, null);
        }

        // Придётся ломать. Копаем тот блок, что мешает: сначала на уровне ног,
        // потом над головой. Исполнитель сам доведёт дело до конца.
        BlockPos dig = feetFree ? new BlockPos(nx, ny + 1, nz) : new BlockPos(nx, ny, nz);
        return new Step(nx, ny, nz, MovementType.DIG, dig);
    }

    /** Куда приземлимся, если шагнуть в колонку и упасть. */
    private static int landing(BlockWorld world, int x, int y, int z, int maxFall) {
        // Проверяем и вход в колонку: нельзя падать сквозь блоки или лаву.
        for (int depth = 0; depth <= maxFall; depth++) {
            int cy = y - depth;
            if (!world.passable(x, cy, z) || !world.passable(x, cy + 1, z)
                    || world.dangerous(x, cy, z) || world.dangerous(x, cy + 1, z)
                    || world.dangerous(x, cy - 1, z)) {
                return Integer.MIN_VALUE;
            }
            if (world.inWater(x, cy, z) || world.hasFloor(x, cy, z)
                    || world.climbable(x, cy, z)) {
                return cy;
            }
        }
        return Integer.MIN_VALUE;
    }

    /** Результат одного шага. */
    private record Step(int x, int y, int z, MovementType type, BlockPos dig) {
    }
}
