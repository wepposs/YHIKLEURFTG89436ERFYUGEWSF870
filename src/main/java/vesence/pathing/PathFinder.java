package vesence.pathing;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import vesence.pathing.goal.Goal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Поиск пути A* по блокам мира — пошаговый.
 *
 * <p>Поиск НЕ выполняется целиком за один вызов. Каждый тик движок раскрывает
 * порцию узлов ({@link #step(int)}) и продолжает со следующего тика. Это важно:
 * полный A* на 200 блоков раскрывает десятки тысяч узлов и занял бы сотни
 * миллисекунд — то есть гарантированный фриз кадра. Пошаговый вариант тратит
 * 2–4 мс за тик и не заметен вообще.
 *
 * <p>Движения — как у игрока: шаг в любую из 8 сторон, подъём на блок прыжком,
 * спуск с падением, прыжок через пропасть, плавание и лазание по лестницам.
 * Диагональ разрешена только когда не срезается угол.
 */
@Environment(EnvType.CLIENT)
public final class PathFinder {

    public enum Status {
        /** Ещё ищем. */
        RUNNING,
        /** Путь найден, забирайте через {@link #result()}. */
        SUCCESS,
        /** Путь не существует либо превышен лимит. */
        FAILED
    }

    /** 8 направлений: 4 ортогональных и 4 диагональных. */
    private static final int[][] DIRECTIONS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    private static final double SQRT2 = Math.sqrt(2.0);

    /**
     * Во сколько раз шаг по воде дороже шага по земле.
     *
     * <p>Прежде здесь стояло десять, и это ломало поведение: поиск обходил озеро
     * в полкилометра вместо того, чтобы переплыть его за минуту, а на узких
     * участках вообще не находил маршрута. Плавание в Minecraft примерно вдвое
     * медленнее бега — так и считаем. Двойка делает воду обычной частью маршрута:
     * если через неё короче, плывём, не строя крюк на пятьсот блоков.
     */
    private static final double WATER_COST = 2.0;

    private final BlockWorld world;
    private final Goal goal;
    private final int maxFall;
    private final int maxNodes;

    private final PriorityQueue<PathNode> open = new PriorityQueue<>((a, b) -> Double.compare(a.f(), b.f()));
    private final Map<Long, PathNode> best = new HashMap<>(8192);
    private final Set<Long> closed = new HashSet<>(8192);

    private Status status = Status.RUNNING;
    private Path result;
    private int expanded;
    private boolean limitHit;

    /**
     * Верхняя граница стоимости. Узел, у которого {@code g + h} уже больше неё,
     * отбрасывается.
     *
     * <p>Без этой границы недостижимая цель (например, точка внутри скалы) заставит
     * A* обойти весь связный регион мира — это сотни тысяч узлов и секунды работы
     * на ровном месте. Граница пропорциональна начальной оценке, поэтому длинный
     * обход по-прежнему разрешён, а бессмысленный поиск обрывается сразу.
     */
    private final double costBound;

    /** Множитель для границы: во сколько раз реальный путь может быть длиннее прямой. */
    private static final double BOUND_FACTOR = 3.0;
    private static final double BOUND_SLACK = 48.0;

    public PathFinder(BlockWorld world, Goal goal, int maxFall, int maxNodes) {
        this(world, goal, maxFall, maxNodes, BlockPos.containing(
                net.minecraft.client.Minecraft.getInstance().player.position()));
    }

    public PathFinder(BlockWorld world, Goal goal, int maxFall, int maxNodes, BlockPos rawStart) {
        this.world = world;
        this.goal = goal;
        this.maxFall = Math.max(1, maxFall);
        this.maxNodes = Math.max(1000, maxNodes);

        BlockPos start = this.normalizeStart(rawStart);
        PathNode startNode = new PathNode(start.getX(), start.getY(), start.getZ(), MovementType.START);
        startNode.g = 0.0;
        startNode.h = goal.heuristic(startNode.x, startNode.y, startNode.z);
        this.open.add(startNode);
        this.best.put(startNode.key(), startNode);

        this.costBound = startNode.h * BOUND_FACTOR + BOUND_SLACK;

        if (goal.isInGoal(startNode.x, startNode.y, startNode.z)) {
            List<PathNode> single = new ArrayList<>(1);
            single.add(startNode);
            this.result = new Path(single, goal, 0.0);
            this.status = Status.SUCCESS;
        }
    }

    /**
     * Приводит стартовую позицию к блоку, на котором реально можно стоять.
     *
     * <p>Игрок может оказаться внутри блока (застрял в листве, стоит на плите,
     * вылез из воды) — тогда стартовый узел не проходит проверку {@code canStand},
     * и поиск не может сделать ни одного шага. Ищем ближайшую подходящую высоту
     * ВВЕРХ.
     *
     * <p>Вниз не ищем намеренно. Раньше поиск уходил на восемь блоков вниз и
     * находил «подходящую» высоту где-то под обрывом. Стартовый узел оказывался
     * на четыре блока ниже игрока, исполнитель не мог его достичь, индекс пути
     * не двигался с нуля — и персонаж бесконечно бегал туда-обратно вокруг
     * недостижимой точки. Старт обязан быть там, где стоит игрок, даже если наш
     * упрощённый тест опоры этого блока не одобряет: дальше A* сам разберётся
     * по соседям.
     */
    private BlockPos normalizeStart(BlockPos raw) {
        if (this.world.canStand(raw.getX(), raw.getY(), raw.getZ())) {
            return raw;
        }
        for (int dy = 1; dy <= 2; dy++) {
            if (this.world.canStand(raw.getX(), raw.getY() + dy, raw.getZ())) {
                return raw.above(dy);
            }
        }
        return raw;
    }

    public Goal goal() {
        return this.goal;
    }

    public Status status() {
        return this.status;
    }

    public Path result() {
        return this.result;
    }

    public int expanded() {
        return this.expanded;
    }

    public boolean limitHit() {
        return this.limitHit;
    }

    /**
     * Раскрывает до {@code budget} узлов.
     *
     * @return текущий статус поиска
     */
    public Status step(int budget) {
        return this.stepInternal(budget, budget, Long.MAX_VALUE);
    }

    /**
     * Раскрывает узлы, пока не выберет ВРЕМЕННОЙ бюджет, но не меньше
     * {@code floor} узлов.
     *
     * <p>Зачем. Фиксированные «4000 узлов за тик» считали не работу, а штуки: на
     * холодном кэше один узел стоит миллисекунды, на горячем — микросекунды. В
     * первом случае тик проседал, во втором — процессор простаивал, а игрок
     * стоял и ждал маршрут. Бюджет в наносекундах нагружает тик ровно настолько,
     * насколько можно без потери кадров, и поиск заканчивается в разы быстрее.
     *
     * @param floor       минимум узлов за вызов — гарантия, что поиск вообще движется
     * @param nanosBudget бюджет времени, наносекунды
     */
    public Status stepTimed(int floor, long nanosBudget) {
        return this.stepInternal(floor, floor * 8, System.nanoTime() + nanosBudget);
    }

    private Status stepInternal(int minExpansions, int maxExpansions, long deadlineNanos) {
        if (this.status != Status.RUNNING) {
            return this.status;
        }

        int done = 0;
        while (done < maxExpansions) {
            // Часы дёргаем не на каждом узле (это само по себе накладно), а
            // раз в 64 раскрытия — и только после того, как минимум выбран.
            if (done >= minExpansions && (done & 63) == 0 && System.nanoTime() >= deadlineNanos) {
                break;
            }

            PathNode current = this.open.poll();
            if (current == null) {
                this.status = Status.FAILED;
                return this.status;
            }

            long currentKey = current.key();
            if (this.best.get(currentKey) != current) continue;
            if (!this.closed.add(currentKey)) {
                continue;
            }
            done++;
            this.expanded++;

            if (this.goal.isInGoal(current.x, current.y, current.z)) {
                this.result = this.reconstruct(current);
                this.status = Status.SUCCESS;
                return this.status;
            }

            for (int[] dir : DIRECTIONS) {
                this.expand(current, dir[0], dir[1]);
            }
            // A swimmer can rise in place even in a narrow water shaft.
            if (this.world.canSwim(current.x, current.y, current.z)
                    && this.world.canSwim(current.x, current.y + 1, current.z)
                    && this.world.passable(current.x, current.y + 2, current.z)) {
                this.offer(current, current.x, current.y + 1, current.z, MovementType.SWIM, WATER_COST);
            }
            this.expandClimb(current, 1);
            this.expandClimb(current, -1);

            if (this.expanded >= this.maxNodes) {
                this.limitHit = true;
                this.status = Status.FAILED;
                return this.status;
            }
        }
        return this.status;
    }

    private void expand(PathNode from, int dx, int dz) {
        boolean diagonal = dx != 0 && dz != 0;
        double stepCost = diagonal ? SQRT2 : 1.0;
        int nx = from.x + dx;
        int nz = from.z + dz;

        if (diagonal && !this.world.diagonalClear(from.x, from.y, from.z, dx, dz)) {
            return;
        }

        // Все варианты предлагаются ОДНОВРЕМЕННО, и выбор делает A* по стоимости.
        //
        // Раньше здесь стоял `return` после первого сработавшего случая, и порядок
        // был такой: шаг → подъём → спуск → прыжок. Спуск почти всегда находил
        // землю под ямой, поэтому до прыжка дело не доходило НИКОГДА — бот
        // послушно спускался в каждую дырку и вылезал обратно вместо того, чтобы
        // её перескочить.

        // 1) Шаг на ту же высоту.
        if (this.world.canStand(nx, from.y, nz)) {
            this.offer(from, nx, from.y, nz, MovementType.WALK, stepCost);
        }

        // 2) Переплыть воду.
        //
        // Вода — не опора, стоять на ней нельзя, но пройти её насквозь можно
        // вплавь. Без этой ветки любой водоём на маршруте делал цель
        // недостижимой, а исполнитель, случайно оказавшись в воде, не знал,
        // куда плыть, и тонул.
        //
        // Цена ВЫСОКАЯ (в десять раз дороже шага). Это принципиально: иначе
        // поиск считал выгодным срезать через пруд, персонаж лез в воду, а потом
        // выбирался и снова бежал в воду. Обойти берегом почти всегда короче по
        // времени, чем плыть, — вот пусть поиск так и считает.
        if (this.world.canSwim(nx, from.y, nz)) {
            this.offer(from, nx, from.y, nz, MovementType.SWIM, stepCost * WATER_COST);
        }
        // Всплыть в воде на блок выше: так из воды и выбираются.
        if (this.world.water(nx, from.y + 1, nz) && this.world.passable(nx, from.y + 2, nz)) {
            this.offer(from, nx, from.y + 1, nz, MovementType.SWIM, stepCost * WATER_COST * 1.2);
        }

        // 3) Подъём на один блок — прыжок.
        if (this.world.canStand(nx, from.y + 1, nz) && this.world.passable(nx, from.y + 2, nz)) {
            this.offer(from, nx, from.y + 1, nz, MovementType.ASCEND, stepCost * 1.6);
        }

        // 4) Прыжок через пропасть — ДО спуска, и с ценой ниже, чем «упасть
        //    в яму и вылезти»: именно так поступил бы игрок.
        this.offerGapJumps(from, dx, dz, stepCost);

        // 5) Спуск: ищем, куда приземлимся.
        int landing = this.world.landingY(nx, from.y, nz, this.maxFall);
        if (landing != Integer.MIN_VALUE && landing < from.y) {
            int fallDistance = from.y - landing;
            MovementType type = fallDistance <= 1 ? MovementType.DESCEND : MovementType.FALL;
            double cost = stepCost + fallDistance * 0.15;
            if (this.world.water(nx, landing, nz)) {
                type = MovementType.SWIM;
                cost *= 1.4;
            }
            this.offer(from, nx, landing, nz, type, cost);
        }
    }

    /**
     * Прыжки через провал шириной 1–3 блока.
     *
     * <p>Цена подобрана так, чтобы прыжок выигрывал у обхода: перелёт на две
     * клетки стоит {@code 2.2}, а «спуститься в яму и вылезти» — минимум
     * {@code 2.0 (спуск) + 1.6 (подъём) = 3.6}. Поэтому A* выбирает прыжок —
     * ровно как живой игрок.
     *
     * <p>Провал в один блок — самый частый случай на обычном рельефе, и раньше
     * его здесь не было вообще: A* честно обходил каждую одиночную дырку
     * стороной, потому что шагнуть в неё нельзя (нет пола), а прыжок не был
     * предложен. Именно это выглядело как «обходит то, что можно перепрыгнуть».
     */
    private void offerGapJumps(PathNode from, int dx, int dz, double stepCost) {
        for (int gap = 1; gap <= 3; gap++) {
            int nx = from.x + dx * gap;
            int nz = from.z + dz * gap;

            // Приземляться нужно на твёрдое и на ту же высоту.
            if (!this.world.canStand(nx, from.y, nz)) {
                continue;
            }
            if (this.world.dangerous(nx, from.y, nz)) {
                continue;
            }

            // Между нами и точкой приземления — пустота в обеих клетках роста.
            // Заодно проверяем, что там действительно ПРОПАСТЬ: прыгать по ровному
            // полу бессмысленно, это только засоряет поиск лишними узлами.
            boolean clear = true;
            boolean realGap = false;
            for (int i = 1; i < gap; i++) {
                int cx = from.x + dx * i;
                int cz = from.z + dz * i;
                if (!this.world.passable(cx, from.y, cz) || !this.world.passable(cx, from.y + 1, cz)) {
                    clear = false;
                    break;
                }
                if (!this.world.hasFloor(cx, from.y, cz)) {
                    realGap = true;
                }
            }
            if (!clear || !realGap) {
                continue;
            }

            this.offer(from, nx, from.y, nz, MovementType.JUMP_GAP, stepCost * gap * 1.1);
        }
    }

    private void expandClimb(PathNode from, int dy) {
        int ny = from.y + dy;
        if (!this.world.climbable(from.x, ny, from.z)) {
            return;
        }
        this.offer(from, from.x, ny, from.z, MovementType.CLIMB, 1.5);
    }

    private void offer(PathNode from, int x, int y, int z, MovementType type, double extraCost) {
        long key = BlockPos.asLong(x, y, z);
        if (this.closed.contains(key)) {
            return;
        }

        double newG = from.g + extraCost;
        PathNode existing = this.best.get(key);
        if (existing != null && existing.g <= newG) {
            return;
        }

        double h = this.goal.heuristic(x, y, z);
        if (newG + h > this.costBound) {
            // Дороже разумного обхода — не наш путь.
            return;
        }

        // Never mutate a queued priority: PriorityQueue does not reheapify it.
        // A fresh entry also preserves parent chains of already discovered paths.
        PathNode node = new PathNode(x, y, z, type);
        this.best.put(key, node);
        node.g = newG;
        node.h = h;
        node.parent = from;
        this.open.add(node);
    }

    private Path reconstruct(PathNode end) {
        List<PathNode> reversed = new ArrayList<>();
        PathNode node = end;
        while (node != null) {
            reversed.add(node);
            node = node.parent;
        }
        Collections.reverse(reversed);
        return new Path(reversed, this.goal, end.g);
    }
}
