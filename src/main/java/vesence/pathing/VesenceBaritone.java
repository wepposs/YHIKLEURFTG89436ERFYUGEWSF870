package vesence.pathing;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import vesence.event.player.EventInput;
import vesence.pathing.goal.Goal;
import vesence.pathing.goal.GoalNear;
import vesence.pathing.goal.GoalXZ;
import vesence.pathing.process.BaritoneProcess;

import java.util.List;

/**
 * Фасад движка ходьбы: то, чем пользуются модули и команды.
 *
 * <p>Держит текущую цель, пошаговый поиск и исполнителя. Тик движка идёт из
 * {@code EventUpdate}, ввод правится из миксина клавиатуры до расчёта движения.
 *
 * <p>Жизненный цикл запроса:
 * <ol>
 *   <li>{@link #setGoalAndPath(Goal)} — цель принята, поиск создан;</li>
 *   <li>каждый тик {@link #tick()} раскрывает порцию узлов;</li>
 *   <li>путь найден → {@link PathExecutor} начинает вести игрока;</li>
 *   <li>путь пройден или застряли → пересчёт от текущей позиции.</li>
 * </ol>
 */
@Environment(EnvType.CLIENT)
public final class VesenceBaritone {

    private static final VesenceBaritone INSTANCE = new VesenceBaritone();

    /**
     * Сколько узлов раскрываем за тик.
     *
     * <p>Прежде здесь было девятьсот, и это было ошибкой: маршрут на сотню
     * блоков требовал шесть секунд поиска, из которых бот стоял на месте.
     * Раскрытие узла — микросекунды (данные блоков кешируются), поэтому четыре
     * тысячи за тик занимают около десяти миллисекунд и не роняют частоту тика,
     * а поиск заканчивается вчетверо быстрее.
     */
    private static final int NODES_PER_TICK = 4_000;

    /** Сколько раз подряд пересчитываем путь, прежде чем сдаться. */
    private static final int MAX_REPATHS = 6;

    /**
     * Насколько близко к цели надо подойти, чтобы считать пересчёт не напрасным.
     *
     * <p>Меньше блока — и любой шаг засчитывался бы как прогресс, включая
     * топтание на месте. Двух блоков хватает, чтобы отличить «реально иду» от
     * «дёргаюсь».
     */
    private static final double PROGRESS_EPSILON = 2.0;

    /** Сколько раз можно ослабить цель (блок → окрестность → только X/Z). */
    private static final int MAX_RELAXATIONS = 3;

    /**
     * Радиус, в котором цель считается достигнутой, если путь уже закончился.
     *
     * <p>Исполнитель останавливается за {@code NODE_REACH} (0.8) до последнего
     * узла, плюс запас на то, что узел мог быть пропущен сторожем. Двух блоков
     * хватает с головой, и при этом это всё ещё «я стою у цели».
     */
    private static final double ARRIVAL_RADIUS = 2.5;

    /**
     * Длина одного перехода при движении к дальней цели «по частям».
     *
     * <p>Девяносто шесть блоков — заведомо внутри загруженной области при
     * минимальной игровой дальности прорисовки (десять чанков — сто шестьдесят
     * блоков). Кусок длиннее не построить: в его конце блоков уже нет, и поиск
     * вернёт пустоту.
     */
    private static final double LEG_DISTANCE = 96.0;

    /**
     * После перехода ближе этого к цели обычный поиск уже справляется сам.
     *
     * <p>Раньше роль порога выполняла и длина перехода, и нижняя граница сразу,
     * из-за чего последний переход выходил нулевой длины: маршрут строился в
     * точку, где мы уже стоим, поиск «доводил» нас до неё же за тик, прибытие
     * требовало следующий переход — бесконечный цикл со спамом в чат.
     */
    private static final double LEG_MIN_DISTANCE = 24.0;

    /**
     * С какого расстояния цель считается дальней.
     *
     * <p>Сто пятьдесят блоков — это почти вся загруженная область при
     * минимальной дальности прорисовки. Цели ближе ищутся обычным поиском
     * напрямую: в них переходы не нужны и только портят работу — на шестидесяти
     * блоках бот дробил маршрут, зря стоял на пересчётах и заваливал чат.
     */
    private static final double FAR_GOAL_DISTANCE = 150.0;

    /** Предел числа переходов: предохранитель от бесконечного «ещё чуть-чуть». */
    private static final int MAX_LEGS = 400;

    private final PathExecutor executor = new PathExecutor();

    private Goal goal;
    private PathFinder finder;
    private int repaths;
    private int relaxes;
    private int ticksWithoutProgress;

    /** Лучшее (наименьшее) расстояние до цели за текущий заход. */
    private double bestGoalDistance = Double.MAX_VALUE;

    private boolean enabled = true;
    private boolean allowSprint = true;
    private int maxFall = 3;
    private int maxNodes = 120_000;

    /** Активный процесс поверх поиска пути (ферма, копание, следование). */
    private BaritoneProcess process;

    /** Пауза: цель и путь сохранены, но движение остановлено. */
    private boolean paused;

    /**
     * Разрешено ли прорубать ход.
     *
     * <p>Включается только после того, как обычный поиск признал, что дороги
     * нет. Держится до отмены: пересчёт маршрута посреди раскопа снова упёрся
     * бы в ту же стену.
     */
    private boolean digging;

    /**
     * О раскопе уже сказали в чат.
     *
     * <p>Без флага получалось вот что: прокоп перестраивается watchdog'ом,
     * сообщение «дороги нет, прорубаю ход» уходит в чат на каждой перестройке —
     * и за секунду набирается седьмой повтор одного и того же.
     */
    private boolean digAnnounced;

    /**
     * Сколько раз подряд можно начать поиск заново, не сдвинувшись с места.
     *
     * <p>Нужен там, где не помог ни поиск, ни переход, ни прямой ход. Сдаваться
     * сразу нельзя: за прошедшие тики игрок сдвинулся и чанки подгрузились, так
     * что та же цель вполне может стать достижимой. Но и повторять вечно нельзя —
     * это тихая остановка без объяснения.
     */
    private static final int MAX_BLIND_RETRIES = 6;

    private int blindRetries;

    /** Когда игрок последний раз реально приближался к цели. */
    private long lastProgressAt = System.currentTimeMillis();

    /**
     * Предел времени на один расчёт маршрута, в тиках.
     *
     * <p>Три секунды. Дольше искать бессмысленно: за это время пешком можно
     * пройти треть маршрута, а бот всё стоит и перебирает узлы.
     */
    private static final int SEARCH_MAX_TICKS = 60;

    private int searchTicks;

    /**
     * Настоящая цель, когда идём к ней по частям.
     *
     * <p>Клиент видит мир только вокруг игрока — за пределами загруженных чанков
     * блоков нет, и A* там честно разводит руками. Игрок в такой ситуации не
     * сдаётся: он идёт в сторону цели, пока не подгрузится следующий участок.
     * Здесь хранится конечная точка, а {@code goal} на время становится
     * промежуточной.
     */
    private Goal realGoal;

    /** Сколько шагов «по частям» уже сделано — предохранитель от вечного пути. */
    private int legs;

    /**
     * Где мы стояли, когда промежуточный переход провалился.
     *
     * <p>Нужно, чтобы отличить «участок не выдержал рельефа, пробуем от нового
     * места» от «стоим на месте, и следующий участок провалится точно так же».
     * Без этого проверка уходила бы в цикл: провал → новый участок → провал, и
     * так до предела переходов, с пересозданием кеша мира каждый раз.
     */
    private BlockPos lastLegFailPos;

    private VesenceBaritone() {
    }

    public static VesenceBaritone get() {
        return INSTANCE;
    }

    // ── Настройки ─────────────────────────────────────────────────────────────

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            this.cancel();
        }
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setAllowSprint(boolean allowSprint) {
        this.allowSprint = allowSprint;
    }

    public boolean isAllowSprint() {
        return this.allowSprint;
    }

    public void setMaxFall(int maxFall) {
        this.maxFall = Math.max(1, Math.min(8, maxFall));
    }

    public int getMaxFall() {
        return this.maxFall;
    }

    public void setMaxNodes(int maxNodes) {
        this.maxNodes = Math.max(5_000, maxNodes);
    }

    // ── Запросы пути ──────────────────────────────────────────────────────────

    public void setGoalAndPath(BlockPos pos) {
        // Цепочка от точного попадания к всё более мягкому: игрок просит встать
        // в конкретный блок, но он может быть занят или внутри стены.
        //
        // Начинаем с радиуса 2, а не 1: «дойди до этого блока» для игрока значит
        // «подойди к нему», а не «встань ровно в него». Радиус 1 на неровном
        // рельефе почти всегда недостижим, и запрос зря проходил через все
        // ослабления, прежде чем найти путь.
        //
        // Последний вариант — колонка с окном по высоте, а НЕ просто X/Z. Просто
        // X/Z выполнено уже в момент запроса, если игрок стоит над целью: поиск
        // мгновенно рапортует об успехе, бот отвечает «пришёл», не сдвинувшись.
        // Именно так ломался спуск в шахту.
        this.setGoalAndPath(java.util.List.of(
                new GoalNear(pos, 2),
                new GoalNear(pos, 4),
                GoalXZ.column(pos.getX(), pos.getZ(), pos.getY(), 6)));
    }

    public void setGoalAndPath(Goal goal) {
        this.goal = goal;
        this.realGoal = goal;
        this.legs = 0;
        this.repaths = 0;
        this.relaxes = 0;
        this.ticksWithoutProgress = 0;
        this.bestGoalDistance = Double.MAX_VALUE;
        this.executor.clear();

        // Дальняя цель — сразу идём к ней по частям, не тратя тики на поиск по
        // пустым чанкам. Иначе бот секунды стоит, «ищет путь» там, где блоков
        // ещё нет, и только потом начинает двигаться.
        Minecraft mc = Minecraft.getInstance();
        if (this.isFarGoal(mc) && this.stepTowardGoal(mc)) {
            return;
        }
        this.startSearch();
    }

    /**
     * То же, но с запасными целями.
     *
     * <p>Точка «прийти в блок» часто недостижима буквально: игрок просит встать
     * в блок, который уже занят, или внутрь стены. Тогда пробуем всё более
     * широкие варианты, а не сдаёмся сразу.
     */
    public void setGoalAndPath(List<Goal> chain) {
        this.goalChain = new java.util.ArrayDeque<>(chain);
        if (this.goalChain.isEmpty()) {
            this.cancel();
            return;
        }
        this.setGoalAndPath(this.goalChain.pollFirst());
    }

    private java.util.ArrayDeque<Goal> goalChain = new java.util.ArrayDeque<>();

    private void startSearch() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || this.goal == null) {
            this.finder = null;
            return;
        }
        BlockWorld world = new BlockWorld(mc.level);
        this.finder = new PathFinder(world, this.goal, this.maxFall, this.maxNodes,
                mc.player.blockPosition());
    }

    /** Отменить всё: и поиск, и движение. */
    public void cancel() {
        this.goal = null;
        this.finder = null;
        this.executor.clear();
        this.repaths = 0;
        this.relaxes = 0;
        this.ticksWithoutProgress = 0;
        this.bestGoalDistance = Double.MAX_VALUE;
        this.goalChain.clear();
        this.digging = false;
        this.digAnnounced = false;
        this.lastProgressAt = System.currentTimeMillis();
        this.searchTicks = 0;
        this.realGoal = null;
        this.legs = 0;
        this.lastLegFailPos = null;
    }

    /**
     * Отменить вообще всё, включая активный процесс.
     *
     * <p>Отдельно от {@link #cancel()} намеренно: процессы сами вызывают
     * {@code cancel()}, чтобы остановить ходьбу перед ломанием блока. Если бы
     * {@code cancel()} снимал ещё и процесс, ферма убивала бы себя на первом же
     * созревшем блоке.
     */
    public void cancelEverything() {
        this.clearProcess();
        this.paused = false;
        this.cancel();
    }

    // ── Процессы ──────────────────────────────────────────────────────────────

    /**
     * Запускает процесс. Предыдущий снимается: одновременно активен только один.
     */
    public void setProcess(BaritoneProcess next) {
        this.clearProcess();
        this.cancel();
        this.paused = false;
        this.process = next;
    }

    public void clearProcess() {
        if (this.process != null) {
            this.process.cancel();
            this.process = null;
        }
    }

    public BaritoneProcess process() {
        return this.process;
    }

    // ── Пауза ─────────────────────────────────────────────────────────────────

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public boolean isPaused() {
        return this.paused;
    }

    public boolean isPathing() {
        return this.goal != null;
    }

    public boolean hasPath() {
        return this.executor.path() != null && !this.executor.path().finished();
    }

    public Goal goal() {
        return this.goal;
    }

    public PathExecutor executor() {
        return this.executor;
    }

    public Path currentPath() {
        return this.executor.path();
    }

    // ── Тик движка ────────────────────────────────────────────────────────────

    /**
     * Вызывается каждый тик клиента. Продвигает поиск и следит за прогрессом.
     */
    public void tick() {
        if (!this.enabled) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            this.cancelEverything();
            return;
        }

        // 1) Процесс (ферма, копание, следование) решает, куда идти.
        //
        // Он НЕ заменяет поиск пути, а только ставит ему цель. Поэтому после
        // него управление идёт дальше: без этого поиск не продвигался бы ни на
        // узел, и процесс вечно ждал бы путь, который никто не считает.
        if (this.process != null) {
            if (!this.process.isActive()) {
                this.process = null;
            } else {
                this.process.tick();
            }
        }

        // Пауза: цель и путь сохранены, но ничего не двигается. Игрок может
        // спокойно осмотреться и продолжить с того же места.
        if (this.paused) {
            return;
        }

        if (this.goal == null) {
            return;
        }

        if (this.finder != null) {
            PathFinder.Status status = this.finder.step(NODES_PER_TICK);

            // Предел времени на один расчёт.
            //
            // Счётчик узлов не всегда спасает: на большом открытом участке поиск
            // честно перебирает десятки тысяч узлов, и всё это время бот стоит.
            // Три секунды — уже неприлично долго для маршрута; если за это время
            // путь не нашёлся, значит выбранная цель плоха, и надо переходить к
            // запасной, а не ждать.
            if (status == PathFinder.Status.RUNNING) {
                if (++this.searchTicks > SEARCH_MAX_TICKS) {
                    System.out.println("[baritone] поиск идёт слишком долго, узлов "
                            + this.finder.expanded() + " — перехожу к запасной цели");
                    this.searchTicks = 0;
                    this.finder = null;
                    status = PathFinder.Status.FAILED;
                }
            } else {
                this.searchTicks = 0;
            }

            if (status == PathFinder.Status.SUCCESS) {
                Path path = this.finder.result();
                this.finder = null;
                if (path != null && !path.isEmpty()) {
                    System.out.println("[baritone] путь найден: узлов " + path.nodes.size()
                            + " к " + (this.goal == null ? "?" : this.goal.describe()));
                    this.executor.setPath(path);
                    this.ticksWithoutProgress = 0;
                } else {
                    this.fail("путь пуст");
                }
            } else if (status == PathFinder.Status.FAILED) {
                boolean limited = true;
                int expanded = 0;
                if (this.finder != null) {
                    limited = this.finder.limitHit();
                    expanded = this.finder.expanded();
                    System.out.println("[baritone] поиск не нашёл путь: узлов " + expanded
                            + (limited ? " (упёрся в предел)" : " (граф исчерпан)"));
                }
                this.finder = null;
                // Есть запасная цель — пробуем её, прежде чем сдаваться.
                if (!this.goalChain.isEmpty()) {
                    Goal next = this.goalChain.pollFirst();
                    if (next != null) {
                        System.out.println("[baritone] fallback goal: " + next.describe());
                        this.goal = next;
                        // Запасная цель — другая точка, значит и мерить прогресс
                        // надо заново, иначе счётчик неудач унаследует чужой.
                        this.bestGoalDistance = Double.MAX_VALUE;
                        this.repaths = 0;
                        this.startSearch();
                        return;
                    }
                }
                // Цепочки нет, но цель можно ослабить: точный блок → окрестность
                // → только X/Z. Без этого запрос «иди в этот блок» на неровном
                // рельефе обрывался бы неудачей, хотя идти совершенно можно.
                Goal softer = this.goal == null ? null : this.goal.relaxed();
                if (softer != null && this.relaxes < MAX_RELAXATIONS) {
                    this.relaxes++;
                    System.out.println("[baritone] relax goal: " + softer.describe());
                    this.goal = softer;
                    this.bestGoalDistance = Double.MAX_VALUE;
                    this.repaths = 0;
                    this.startSearch();
                    return;
                }
                // Дальняя цель — сразу идём к ней по частям.
                //
                // Прорубаться на двести блоков сквозь невидимую границу
                // загруженных чанков бессмысленно: там нет блоков, которые можно
                // копать, есть только пустота. Причина неудачи не в стене, а в
                // расстоянии, и лечится она переходом, а не киркой.
                if (this.isFarGoal(mc) && this.stepTowardGoal(mc)) {
                    return;
                }

                // Последняя попытка — прорубить ход ступеньками.
                //
                // Игрок, которому надо на верхушку скалы или в пещеру под собой,
                // не разворачивается со словами «пути нет»: он копает наклонный
                // ход. Обычный поиск копание не рассматривает вообще — иначе бот
                // предпочёл бы прокопаться сквозь бугор вместо того, чтобы его
                // обойти. Поэтому прокоп включается только здесь, когда всё
                // остальное уже отказало.
                if (!this.digging && this.startDig(mc)) {
                    return;
                }

                // Совсем не вышло — идём к цели по частям.
                //
                // Дальняя цель почти всегда «недостижима» по одной причине:
                // чанки за спиной игрока выгружены, и в них нет блоков, по
                // которым можно строить маршрут. Игрок не сдаётся — он бежит в
                // ту сторону, пока мир не подгрузится. Здесь то же самое:
                // берём промежуточную точку на пути к настоящей цели.
                if (this.stepTowardGoal(mc)) {
                    return;
                }

                // Последнее средство — прямой ход.
                //
                // Раньше здесь стояло «путь не найден, остановился», и это было
                // худшее из возможных поведений: игрок просил идти, а бот вставал
                // и отчитывался о неудаче. Живой игрок в такой ситуации не
                // останавливается — он идёт в нужную сторону, переплывает воду,
                // перепрыгивает бугры, а если совсем стена — прорубается.
                //
                // Направление известно заранее, поэтому прямой ход строится
                // мгновенно и без перебора. Пусть он не самый короткий: его дело —
                // двигать игрока к цели, пока не подгрузятся чанки и обычный поиск
                // снова не сможет построить нормальный маршрут.
                if (this.startDirect(mc)) {
                    return;
                }

                // Не вышло даже это. Не сдаёмся, а начинаем поиск заново: за
                // прошедшие тики игрок сдвинулся, чанки подгрузились, и та же
                // цель вполне может стать достижимой.
                if (++this.blindRetries <= MAX_BLIND_RETRIES) {
                    this.relaxes = 0;
                    this.startSearch();
                    return;
                }
                this.blindRetries = 0;
                this.fail(limited ? "цель недостижима, лимит поиска" : "цель недостижима");
            }
            return;
        }

        // A finished (including single-node) path cannot receive input anymore.
        // Handle completion here rather than waiting for apply() to request it.
        if (this.executor.path() != null && this.executor.path().finished()) {
            if (this.goalReached(mc)) this.arrive();
            else this.repath();
            return;
        }

        // Путь есть — исполнитель ведёт игрока, мы только следим за пересчётом.
        if (this.executor.needsRepath()) {
            this.executor.acknowledgeRepath();
            if (this.goalReached(mc)) {
                this.arrive();
                return;
            }
            this.repath();
            return;
        }

        if (this.executor.path() != null && !this.executor.path().finished()) {
            if (this.executor.humanizer().isBlocked()) {
                this.ticksWithoutProgress++;
            } else {
                this.ticksWithoutProgress = 0;
            }
            if (this.ticksWithoutProgress > 60) {
                this.ticksWithoutProgress = 0;
                this.repath();
            }
        }
    }

    private boolean goalReached(Minecraft mc) {
        if (this.goal == null || mc.player == null) {
            return false;
        }
        BlockPos pos = BlockPos.containing(mc.player.position());
        if (this.goal.isInGoal(pos)) {
            return true;
        }
        // Путь закончился, но строгая проверка цели не прошла.
        //
        // Так бывает почти всегда, когда просят «встань в этот блок»: цель —
        // сфера в блочных координатах, а исполнитель останавливается за
        // NODE_REACH до узла. Формально мы в блоке не стоим, фактически —
        // стоим вплотную к нему. Раньше это приводило к худшему из возможных
        // сообщений: игрок видел «не удалось дойти», стоя в двух шагах от цели.
        //
        // У процесса послабления нет намеренно: ему надо встать в конкретную
        // клетку, чтобы дотянуться до блока. «Почти дошёл» для него значит «не
        // достал», и лучше пусть подойдёт точнее, чем возьмётся за новый блок.
        if (this.process != null) {
            return false;
        }
        return this.arrivedAtPathEnd(mc);
    }

    /**
     * Дошли ли до конца пройденного пути.
     *
     * <p>Прямой ход и прокоп могут закончиться раньше цели. Послабление
     * расстояния допустимо только если последний узел действительно входит
     * в текущую цель; конец частичного пути требует пересчёта, не прибытия.
     */
    private boolean arrivedAtPathEnd(Minecraft mc) {
        Path path = this.currentPath();
        if (path == null || mc.player == null) {
            return false;
        }
        PathNode last = path.last();
        if (last == null || this.goal == null || !this.goal.isInGoal(last.pos())) {
            return false;
        }
        double dx = mc.player.getX() - last.centerX();
        double dy = mc.player.getY() - last.y;
        double dz = mc.player.getZ() - last.centerZ();
        return dx * dx + dz * dz <= ARRIVAL_RADIUS * ARRIVAL_RADIUS && Math.abs(dy) <= 2.0;
    }

    private void arrive() {
        // У процесса прибытие — не конец работы, а её следующий шаг: он подошёл к
        // блоку и сейчас начнёт его ломать. Если здесь снять процесс и написать
        // «пришёл», ферма обрывалась бы после первого же подхода к первому
        // блоку — и выглядела бы полностью сломанной.
        if (this.process != null) {
            this.cancel();
            return;
        }

        // Дошли до промежуточной точки, а настоящая цель ещё впереди: это не
        // прибытие, а конец одного перехода. Молча берём следующий.
        Minecraft mc = Minecraft.getInstance();
        if (this.realGoal != null && mc.player != null
                && !this.realGoal.isInGoal(BlockPos.containing(mc.player.position()))) {
            if (this.stepTowardGoal(mc)) {
                return;
            }
            // Дальше переходы не нужны — до цели рукой подать, и обычный поиск
            // дотащит. Возвращаем настоящую цель и молча идём дальше: рапортовать
            // «пришёл» в середине пути нельзя.
            this.resumeRealGoal();
            return;
        }

        BaritoneMessages.success("пришёл");
        this.cancelEverything();
    }

    /**
     * Вернуть настоящую цель после промежуточного перехода.
     *
     * <p>Отдельный метод, а не присваивание в двух местах: здесь важно, что
     * настоящая цель и счётчик переходов НЕ сбрасываются, — иначе вся история
     * «идём по частям» начиналась бы заново на каждом переходе.
     */
    private void resumeRealGoal() {
        this.goal = this.realGoal;
        this.repaths = 0;
        this.relaxes = 0;
        this.ticksWithoutProgress = 0;
        this.bestGoalDistance = Double.MAX_VALUE;
        this.executor.clear();
        this.startSearch();
    }

    /**
     * Прорубить ход ступеньками к цели на другой высоте.
     *
     * <p>Не поиск, а построение: направление известно, остаётся проверить, что
     * очередной блок поддаётся. Поэтому это делается мгновенно, в отличие от
     * попытки отдать копание на откуп A* — тот перебирал сто двадцать тысяч
     * узлов и не находил ничего.
     *
     * @return {@code true}, если ход построен и исполнитель уже ведёт по нему
     */
    private boolean startDig(Minecraft mc) {
        if (this.realGoal == null || mc.player == null || mc.level == null) {
            return false;
        }
        BlockPos anchor = this.realGoal.anchor();
        if (anchor == null) {
            return false;
        }

        BlockPos start = mc.player.blockPosition();
        List<PathNode> nodes = DigPlanner.plan(new BlockWorld(mc.level), start, anchor);
        if (nodes == null) {
            return false;
        }

        this.digging = true;
        this.relaxes = 0;
        this.bestGoalDistance = Double.MAX_VALUE;
        this.repaths = 0;
        System.out.println("[baritone] прямой дороги нет, прорубаю ход: "
                + nodes.size() + " шагов");
        // В чат — один раз на всю затею. Прокоп перестраивается: watchdog сбрасывает
        // путь, поиск снова ничего не находит, прокоп строится заново. Это нормальный
        // ход работы, а не новое событие, и рапортовать о нём по кругу не нужно.
        if (!this.digAnnounced) {
            this.digAnnounced = true;
            BaritoneMessages.info("дороги нет, прорубаю ход");
        }

        this.executor.setPath(new Path(nodes, this.goal, nodes.size()));
        return true;
    }

    /**
     * Прямой ход к цели — когда ни поиск, ни переходы не помогли.
     *
     * <p>Это последнее средство, и оно обязано сработать почти всегда: копать
     * можно в любую сторону, а вода не обходится, а переплывается. Отказ
     * возможен только в стену из неразрушимого, и тогда остаётся повторить поиск
     * — игрок за это время сдвинулся и мир подгрузился.
     *
     * @return {@code true}, если ход построен и исполнитель уже ведёт по нему
     */
    private boolean startDirect(Minecraft mc) {
        if (this.realGoal == null || mc.player == null || mc.level == null) {
            return false;
        }
        BlockPos anchor = this.realGoal.anchor();
        if (anchor == null) {
            return false;
        }

        BlockPos start = mc.player.blockPosition();
        // Direct fallback must obey the same configured fall limit as A*;
        // it is not allowed to manufacture a ten-block FALL node.
        List<PathNode> nodes = DirectPlanner.plan(new BlockWorld(mc.level), start, anchor, this.maxFall);
        if (nodes == null || nodes.size() < 2) {
            return false;
        }

        this.blindRetries = 0;
        this.relaxes = 0;
        this.bestGoalDistance = Double.MAX_VALUE;
        this.repaths = 0;
        System.out.println("[baritone] иду напрямую: шагов " + nodes.size() + " к " + anchor);
        this.executor.setPath(new Path(nodes, this.goal, nodes.size()));
        return true;
    }

    /**
     * Настоящая цель дальше {@link #FAR_GOAL_DISTANCE} — значит, она за границей
     * загруженных чанков, и обычный поиск до неё в принципе не дойдёт.
     */
    private boolean isFarGoal(Minecraft mc) {
        if (this.realGoal == null || mc.player == null) {
            return false;
        }
        BlockPos anchor = this.realGoal.anchor();
        if (anchor == null) {
            return false;
        }
        return Math.sqrt(mc.player.blockPosition().distSqr(anchor)) > FAR_GOAL_DISTANCE;
    }

    /**
     * Переход к цели «по частям» — по одному участку за раз.
     *
     * <p>Зачем это нужно. Клиент видит только загруженные чанки; дальше блоков
     * просто нет, и поиск пути там честно не находит ничего. Раньше это
     * выглядело так: просишь идти на двести блоков — бот стоит и говорит «путь
     * не найден», хотя идти совершенно можно. Живой игрок в такой ситуации
     * просто бежит в нужную сторону, пока мир не подгрузится.
     *
     * <p>Здесь то же самое: берём точку на луче к настоящей цели, в пределах
     * гарантированно загруженной области, и идём к ней. Дойдя, берём следующую.
     *
     * @return {@code true}, если новый участок начат
     */
    private boolean stepTowardGoal(Minecraft mc) {
        if (this.realGoal == null || mc.player == null) {
            return false;
        }
        BlockPos anchor = this.realGoal.anchor();
        if (anchor == null) {
            return false;
        }

        BlockPos here = BlockPos.containing(mc.player.position());
        double dx = anchor.getX() - here.getX();
        double dz = anchor.getZ() - here.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);

        // Ближе порога переходы не нужны: область уже загружена, и обычный
        // поиск справится сам. Прежде порог был вдвое ниже, и получалось
        // вот что: до цели оставалось двадцать четыре блока, переход
        // «строился» в точку, где мы уже стоим, поиск мгновенно «доводил» нас
        // до неё же, прибытие звало следующий переход — и так по кругу, с
        // сообщением в чат на каждом витке.
        if (distance < FAR_GOAL_DISTANCE) {
            return false;
        }
        // Предохранитель от вечного «ещё чуть-чуть»: участков не может быть
        // больше, чем нужно, чтобы пройти всё расстояние с запасом.
        if (this.legs >= MAX_LEGS) {
            return false;
        }

        double step = Math.min(LEG_DISTANCE, distance - LEG_MIN_DISTANCE);
        int tx = here.getX() + (int) Math.round(dx / distance * step);
        int tz = here.getZ() + (int) Math.round(dz / distance * step);

        boolean first = this.legs == 0;
        this.legs++;
        System.out.println("[baritone] leg " + this.legs + ": " + tx + " " + tz
                + " (до цели " + (int) distance + " м)");
        // В чат — один раз, в начале пути. Дальше каждое сообщение только шум:
        // игрок и так видит, что бот идёт.
        if (first) {
            BaritoneMessages.info("цель далеко, иду по частям");
        }
        this.startLeg(new GoalXZ(tx, tz));
        return true;
    }

    /**
     * Начать промежуточный переход, не трогая настоящую цель.
     *
     * <p>Отдельно от {@link #setGoalAndPath(Goal)} намеренно: тот запоминает
     * переданную цель как настоящую, и переход «по частям» затирал бы конечную
     * точку первой же промежуточной — бот останавливался бы в сорока блоках от
     * того, куда его послали, и рапортовал «пришёл».
     */
    private void startLeg(Goal leg) {
        this.goal = leg;
        this.repaths = 0;
        this.relaxes = 0;
        this.ticksWithoutProgress = 0;
        this.bestGoalDistance = Double.MAX_VALUE;
        this.executor.clear();
        this.startSearch();
    }

    private void repath() {
        if (this.goal == null) {
            return;
        }

        // Прокоп закончился (или сорвался) — дальше пробуем обычный маршрут.
        //
        // Сбрасывать флаг обязательно: иначе после одного раскопа движок до
        // конца захода считал бы копание уже опробованным и на новой высоте
        // отказывался бы копать снова.
        this.digging = false;

        // Пересчёт засчитывается только если он не приблизил нас к цели.
        //
        // Раньше счётчик рос на каждом пересчёте подряд, и бот сдавался, успев
        // подойти к цели на пять блоков: спуск в овраг требует нескольких
        // пересчётов, но это движение ВПЕРЁД, а не топтание. Живой игрок в такой
        // ситуации не бросает дело.
        Minecraft mc = Minecraft.getInstance();
        double distance = this.distanceToGoal(mc);
        boolean progressed = distance < this.bestGoalDistance - PROGRESS_EPSILON;
        if (progressed) {
            this.bestGoalDistance = distance;
            this.repaths = 0;
            this.lastProgressAt = System.currentTimeMillis();
        } else {
            this.repaths++;
        }

        // Сдаёмся не по счёту пересчётов, а по времени без движения вперёд.
        //
        // Счёт был жестоким: шесть пересчётов подряд набирались за две секунды —
        // маршрут на восемьдесят блоков просто не успевал начаться, а бот уже
        // объявлял «не удалось дойти» и вставал. Живой игрок так не делает: он
        // даёт себе время дойти, а сдаётся, только если реально застрял.
        //
        // Теперь пересчёт — это просто пересчёт. Провал объявляется, если их
        // набралось много И с последнего настоящего продвижения прошло не меньше
        // десяти секунд. За это время любой честный маршрут успевает сдвинуть
        // игрока с места хотя бы на пару блоков.
        boolean stuckForLong = System.currentTimeMillis() - this.lastProgressAt > GIVE_UP_AFTER_MS;
        System.out.println("[baritone] пересчёт №" + this.repaths + ", цель "
                + (this.goal == null ? "?" : this.goal.describe())
                + ", до цели " + String.format("%.1f", distance)
                + ", поз=" + (mc.player == null ? "?" : String.format("%.1f/%.1f/%.1f",
                        mc.player.getX(), mc.player.getY(), mc.player.getZ()))
                + ", путь=" + (this.executor.path() == null ? "нет"
                        : (this.executor.path().size() + "/" + this.executor.path().index))
                + ", конец=" + (this.executor.path() == null ? "?"
                        : String.valueOf(this.executor.path().last())));
        if (this.repaths > MAX_REPATHS && stuckForLong) {
            this.fail("не удалось дойти");
            return;
        }
        this.executor.clear();
        this.startSearch();
    }

    /**
     * Сколько времени без продвижения к цели терпим, прежде чем признать неудачу.
     *
     * <p>Десять секунд — с большим запасом: даже медленный подъём в гору даёт
     * продвижение чаще, чем раз в десять секунд.
     */
    private static final long GIVE_UP_AFTER_MS = 10_000L;

    /** Расстояние от игрока до ближайшей точки цели. */
    private double distanceToGoal(Minecraft mc) {
        if (this.goal == null || mc.player == null) {
            return Double.MAX_VALUE;
        }
        BlockPos pos = BlockPos.containing(mc.player.position());
        return this.goal.heuristic(pos.getX(), pos.getY(), pos.getZ());
    }

    private void fail(String reason) {
        // У процесса свой план на неудачу: вычеркнуть блок и взяться за
        // следующий. Сообщать «остановился» и снимать его здесь нельзя — ферма
        // умирала бы на первом недостижимом блоке.
        if (this.process != null) {
            this.process.onPathFailed();
            this.cancel();
            return;
        }

        // Не сдаёмся на полпути к дальней цели.
        //
        // Промежуточный переход может упереться в реку или овраг, и это не повод
        // объявлять провал: настоящая цель всё ещё впереди. Берём следующий
        // переход уже от нового места — маршрут из другой точки часто находится.
        // Число переходов ограничено, поэтому бесконечного «ещё чуть-чуть» нет.
        if (this.realGoal != null && this.legs < MAX_LEGS) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                BlockPos here = BlockPos.containing(mc.player.position());
                boolean moved = this.lastLegFailPos == null || !this.lastLegFailPos.equals(here);
                if (moved) {
                    this.lastLegFailPos = here;
                    if (this.stepTowardGoal(mc)) {
                        System.out.println("[baritone] leg: " + reason + " — беру следующий участок");
                        return;
                    }
                }
            }
        }

        // Дублируем в stdout: сообщение в чат не попадает в лог, и «почему бот
        // встал» иначе невозможно отличить от «бот дошёл».
        System.out.println("[baritone] stop: " + reason);
        BaritoneMessages.error(reason + ", остановился.");
        this.cancel();
    }

    // ── Ввод ──────────────────────────────────────────────────────────────────

    /**
     * Правка ввода игрока. Вызывается из миксина клавиатуры.
     *
     * @return {@code true}, если движок забрал управление движением
     */
    public boolean onInput(EventInput event) {
        if (!this.enabled || this.paused || this.goal == null) {
            return false;
        }
        if (!this.hasPath()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.screen() != null) {
            return false;
        }

        this.executor.setSprintAllowed(this.allowSprint);
        this.executor.apply(event);
        return true;
    }
}
