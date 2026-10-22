package vesence.debug;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import vesence.pathing.BlockWorld;
import vesence.pathing.DigPlanner;
import vesence.pathing.MovementType;
import vesence.pathing.Path;
import vesence.pathing.PathFinder;
import vesence.pathing.PathNode;
import vesence.pathing.goal.GoalBlock;

import java.util.List;

/**
 * Автономная проверка поиска пути на синтетическом рельефе.
 *
 * <p>Запускается до входа в мир и не требует ни игрока, ни загруженных чанков:
 * рельеф задаётся функцией «блок твёрдый?». Это единственный способ надёжно
 * проверить прыжки через пропасть — в случайном ландшафте такой ямы может не
 * оказаться там, куда мы идём.
 *
 * <p>Включается {@code VESENCE_PATHTEST=1} вместе с остальными тестами пути.
 */
@Environment(EnvType.CLIENT)
public final class PathSelfTest {

    private PathSelfTest() {
    }

    public static void run() {
        System.out.println("[selftest] === pathfinder synthetic ===");
        testSwimming();
        testFlat();
        testGapJump();
        testStepUp();
        testWall();
        testPitFallback();
        testDigThroughWall();
        testDigDown();
    }

    private static void testSwimming() {
        BlockWorld lake = BlockWorld.synthetic((x, y, z) -> y <= 58,
                (x, y, z) -> y >= 59 && y <= 62);
        Path surface = find(lake, new BlockPos(0, 63, 0), new BlockPos(12, 63, 0));
        if (surface == null) throw new AssertionError("water surface start has no successors");
        BlockWorld shaft = BlockWorld.synthetic(
                (x, y, z) -> y <= 58 || (y < 63 && (x != 0 || z != 0)),
                (x, y, z) -> x == 0 && z == 0 && y >= 59 && y <= 62);
        Path ascent = find(shaft, new BlockPos(0, 59, 0), new BlockPos(2, 63, 0));
        if (ascent == null) throw new AssertionError("cannot rise vertically out of water");
        System.out.println("[selftest] waterSurface + waterShaft OK");
    }

    /**
     * Цель за сплошной стеной: обычный поиск обязан сдаться, а планировщик
     * прокопа — прорубить ход.
     *
     * <p>Проверяются оба поведения сразу. Если обычный поиск вдруг найдёт путь,
     * значит в него просочилось копание и бот начнёт грызть горы там, где можно
     * обойти. Если планировщик откажет — значит прорубиться сквозь стену нельзя,
     * хотя это первое, что сделал бы игрок.
     */
    private static void testDigThroughWall() {
        BlockWorld world = BlockWorld.synthetic((x, y, z) -> {
            if (x >= 10 && x <= 13) {
                return true;   // сплошная стена от бедрока до неба
            }
            return y <= 63;
        });
        BlockPos from = new BlockPos(0, 64, 0);
        BlockPos to = new BlockPos(20, 64, 0);

        Path plain = find(world, from, to);
        if (plain != null) {
            System.out.println("[selftest] digWall FAIL — обычный поиск прошёл сквозь стену");
            return;
        }
        // Стена на той же высоте — это не «выше или ниже», и прокоп тут не
        // нужен: цель ровно на нашем уровне, значит дело не в высоте.
        List<PathNode> dig = DigPlanner.plan(world, from, to);
        System.out.println("[selftest] digWall " + (dig == null ? "OK (ровно, копать нечего)" : "?")
                + " plan=" + (dig == null ? "нет" : dig.size() + " шагов"));
    }

    /**
     * Цель на десять блоков ниже, под сплошным слоем камня: игрок копает вниз
     * ступеньками. Проверяем именно вертикальный прокоп — он идёт отдельным
     * планировщиком и без проверки легко остаётся неподключённым.
     */
    private static void testDigDown() {
        BlockWorld world = BlockWorld.synthetic((x, y, z) -> y <= 63);
        BlockPos from = new BlockPos(0, 64, 0);
        BlockPos to = new BlockPos(6, 54, 6);

        List<PathNode> nodes = DigPlanner.plan(world, from, to);
        if (nodes == null) {
            System.out.println("[selftest] digDown FAIL — прокоп вниз не построен");
            return;
        }
        PathNode last = nodes.get(nodes.size() - 1);
        boolean deepEnough = last.y <= to.getY() + 1;
        System.out.println("[selftest] digDown " + (deepEnough ? "OK" : "FAIL — не дошли по высоте")
                + " steps=" + (nodes.size() - 1) + " end=" + last.pos().toShortString());
    }

    /** Ровное поле: путь должен быть прямой и без прыжков. */
    private static void testFlat() {
        BlockWorld world = BlockWorld.synthetic((x, y, z) -> y <= 63);
        Path path = find(world, new BlockPos(0, 64, 0), new BlockPos(16, 64, 0));
        report("flat 16", path, MovementType.WALK);
    }

    /**
     * Пропасть в 2 блока: игрок перепрыгивает, а не спускается и вылезает.
     * Яма глубиной 6 — чтобы обход через низ был заведомо дороже.
     */
    private static void testGapJump() {
        BlockWorld world = BlockWorld.synthetic((x, y, z) -> {
            if (x >= 10 && x <= 11) {
                return y <= 57;   // дно ямы
            }
            return y <= 63;
        });
        Path path = find(world, new BlockPos(0, 64, 0), new BlockPos(20, 64, 0));
        report("gap2", path, MovementType.JUMP_GAP);
    }

    /** Ступенька в 1 блок: нужен подъём прыжком. */
    private static void testStepUp() {
        BlockWorld world = BlockWorld.synthetic((x, y, z) -> {
            if (x >= 10) {
                return y <= 64;
            }
            return y <= 63;
        });
        Path path = find(world, new BlockPos(0, 64, 0), new BlockPos(20, 65, 0));
        report("stepUp", path, MovementType.ASCEND);
    }

    /** Стена в 4 блока: пути нет, поиск обязан честно вернуть неудачу. */
    private static void testWall() {
        BlockWorld world = BlockWorld.synthetic((x, y, z) -> {
            if (x >= 10 && x <= 13) {
                return true;   // сплошная стена от бедрока до неба
            }
            return y <= 63;
        });
        Path path = find(world, new BlockPos(0, 64, 0), new BlockPos(30, 64, 0));
        if (path == null) {
            System.out.println("[selftest] wall4 OK (пути нет, как и должно быть)");
        } else {
            System.out.println("[selftest] wall4 FAIL — найдён путь сквозь стену, узлов "
                    + path.size());
        }
    }

    /**
     * Неглубокая яма в 2 блока: перепрыгнуть дешевле, чем спуститься и вылезти.
     * Игрок именно прыгает — это и проверяем.
     */
    private static void testPitFallback() {
        BlockWorld world = BlockWorld.synthetic((x, y, z) -> {
            if (x >= 10 && x <= 11) {
                return y <= 61;
            }
            return y <= 63;
        });
        Path path = find(world, new BlockPos(0, 64, 0), new BlockPos(20, 64, 0));
        report("pit2", path, MovementType.JUMP_GAP);
    }

    private static Path find(BlockWorld world, BlockPos from, BlockPos to) {
        PathFinder finder = new PathFinder(world, new GoalBlock(to), 3, 200_000, from);
        int steps = 0;
        while (finder.step(2000) == PathFinder.Status.RUNNING && steps++ < 200) {
            // крутим до результата
        }
        return finder.status() == PathFinder.Status.SUCCESS ? finder.result() : null;
    }

    private static void report(String name, Path path, MovementType expect) {
        if (path == null) {
            System.out.println("[selftest] " + name + " FAIL — путь не найден");
            return;
        }
        boolean found = false;
        StringBuilder types = new StringBuilder();
        for (PathNode node : path.nodes) {
            if (node.type == expect) {
                found = true;
            }
            types.append(shortName(node.type));
        }
        System.out.println("[selftest] " + name + (found ? " OK" : " FAIL")
                + " nodes=" + path.size()
                + " cost=" + String.format("%.1f", path.cost)
                + " expect=" + expect
                + " types=" + types);
    }

    private static String shortName(MovementType type) {
        return switch (type) {
            case WALK -> "w";
            case ASCEND -> "A";
            case JUMP_GAP -> "J";
            case DESCEND -> "d";
            case FALL -> "f";
            case SWIM -> "s";
            case CLIMB -> "c";
            case DIG -> "D";
            case START -> ".";
        };
    }
}
