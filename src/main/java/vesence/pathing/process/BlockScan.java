package vesence.pathing.process;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import vesence.pathing.BlockInteraction;

import java.util.Set;
import java.util.function.Predicate;

/**
 * Поиск подходящих блоков рядом с игроком.
 *
 * <p>Общий для {@code #farm} и {@code #mine}: оба ищут ближайший блок по
 * условию. Вынесено отдельно, чтобы обход куба и выбор ближайшего не
 * дублировались и не разъезжались в поведении.
 */
@Environment(EnvType.CLIENT)
public final class BlockScan {

    /** Предохранитель: больше этого радиуса не сканируем, иначе лаг на кадр. */
    public static final int MAX_RADIUS = 48;

    private BlockScan() {
    }

    /**
     * Есть ли у блока открытая грань — то есть сосед, к которому можно подойти.
     *
     * <p>Без этой проверки {@code #mine dirt} находил пласт земли под ногами:
     * поиск пути честно отвечал «не дойти» — блок внутри породы, — процесс
     * вычёркивал его и брал следующий такой же, и так по кругу. Со стороны это
     * выглядело как «команда не работает» и засыпало лог сообщениями.
     *
     * <p>Игрок копает то, что видит. Блок без единой открытой грани не виден
     * никак, значит и в списке целей ему делать нечего.
     */
    public static boolean hasOpenFace(net.minecraft.world.level.LevelReader level, BlockPos pos) {
        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
            BlockPos neighbour = pos.relative(direction);
            if (!level.getBlockState(neighbour).isSolidRender()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Позиция, откуда до блока можно дотянуться.
     *
     * <p>Это и была главная ошибка первых версий {@code #mine} и {@code #farm}:
     * цель ставилась в САМ блок. Поиск пути честно искал дорогу внутрь породы и
     * не находил её — блок непроходим по определению. Со стороны это выглядело
     * как «команда не работает».
     *
     * <p>Правильно — идти не в блок, а к месту рядом с ним. Перебираем соседей
     * по горизонтали и по вертикали, берём тот, где можно стоять и откуда блок
     * в пределах вытянутой руки.
     *
     * @return позиция для ходьбы либо {@code null}, если подойти некуда
     */
    public static BlockPos standPositionNear(Minecraft mc, BlockPos block) {
        if (mc.level == null || mc.player == null) {
            return null;
        }
        // Не копаем блок, на котором стоим.
        //
        // Это была самая дорогая ошибка: ближайшим «подходящим» блоком почти
        // всегда оказывается земля под ногами. Процесс наводился на неё, провал
        // в яму, снова находил землю под ногами — и так без конца. Игрок так не
        // делает: он копает перед собой, а не себе под ноги.
        BlockPos player = mc.player.blockPosition();
        if (block.getX() == player.getX() && block.getZ() == player.getZ()
                && block.getY() < player.getY()) {
            return null;
        }

        // Полоса по высоте: копаем то, что на нашем уровне.
        //
        // Без неё поиск находил пласты земли в пещерах на десять блоков ниже —
        // формально «открытая грань есть», фактически туда нет дороги, и процесс
        // бесконечно перебирал такие блоки.
        int playerY = player.getY();
        if (Math.abs(block.getY() - playerY) > VERTICAL_BAND) {
            return null;
        }

        // Кандидатов перебираем от БЛИЖАЙШЕГО к игроку, а не в фиксированном
        // порядке. Порядок «сверху, потом с боков» приводил к тому, что бот шёл
        // вставать на блок сверху — а туда путь либо длиннее, либо его нет вовсе,
        // хотя сбоку можно подойти в два шага.
        BlockPos[] candidates = {
                block.north(), block.south(), block.east(), block.west(),
                block.above(), block.above(2),
                block.below(),
        };
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (BlockPos candidate : candidates) {
            if (candidate.equals(player)) {
                // Стоять уже стоим — подходить некуда и незачем.
                return player;
            }
            if (!canStandAt(mc, candidate)) {
                continue;
            }
            if (!BlockInteraction.inReachFrom(candidate, block)) {
                continue;
            }
            // Стоянка должна быть на уровне игрока.
            //
            // Подъём на два-три блока — отдельная задача (лестница, обход), и
            // поиск пути на ней либо срывается, либо уводит в обход через
            // полкарты. Отсекаем сразу: если рядом нет места на своём уровне,
            // блок нам не по зубам.
            if (Math.abs(candidate.getY() - playerY) > 1) {
                continue;
            }
            // Высота важнее расстояния по горизонтали.
            //
            // Забраться на три блока вверх — это отдельная задача (нужна лестница
            // или обход), и поиск пути на ней либо срывается, либо строит маршрут
            // в обход через полкарты. Стоянка на своём уровне всегда достижимее,
            // даже если идти до неё на пару шагов дальше.
            int dy = Math.abs(candidate.getY() - playerY);
            double score = dy * 16.0 + candidate.distSqr(player);
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    /**
     * Ближайший подходящий блок, до которого можно дотянуться ПРЯМО СЕЙЧАС.
     *
     * <p>Это то, что делает игрок: копает то, что перед ним, а не строит маршрут
     * к блоку за тридевять земель. Такой блок не требует поиска пути вообще —
     * подошли, навели взгляд, сломали.
     *
     * <p>Проверка видимости здесь обязательна: ломание идёт через клавишу атаки,
     * то есть по блоку под прицелом. Если нужный блок закрыт соседним, ваниль
     * будет ломать соседний, и прогресс не сдвинется.
     *
     * @return позиция блока либо {@code null}, если в руке ничего подходящего нет
     */
    public static BlockPos findInReach(Minecraft mc, Predicate<BlockState> test,
                                       Set<BlockPos> blacklist) {
        if (mc.level == null || mc.player == null) {
            return null;
        }
        BlockPos player = mc.player.blockPosition();
        BlockPos best = null;
        double bestDistSq = Double.MAX_VALUE;

        // Радиус обзора: чуть больше дальности руки, чтобы не пропустить блок на
        // самом её пределе, но и не перебирать полкарты каждый тик.
        int r = (int) Math.ceil(BlockInteraction.REACH) + 1;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos pos = player.offset(dx, dy, dz);
                    if (blacklist != null && blacklist.contains(pos)) {
                        continue;
                    }
                    // Не копаем себе под ноги: это яма под собой, а не работа.
                    if (pos.getX() == player.getX() && pos.getZ() == player.getZ()
                            && pos.getY() < player.getY()) {
                        continue;
                    }
                    if (!BlockInteraction.inReach(mc, pos)) {
                        continue;
                    }
                    if (!test.test(mc.level.getBlockState(pos))) {
                        continue;
                    }
                    // Проверка видимости здесь больше не нужна: прицел мы
                    // задаём сами, а ломание идёт через gameMode напрямую.
                    double distSq = (double) dx * dx + (double) dy * dy + (double) dz * dz;
                    if (distSq < bestDistSq) {
                        bestDistSq = distSq;
                        best = pos;
                    }
                }
            }
        }
        return best;
    }

    /**
     * Насколько выше или ниже игрока может быть блок, чтобы его ещё имело смысл
     * копать.
     *
     * <p>Три блока — это «под ногами» и «над головой». Всё, что дальше, требует
     * либо лестницы, либо раскопа, а этого процесс не умеет; притворяться, что
     * умеет, хуже, чем честно не браться.
     */
    public static final int VERTICAL_BAND = 3;

    /** Можно ли стоять в этой клетке: свободно в ногах и голове, опора снизу. */
    private static boolean canStandAt(Minecraft mc, BlockPos pos) {
        if (mc.level == null) {
            return false;
        }
        if (mc.level.getBlockState(pos).isSolidRender()) {
            return false;
        }
        if (mc.level.getBlockState(pos.above()).isSolidRender()) {
            return false;
        }
        return mc.level.getBlockState(pos.below()).isSolidRender();
    }

    /**
     * Ближайший подходящий блок в кубе вокруг центра.
     *
     * <p>Идём от ближних колец к дальним и останавливаемся на первом найденном:
     * так не нужно обходить весь куб, а результат тот же — ближайший блок.
     *
     * @param radius    радиус поиска в блоках
     * @param test      условие на блок
     * @param blacklist позиции, которые уже пробовали и бросили
     * @return позиция ближайшего подходящего блока либо {@code null}
     */
    public static BlockPos findNearest(Minecraft mc, BlockPos center, int radius,
                                       Predicate<BlockState> test, Set<BlockPos> blacklist) {
        if (mc.level == null) {
            return null;
        }
        int r = Math.min(Math.max(1, radius), MAX_RADIUS);

        // Запоминаем лучший результат по кольцу: внутри одного радиуса порядок
        // обхода произвольный, и брать первый попавшийся — значит иногда идти к
        // дальнему блоку, хотя рядом есть ближний.
        for (int ring = 0; ring <= r; ring++) {
            BlockPos best = null;
            double bestDistSq = Double.MAX_VALUE;

            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    for (int dy = -ring; dy <= ring; dy++) {
                        // Только поверхность кольца: внутренность уже проверена
                        // на предыдущих итерациях.
                        if (Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) != ring) {
                            continue;
                        }
                        BlockPos pos = center.offset(dx, dy, dz);
                        if (blacklist != null && blacklist.contains(pos)) {
                            continue;
                        }
                        BlockState state = mc.level.getBlockState(pos);
                        if (!test.test(state)) {
                            continue;
                        }
                        // Только блоки, к которым можно подойти: у закрытого со
                        // всех сторон блока пути нет и быть не может, а у блока
                        // без места для стоянки рядом — тем более.
                        if (!hasOpenFace(mc.level, pos)) {
                            continue;
                        }
                        if (standPositionNear(mc, pos) == null) {
                            continue;
                        }
                        double distSq = (double) dx * dx + (double) dy * dy + (double) dz * dz;
                        if (distSq < bestDistSq) {
                            bestDistSq = distSq;
                            best = pos;
                        }
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }
}
