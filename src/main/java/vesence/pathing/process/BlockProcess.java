package vesence.pathing.process;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import vesence.pathing.BaritoneMessages;
import vesence.pathing.BlockInteraction;
import vesence.pathing.ToolSelector;
import vesence.pathing.VesenceBaritone;

import java.util.HashSet;
import java.util.Set;

/**
 * Общая логика процессов «найти блок и сломать его».
 *
 * <p>{@code #farm} и {@code #mine} отличаются ровно одним: какие блоки считать
 * подходящими. Всё остальное — поиск ближайшего, подход, ломание, отказ от
 * недостижимых целей — у них одинаковое, и дублировать это значило бы чинить
 * каждый баг дважды.
 */
@Environment(EnvType.CLIENT)
public abstract class BlockProcess implements BaritoneProcess {

    /** Сколько раз пробуем дойти до одного блока, прежде чем бросить его. */
    private static final int MAX_ATTEMPTS = 3;

    /**
     * Сколько тиков терпим ломание одного блока, прежде чем считать его
     * неломаемым.
     *
     * <p>Твёрдый блок вроде обсидиана без нужной кирки не поддаётся вообще, и без
     * этого предела процесс завис бы на нём навсегда. Десять секунд — с запасом
     * больше, чем нужно любому блоку в игре.
     */
    private static final int BREAK_TIMEOUT_TICKS = 200;

    /**
     * Сколько тиков терпим полное отсутствие результата, прежде чем остановиться.
     *
     * <p>Без этого предела процесс мог бесконечно перебирать блоки: нашёл
     * недостижимый, вычеркнул, взял следующий такой же — и так по кругу, потому
     * что подходящих блоков в радиусе сотни. Со стороны это «команда работает
     * вечно и ничего не делает». Двадцать секунд без единого сломанного блока —
     * достаточный повод честно сказать, что дело не идёт.
     */
    private static final int NO_PROGRESS_LIMIT_TICKS = 400;

    /** Диагностика: печатать, что процесс видит и делает. */
    private static final boolean DEBUG = System.getenv("VESENCE_PATHDEBUG") != null;

    private final int radius;

    /** Блоки, до которых дойти не удалось — чтобы не зацикливаться на них. */
    private final Set<BlockPos> blacklist = new HashSet<>();

    private BlockPos target;
    private int attempts;
    private boolean finished;

    /** Уже ломаем этот блок — значит путь ставить не надо. */
    private boolean breaking;
    private int breakTicks;

    /** Сколько блоков уже сломано за этот запуск — для честного итога. */
    private int broken;

    /**
     * Тиков подряд без продвижения.
     *
     * <p>Продвижением считается либо сломанный блок, либо смещение игрока хотя бы
     * на полблока. Именно это отличает «иду к блоку» от «стою и ничего не делаю»,
     * и никакой другой признак тут не годится: поход к блоку за тридцать блоков
     * занимает больше времени, чем любой разумный таймаут, но это работа, а не
     * простой.
     */
    private int idleTicks;

    /** Позиция, с которой сравниваем смещение, — для подсчёта продвижения. */
    private net.minecraft.world.phys.Vec3 lastProgressPos;

    /** Сколько блоков было сломано на момент последней проверки. */
    private int lastProgressBroken;

    protected BlockProcess(int radius) {
        this.radius = radius;
    }

    /** Подходит ли блок. */
    protected abstract boolean matches(BlockState state);

    /** Что сказать, когда работы больше нет. */
    protected abstract String doneMessage(int broken);

    /** Что сказать, когда работа встала, не принеся ни одного блока. */
    protected abstract String stalledMessage(int broken);

    @Override
    public boolean isActive() {
        return !this.finished;
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            this.finished = true;
            return;
        }

        // Предел бесплодной работы.
        //
        // Провал означает «не продвигаюсь», а не «ещё не дошёл». Раньше счётчик
        // рос в каждом тике и обнулялся только при удачном ударе, поэтому
        // честный поход к блоку за двадцать секунд объявлялся провалом: бот шёл,
        // спускался, приближался — и на полпути получал «не смог подойти».
        if (this.hasProgressed(mc)) {
            this.idleTicks = 0;
        } else if (++this.idleTicks > NO_PROGRESS_LIMIT_TICKS) {
            this.finished = true;
            BlockInteraction.stop(mc);
            BaritoneMessages.error(this.stalledMessage(this.broken));
            return;
        }

        // Блок мог исчезнуть сам: его сломал другой игрок, его убрал сервер.
        // Тогда прежняя цель недействительна.
        if (this.target != null && !this.matches(mc.level.getBlockState(this.target))) {
            this.dropTarget();
        }

        if (this.target == null) {
            // Сначала смотрим, что можно сломать прямо сейчас, не сходя с места.
            //
            // Это и есть поведение игрока: он копает то, что перед ним. Поиск
            // пути к дальнему блоку — запасной вариант, а не основной: маршрут
            // к блоку в толще породы почти всегда не строится, и попытка идти
            // туда лишь тратит время и забивает чёрный список.
            BlockPos near = BlockScan.findInReach(mc, this::matches, this.blacklist);
            if (near != null) {
                this.target = near;
            } else {
                this.target = BlockScan.findNearest(mc, mc.player.blockPosition(),
                        this.radius, this::matches, this.blacklist);
            }
            this.attempts = 0;
            this.breaking = false;
            if (this.target == null) {
                this.finished = true;
                BlockInteraction.stop(mc);
                BaritoneMessages.success(this.doneMessage(this.broken));
                return;
            }
        }

        if (DEBUG && this.idleTicks % 10 == 0) {
            System.out.println("[proc] " + this.name()
                    + " цель=" + this.target
                    + " ломаю=" + this.breaking
                    + " тиковЛомания=" + this.breakTicks
                    + " сломано=" + this.broken
                    + " isDestroying=" + (mc.gameMode != null && mc.gameMode.isDestroying())
                    + " stage=" + (mc.gameMode == null ? -1 : mc.gameMode.getDestroyStage())
                    + " yaw=" + String.format("%.1f", mc.player.getYRot())
                    + " pitch=" + String.format("%.1f", mc.player.getXRot())
                    + " hit=" + (mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult b ? b.getBlockPos() : String.valueOf(mc.hitResult == null ? "null" : mc.hitResult.getType())));
        }

        if (BlockInteraction.inReach(mc, this.target)) {
            if (DEBUG && !this.breaking) {
                System.out.println("[proc] " + this.name() + " в досягаемости, ломаю " + this.target);
            }
            this.breakTarget(mc);
            return;
        }

        // Блок далеко. Если мы ещё не идём — ставим путь. Если идём, ничего не
        // делаем: поиск и исполнитель уже работают, вмешиваться незачем.
        this.breaking = false;
        VesenceBaritone engine = VesenceBaritone.get();
        if (!engine.isPathing()) {
            this.attempts++;
            if (this.attempts > MAX_ATTEMPTS) {
                // Не дошли за несколько попыток — блок фактически недостижим
                // (в стене, за обрывом, на другом уровне). Берём следующий.
                this.blacklist.add(this.target);
                this.dropTarget();
                return;
            }
            // Идём не В блок, а К блоку: в сам блок дороги нет по определению,
            // он непроходим. Цель — клетка рядом, откуда он достаётся рукой.
            BlockPos stand = BlockScan.standPositionNear(mc, this.target);
            if (stand == null) {
                // Подойти некуда — блок в толще породы или за пределами полосы.
                // Вычёркиваем сразу, не тратя попытки на заведомо провальный поиск.
                this.blacklist.add(this.target);
                this.dropTarget();
                return;
            }
            // Радиус 0 — встать именно в эту клетку. Иначе «почти дошёл» значит
            // «не достал», и процесс снова возьмётся за новый блок.
            engine.setGoalAndPath(new vesence.pathing.goal.GoalNear(stand, 0));
        }
    }

    /**
     * Продвинулся ли процесс с прошлого тика.
     *
     * <p>Два признака: сломан блок или игрок сместился хотя бы на полблока.
     * Смещение важно потому, что путь к дальнему блоку — это десятки секунд
     * честной ходьбы, и по одному лишь счётчику сломанных блоков такая работа
     * выглядела бы простоем.
     */
    private boolean hasProgressed(Minecraft mc) {
        boolean progressed = false;
        if (this.broken != this.lastProgressBroken) {
            this.lastProgressBroken = this.broken;
            progressed = true;
        }
        if (mc.player != null) {
            net.minecraft.world.phys.Vec3 now = mc.player.position();
            if (this.lastProgressPos == null
                    || this.lastProgressPos.distanceToSqr(now) > 0.25) {
                this.lastProgressPos = now;
                progressed = true;
            }
        }
        return progressed;
    }

    /** Ломание блока, до которого уже дошли. */
    private void breakTarget(Minecraft mc) {
        // Путь отменяем: иначе исполнитель продолжит тянуть игрока дальше и
        // будет сбивать прицел, пока мы ломаем. Один раз, а не каждый тик.
        if (!this.breaking) {
            this.breaking = true;
            this.breakTicks = 0;
            VesenceBaritone engine = VesenceBaritone.get();
            if (engine.isPathing()) {
                engine.cancel();
            }
        }

        if (++this.breakTicks > BREAK_TIMEOUT_TICKS) {
            // Не поддался — вычёркиваем. Иначе процесс застрянет навсегда.
            this.blacklist.add(this.target);
            BlockInteraction.stop(mc);
            this.dropTarget();
            return;
        }

        // Инструмент — перед каждым ударом.
        //
        // Игрок не копает камень рукой, если в хотбаре лежит кирка. Без этого
        // руда ломалась бы втрое дольше, а обсидиан — не ломался бы вовсе, и
        // процесс выглядел бы сломанным, хотя делает всё «правильно».
        ToolSelector.equipFor(mc, this.target);

        if (BlockInteraction.breakBlock(mc, this.target)) {
            this.broken++;
            // Появился результат — счётчик бесплодных тиков начинается заново.
            this.idleTicks = 0;
            this.dropTarget();
        }
    }

    private void dropTarget() {
        // Отпускаем атаку обязательно.
        //
        // Забыть про это — значит оставить клавишу зажатой без цели: прицел
        // больше никто не наводит, и ваниль начинает ломать всё, что случайно
        // оказалось перед игроком. Снаружи это «бот встал и грызёт булыжник».
        if (this.breaking) {
            BlockInteraction.stop(Minecraft.getInstance());
        }
        this.target = null;
        this.breaking = false;
        this.breakTicks = 0;
        this.attempts = 0;
    }

    @Override
    public void onPathFailed() {
        // Блок недостижим: вычёркиваем и берём следующий. Без этого ферма
        // вставала бы намертво на первом блоке в стене.
        if (this.target != null) {
            this.blacklist.add(this.target);
            this.dropTarget();
        }
    }

    @Override
    public void cancel() {
        BlockInteraction.stop(Minecraft.getInstance());
    }
}
