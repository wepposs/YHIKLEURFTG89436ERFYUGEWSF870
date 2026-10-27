package vesence.pathing;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Прокоп к цели, до которой нет дороги: ступеньками вниз или вверх.
 *
 * <p>Зачем отдельный планировщик, а не поиск. Первая версия включала копание
 * прямо в A* — «любой твёрдый блок можно сломать, вот тебе ребро графа». Идея
 * красивая, но в игре она не работает: как только твёрдый блок перестаёт быть
 * препятствием, всё трёхмерное пространство вокруг становится проходимым, и
 * поиск честно перебирает сто двадцать тысяч узлов, так и не дойдя до цели.
 * Замер это подтвердил: восемь секунд на месте и «не удалось дойти».
 *
 * <p>Живой игрок так не делает. Ему надо на десять блоков ниже — он копает
 * наклонный ход ступеньками, по одному блоку за шаг, и идёт по нему. Никакого
 * перебора: направление известно заранее, остаётся проверять, что очередной
 * блок вообще поддаётся.
 *
 * <p>Отсюда и форма: это не поиск, а построение. Один проход, до сотни шагов,
 * мгновенно и предсказуемо.
 */
@Environment(EnvType.CLIENT)
public final class DigPlanner {

    /** Сколько шагов ступеньки делаем максимум. Хватает на сорок блоков вниз. */
    private static final int MAX_STEPS = 160;

    /** С какой высоты позволяем упасть вместо копания. */
    private static final int MAX_DROP = 8;

    private DigPlanner() {
    }

    /**
     * Строит ход ступеньками от {@code start} к высоте цели.
     *
     * <p>Шаг — один блок вниз (или вверх) и один блок в сторону, к цели по
     * горизонтали. Такой ход и проходим, и обратим: по нему можно подняться
     * назад, если на месте ничего не нашлось. Вертикальная шахта дешевле в
     * блоках, но из неё потом не выбраться, а игрок всегда копает с возвратом.
     *
     * @return узлы хода (первый — {@link MovementType#START}) либо {@code null},
     *         если прокопать нельзя
     */
    public static List<PathNode> plan(BlockWorld world, BlockPos start, BlockPos goal) {
        int dy = goal.getY() - start.getY();
        int dx = Math.abs(goal.getX() - start.getX());
        int dz = Math.abs(goal.getZ() - start.getZ());
        int gx = goal.getX();
        int gy = goal.getY();
        int gz = goal.getZ();

        // Последний блок: цель в одной клетке под нами — стоим ровно над ней
        // или в соседней клетке (зигзаг спуска заканчивается сбоку от столба).
        //
        // Ступеньки тут уже не построить — «на другую высоту» мы фактически
        // пришли. Раньше план требовал стоять строго над целью и возвращал null
        // для соседней клетки, движок уходил в слепые пробы, и бот наматывал
        // круги вокруг цели, хотя до неё оставалось сломать один блок и
        // спуститься. Это и выглядело как «пришёл в нужную точку и бегает
        // вокруг».
        //
        // Исполнитель у DIG-узла ломает и блок в ногах, и блок над ним, поэтому
        // для соседней колонны блок над целью тоже обязан поддаваться: иначе
        // бот упрётся взглядом в неразрушимое и будет ждать таймаут вместо
        // финиша.
        if (dy == -1 && dx + dz <= 1
                && world.diggable(gx, gy, gz)
                && !world.dangerous(gx, gy, gz)
                && (dx + dz == 0 || world.passable(gx, gy + 1, gz)
                        || world.diggable(gx, gy + 1, gz))
                // Пол под целью тоже проверяем: после слома блока игрок упадёт
                // именно туда, и лава под целью означает смерть вместо прибытия.
                && !world.dangerous(gx, gy - 1, gz)) {
            List<PathNode> last = new ArrayList<>(1);
            last.add(new PathNode(gx, gy, gz, MovementType.DIG));
            return last;
        }

        // Ровно два блока строго вниз: копаем вертикальную шахту — классическое
        // «прокопать под собой и спуститься». Страшной «необратимой» шахты тут
        // нет: обе клетки пробиваются насквозь, и по ним же можно подняться
        // обратно. Диагональная лестница дошла бы сюда тоже (шаг вбок и
        // последний блок сверху), но это лишний сломанный блок и вид со
        // стороны, будто бот топчется вокруг цели вместо того чтобы копнуть
        // вниз.
        if (dy == -2 && dx + dz == 0
                && world.diggable(gx, gy + 1, gz)
                && world.diggable(gx, gy, gz)
                && !world.dangerous(gx, gy + 1, gz)
                && !world.dangerous(gx, gy, gz)
                && !world.dangerous(gx, gy - 1, gz)) {
            List<PathNode> nodes = new ArrayList<>(2);
            nodes.add(new PathNode(gx, gy + 1, gz, MovementType.DIG));
            nodes.add(new PathNode(gx, gy, gz, MovementType.DIG));
            return nodes;
        }

        if (Math.abs(dy) < 2) {
            // Цель почти на нашей высоте — значит дело не в высоте, и копать
            // нечего: обычный поиск отказал по другой причине.
            return null;
        }
        boolean down = dy < 0;

        List<PathNode> nodes = new ArrayList<>();
        PathNode current = new PathNode(start.getX(), start.getY(), start.getZ(),
                MovementType.START);
        nodes.add(current);

        int x = start.getX();
        int y = start.getY();
        int z = start.getZ();

        for (int step = 0; step < MAX_STEPS; step++) {
            // Дошли до нужной высоты — дальше пусть идёт обычный поиск, ему
            // теперь есть по чему строить маршрут.
            if (down ? y <= goal.getY() + 1 : y >= goal.getY() - 1) {
                break;
            }

            // Шаг делаем ТОЛЬКО по одной оси.
            //
            // Раньше здесь брались оба знака сразу, и ход получался диагональным.
            // По карте он выглядел короче, а на деле был непроходим: в диагональной
            // шахте шириной в блок остаются целыми два угловых столба, и игрок
            // физически не может протолкнуться наискосок — упирается в угол и
            // стоит. Снаружи это выглядело так: прокопал двенадцать блоков вниз,
            // встал в яме и бьётся в стену.
            //
            // Живой игрок копает лестницу по одной оси за шаг. Так и делаем: ось
            // выбираем ту, где до цели осталось больше, — она сама чередуется, и
            // ход сам выходит к нужной точке по горизонтали.
            int goalDx = goal.getX() - x;
            int goalDz = goal.getZ() - z;
            int dirX = 0;
            int dirZ = 0;
            if (Math.abs(goalDx) >= Math.abs(goalDz) && goalDx != 0) {
                dirX = Integer.signum(goalDx);
            } else if (goalDz != 0) {
                dirZ = Integer.signum(goalDz);
            } else if (goalDx != 0) {
                dirX = Integer.signum(goalDx);
            }

            // Стоим ровно над целью (или под ней) — сторону выбираем по тому,
            // где свободнее: копать в монолит наугад нельзя.
            if (dirX == 0 && dirZ == 0) {
                dirX = bestSide(world, x, y, z, down);
                if (dirX == 0) {
                    return null;
                }
            }

            int nx = x + dirX;
            int nz = z + dirZ;
            int ny = down ? y - 1 : y + 1;

            // Спуск с обрыва или с кроны дерева: копать тут нечего, надо просто
            // шагнуть и упасть. Без этой ветки прокоп отказывался строиться
            // ровно там, где его чаще всего и просят — на неровном краю.
            if (down) {
                int drop = dropLanding(world, nx, ny, nz);
                if (drop != Integer.MIN_VALUE) {
                    PathNode fall = new PathNode(nx, drop, nz, MovementType.FALL);
                    fall.parent = current;
                    nodes.add(fall);
                    current = fall;
                    x = nx;
                    y = drop;
                    z = nz;
                    continue;
                }
            }

            int dug = digStep(world, x, y, z, nx, ny, nz);
            if (dug < 0) {
                return null;
            }

            PathNode node = new PathNode(nx, ny, nz, MovementType.DIG);
            node.dig = new BlockPos(nx, ny, nz);
            node.parent = current;
            nodes.add(node);
            current = node;

            x = nx;
            y = ny;
            z = nz;
        }

        // Не добрались до нужной высоты — ход бесполезен, лучше честно отказать.
        if (down ? y > goal.getY() + 1 : y < goal.getY() - 1) {
            return null;
        }
        return nodes.size() > 1 ? nodes : null;
    }

    /**
     * Можно ли сделать очередной шаг ступеньки.
     *
     * @return число блоков под раскоп (для журнала) либо {@code -1}, если шаг
     *         невозможен
     */
    private static int digStep(BlockWorld world, int x, int y, int z,
                               int nx, int ny, int nz) {
        // Обе клетки роста должны стать свободными: в них стоим и в них дышим.
        if (!clearable(world, nx, ny, nz) || !clearable(world, nx, ny + 1, nz)) {
            return -1;
        }
        // Опора под ногами: без неё шаг превращается в падение в неизвестность.
        if (!world.hasFloor(nx, ny, nz) && !world.climbable(nx, ny, nz)) {
            return -1;
        }
        // Свой блок под ногами тоже не должен рушиться — иначе, прокопав шаг,
        // мы окажемся в воздухе над пустотой.
        int dug = 0;
        if (!world.passable(nx, ny, nz)) {
            dug++;
        }
        if (!world.passable(nx, ny + 1, nz)) {
            dug++;
        }
        return dug;
    }

    /** Клетка либо уже свободна, либо её можно прорубить. */
    private static boolean clearable(BlockWorld world, int x, int y, int z) {
        if (world.dangerous(x, y, z) || world.water(x, y, z)) {
            return false;
        }
        return world.passable(x, y, z) || world.diggable(x, y, z);
    }

    /**
     * Куда приземлимся, если шагнуть в колонку и упасть.
     *
     * <p>Прокоп начинается с той точки, где стоит игрок, а она бывает какой
     * угодно: верхушка дерева, кромка обрыва, крыша. Первый же шаг оттуда — это
     * не копание, а падение, и требовать под ним опору нельзя: её нет, и
     * планировщик честно отказывался работать ровно в самом начале.
     *
     * @return высота приземления либо {@link Integer#MIN_VALUE}
     */
    private static int dropLanding(BlockWorld world, int x, int y, int z) {
        int depth = 0;
        while (depth < MAX_DROP) {
            if (!world.passable(x, y - depth, z) || !world.passable(x, y - depth + 1, z)) {
                return Integer.MIN_VALUE;
            }
            if (world.dangerous(x, y - depth, z) || world.dangerous(x, y - depth + 1, z)) {
                return Integer.MIN_VALUE;
            }
            if (world.hasFloor(x, y - depth, z) || world.climbable(x, y - depth, z)) {
                return y - depth;
            }
            depth++;
        }
        return Integer.MIN_VALUE;
    }

    /**
     * В какую сторону копать, если цель ровно над нами или под нами.
     *
     * <p>Выбираем ту, где следующий шаг вообще возможен: если слева монолит из
     * бедрока, а справа обычный камень, копать надо направо.
     *
     * @return {@code +1} или {@code -1} по X либо {@code 0}, если некуда
     */
    private static int bestSide(BlockWorld world, int x, int y, int z, boolean down) {
        int ny = down ? y - 1 : y + 1;
        for (int dir : new int[]{1, -1}) {
            if (digStep(world, x, y, z, x + dir, ny, z) >= 0) {
                return dir;
            }
        }
        return 0;
    }
}
