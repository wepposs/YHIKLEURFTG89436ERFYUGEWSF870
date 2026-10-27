package vesence.pathing;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.behavior.IPathingBehavior;
import baritone.api.event.events.PathEvent;
import baritone.api.event.listener.AbstractGameEventListener;
import baritone.api.pathing.calc.IPath;
import baritone.api.pathing.movement.IMovement;
import baritone.api.pathing.path.IPathExecutor;
import baritone.api.utils.BetterBlockPos;
import baritone.pathing.movement.movements.MovementAscend;
import baritone.pathing.movement.movements.MovementDescend;
import baritone.pathing.movement.movements.MovementDiagonal;
import baritone.pathing.movement.movements.MovementFall;
import baritone.pathing.movement.movements.MovementPillar;
import baritone.pathing.movement.movements.MovementTraverse;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import vesence.event.EventInit;
import vesence.event.EventManager;
import vesence.event.impl.EventUpdate;
import vesence.event.player.EventInput;
import vesence.event.player.EventRotation;
import vesence.event.render.EventRender3D;
import vesence.pathing.goal.Goal;
import vesence.pathing.goal.GoalBlock;
import vesence.pathing.goal.GoalNear;
import vesence.pathing.goal.GoalRunAway;
import vesence.pathing.goal.GoalXZ;
import vesence.pathing.process.BaritoneProcess;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Baritone для клиента: маршрут считает настоящий Baritone, ходит — Vesence.
 *
 * <p>Разделение обязанностей, ради которого всё затевалось:
 *
 * <ul>
 *   <li><b>Мозг — настоящий Baritone</b> (библиотека v1.20.0, LGPL, ветка MC
 *       26.3). Он ищет путь с учётом всего мира: где копать, где спуститься,
 *       где обойти; его поиск доходит туда, куда домашний A* не доходил никогда.
 *       Но ИСПОЛНЯТЬ маршрут ему не даём: как только путь посчитан
 *       ({@code CALC_FINISHED_NOW_EXECUTING}), мы забираем его и гасим
 *       исполнение Baritone в тот же тик — ни одного входа он записать не
 *       успевает.</li>
 *   <li><b>Ноги и голова — кастомное движение Vesence</b>
 *       ({@link PathExecutor}): походка и прыжки пишутся в {@code EventInput},
 *       голова доворачивается пружиной {@link RotationSmoother} покадрово в
 *       {@code EventRotation} (и прямо пишет {@code player.setYRot} — сервер
 *       видит ту же голову, что и глаз), копание — своим {@code applyDig} с
 *       наводкой на центр блока и подбором инструмента. Это тот самый слой,
 *       который клиент оттачивал пять кругов, — теперь он ходит по чужим
 *       честным маршрутам.</li>
 *   <li><b>Словарь движений сужен до того, что исполнитель умеет честно</b>:
 *       ходьба, диагонали по плоскости, подкоп, подъём на блок, спуск,
 *       падение до {@code maxFall}, вода. Паркур, установка блоков, водное
 *       ведро и диагональные подъёмы выключены настройками — Baritone не
 *       закладывает в маршрут то, что исполнитель не сможет пройти, и бот не
 *       падает с чужих акробатических планов.</li>
 *   <li><b>Обвязка — своя</b>: цели и команды (классы {@code vesence.pathing},
 *       {@code #goto}, {@code #mine}), процессы ({@link BaritoneProcess}:
 *       follow, mine, explore, farm), сообщения ({@link BaritoneMessages}),
 *       визуал ({@link PathRenderer}: лента и метка). Чат, уведомления и
 *       рендер Baritone выключены, чтобы в клиенте не было второй личности.</li>
 * </ul>
 *
 * <p>Пересчёт на ходу: исполнитель сам просит новый маршрут
 * ({@link PathExecutor#needsRepath()} — отклонился, упёрся, путь кончился, а
 * цель нет), фасад снова дёргает Baritone, и пока тот считает, старые ноги
 * продолжают идти — бот не замирает столбом между участками. Слепые пересчёты
 * (не приближающие к цели) считаются; после {@value #MAX_BLIND_RETRIES} подряд
 * — честный провал вместо вечного топтания.
 *
 * <p>Экземпляр Baritone создаётся его собственным миксином в клиент Minecraft,
 * поэтому здесь он берётся лениво: {@link #baritone()} повторяет попытку каждый
 * тик, пока не получит готовый экземпляр.
 */
@Environment(EnvType.CLIENT)
public final class VesenceBaritone {

    private static final VesenceBaritone INSTANCE = new VesenceBaritone();

    public static VesenceBaritone get() {
        return INSTANCE;
    }

    private VesenceBaritone() {
    }

    // ── Константы ────────────────────────────────────────────────────────────

    /**
     * «Пришёл»: цель достигнута, если стоим в её пределах ИЛИ путь пройден до
     * конца и до последнего узла не дальше этого. Строгая проверка цели часто
     * не проходит из-за того, что исполнитель останавливается чуть раньше узла.
     */
    private static final double ARRIVAL_RADIUS = 2.5;

    /** Насколько ближе к цели должен привести пересчёт, чтобы считаться прогрессом. */
    private static final double PROGRESS_EPSILON = 2.0;

    /** Сколько слепых пересчётов подряд терпеть до честного провала. */
    private static final int MAX_BLIND_RETRIES = 3;

    // ── Состояние ────────────────────────────────────────────────────────────

    /** Экземпляр Baritone. {@code null}, пока клиент не создан, — берём лениво. */
    private IBaritone baritone;

    /** Включён ли движок. Команды включают его сами перед запуском пути. */
    private boolean enabled = true;

    /** Рисовать ли ленту маршрута ({@code #set showpath}). */
    private boolean showPath = true;

    /** Бегать ли, когда можно ({@code #set sprint}). */
    private boolean allowSprint = true;

    /** Максимальная высота безопасного падения без воды. */
    private int maxFall = 3;

    /**
     * Бюджет узлов поиска. Настоящему Baritone не нужен (он сам управляет
     * своими лимитами), поле живо только ради совместимости {@code #set}.
     */
    private int maxNodes = 60_000;

    /** Активный процесс (следовать, фармить, исследовать). Не более одного. */
    private BaritoneProcess process;

    /**
     * Пауза: цель и путь помнятся, движение остановлено. Ноги просто не
     * получают ввод (обработчики gated), голова не доворачивается — бот
     * замирает там, где стоял, и продолжает с той же точки.
     */
    private boolean paused;

    /** Цель Vesence — для описаний в чате, метки на земле и проверки прихода. */
    private Goal goal;

    /** Та же цель, переведённая на язык Baritone. Именно её он считает. */
    private baritone.api.pathing.goals.Goal translatedGoal;

    /**
     * Цепочка ослаблений цели: когда строгая цель недостижима, пробуем
     * следующую, прежде чем объявить провал. «Встань в блок» → «подойди
     * вплотную» → «дойди до колонки».
     */
    private final ArrayDeque<Goal> goalChain = new ArrayDeque<>();

    /**
     * Baritone прямо сейчас считает маршрут по нашей цели.
     *
     * <p>События {@code CALC_FINISHED}/{@code CALC_FAILED}/{@code AT_GOAL} и
     * кража пути сбрасывают флаг; тик по нему ловит потерю управления
     * (смерть, внешний сброс) — иначе ждали бы путь вечно.
     */
    private boolean goalActive;

    /**
     * Ноги: исполнитель Vesence. Живой — по нему ходят отладочные стенды
     * ({@code PathTest}, {@code DigTest}) и рисуется лента.
     */
    private final PathExecutor executor = new PathExecutor();

    /** Подряд пересчётов, не приблизивших к цели; сбрасывается прогрессом. */
    private int blindRetries;

    /** Лучшее расстояние до цели за заход — прогресс меряется по нему. */
    private double bestGoalDistance = Double.MAX_VALUE;

    /** Лог живых ног уже напечатан — одна строка на сессию, без спама. */
    private boolean legsAnnounced;

    private int dbgTick;  //DBG
    private int dbgInput; //DBG
    private int dbgGate;  //DBG
    private int dbgSquash;   //DBG
    private int dbgCanceled; //DBG

    /** Защита от рекурсии: setGoalAndPath может диспатчить события синхронно. */
    private boolean inStealPath;

    public void init() {
        PathRenderer.init();
        EventManager.register(this);
    }

    // ── Доступ к Baritone ────────────────────────────────────────────────────

    /**
     * Экземпляр Baritone, лениво. Первую удачную попытку сопровождает
     * регистрация слушателя событий и применение настроек.
     *
     * @return {@code null}, если клиент ещё не создан или Baritone не поднялся
     */
    private IBaritone baritone() {
        if (this.baritone != null) {
            return this.baritone;
        }
        IBaritone acquired;
        try {
            acquired = BaritoneAPI.getProvider().getPrimaryBaritone();
        } catch (Throwable t) {
            System.out.println("[baritone] движок недоступен: " + t);
            return null;
        }
        if (acquired == null) {
            return null;
        }
        this.baritone = acquired;
        acquired.getGameEventHandler().registerEventListener(new AbstractGameEventListener() {
            @Override
            public void onPathEvent(PathEvent event) {
                if (event != PathEvent.CANCELED || ++dbgCanceled % 100 == 1) { //DBG
                    System.out.println("[dbg] pathEvent " + event + " goalActive=" + goalActive); //DBG
                } //DBG
                switch (event) {
                    case CALC_FINISHED_NOW_EXECUTING -> {
                        if (goalActive) {
                            stealPath();
                        } else {
                            // Расчёт, которого мы не заказывали: не игнорируем
                            // (Baritone исполнит его сам и украдёт ввод), а гасим.
                            squashStray("чужой расчёт завершился");
                        }
                    }
                    case AT_GOAL -> onArrived();
                    case CALC_FAILED -> onSearchFailed();
                    default -> { }
                }
            }
        });
        applySettings();
        return acquired;
    }

    /**
     * Настройки Baritone под связку «его мозг — наши ноги».
     *
     * <p>Главное здесь — словарь движений. Baritone умеет закладывать в
     * маршрут паркур через пропасти, мосты из поставленных блоков, прыжки на
     * столбах и падение с ведром воды; исполнитель Vesence ходит только
     * честную ходьбу, копку, подъёмы-спуски на блок и падения до
     * {@code maxFall}. Всё, что сверх этого, выключено: маршрут, который
     * нельзя пройти ногами клиента, — это падение в пропасть на ровном месте.
     *
     * <p>Остальное: копать разрешено (ради этого мозг и ставили), чат и
     * уведомления Baritone выключены (пишет только {@link BaritoneMessages}),
     * его рендер выключен (рисует {@link PathRenderer}), античит-совместимость
     * включена.
     */
    private void applySettings() {
        Settings settings = BaritoneAPI.getSettings();

        // Словарь движений — только то, что исполнитель проходит честно.
        settings.allowBreak.value = true;
        settings.allowPlace.value = false;
        settings.allowSprint.value = this.allowSprint;
        settings.allowParkour.value = false;
        settings.allowParkourAscend.value = false;
        settings.sprintAscends.value = false;
        settings.allowDiagonalAscend.value = false;
        settings.allowDiagonalDescend.value = false;
        settings.allowDownward.value = true;
        settings.allowWaterBucketFall.value = false;
        settings.autoTool.value = true;
        settings.allowInventory.value = false;
        settings.antiCheatCompatibility.value = true;
        settings.maxFallHeightNoWater.value = this.maxFall;

        // Голова во время исполнения — наша (пружина исполнителя); Baritone
        // до исполнения не доходит, но его сглаживание всё равно выключаем,
        // чтобы ничего не конкурировало с кастомным движением.
        settings.freeLook.value = false;
        settings.smoothLook.value = false;

        // Чат и уведомления — только наши.
        settings.chatControl.value = false;
        settings.chatDebug.value = false;
        settings.echoCommands.value = false;
        settings.logAsToast.value = false;
        settings.desktopNotifications.value = false;
        settings.notificationOnPathComplete.value = false;
        settings.notificationOnExploreFinished.value = false;
        settings.notificationOnFarmFail.value = false;
        settings.notificationOnBuildFinished.value = false;
        settings.notificationOnMineFail.value = false;

        // Рендер Baritone — выключен, рисуем сами.
        settings.renderPath.value = false;
        settings.renderGoal.value = false;
        settings.renderSelectionBoxes.value = false;
        settings.renderCachedChunks.value = false;

        // Цель протухла (блок сломали) — не бросать маршрут, а пересчитать.
        settings.cancelOnGoalInvalidation.value = false;
    }

    // ── Настройки Vesence ────────────────────────────────────────────────────

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            cancelEverything();
        }
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setAllowSprint(boolean allowSprint) {
        this.allowSprint = allowSprint;
        this.executor.setSprintAllowed(allowSprint);
        if (this.baritone != null) {
            BaritoneAPI.getSettings().allowSprint.value = allowSprint;
        }
    }

    public boolean isAllowSprint() {
        return this.allowSprint;
    }

    public void setMaxFall(int maxFall) {
        this.maxFall = Math.max(1, Math.min(8, maxFall));
        if (this.baritone != null) {
            BaritoneAPI.getSettings().maxFallHeightNoWater.value = this.maxFall;
        }
    }

    public int getMaxFall() {
        return this.maxFall;
    }

    /** Бюджет узлов собственного поиска; настоящим Baritone не используется. */
    public void setMaxNodes(int maxNodes) {
        this.maxNodes = Math.max(5_000, maxNodes);
    }

    public int getMaxNodes() {
        return this.maxNodes;
    }

    /** Рисовать ли ленту маршрута. */
    public void setShowPath(boolean showPath) {
        this.showPath = showPath;
    }

    public boolean isShowPath() {
        return this.showPath;
    }

    /**
     * Живая голова ({@code #set look}): пружина исполнителя доворачивает
     * взгляд ({@code #set look off} оставляет наведение только на копание —
     * не выключено полностью, чтобы бот не перестал попадать по блокам).
     */
    private boolean lookSmoothing = true;

    public void setLookSmoothing(boolean lookSmoothing) {
        this.lookSmoothing = lookSmoothing;
    }

    public boolean isLookSmoothing() {
        return this.lookSmoothing;
    }

    // ── Постановка цели ──────────────────────────────────────────────────────

    /**
     * «Дойди до этого блока».
     *
     * <p>Цепочка ослаблений: сначала подойти вплотную (радиус 2 — «дойди до
     * блока» для игрока значит «встань рядом»), потом мягче (радиус 4), потом
     * колонка с окном по высоте. Baritone сам решает, как дойти: в обход или
     * сквозь стену с подкопом — ослабления нужны только для заведомо
     * недостижимых целей.
     */
    public void setGoalAndPath(BlockPos pos) {
        setGoalAndPath(List.of(
                new GoalNear(pos, 2),
                new GoalNear(pos, 4),
                GoalXZ.column(pos.getX(), pos.getZ(), pos.getY(), 6)));
    }

    public void setGoalAndPath(Goal goal) {
        setGoalAndPath(List.of(goal));
    }

    /**
     * Цепочка целей от строгой к мягкой: первая выдаётся сразу, остальные
     * пробуются по очереди, если поиск до предыдущей провалился.
     */
    public void setGoalAndPath(List<Goal> chain) {
        if (chain == null || chain.isEmpty()) {
            return;
        }
        this.goalChain.clear();
        for (int i = chain.size() - 1; i >= 1; i--) {
            this.goalChain.push(chain.get(i));
        }
        startGoal(chain.get(0));
    }

    /**
     * Переводит цель на язык Baritone и заказывает расчёт маршрута.
     *
     * <p>Исполнитель гасится: новый заказ — с чистого листа (старые ноги шли
     * в другую точку). Внутренний пересчёт того же направления
     * ({@link #requestRecalc}) путь НЕ снимает — бот продолжает идти, пока
     * Baritone считает замену.
     */
    private void startGoal(Goal goal) {
        IBaritone baritone = baritone();
        if (baritone == null) {
            BaritoneMessages.error("движок ещё не готов, попробуй снова");
            return;
        }
        baritone.api.pathing.goals.Goal translated = translate(goal);
        if (translated == null) {
            fail("не понял цель «" + goal.describe() + "»");
            return;
        }
        this.executor.clear();
        this.goal = goal;
        this.translatedGoal = translated;
        this.paused = false;
        this.goalActive = true;
        this.blindRetries = 0;
        this.bestGoalDistance = Double.MAX_VALUE;
        baritone.getCustomGoalProcess().setGoalAndPath(translated);
    }

    /**
     * Цель Vesence → цель Baritone. Классы совпадают по смыслу один в один,
     * перевод механический.
     *
     * <p>Единственная тонкость — {@link GoalXZ} с окном по высоте: у Baritone
     * колонка высоты не имеет, поэтому окно переводится в шар
     * {@code GoalNear} по центру окна — так «ослабленная» цель не позволяет
     * объявить приход, встав над шахтой вместо спуска в неё.
     *
     * @return {@code null}, если цель неизвестного типа и без якоря
     */
    private static baritone.api.pathing.goals.Goal translate(Goal goal) {
        if (goal instanceof GoalBlock block) {
            return new baritone.api.pathing.goals.GoalBlock(block.x, block.y, block.z);
        }
        if (goal instanceof GoalNear near) {
            return new baritone.api.pathing.goals.GoalNear(
                    new BlockPos(near.x, near.y, near.z), near.radius);
        }
        if (goal instanceof GoalXZ xz) {
            if (!xz.hasHeightWindow()) {
                return new baritone.api.pathing.goals.GoalXZ(xz.x, xz.z);
            }
            int midY = (xz.minY() + xz.maxY()) / 2;
            int radius = Math.max(2, (xz.maxY() - xz.minY()) / 2 + 1);
            return new baritone.api.pathing.goals.GoalNear(
                    new BlockPos(xz.x, midY, xz.z), radius);
        }
        if (goal instanceof GoalRunAway runAway) {
            return new baritone.api.pathing.goals.GoalRunAway(
                    runAway.distance, null, runAway.from.toArray(new BlockPos[0]));
        }
        BlockPos anchor = goal.anchor();
        if (anchor != null) {
            return new baritone.api.pathing.goals.GoalNear(anchor, 2);
        }
        return null;
    }

    // ── Остановка ────────────────────────────────────────────────────────────

    /**
     * Остановить движение и забыть цель. Процесс (если был) остаётся жив:
     * например, {@code #follow} потерял цель из виду — путь снят, но процесс
     * ищет её дальше.
     */
    public void cancel() {
        this.goalActive = false;
        this.goal = null;
        this.translatedGoal = null;
        this.goalChain.clear();
        this.blindRetries = 0;
        this.bestGoalDistance = Double.MAX_VALUE;
        this.executor.clear();
        // baritone() здесь намеренно НЕ зовём: останавливать нечего, если
        // Baritone ещё не поднят, а будить его ради пустого запроса — лишний шум.
        IBaritone baritone = this.baritone;
        if (baritone != null) {
            baritone.getCustomGoalProcess().setGoal(null);
            baritone.getPathingBehavior().cancelEverything();
        }
    }

    /** Полная остановка: снять процесс, сбросить паузу, забыть цель. */
    public void cancelEverything() {
        clearProcess();
        this.paused = false;
        cancel();
    }

    /** Сделать процесс активным. Одновременно может жить только один. */
    public void setProcess(BaritoneProcess next) {
        clearProcess();
        cancel();
        this.paused = false;
        this.process = next;
    }

    private void clearProcess() {
        BaritoneProcess current = this.process;
        if (current == null) {
            return;
        }
        this.process = null;
        current.cancel();
    }

    public BaritoneProcess process() {
        return this.process;
    }

    /** Пауза: ноги не получают ввод, цель и путь помнятся; снятие — продолжение. */
    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public boolean isPaused() {
        return this.paused;
    }

    // ── Состояние пути ───────────────────────────────────────────────────────

    /** Идёт ли выполнение цели. Семантика прежняя: цель поставлена — идём. */
    public boolean isPathing() {
        return this.goal != null;
    }

    /** Есть ли маршрут, по которому ноги уже идут (или вот-вот пойдут). */
    public boolean hasPath() {
        Path path = this.executor.path();
        return (path != null && !path.finished()) || this.goalActive;
    }

    /**
     * Vesence прямо сейчас ведёт маршрут: цель поставлена (идёт расчёт) или
     * ноги уже шагают.
     *
     * <p>Пока это true, Baritone не должен трогать ввод игрока: ноги пишут
     * походку через {@code EventInput}, а он возникает только из ванильного
     * {@code KeyboardInput} (миксин {@code BaritoneInputOverrideMixin} за этим
     * следит).
     */
    public boolean isDriving() {
        return this.enabled && (this.goal != null || this.executor.isActive());
    }

    public Goal goal() {
        return this.goal;
    }

    /** Живой исполнитель: его походка, голова и копание — кастомное движение. */
    public PathExecutor executor() {
        return this.executor;
    }

    /** Текущий маршрут в формате Vesence, либо {@code null}, пока считается. */
    public Path currentPath() {
        return this.executor.path();
    }

    // ── Тик ──────────────────────────────────────────────────────────────────

    /**
     * Вызывается каждый тик клиента: двигает процессы, следит за приходом и
     * застреванием ног, ловит обрыв расчёта.
     */
    private void tick() {
        if (!this.enabled) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            cancelEverything();
            return;
        }

        // 1) Процесс (ферма, следование, исследование) решает, куда идти.
        //    Он не заменяет движение, а только ставит цели — как и раньше.
        BaritoneProcess process = this.process;
        if (process != null) {
            if (!process.isActive()) {
                this.process = null;
                cancel();
                BaritoneMessages.info("процесс " + process.name() + " завершён");
            } else {
                process.tick();
            }
        }

        if (this.paused || this.goal == null) {
            return;
        }

        // Ввод и движение — наши: каждый тик страховочный контроль (см. squashStray).
        guardCustomMovement(mc);

        // 2) Цель достигнута — прибываем. Проверяем ДО isActive(): пустой
        //    украденный путь гасит исполнителя, а игрок уже стоит в цели;
        //    без этой проверки прибытие не сработает и бот замрёт навсегда.
        if (goalReached(mc)) {
            arrive();
            return;
        }

        // 3) Маршрут в наших руках: исполнитель ведёт, мы следим за концом
        //    участка и застреванием. Важно: проверяем наличие пути, а не
        //    isActive() — на ПРОЙДЕННОМ пути isActive() уже false, и условие
        //    finished() внутри никогда не сработало бы: частичный маршрут
        //    Baritone (обрыв расчёта) оставил бы бота молча стоять навсегда.
        if (this.executor.path() != null) {
            if (this.executor.path().finished() || this.executor.needsRepath()) {
                requestRecalc(mc);
            }
            return;
        }

        // 3) Маршрут только считается: Baritone не должен терять управление
        //    (смерть, внешний сброс) — иначе ждали бы путь вечно.
        if (this.goalActive) {
            IBaritone baritone = baritone();
            if (baritone == null) {
                return;
            }
            if (!baritone.getCustomGoalProcess().isActive()) {
                fail("маршрут прерван");
            }
        }
    }

    /**
     * Baritone затеял расчёт/исполнение сам, без нашего заказа. Гасим: иначе он
     * доходит до {@code InputOverrideHandler.onTick}, тот ставит
     * {@code player.input = PlayerMovementInput}, ванильный {@code KeyboardInput}
     * перестаёт тикать — вместе с ним умирает {@code EventInput} и кастомные ноги.
     */
    private void squashStray(String why) {
        IBaritone baritone = this.baritone;
        if (baritone == null) {
            return;
        }
        baritone.getPathingBehavior().cancelEverything();
        baritone.getCustomGoalProcess().setGoal(null);
        try {
            baritone.getInputOverrideHandler().clearAllKeys();
        } catch (Throwable ignored) {
            // не критично: ввод вернёт guardCustomMovement
        }
        if (++this.dbgSquash % 100 == 1) { //DBG
            System.out.println("[dbg] squash #" + this.dbgSquash + ": " + why //DBG
                    + " current=" + (baritone.getPathingBehavior().getCurrent() != null) //DBG
                    + " path=" + baritone.getPathingBehavior().getPath().isPresent()); //DBG
        } //DBG
    }

    /**
     * Держит ввод у Vesence, пока мы ведём маршрут: гасит самочинную активность
     * Baritone и возвращает ванильный {@code KeyboardInput} — единственный
     * источник {@code EventInput} для исполнителя.
     */
    private void guardCustomMovement(Minecraft mc) {
        IBaritone baritone = this.baritone;
        if (baritone != null && !this.goalActive
                && baritone.getPathingBehavior().isPathing()) {
            squashStray("Baritone пошёл сам");
        }
        if (mc.player != null && !(mc.player.input instanceof KeyboardInput)) {
            mc.player.input = new KeyboardInput(mc.options);
        }
    }

    /**
     * Кража пути: Baritone досчитал и собрался исполнять — вместо этого
     * забираем маршрут себе и гасим его исполнение в тот же тик. Ни одного
     * ввода Baritone записать не успевает: ноги остаются кастомными.
     */
    private void stealPath() {
        if (!this.goalActive || this.inStealPath) {
            return;
        }
        IBaritone baritone = this.baritone;
        if (baritone == null) {
            return;
        }
        this.inStealPath = true;
        try {
            IPathingBehavior pathing = baritone.getPathingBehavior();
            IPathExecutor current = pathing.getCurrent();
            IPath computed = current != null ? current.getPath() : pathing.getPath().orElse(null);

            this.goalActive = false;
            baritone.getCustomGoalProcess().setGoal(null);
            pathing.cancelEverything();

            if (computed == null) {
                // Путь не отдали — исполнитель сам попросит пересчёт на следующем тике.
                return;
            }
            Path adapted = adapt(computed, this.goal);
            if (adapted == null || adapted.isEmpty()) {
                // Нулевой маршрут: Baritone считает, что цель уже под ногами.
                // Пересчёт снова вернёт пусто — это прибытие, а не тупик.
                System.out.println("[dbg] steal: пустой маршрут — считаем прибытием"); //DBG
                arrive();
                return;
            }
            this.executor.setPath(adapted);
            this.blindRetries = 0;
            System.out.println("[baritone] маршрут: узлов " + adapted.nodes.size()
                    + ", цель " + (this.goal != null ? this.goal.describe() : "?"));
        } finally {
            this.inStealPath = false;
        }
    }

    /**
     * Пересчитать маршрут от текущего положения: участок кончился, ноги
     * отклонились или упёрлись. Старый путь не снимаем — бот продолжает идти,
     * пока Baritone считает замену; новый путь подменяет старый на лету.
     */
    private void requestRecalc(Minecraft mc) {
        if (this.goalActive || this.translatedGoal == null) {
            return;
        }
        IBaritone baritone = baritone();
        if (baritone == null) {
            return;
        }

        // Маршрут выбродили и мы уже внутри цели — это приход, а не повод
        // считать заново. Без этой проверки успех зависел от того, успеет ли
        // Baritone сам отрапортовать AT_GOAL, и бот стоял в цели без финала.
        // Именно arrive(), а не onArrived(): тот guarded по goalActive, а мы
        // здесь как раз вне активного расчёта.
        BlockPos at = mc.player.blockPosition();
        if (this.goal != null && this.goal.isInGoal(at.getX(), at.getY(), at.getZ())) {
            arrive();
            return;
        }

        // Прогресс: пересчёт вслепую (не приблизили к цели) терпим ограниченно.
        double distance = goalDistance(mc);
        if (distance < this.bestGoalDistance - PROGRESS_EPSILON) {
            this.blindRetries = 0;
        } else {
            this.blindRetries++;
        }
        this.bestGoalDistance = Math.min(this.bestGoalDistance, distance);
        if (this.blindRetries > MAX_BLIND_RETRIES) {
            // Прогресса нет. Прежде чем ронять цель, ослабляем её: цепочка
            // послаблений существует ровно для точек, куда не попасть
            // (закопаны в холм, отрезаны). Baritone на строгую цель отдаёт
            // частичный маршрут, и бот вечно топчется на его конце.
            Goal next = this.goalChain.poll();
            if (next != null) {
                System.out.println("[baritone] fallback goal: " + next.describe());
                startGoal(next);
                return;
            }
            fail("застрял и не смог освободиться");
            return;
        }

        System.out.println("[baritone] пересчёт №" + this.blindRetries
                + ", до цели " + String.format("%.1f", distance));
        this.executor.acknowledgeRepath();
        this.goalActive = true;
        baritone.getCustomGoalProcess().setGoalAndPath(this.translatedGoal);
    }

    /** Расстояние от игрока до якоря цели; без якоря — бесконечность. */
    private double goalDistance(Minecraft mc) {
        if (this.goal == null || mc.player == null) {
            return Double.MAX_VALUE;
        }
        BlockPos anchor = this.goal.anchor();
        if (anchor == null) {
            return Double.MAX_VALUE;
        }
        return mc.player.position().distanceTo(Vec3.atCenterOf(anchor));
    }

    /**
     * Цель достигнута: стоим в её пределах, либо путь пройден до конца и
     * последний узел рядом.
     */
    private boolean goalReached(Minecraft mc) {
        if (this.goal == null || mc.player == null) {
            return false;
        }
        BlockPos pos = BlockPos.containing(mc.player.position());
        if (this.goal.isInGoal(pos.getX(), pos.getY(), pos.getZ())) {
            return true;
        }
        // Путь пройден до конца: прибывать можно, только если его последний
        // узел сам лежит в цели. Baritone регулярно отдаёт частичный маршрут
        // (обрыв расчёта), и конец такого пути может быть в десятках блоков
        // от цели — объявлять там «пришёл» нельзя, путь уходит на пересчёт.
        Path path = this.executor.path();
        if (path != null && path.finished()) {
            PathNode last = path.last();
            if (last != null) {
                BlockPos lp = last.pos();
                return this.goal.isInGoal(lp.getX(), lp.getY(), lp.getZ());
            }
        }
        return false;
    }

    /**
     * Прибытие. С процессом — молча освобождаем цель: процесс сам решит, что
     * дальше (follow выдаст новую точку, explore возьмёт следующий участок).
     * Без процесса — пишем в чат, как раньше.
     */
    private void arrive() {
        if (this.process != null) {
            cancel();
            return;
        }
        BaritoneMessages.success("пришёл");
        cancel();
    }

    /**
     * Baritone сам дошёл до цели (крайне редко: мы забираем путь до исполнения).
     * Обрабатываем как обычное прибытие.
     */
    private void onArrived() {
        if (!this.goalActive) {
            return;
        }
        arrive();
    }

    /**
     * Поиск провалился: сначала пробуем следующее ослабление цели из цепочки,
     * и только когда цепочка кончилась — честный провал.
     */
    private void onSearchFailed() {
        if (!this.goalActive) {
            return;
        }
        this.goalActive = false;
        IBaritone baritone = this.baritone;
        if (baritone != null) {
            baritone.getCustomGoalProcess().setGoal(null);
        }
        // Поиск провалился, но, может, мы уже стоим в цели (её ослабили раньше,
        // а Baritone не нашёл ничего лучше текущего места) — тогда прийти,
        // а не бесполезно ослаблять дальше. arrive() вместо onArrived():
        // goalActive мы только что погасили, и guarded-версия бы промолчала.
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && this.goal != null) {
            BlockPos at = mc.player.blockPosition();
            if (this.goal.isInGoal(at.getX(), at.getY(), at.getZ())) {
                arrive();
                return;
            }
        }
        Goal next = this.goalChain.poll();
        if (next != null) {
            System.out.println("[baritone] fallback goal: " + next.describe());
            startGoal(next);
            return;
        }
        fail("маршрут не найден");
    }

    /** Провал: процессу — {@code onPathFailed}, иначе сообщение в чат и стоп. */
    private void fail(String reason) {
        BaritoneProcess process = this.process;
        cancel();
        if (process != null) {
            process.onPathFailed();
            return;
        }
        System.out.println("[baritone] stop: " + reason);
        BaritoneMessages.error(reason + ", остановился.");
    }

    // ── Адаптер пути ─────────────────────────────────────────────────────────

    /**
     * Путь Baritone → путь Vesence: те же клетки, типы движений переведены по
     * классам. По этому пути ходит исполнитель, его же ест лента и стенды.
     */
    private static Path adapt(IPath path, Goal goal) {
        List<BetterBlockPos> positions = path.positions();
        List<IMovement> movements = path.movements();
        List<PathNode> nodes = new ArrayList<>(positions.size());
        Minecraft mc = Minecraft.getInstance();
        for (int i = 0; i < positions.size(); i++) {
            BetterBlockPos pos = positions.get(i);
            PathNode node;
            if (i == 0) {
                node = new PathNode(pos.getX(), pos.getY(), pos.getZ(), MovementType.START);
            } else {
                IMovement movement = i - 1 < movements.size() ? movements.get(i - 1) : null;
                MovementType type = mapMovement(movement, pos, mc);
                node = new PathNode(pos.getX(), pos.getY(), pos.getZ(), type);
                if (type == MovementType.DIG) {
                    node.dig = new BlockPos(pos.getX(), pos.getY(), pos.getZ());
                }
            }
            nodes.add(node);
        }
        return new Path(nodes, goal, 0.0);
    }

    /**
     * Класс движения Baritone → тип узла Vesence.
     *
     * <p>Копание отдельным классом не представлено: Baritone ломает блоки
     * внутри {@code MovementTraverse}/{@code MovementDescend}. Чтобы
     * исполнитель знал, что клетку надо прорубить, смотрим на сам блок
     * назначения: вход в монолитный блок — это DIG.
     */
    private static MovementType mapMovement(IMovement movement, BlockPos dest, Minecraft mc) {
        if (movement instanceof MovementAscend || movement instanceof MovementPillar) {
            return MovementType.ASCEND;
        }
        if (movement instanceof MovementDescend) {
            return MovementType.DESCEND;
        }
        if (movement instanceof MovementFall) {
            return MovementType.FALL;
        }
        if ((movement instanceof MovementTraverse || movement instanceof MovementDiagonal)
                && mc.level != null) {
            if (needsBreak(mc, dest) || needsBreak(mc, dest.above())) {
                return MovementType.DIG;
            }
        }
        return MovementType.WALK;
    }

    /** Монолитный блок, который придётся сломать, чтобы войти в клетку. */
    private static boolean needsBreak(Minecraft mc, BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        return !state.isAir() && !state.getCollisionShape(mc.level, pos).isEmpty();
    }

    // ── События клиента ──────────────────────────────────────────────────────

    /** Тик движка от клиентского тика. */
    @EventInit
    public void onUpdate(EventUpdate event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (++this.dbgTick % 20 == 0) { //DBG
            Path p = this.executor.path(); //DBG
            System.out.println("[dbg] update#" + this.dbgTick + " enabled=" + this.enabled + " paused=" + this.paused //DBG
                    + " goalActive=" + this.goalActive //DBG
                    + " goal=" + (this.goal == null ? "null" : this.goal.describe()) //DBG
                    + " exec=" + this.executor.isActive() + " idx=" + (p == null ? -1 : p.index) //DBG
                    + " nodes=" + (p == null ? -1 : p.nodes.size()) //DBG
                    + " fin=" + (p != null && p.finished()) + " repath=" + this.executor.needsRepath() //DBG
                    + " reached=" + (this.goal != null && this.goalReached(mc)) //DBG
                    + " screen=" + (mc.gui.screen() == null ? "-" : mc.gui.screen().getClass().getSimpleName()) //DBG
                    + " input=" + (mc.player.input == null ? "-" : mc.player.input.getClass().getSimpleName())); //DBG
        } //DBG
        tick();
    }

    /**
     * Ноги: походка исполнителя пишется во ввод игрока.
     *
     * <p>Пока маршрут ведёт Baritone-расчёт, ног нет — игрок стоит (или идёт
     * своим ходом, если путь старый ещё жив). Как только путь украден —
     * вперёд/стрейф/прыжок/спринт считает {@link PathExecutor}, а миксин
     * клавиатуры переносит их в {@code keyPresses}.
     */
    @EventInit
    public void onInputEvent(EventInput event) {
        if (!this.enabled || this.paused || this.goal == null) {
            if (this.goal != null && ++this.dbgGate % 40 == 0) { //DBG
                System.out.println("[dbg] input gate1: enabled=" + this.enabled + " paused=" + this.paused); //DBG
            } //DBG
            return;
        }
        if (!this.executor.isActive()) {
            if (++this.dbgGate % 40 == 0) { //DBG
                System.out.println("[dbg] input gate2: executor неактивен, goal=" + this.goal.describe()); //DBG
            } //DBG
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.screen() != null) {
            if (++this.dbgGate % 40 == 0) { //DBG
                System.out.println("[dbg] input gate3: screen=" //DBG
                        + (mc.gui.screen() == null ? "-" : mc.gui.screen().getClass().getSimpleName())); //DBG
            } //DBG
            return;
        }
        if (++this.dbgInput % 100 == 0) { //DBG
            Path p = this.executor.path(); //DBG
            System.out.println("[dbg] input apply#" + this.dbgInput //DBG
                    + " idx=" + (p == null ? -1 : p.index) + " pos=" + mc.player.position()); //DBG
        } //DBG
        this.executor.apply(event);
        if (!this.legsAnnounced) {
            this.legsAnnounced = true;
            System.out.println("[vesence] ноги ведут маршрут: исполнитель пишет ввод через EventInput");
        }
    }

    /**
     * Голова: пружина исполнителя доворачивает взгляд покадрово и прямо пишет
     * {@code player.setYRot/setXRot} — сервер видит ту же голову, что и глаз.
     */
    @EventInit
    public void onRotation(EventRotation event) {
        if (!this.enabled || this.paused || this.goal == null) {
            return;
        }
        if (!this.executor.isActive()) {
            return;
        }
        this.executor.applyCameraRotation(event);
    }

    /** Отрисовка ленты маршрута и метки цели — наш визуал поверх Baritone. */
    @EventInit
    public void onRender3D(EventRender3D event) {
        if (!this.enabled || !this.showPath) {
            return;
        }
        Path path = currentPath();
        if (path == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        List<PathNode> nodes = path.remaining();
        if (nodes.isEmpty()) {
            return;
        }

        // Метка цели.
        //
        // Раньше рисовали строго goal.anchor(). Но у GoalXZ без окна по высоте
        // «якорь» — выдуманная высота 64: на холме метка висела в воздухе, в
        // яме уходила в камень — «точка, куда идёт, отображается криво». Теперь:
        //  • маршрут уже привёл к цели — показываем блок, куда бот реально
        //    приходит (последний узел);
        //  • цель далеко (маршрут обрывается на краю чанков) — якорь, но
        //    прижатый к земле, если цель высоту не фиксирует.
        BlockPos goalAt;
        if (goal == null || goal.isInGoal(mc.player.getBlockX(),
                mc.player.getBlockY(), mc.player.getBlockZ())) {
            goalAt = nodes.get(nodes.size() - 1).pos();
        } else {
            goalAt = snapGoalToGround(mc, goal.anchor(), goal);
        }

        PathRenderer.draw(event.getMatrixStack(), event.getCameraPos(), event.getCameraRotation(),
                nodes, goalAt, mc.player.position().add(0, mc.player.getEyeHeight(), 0));
    }

    private static BlockPos snapGoalToGround(Minecraft mc, BlockPos anchor, Goal goal) {
        if (anchor == null) {
            return null;
        }
        // Цель фиксирует высоту — метка стоит ровно там, куда идём.
        if (goal != null && goal.isInGoal(anchor.getX(), anchor.getY(), anchor.getZ())) {
            return anchor;
        }
        int ground = mc.level.getHeight(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                anchor.getX(), anchor.getZ()) - 1;
        if (goal.isInGoal(anchor.getX(), ground, anchor.getZ())) {
            return new BlockPos(anchor.getX(), ground, anchor.getZ());
        }
        return anchor;
    }
}
