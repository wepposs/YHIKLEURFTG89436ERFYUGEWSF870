package vesence.pathing;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import vesence.event.player.EventInput;
import vesence.event.player.EventRotation;

/**
 * Исполнитель пути: превращает узлы в нажатия клавиш и поворот головы.
 *
 * <p>Разделение по частоте:
 * <ul>
 *   <li>тик ({@link #apply}) — куда идти и прыгать ли;</li>
 *   <li>кадр ({@link #applyCameraRotation}) — куда смотрит голова.</li>
 * </ul>
 *
 * <p>Голова всегда смотрит по курсу движения, как у живого игрока: направление
 * поворота задаёт {@link RotationSmoother}, который считает угол по реальному
 * времени кадра. Поэтому персонаж идёт «вперёд», а не боком, и при этом не
 * дёргает головой.
 */
@Environment(EnvType.CLIENT)
public final class PathExecutor {

    /** Насколько близко к узлу считаем его достигнутым (по горизонтали). */
    private static final double NODE_REACH = 0.8;

    /**
     * Допуск достижения узла в воде.
     *
     * <p>Пловец не стоит на опоре: его носит течением, он покачивается на волнах
     * и всплывает рывками. Сухопутный допуск 0.8 в воде почти не достигался,
     * индекс пути замирал, и бот «плавал по кругу» вокруг узла, который уже
     * проплыл. Здесь точность не нужна вовсе: куда плыть, решает берег впереди,
     * а не конкретная клетка воды.
     */
    private static final double SWIM_NODE_REACH = 1.4;

    /** Сколько тиков упора в препятствие терпим, прежде чем перестроить путь. */
    private static final int REPATH_AFTER_STUCK_TICKS = 24;

    /** Насколько вперёд смотрит идущий человек: чуть вниз, под ноги. */
    private static final float WALK_PITCH = 6.0f;

    /**
     * Сколько тиков терпим копание одного блока.
     *
     * <p>Пятнадцать секунд — с запасом больше, чем нужно любому блоку в игре с
     * подходящим инструментом (обсидиан алмазной киркой — около десяти). Всё,
     * что дольше, означает «инструмента нет и не будет», и продолжать значит
     * стоять на месте вечно.
     */
    private static final int DIG_TIMEOUT_TICKS = 300;

    /**
     * Сколько тиков терпим «блок раскопа вне досягаемости», прежде чем просить
     * пересчёт. Это не работа, а тупик: короткий таймаут, а не {@link #DIG_TIMEOUT_TICKS}.
     * Запас больше обычного подхода пешком (~45 тиков), чтобы не срабатывать на ходьбе.
     */
    private static final int DIG_UNREACHABLE_TICKS = 60;

    private final Humanizer humanizer = new Humanizer();
    private final RotationSmoother rotation = new RotationSmoother(0.0f, 0.0f);

    private Path path;
    private int stuckTicks;

    /** Позиция прошлого тика для сторожа застревания; NaN — ещё не мерили. */
    private double lastTrackX = Double.NaN;
    private double lastTrackZ;

    /** Впереди раскоп — на нём счётчики застревания молчат. */
    private boolean diggingPath;
    private boolean repathRequested;

    private boolean sprintAllowed = true;

    /** Направление на текущий узел. */
    private float intentYaw;
    private float intentPitch = WALK_PITCH;

    private boolean jumpRequested;

    /** Бежим ли в этом тике — нужно для решения о подскоке. */
    private boolean sprintingNow;

    /** Тиков подряд копаем один и тот же узел. */
    private int digTicks;

    /** Ключ узла, который копаем сейчас — чтобы счётчик сбрасывался при смене. */
    private long digNodeKey;

    public Humanizer humanizer() {
        return this.humanizer;
    }

    public RotationSmoother rotation() {
        return this.rotation;
    }

    public void setSprintAllowed(boolean sprintAllowed) {
        this.sprintAllowed = sprintAllowed;
    }

    public Path path() {
        return this.path;
    }

    public void setPath(Path path) {
        // Струнная стяжка: прямые участки теряют промежуточные клетки сетки,
        // и курс перестаёт вилять на каждой диагонали («ходит зигзагами»).
        this.path = PathSimplifier.simplify(path);
        this.stuckTicks = 0;
        this.repathRequested = false;
        this.jumpRequested = false;
        this.strafeAssist = false;
        this.bigTurn = false;
        this.humanizer.reset();
        this.smoothCache = null;
        this.smoothCachePath = null;

        // Первый узел — START, то есть точка, из которой путь построен. Идти к
        // ней не нужно и вредно: она может оказаться на полблока в стороне или
        // чуть ниже, и тогда персонаж разворачивается к ней, проскакивает,
        // разворачивается обратно — и так по кругу, не продвигаясь по маршруту.
        // Начинаем со второго.
        if (this.path != null && this.path.nodes.size() > 1) {
            this.path.index = 1;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            // Ставим камеру на текущий взгляд ОДИН раз за всё время работы.
            //
            // Раньше это делалось на каждом новом пути — и вот что получалось.
            // Пока путь идёт, камера ведётся сглаженно и отстаёт от тела на
            // десятки градусов. На пересчёте пути сглаживание принудительно
            // приравнивалось к углу тела — и камера прыгала на эти десятки
            // градусов за один кадр. Замер показывал 101° за кадр, то есть
            // ровно тот «щелчок головой», на который жалуется игрок.
            //
            // Теперь сглаживание живёт непрерывно: камера подхватывает новый
            // курс с того места, где была, и доворачивается плавно.
            if (!this.rotationReady) {
                this.rotation.snapTo(mc.player.getYRot(), mc.player.getXRot());
                this.rotationReady = true;
            }
            this.intentYaw = mc.player.getYRot();
            this.intentPitch = WALK_PITCH;
        }
    }

    /** Была ли камера хоть раз выставлена — чтобы не снапить её на каждом пути. */
    private boolean rotationReady;

    public void clear() {
        this.syncBodyToCamera();
        this.path = null;
        this.stuckTicks = 0;
        this.repathRequested = false;
        this.jumpRequested = false;
        this.strafeAssist = false;
        this.bigTurn = false;
        this.digTicks = 0;
        this.digNodeKey = 0L;
        this.smoothCache = null;
        this.smoothCachePath = null;
        this.bodyReady = false;
        this.diggingPath = false;
        // Отпускаем удар: иначе клавиша атаки останется зажатой без цели, и
        // ваниль начнёт ломать всё, что случайно окажется перед игроком.
        BlockInteraction.stop(Minecraft.getInstance());
    }

    /**
     * Держит тело и камеру вместе в момент остановки.
     *
     * <p>Пока путь есть, камера ведётся сглаженно и отстаёт от тела на десятки
     * градусов. Как только путь кончился, сглаживание перестаёт применяться — и
     * камера мгновенно прыгает к углу тела. Это и есть «щелчок головой» в конце
     * маршрута.
     *
     * <p>Лечится не приравниванием тела к камере (это дёргало бы персонажа), а
     * доворотом камеры к телу за несколько кадров: сглаживание продолжает
     * работать, пока разрыв не закроется.
     */
    private void syncBodyToCamera() {
        this.rotation.setTarget(this.rotation.yaw(), this.rotation.pitch());
    }

    public boolean isActive() {
        return this.path != null && !this.path.finished();
    }

    public boolean needsRepath() {
        return this.repathRequested;
    }

    public void acknowledgeRepath() {
        this.repathRequested = false;
        this.stuckTicks = 0;
    }

    /**
     * Тик: решает, куда идти. Вызывается из миксина клавиатуры до того, как
     * ваниль посчитает движение.
     */
    public void apply(EventInput event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (this.path == null || this.path.isEmpty()) {
            return;
        }

        boolean usingItem = mc.player.isUsingItem();

        // Индекс пути двигаем ВСЕГДА — в том числе в воде.
        //
        // Прежде в воде исполнитель выходил раньше, чем доходил до этой строки, и
        // индекс замирал на первом водном узле. Из-за этого «первый сухой узел
        // впереди» был всё время одним и тем же, а плавание ходило туда-обратно:
        // игрок проплывал мимо узлов, индекс не двигался, и цель снова оказывалась
        // позади. Снаружи это «плавает по кругу и не вылезает».
        this.advanceIfReached(mc);

        // В воде маршрут по узлам бесполезен: узлы стоят на дне или на
        // поверхности, а игрок плавает между ними и «крутится по кругу» —
        // подходит к узлу, не может его достичь по высоте, разворачивается,
        // возвращается. Живой игрок в такой ситуации просто плывёт к берегу.
        if (mc.player.isInWater()) {
            this.applySwim(mc, event);
            return;
        }

        // Раскоп впереди? Тогда застревание и сторож узла молчат: стоять на
        // месте во время копания — это работа, а не поломка.
        this.diggingPath = this.hasDigAhead();

        // Упали с маршрута — пересчитываем от того места, где оказались.
        //
        // Без этой проверки исполнитель продолжал тянуть игрока к узлам, которые
        // остались наверху за обрывом: он разворачивался, бился в стену обрыва,
        // снова разворачивался. Живой игрок в такой ситуации просто строит
        // маршрут заново от себя.
        if (this.fellOffPath(mc)) {
            this.repathRequested = true;
            return;
        }

        if (this.path.finished()) {
            this.syncBodyToCamera();
            this.stopMovement(event);
            this.repathRequested = true;
            return;
        }

        // Тактический узел: с него читаем, что делать ногами и головой —
        // прыгать, копать, куда наклонить взгляд. Курс тела считает НЕ он,
        // а «морковка» ниже: узел прыгает дискретно, точка впереди — плавно.
        PathNode node = this.tacticalTarget(mc);
        if (node == null) {
            node = this.path.current();
        }
        if (node == null) {
            this.stopMovement(event);
            return;
        }

        // Узел «прорубить ход».
        //
        // Такой узел лежит ВНУТРИ блока: стоять там нельзя, пока блок не сломан.
        // Если он уже в пределах руки — останавливаемся и копаем; если ещё нет —
        // идём к нему обычным порядком и докопаемся, когда подойдём.
        if (node.type == MovementType.DIG) {
            if (this.applyDig(mc, event, node)) {
                // Копание — это работа, а не застревание: пока исполнитель
                // реально ломает блок, счётчик «встал насмерть» обнуляется.
                // Иначе на камне без кирки (150 тиков на блок) пересчёт
                // срабатывал посреди раскопа, план строился заново, прогресс
                // ломания обнулялся — и бот вечно «ломал один блок и ходил
                // назад-вперёд».
                this.stuckTicks = 0;
                return;
            }
        }

        // Курс — на «морковку»: точку в паре блоков впереди вдоль ломаной
        // маршрута, а не в центр ближайшей клетки.
        //
        // Почему не центр узла. Когда до цели меньше блока, азимут на неё
        // вращается со скоростью сотни градусов в секунду: шаг влево — и она
        // уже «на семь часов». Плюс дальний узел сглаживания переключался между
        // соседями каждый тик, и курс дёргался туда-сюда на двадцать градусов.
        // Именно это игрок видел как «вертится, когда бежит». Точка впереди
        // физически не может крутиться быстро: чтобы азимут изменился на
        // градус, нужно пройти сантиметры.
        Vec3 aim = this.clampToClearance(mc, this.carrotPoint(mc));
        this.intentYaw = this.headingTo(mc, aim);
        this.intentPitch = this.pitchFor(node);

        // Тело и камера — ОДНО И ТО ЖЕ значение.
        //
        // Раньше здесь было разделение: тело ставилось точно на курс, а камера
        // велась сглаженно. Идея была «движение точно к узлу, а плавность только
        // для глаза». На практике это дало два бага сразу:
        //
        //  • в момент остановки сглаживание перестаёт применяться, камера
        //    прыгает к углу тела — замер показывал 101° за кадр. Это и есть
        //    «щелчок головой»;
        //  • тело, повёрнутое на десятки градусов впереди камеры, выглядит в F5
        //    как персонаж, идущий боком.
        //
        // Сейчас оба угла берутся из одного сглаженного значения. Голова
        // поворачивается плавно и туда, куда идёт, — как у живого игрока,
        // который ведёт мышь. Разрыва нет, прыжка в конце нет.
        this.rotation.setTarget(this.intentYaw, this.intentPitch);
        this.rotation.update();
        mc.player.setYRot(this.rotation.yaw());
        mc.player.setXRot(this.rotation.pitch());

        boolean sprinting = this.shouldSprint(mc, usingItem);
        this.sprintingNow = sprinting;
        this.humanizer.tick(true, usingItem, sprinting);

        // Сторож: если на один и тот же узел мы целимся слишком долго, с ним
        // что-то не так — например, он висит в недостижимом месте. Пропускаем
        // его, вместо того чтобы бесконечно кружить вокруг.
        this.watchNode(mc, node);

        // Ноги доворачивают то, что не успела голова.
        //
        // Поворот головы ограничен по скорости — это защита от рывков. Но пока
        // она доворачивается, «вперёд» ведёт игрока ПО СТАРОМУ курсу, и на бегу
        // он сносится в сторону от маршрута на блок-другой. Отсюда «сбивается с
        // пути». Поэтому при большой ошибке курса нажимаем ещё и боковую
        // клавишу: движение идёт под сорок пять градусов к телу — заметно ближе
        // к цели, чем строго вперёд. Это ровно то, что делает игрок в тесноте:
        // доворачивает мышью и помогает стрейфом.
        //
        // На разворотах впереди больше ста пятнадцати градусов шаг вовсе
        // отпускаем (возвращаем ниже девяноста пяти): полсекунды голова
        // довернёт, и идти станет некуда. Пятиться задом через весь маршрут —
        // хуже, чем короткая пауза в шаге. Пороги включения и выключения
        // разные — гистерезис, иначе шаг дёргался бы на шуме угла.
        float err = net.minecraft.util.Mth.wrapDegrees(this.intentYaw - this.rotation.yaw());
        float absErr = Math.abs(err);
        // Гистерезис: пороги включения и выключения разные, иначе клавиши
        // щёлкают на шуме и персонаж идёт рваной змейкой.
        this.strafeAssist = this.strafeAssist ? absErr > STRAFE_ASSIST_OFF : absErr > STRAFE_ASSIST_ON;
        this.bigTurn = this.bigTurn ? absErr > BIG_TURN_OFF : absErr > BIG_TURN_ON;

        // Вход в поворот — дуга, а не остановка.
        //
        // Скорость «вперёд» плавно падает начиная с пятидесяти пяти градусов
        // ошибки курса и к границе крутого разворота доходит до трети. Вместе
        // со стрейф-помощью это даёт траекторию, которую чертит живой игрок:
        // притормозил, резанул угол, не останавливаясь. Прежний бинарный
        // «шаг/стоп» на каждом углу выглядел именно как робот.
        float forward;
        if (this.bigTurn) {
            forward = 0.0f;
        } else if (absErr > TURN_SLOW_START) {
            forward = net.minecraft.util.Mth.clampedMap(absErr, TURN_SLOW_START, BIG_TURN_ON, 1.0f, TURN_SLOW_MIN);
        } else {
            forward = 1.0f;
        }
        event.setForward(forward);

        float strafe = 0.0f;
        if (!this.bigTurn && this.strafeAssist) {
            // err > 0 — цель правее курса тела: поджимаем правую клавишу.
            strafe = err > 0.0f ? -1.0f : 1.0f;
        }
        if (strafe == 0.0f) {
            strafe = this.unstickStrafe(mc);
        }
        event.setStrafe(strafe);

        this.jumpRequested = !this.bigTurn && this.shouldJump(mc, node, absErr);
        event.setJump(this.jumpRequested);
        // Спринт только на сравнительно прямом курсе: на поворотах игрок
        // естественно сбавляет ход.
        event.setSprint(sprinting && !this.bigTurn && absErr < TURN_SPRINT_MAX);
        event.setSneak(false);

        this.trackStuck(mc);
        this.debugTick(mc, node, sprinting);
    }

    /**
     * Кадр: тот же доворот, но с дробным временем кадра.
     *
     * <p>Событие приходит из {@code Camera.alignWithEntity} на каждой отрисовке,
     * поэтому здесь голова поворачивается плавно, а не ступеньками по 20 Гц.
     */
    public void applyCameraRotation(EventRotation event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        // Осматривание живёт в кадре, а не в тике: глаз видит именно кадры, и
        // взгляд, посчитанный двадцать раз в секунду, дёргался бы ступеньками.
        boolean active = this.path != null && !this.path.isEmpty();
        if (!active) {
            return;
        }

        // Осматривание убрано: взгляд головы строго по курсу.
        this.rotation.update();
        float travelYaw = this.rotation.yaw();

        long now = System.nanoTime();
        float dt = (now - this.bodyLastNanos) / 1_000_000_000.0f;
        this.bodyLastNanos = now;
        dt = net.minecraft.util.Mth.clamp(dt, 0.0f, 0.1f);

        if (!this.bodyReady) {
            this.bodyReady = true;
            // Стартуем ОТКУДА смотрит игрок, а не снапаем на курс.
            //
            // Раньше первый кадр пути телепортировал камеру в направление
            // движения: EventRotation прыгал на 50–60° за кадр — тест ловил
            // это как «рывок головой». Теперь камера и тело начинают с текущего
            // взгляда и доворачиваются штатной скоростью.
            this.cameraYaw = mc.player.getYRot();
            this.bodyYaw = this.cameraYaw;
        }

        // Камера — статична.
        //
        // Картинка не должна ездить. Раньше камера шла за курсом вплотную, и
        // каждый изгиб маршрута поворачивал изображение — смотреть невозможно.
        // Теперь камера держит своё направление и трогается только тогда, когда
        // персонаж ушёл от него по-настоящему далеко. Короткие повороты маршрута
        // картинку не шевелят вообще.
        //
        // Осматривание по сторонам камеру тоже не трогает: оно живёт только в
        // угле головы модели.
        float cameraDelta = net.minecraft.util.Mth.wrapDegrees(travelYaw - this.cameraYaw);
        if (Math.abs(cameraDelta) > CAMERA_SNAP) {
            // Ушёл почти в противоположную сторону — иначе он убежит из кадра.
            this.cameraYaw = travelYaw;
        } else if (Math.abs(cameraDelta) > CAMERA_FOLLOW_ANGLE) {
            this.cameraYaw += Math.signum(cameraDelta) * CAMERA_SPEED * dt;
        }

        event.setYaw(this.cameraYaw);
        event.setPitch(this.rotation.pitch());

        // Голова смотрит строго по курсу: никакого осматривания по сторонам.
        mc.player.yHeadRot = travelYaw;
        mc.player.yHeadRotO = travelYaw;

        // Корпус идёт за курсом вплотную.
        //
        // Раньше он отставал намеренно — «человек поворачивается не мгновенно».
        // На практике это дало ровно то, на что была жалоба: тело запаздывало за
        // направлением бега, и персонаж со стороны бежал БОКОМ. Отставание
        // корпуса от курса — это не человечность, это брак анимации: живой игрок
        // в Minecraft поворачивается вместе с мышью, то есть мгновенно.
        //
        // Поэтому мёртвая зона здесь маленькая и чисто техническая: она гасит
        // дрожание сетки маршрута, но не даёт корпусу отстать от ног.
        float delta = net.minecraft.util.Mth.wrapDegrees(travelYaw - this.bodyYaw);
        if (Math.abs(delta) > BODY_SNAP) {
            this.bodyYaw = travelYaw;
        } else if (Math.abs(delta) > BODY_FOLLOW_ANGLE) {
            this.bodyYaw += Math.signum(delta) * BODY_SPEED * dt;
        }

        mc.player.yBodyRot = this.bodyYaw;
        mc.player.yBodyRotO = this.bodyYaw;
    }

    /**
     * Насколько персонаж должен уйти от направления камеры, чтобы камера
     * повернулась.
     *
     * <p>Раньше здесь было сорок градусов, и камера догоняла курс со скоростью
     * 45°/с — картинка медленно вращалась почти всю дорогу, и это читалось как
     * «персонаж вертится». Теперь курс ведётся «морковкой» и сам по себе
     * плавный, поэтому камеру можно держать близко к направлению движения:
     * двенадцать градусов гасят остаточное дрожание, но рассинхрон с курсом
     * больше не заметен.
     */
    private static final float CAMERA_FOLLOW_ANGLE = 12.0f;

    /** Скорость подтягивания камеры, градусов в секунду. */
    private static final float CAMERA_SPEED = 300.0f;

    /** Когда камера разворачивается сразу: персонаж почти убежал из кадра. */
    private static final float CAMERA_SNAP = 100.0f;

    /** Направление камеры: своё, отдельное от курса и от головы. */
    private float cameraYaw;

    /**
     * Мёртвая зона корпуса: насколько курс должен уйти, чтобы корпус начал
     * доворачиваться.
     *
     * <p>Двенадцать градусов — только чтобы погасить дрожание сетки маршрута.
     * Всё, что больше, корпус догоняет почти мгновенно: отставание корпуса от ног
     * выглядит как бег боком, и этого быть не должно.
     */
    private static final float BODY_FOLLOW_ANGLE = 12.0f;

    /** Скорость доворота корпуса, градусов в секунду. */
    private static final float BODY_SPEED = 720.0f;

    /** Насколько сильный разворот корпус повторяет мгновенно. */
    private static final float BODY_SNAP = 140.0f;

    /** Угол корпуса — живёт в кадре, как и взгляд. */
    private float bodyYaw;

    private boolean bodyReady;

    private long bodyLastNanos = System.nanoTime();

    /**
     * Поведение в воде: плыть к сухой части маршрута.
     *
     * <p>Раньше здесь искался «ближайший берег» кольцами вокруг игрока. Это была
     * ошибка: ближайший сухой блок — не обязательно тот, куда нам надо. Персонаж
     * выбирался на мыс в стороне, пересчитывал путь, тот снова шёл через воду —
     * и он прыгал обратно. Отсюда «плавает вдоль берега и лезет в воду».
     *
     * <p>Правило простое и без исключений: плывём к первому сухому узлу
     * маршрута впереди — это и есть точка выхода на берег по пути к цели.
     * Всплываем пробелом, а если выход глубоко под нами (зашли с высокого
     * берега) — отпускаем пробел и погружаемся к нему. Ни одного пути, ведущего
     * к остановке: раньше здесь можно было запросить пересчёт и встать в воде,
     * если рядом не нашлось берега.
     */
    private void applySwim(Minecraft mc, EventInput event) {
        // Follow adjacent route nodes, not a distant bank through intervening walls.
        if (this.path.finished()) {
            this.stopMovement(event);
            this.repathRequested = true;
            return;
        }
        PathNode target = this.firstDryNodeAhead(mc);
        if (target == null) {
            target = this.path.current();
        }
        if (target == null) {
            this.stopMovement(event);
            this.repathRequested = true;
            return;
        }
        this.humanizer.tick(true, false, false);
        // Refresh observations in water too: previously they stayed frozen forever.
        //
        // Терпение вдвое больше сухопутного: вода вязкая, игрок в ней толкается
        // медленнее, и «почти не двигаемся» здесь бывает в штатном заплыве, а не
        // только когда уткнулись в дно. Раньше пересчёт запрашивался каждые две
        // секунды спокойного плавания — бот дёргался и терял маршрут.
        if (this.humanizer.blockedTicks() >= SWIM_REPATH_TICKS) {
            this.repathRequested = true;
        }
        double dx = target.centerX() - mc.player.getX();
        double dz = target.centerZ() - mc.player.getZ();
        float yaw = (dx * dx + dz * dz < 0.01)
                ? mc.player.getYRot()
                : (float) Math.toDegrees(Math.atan2(-dx, dz));

        this.intentYaw = yaw;
        this.intentPitch = 4.0f;

        // Тело и камера — одно значение, как и на суше.
        this.rotation.setTarget(this.intentYaw, this.intentPitch);
        this.rotation.update();
        mc.player.setYRot(this.rotation.yaw());
        mc.player.setXRot(this.rotation.pitch());

        // Нырять или всплывать.
        //
        // Пробел, зажатый навсегда, — вторая причина «ломается в воде»: если
        // вход в воду был с высокого берега, а выход маршрута ниже, персонаж
        // вечно болтался на поверхности и не мог ни подойти к узлу, ни выйти на
        // сушу. Теперь, пока выход заметно глубже нас, пробел отпущен — гребец
        // погружается к нему; как только сравнялись, снова зажат — и последние
        // метры к берегу идут по поверхности, с зацепом за кромку.
        boolean dive = mc.player.getY() > target.centerY() + 0.8;

        event.setForward(1.0f);
        event.setStrafe(0.0f);
        event.setJump(!dive);
        // Спринт в воде — не украшение: ваное ускорение вплавь заметно быстрее
        // простого гребка. В нырке он выключен: там и так тянет ко дну, а
        // рывок к поверхности не нужен.
        boolean sprint = this.sprintAllowed && !dive
                && mc.player.getFoodData().getFoodLevel() > 6;
        event.setSprint(sprint);
        event.setSneak(false);

        this.jumpRequested = !dive;
        this.sprintingNow = sprint;

        this.debugSwim(mc, yaw, String.valueOf(target));
    }

    /** Сколько тиков терпим «почти не двигаемся» в воде, прежде чем пересчитать путь. */
    private static final int SWIM_REPATH_TICKS = 80;

    /**
     * Первый узел маршрута впереди, который находится на суше.
     *
     * <p>Пропускаем воду: плыть к узлу, который сам в воде, бессмысленно — мы и
     * так в ней. Нужна именно точка выхода.
     */
    private PathNode firstDryNodeAhead(Minecraft mc) {
        if (this.path == null) {
            return null;
        }
        for (int i = this.path.index; i < this.path.nodes.size(); i++) {
            PathNode node = this.path.nodes.get(i);
            if (!this.isWaterAt(mc, node.x, node.y, node.z)) {
                return node;
            }
        }
        return null;
    }

    private boolean isWaterAt(Minecraft mc, int x, int y, int z) {
        if (mc.level == null) {
            return false;
        }
        var pos = new net.minecraft.core.BlockPos(x, y, z);
        return mc.level.getBlockState(pos).liquid()
                || mc.level.getBlockState(pos.above()).liquid();
    }

    private int swimDebugTicks;

    private void debugSwim(Minecraft mc, float yaw, String target) {
        if (System.getenv("VESENCE_PATHDEBUG") == null) {
            return;
        }
        if (++this.swimDebugTicks % 10 != 0) {
            return;
        }
        System.out.println("[swim] pos=" + String.format("%.1f/%.1f/%.1f",
                mc.player.getX(), mc.player.getY(), mc.player.getZ())
                + " yaw=" + String.format("%.1f", yaw)
                + " idx=" + this.path.index + "/" + this.path.nodes.size()
                + " target=" + target);
    }

    /**
     * Расстояние до «морковки» — точки впереди, на которую держим курс.
     *
     * <p>Пешком — чуть ближе, бегом — дальше: на скорости точка в двух блоках
     * пролетается за полсекунды, и курс начинал бы «мигать» на каждом узле.
     * Три с половиной блока на бегу — это горизонт планирования живого игрока:
     * он смотрит не под ноги, а чуть вперёд.
     */
    private static final double CARROT_WALK = 2.2;
    private static final double CARROT_SPRINT = 3.5;

    /** Насколько глубоко вдоль маршрута ищем точку-морковку, узлов. */
    private static final int CARROT_MAX_NODES = 8;

    /**
     * Пороги боковой подсадки, градусов: включение и выключение.
     *
     * <p>Два порога вместо одного — гистерезис. С одним порогом ошибка курса
     * дрожала ровно вокруг него, боковая клавиша щёлкала туда-обратно каждый
     * тик, и персонаж шёл рваной змейкой. Теперь включение на тридцати,
     * выключение на двадцати: переключение возможно только через заметную
     * дугу, а не на шуме.
     */
    private static final float STRAFE_ASSIST_ON = 30.0f;
    private static final float STRAFE_ASSIST_OFF = 20.0f;

    /**
     * Пороги остановки шага на развороте, градусов: включение и выключение.
     *
     * <p>Больше ста пятнадцати — это разворот почти спиной вперёд: идти
     * «вперёд» в таком положении значит уходить с маршрута. Ниже девяноста
     * пяти шаг возвращается. Гистерезис — по той же причине, что у стрейфа.
     */
    private static final float BIG_TURN_ON = 115.0f;
    private static final float BIG_TURN_OFF = 95.0f;

    /**
     * Плавное торможение перед поворотом: с какой ошибки курса начинаем
     * сбавлять шаг и до какой доли скорости.
     *
     * <p>Живой игрок входит в угол дугой — заранее притормаживает и режет
     * радиус, а не идёт полным ходом до упора и не замирает на месте.
     */
    private static final float TURN_SLOW_START = 55.0f;
    private static final float TURN_SLOW_MIN = 0.35f;

    /** Круче этого угла спринт выключаем: в поворот игроки входят без бега. */
    private static final float TURN_SPRINT_MAX = 75.0f;

    /**
     * Ближе этого расстояния до угла спринт-прыжки прекращаем, блоков.
     *
     * <p>В воздухе управление слабое: прыжок прямо перед поворотом — это
     * гарантированный пролёт мимо линии и зигзаг-коррекция после приземления.
     * Пять блоков — примерно длина одного прыжка на бегу.
     */
    private static final double HOP_CORNER_DISTANCE = 5.0;

    /** Держим ли сейчас боковую подсадку и остановку шага — состояния гистерезиса. */
    private boolean strafeAssist;
    private boolean bigTurn;

    /** Куда смотреть: на подъёме чуть вверх, на спуске чуть вниз. */
    private float pitchFor(PathNode node) {
        return switch (node.type) {
            case ASCEND, JUMP_GAP -> 2.0f;
            case DESCEND, FALL -> 14.0f;
            default -> WALK_PITCH;
        };
    }

    /**
     * Точка-«морковка»: на {@code CARROT_*} блоков впереди вдоль ломаной маршрута.
     *
     * <p>Строится от текущей позиции игрока по сегментам пути: от игрока к
     * текущему узлу, от него к следующему и так далее, пока не наберётся нужная
     * дистанция. Узел раскопа обрывает поиск точки: к нему ведём буквально,
     * пока не срубим блок.
     */
    private Vec3 carrotPoint(Minecraft mc) {
        double remaining = this.sprintingNow ? CARROT_SPRINT : CARROT_WALK;
        double ax = mc.player.getX();
        double ay = mc.player.getY();
        double az = mc.player.getZ();

        int from = Math.min(this.path.index, this.path.nodes.size() - 1);
        int limit = Math.min(this.path.nodes.size() - 1, from + CARROT_MAX_NODES);
        for (int i = from; i <= limit; i++) {
            PathNode node = this.path.nodes.get(i);
            double bx = node.centerX();
            double by = node.centerY();
            double bz = node.centerZ();

            if (node.type == MovementType.DIG) {
                return new Vec3(bx, by, bz);
            }

            double dx = bx - ax;
            double dz = bz - az;
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len >= remaining) {
                double t = len < 1.0E-6 ? 0.0 : remaining / len;
                return new Vec3(ax + dx * t, ay + (by - ay) * t, az + dz * t);
            }
            remaining -= len;
            ax = bx;
            ay = by;
            az = bz;
        }
        return new Vec3(ax, ay, az);
    }

    /**
     * Обрезает точку-морковку по проходимости коридора.
     *
     * <p>«Морковка» срезает углы по ломаной — это и задумано, но срезать угол
     * сквозь стену нельзя. Проверяем коридор от игрока к точке так же, как
     * {@link #canWalkStraight}: на уровне ног и головы свободно, под ногами
     * опора. Первая закрытая проба обрубает точку перед препятствием.
     *
     * <p>Побочный эффект, и он полезный: перед ямой или ступенькой точка
     * ложится ровно на край — персонаж идёт к краю и шагает вниз (или прыгает
     * вверх) уже ведомый поиском, а не азимутом сквозь препятствие.
     */
    private Vec3 clampToClearance(Minecraft mc, Vec3 target) {
        double px = mc.player.getX();
        double pz = mc.player.getZ();
        double dx = target.x - px;
        double dz = target.z - pz;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.6) {
            return target;
        }
        int feet = (int) Math.floor(mc.player.getY());
        int samples = (int) Math.ceil(len * 4.0);
        for (int s = 1; s <= samples; s++) {
            double t = (double) s / samples;
            int x = (int) Math.floor(px + dx * t);
            int z = (int) Math.floor(pz + dz * t);
            if (!this.clearAt(mc, x, feet, z) || !this.clearAt(mc, x, feet + 1, z)
                    || !this.hasFloorAt(mc, x, feet - 1, z)) {
                double cut = (double) (s - 1) / samples;
                return new Vec3(px + dx * cut, target.y, pz + dz * cut);
            }
        }
        return target;
    }

    /**
     * Азимут на точку впереди.
     *
     * <p>Если точка почти под ногами (менее трети блока), азимут вырождается:
     * {@code atan2} выдаёт случайный угол, и голова скачет каждый тик — ровно то
     * «крутится на месте», от которого мы лечимся. В этом случае сохраняем
     * направление последнего пройденного участка: человек, дойдя до края или до
     * ступеньки, продолжает шагать вперёд, а не разворачивается.
     */
    private float headingTo(Minecraft mc, Vec3 aim) {
        double dx = aim.x - mc.player.getX();
        double dz = aim.z - mc.player.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal >= 0.35) {
            return (float) Math.toDegrees(Math.atan2(-dx, dz));
        }
        PathNode previous = this.previousNode();
        PathNode next = this.path == null ? null : this.path.current();
        if (previous != null && next != null) {
            double pdx = next.centerX() - previous.centerX();
            double pdz = next.centerZ() - previous.centerZ();
            if (pdx * pdx + pdz * pdz > 0.01) {
                return (float) Math.toDegrees(Math.atan2(-pdx, pdz));
            }
        }
        return this.intentYaw;
    }

    /** Кэш тактического узла: путь и индекс, на которых он считался, и возраст. */
    private PathNode smoothCache;
    private Path smoothCachePath;
    private int smoothCacheIndex = -1;
    private int smoothCacheAge;

    /** Пересчитывать тактический узел не чаще этого, тиков. */
    private static final int SMOOTH_REFRESH_TICKS = 4;

    /**
     * Тактический узел с гистерезисом.
     *
     * <p>{@link #smoothedTarget} проверяет прямой коридор до каждого из
     * кандидатов — это сотни обращений к миру за тик. Пересчитывать его каждый
     * тик и незачем, и вредно: на бегу дальний конец коридора переключается
     * между соседними узлами, и производные от него решения (прыжок, наклон
     * головы) начинают мерцать. Раз в четыре тика или при смене текущего узла —
     * достаточно.
     */
    private PathNode tacticalTarget(Minecraft mc) {
        if (this.smoothCache != null && this.smoothCachePath == this.path
                && this.smoothCacheIndex == this.path.index
                && this.smoothCacheAge < SMOOTH_REFRESH_TICKS) {
            this.smoothCacheAge++;
            return this.smoothCache;
        }
        PathNode fresh = this.smoothedTarget(mc);
        this.smoothCache = fresh;
        this.smoothCachePath = this.path;
        this.smoothCacheIndex = this.path.index;
        this.smoothCacheAge = 0;
        return fresh;
    }

    /** Насколько далеко вперёд смотрим при сглаживании, узлов. */
    private static final int MAX_SMOOTH_AHEAD = 10;

    /** Дальше этого от узла считаем, что сбились с маршрута, и строим заново. */
    private static final double OFF_PATH_DISTANCE = 6.0;

    /**
     * Сбились ли с маршрута.
     *
     * <p>Считаем по расстоянию до ближайшего впереди узла, а не до текущего:
     * текущий может остаться позади просто потому, что мы его обошли. Важно
     * поймать случай «упал в яму и остался внизу», когда весь путь висит на
     * высоте.
     */
    private boolean fellOffPath(Minecraft mc) {
        if (this.path == null || mc.player == null) {
            return false;
        }
        // Считаем расстояние до ЛИНИИ маршрута, а не до точек-узлов.
        //
        // После струнной стяжки узлы стоят разреженно: прямая может тянуться на
        // двадцать-тридцать блоков. Старая проверка «расстояние до ближайшего
        // узла» в середине такой прямой давала десятки блоков — исполнитель
        // каждый тик считал себя сошедшим с маршрута, требовал пересчёт и
        // выходил из apply() ДО управления ногами. Бот вставал как вкопанный в
        // вечном цикле пересчётов («вообще не ходит»).
        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();
        double best = Double.MAX_VALUE;
        int limit = Math.min(this.path.nodes.size() - 1, this.path.index + 8);
        for (int i = Math.max(1, this.path.index); i <= limit; i++) {
            double d = distanceToSegment(px, py, pz,
                    this.path.nodes.get(i - 1), this.path.nodes.get(i));
            if (d < best) {
                best = d;
            }
        }
        if (best == Double.MAX_VALUE) {
            // Вырожденный путь из одного узла — проверяем по-старому.
            PathNode node = this.path.current();
            if (node == null) {
                return false;
            }
            double dx = node.centerX() - px;
            double dy = node.centerY() - py;
            double dz = node.centerZ() - pz;
            best = Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        return best > OFF_PATH_DISTANCE;
    }

    /**
     * Расстояние от точки до отрезка в пространстве.
     *
     * <p>Проекция точки на прямую отрезка зажимается в [0,1]: если игрок ещё не
     * дошёл до начала отрезка или уже прошёл его конец, расстоянием считается
     * расстояние до соответствующего конца.
     */
    private static double distanceToSegment(double px, double py, double pz, PathNode a, PathNode b) {
        double ax = a.centerX();
        double ay = a.centerY();
        double az = a.centerZ();
        double abx = b.centerX() - ax;
        double aby = b.centerY() - ay;
        double abz = b.centerZ() - az;
        double apx = px - ax;
        double apy = py - ay;
        double apz = pz - az;
        double lenSq = abx * abx + aby * aby + abz * abz;
        double t = lenSq < 1.0E-9 ? 0.0 : (apx * abx + apy * aby + apz * abz) / lenSq;
        t = Math.max(0.0, Math.min(1.0, t));
        double dx = apx - abx * t;
        double dy = apy - aby * t;
        double dz = apz - abz * t;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Самый дальний узел, до которого можно дойти по прямой.
     *
     * <p>Это главное, чего не хватало. Раньше исполнитель вёл игрока строго по
     * центрам узлов, по одному: дойди до этого, потом до следующего. На ровном
     * месте это выглядит нормально, но стоит маршруту повернуть — и персонаж
     * послушно выписывает ломаную по клеткам, цепляет углы блоков, застревает,
     * отходит, снова цепляет. Со стороны — «танцует на месте три часа».
     *
     * <p>Живой игрок так не ходит. Он смотрит на маршрут и идёт по нему
     * насколько видно, срезая промежуточные точки. Здесь то же самое: находим
     * самый дальний узел, к которому ведёт свободный прямой коридор, и идём к
     * нему. Промежуточные узлы при этом не игнорируются — они остаются в пути и
     * проверяются на достижение, просто голова и ноги смотрят дальше.
     *
     * @return узел-цель либо {@code null}, если сглаживать нечего
     */
    private PathNode smoothedTarget(Minecraft mc) {
        if (this.path == null || mc.player == null || mc.level == null) {
            return null;
        }
        int limit = Math.min(this.path.nodes.size() - 1,
                this.path.index + MAX_SMOOTH_AHEAD);

        PathNode best = null;
        for (int i = this.path.index; i <= limit; i++) {
            PathNode candidate = this.path.nodes.get(i);
            // Узел с копанием — граница сглаживания.
            //
            // Он лежит внутри блока: «идти по прямой» туда нельзя по определению,
            // и сглаживание через него увело бы исполнителя в стену мимо
            // раскопа. Сначала докапываемся до него, потом сглаживаем дальше.
            if (candidate.type == MovementType.DIG) {
                if (i == this.path.index) {
                    best = candidate;
                }
                break;
            }
            if (!this.canWalkStraight(mc, candidate)) {
                // Дальше проверять бессмысленно: если не видно этого узла, то и
                // все следующие за ним закрыты тем же препятствием.
                break;
            }
            best = candidate;
        }
        return best;
    }

    /**
     * Ведёт ли свободный прямой коридор от игрока к узлу.
     *
     * <p>Проверяем не «видимость», а именно проходимость: на каждом шаге вдоль
     * отрезка должны быть свободны клетка ног и клетка головы, а под ногами —
     * твёрдая опора. Иначе «по прямой» закончится падением с обрыва или
     * застреванием в стене.
     *
     * <p>Узел должен быть на нашей высоте: подъём и спуск сглаживанию не
     * подлежат, их ведёт сам поиск.
     */
    private boolean canWalkStraight(Minecraft mc, PathNode node) {
        double feetY = mc.player.getY();
        if (Math.abs(node.y - feetY) > 0.6) {
            return false;
        }
        double fromX = mc.player.getX();
        double fromZ = mc.player.getZ();
        double dx = node.centerX() - fromX;
        double dz = node.centerZ() - fromZ;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.5) {
            return true;
        }

        int feet = (int) Math.floor(feetY);
        // Шаг в четверть блока: мельче — лишние проверки, крупнее — можно
        // проскочить угловой блок.
        int samples = (int) Math.ceil(length * 4.0);
        for (int s = 1; s <= samples; s++) {
            double t = (double) s / samples;
            int x = (int) Math.floor(fromX + dx * t);
            int z = (int) Math.floor(fromZ + dz * t);
            if (!this.clearAt(mc, x, feet, z) || !this.clearAt(mc, x, feet + 1, z)) {
                return false;
            }
            if (!this.hasFloorAt(mc, x, feet - 1, z)) {
                return false;
            }
        }
        return true;
    }

    private boolean clearAt(Minecraft mc, int x, int y, int z) {
        var pos = new net.minecraft.core.BlockPos(x, y, z);
        return mc.level.getBlockState(pos).getCollisionShape(mc.level, pos).isEmpty();
    }

    /** Прорублены ли обе клетки узла раскопа — ноги и голова. */
    private boolean digCleared(Minecraft mc, PathNode node) {
        return this.clearAt(mc, node.x, node.y, node.z)
                && this.clearAt(mc, node.x, node.y + 1, node.z);
    }

    private boolean hasFloorAt(Minecraft mc, int x, int y, int z) {
        var pos = new net.minecraft.core.BlockPos(x, y, z);
        return !mc.level.getBlockState(pos).getCollisionShape(mc.level, pos).isEmpty();
    }

    private void advanceIfReached(Minecraft mc) {
        while (!this.path.finished()) {
            PathNode node = this.path.current();
            if (node == null) {
                return;
            }
            double dx = node.centerX() - mc.player.getX();
            double dz = node.centerZ() - mc.player.getZ();
            double dy = node.centerY() - mc.player.getY();

            // В воде допуск шире: пловца носит по волнам, и сухопутная точность
            // там недостижима — индекс замирал, и маршрут «заканчивался»
            // посреди водоёма.
            double reach = node.type == MovementType.SWIM ? SWIM_NODE_REACH : NODE_REACH;
            boolean closeHorizontally = dx * dx + dz * dz <= reach * reach;

            // Узел выше нас требует реального подъёма: пока не забрались — не
            // считаем его пройденным, иначе персонаж будет «доходить» до вершины,
            // стоя у её подножия.
            //
            // Узел ниже нас считается достигнутым сразу: мы стоим над ним, и
            // спуск приведёт туда сам. Но допуск ограничен четырьмя блоками —
            // иначе при падении в глубокую воду весь маршрут разом помечался
            // пройденным, и персонаж оставался в воде без цели.
            //
            // Узел плавания в воде засчитывается по одной горизонтали, без окна
            // по высоте: куда плыть, решает сухой выход впереди, а не конкретная
            // клетка воды, и проплыли мы её на поверхности или в толще — неважно.
            //
            // Для узла раскопа допуска нет вовсе: он лежит ВНУТРИ блока, и «мы
            // висим над ним в четырёх блоках» — это не «мы его прошли». С четырёх-
            // блочным допуском индекс перескакивал несколько ступеней разом,
            // текущим становился узел, до которого не дотянуться рукой, и бот
            // замирал в яме, глядя в стену.
            //
            // И главное: узел раскопа пройден, только когда коридор РЕАЛЬНО
            // прорублен. Раньше хватало подойти на 0.8 блока — а подойти вплотную
            // можно к нетронутой стене. Индекс перескакивал стену, бот ломал в
            // лучшем случае один блок, потом упирался в остаток и начинал через
            // него прыгать: «сломал один блок и думает, что красавчик».
            boolean surfaceSwim = mc.player.isInWater() && node.type == MovementType.SWIM;
            boolean reached = surfaceSwim
                    ? closeHorizontally
                    : node.type == MovementType.DIG
                    ? closeHorizontally && dy <= 1.0 && dy >= -1.0 && this.digCleared(mc, node)
                    : closeHorizontally && dy <= 1.6 && dy >= -4.0;

            // Проскочили узел на бегу — не возвращаемся за ним.
            //
            // Это второй источник «кругов»: при беге игрок легко пролетает мимо
            // точки на 0.9 блока, радиус 0.8 не срабатывает, и персонаж
            // разворачивается назад. Через полсекунды — снова пролетает и снова
            // разворачивается. Лечится проекцией: если узел остался ПОЗАДИ
            // направления движения, он пройден.
            //
            // Для узла раскопа правило «недостижимо ниже» НЕ действует: раскоп —
            // это и есть яма под нами, в которую надо войти. Иначе весь маршрут
            // прокопа выбрасывался разом: индекс прыгал к концу, путь объявлялся
            // пройденным, прокоп перестраивался по кругу, а бот не двигался.
            boolean passed = reached
                    || this.isBehind(mc, node, dy)
                    || (node.type != MovementType.DIG && this.isUnreachablyBelow(mc, node, dy));

            if (!passed) {
                return;
            }
            this.path.index++;
        }
    }

    /**
     * Узел висит заметно ниже нас, а мы стоим на опоре — значит он недостижим.
     *
     * <p>Так бывает, когда путь построен от подножия обрыва, а игрок стоит на его
     * вершине: до узла четыре блока вниз, по горизонтали — блок. Исполнитель
     * подходит к краю, «достигает» радиуса 0.8 не может, разворачивается к тому
     * же узлу с другой стороны и так ходит по кругу бесконечно. Ровно то, что
     * выглядело как «бегает по кругу».
     *
     * <p>Правильное решение — не спускаться к нему, а идти дальше по маршруту:
     * следующие узлы уже на нашей высоте.
     */
    private boolean isUnreachablyBelow(Minecraft mc, PathNode node, double dy) {
        if (dy > -1.6) {
            return false;
        }
        if (!mc.player.onGround()) {
            return false;
        }

        // Маршрут продолжает спускаться — это не «недостижимый узел», а лестница
        // вниз. Туда надо шагнуть и спуститься, а не вычёркивать узел.
        //
        // Без этой проверки получался вечный цикл пересчёта: путь на спуск
        // состоял из узлов ниже нас, все они разом объявлялись недостижимыми,
        // индекс доскакивал до конца, путь считался пройденным, движок строил
        // ровно такой же новый — и так девяносто семь раз подряд, стоя на месте.
        PathNode next = this.nodeAt(this.path.index + 1);
        if (next != null && next.y < node.y) {
            return false;
        }

        double dx = node.centerX() - mc.player.getX();
        double dz = node.centerZ() - mc.player.getZ();
        return dx * dx + dz * dz < 3.0 * 3.0;
    }

    /** Узел по индексу или {@code null}, если такого нет. */
    private PathNode nodeAt(int index) {
        if (this.path == null || index < 0 || index >= this.path.nodes.size()) {
            return null;
        }
        return this.path.nodes.get(index);
    }

    /**
     * Остался ли узел позади нас.
     *
     * <p>Считаем по скалярному произведению: вектор на узел против направления
     * от предыдущего узла к текущему. Отрицательное — значит узел сзади.
     */
    private boolean isBehind(Minecraft mc, PathNode node, double dy) {
        if (Math.abs(dy) > 1.6) {
            return false;
        }
        PathNode previous = this.previousNode();
        if (previous == null) {
            return false;
        }
        double segX = node.centerX() - previous.centerX();
        double segZ = node.centerZ() - previous.centerZ();
        double lenSq = segX * segX + segZ * segZ;
        if (lenSq < 1.0E-4) {
            return false;
        }
        double toX = node.centerX() - mc.player.getX();
        double toZ = node.centerZ() - mc.player.getZ();
        double projection = (toX * segX + toZ * segZ) / lenSq;

        // Узел позади — значит мы его прошли, и неважно, насколько далеко ушли.
        //
        // Раньше здесь стоял ещё и предел расстояния (два блока), и он делал
        // ровно одну вредную вещь: на бегу персонаж уходил от узла на десять
        // блоков, узел навсегда оставался «недостигнутым», счётчик пути
        // застревал на нём — и дальше маршрут вёл себя так, будто впереди
        // препятствие. Отсюда были и внезапные пересчёты, и потерянное время.
        //
        // Скалярное произведение уже отвечает на нужный вопрос: отрицательное —
        // узел за спиной. Предел расстояния тут ничего не защищает, а только
        // ломает нормальный бег.
        double horizontalSq = toX * toX + toZ * toZ;
        return projection < -0.05 && horizontalSq < 400.0;
    }

    /** Сколько тиков терпим попытки дойти до одного узла, прежде чем его пропустить. */
    private static final int NODE_WATCHDOG_TICKS = 80;

    private int watchNodeIndex = -1;
    private int watchTicks;
    private double watchLastDist = Double.MAX_VALUE;

    /**
     * Сторож зависания на узле.
     *
     * <p>Даже с правильным стартом бывает, что конкретный узел недостижим: его
     * закрыли блоком, он висит в воде, игрок не может на него встать. Раньше это
     * означало вечное кружение вокруг одной точки. Теперь по истечении таймера
     * узел просто пропускается.
     */
    private void watchNode(Minecraft mc, PathNode node) {
        // На раскопе сторож молчит: там «долго на одном узле» — это и есть работа.
        // Свой предел терпения у копания уже есть (DIG_TIMEOUT_TICKS), и он
        // срабатывает по-настоящему застрявшему ходу.
        if (this.diggingPath) {
            this.watchTicks = 0;
            return;
        }

        // Диагностика застревания: печатаем, что вокруг игрока и куда он
        // целится. Без этого причины «стоит на месте» приходится угадывать.
        if (this.humanizer.blockedTicks() == 20 && System.getenv("VESENCE_PATHDEBUG") != null) {
            this.dumpStuck(mc, node);
        }

        if (this.path.index != this.watchNodeIndex) {
            this.watchNodeIndex = this.path.index;
            this.watchTicks = 0;
            this.watchLastDist = Double.MAX_VALUE;
            return;
        }
        // Пока расстояние до узла сокращается — исполнитель продвигается, и
        // таймер молчит. Долгая прямая после струнной стяжки проходится
        // секундами: фиксированный лимит принимал нормальный бег за зависание,
        // пропускал угол и запускал пересчёт прямо посреди пути.
        if (mc.player != null) {
            double dx = node.centerX() - mc.player.getX();
            double dy = node.centerY() - mc.player.getY();
            double dz = node.centerZ() - mc.player.getZ();
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist < this.watchLastDist - 0.02) {
                this.watchTicks = 0;
            }
            this.watchLastDist = dist;
        }
        if (++this.watchTicks < NODE_WATCHDOG_TICKS) {
            return;
        }
        this.watchTicks = 0;
        this.watchNodeIndex = -1;

        // Узел висит слишком долго — маршрут в этом месте невыполним.
        //
        // Раньше здесь узел просто пропускался, и это было хуже, чем казалось:
        // счётчик пути рос, бот «проходил» узлы, а сам стоял на месте. Со стороны
        // это выглядело как «идёт и не идёт», а в отчёте — как движение вперёд.
        //
        // Живой игрок в такой ситуации не вычёркивает препятствие из плана, а
        // строит маршрут заново от того места, где реально стоит. Поэтому просим
        // пересчёт, а узел пропускаем лишь как страховку на случай, если пересчёт
        // по каким-то причинам не случится.
        this.repathRequested = true;
        this.path.index++;
        if (System.getenv("VESENCE_PATHDEBUG") != null) {
            System.out.println("[exec] узел недостижим, пересчёт: " + node);
        }
    }

    /** Печатает окружение игрока в момент застревания. */
    private void dumpStuck(Minecraft mc, PathNode node) {
        var base = mc.player.blockPosition();
        StringBuilder sb = new StringBuilder();
        sb.append("[stuck] pos=").append(String.format("%.2f/%.2f/%.2f",
                mc.player.getX(), mc.player.getY(), mc.player.getZ()));
        sb.append(" node=").append(node);
        sb.append(" yaw=").append(String.format("%.1f", this.rotation.yaw()));
        sb.append(" intent=").append(String.format("%.1f", this.intentYaw));
        sb.append(" onGround=").append(mc.player.onGround());
        sb.append(" jump=").append(this.jumpRequested);
        sb.append(" | ");
        // Клетка под ногами, на уровне ног и на уровне головы — по вертикали.
        for (int dy = -1; dy <= 2; dy++) {
            var p = base.above(dy);
            var state = mc.level.getBlockState(p);
            boolean solid = !state.getCollisionShape(mc.level, p).isEmpty();
            sb.append("y+").append(dy).append('=')
                    .append(state.getBlock().getDescriptionId().replace("block.minecraft.", ""))
                    .append(solid ? "(тв)" : "").append(' ');
        }
        System.out.println(sb);
    }

    /**
     * Боковой шаг, когда упёрлись.
     *
     * <p>Главная причина «стоит на месте», которую не видно в цифрах: персонаж
     * идёт точно в центр следующего узла, а между ним и узлом — угол блока.
     * Minecraft гасит такое движение о выступ, и игрок не сдвигается вообще,
     * хотя кнопка «вперёд» зажата. Живой человек в этой ситуации просто
     * обходит угол боком.
     *
     * <p>Поэтому при застревании добавляем боковое нажатие — в ту сторону, где
     * действительно свободно. Это не «умный» обход, а ровно то, что делает рука
     * на клавиатуре.
     *
     * @return +1 — влево, −1 — вправо, 0 — не надо
     */
    private float unstickStrafe(Minecraft mc) {
        if (this.humanizer.blockedTicks() < 6 || mc.player == null || mc.level == null) {
            return 0.0f;
        }
        float yaw = this.intentYaw;
        double rad = Math.toRadians(yaw);

        // В Minecraft при yaw = 0 взгляд направлен в +Z, а левая рука — в +X.
        double leftX = Math.cos(rad);
        double leftZ = Math.sin(rad);

        boolean leftOpen = this.sideOpen(mc, leftX, leftZ);
        boolean rightOpen = this.sideOpen(mc, -leftX, -leftZ);

        if (leftOpen && !rightOpen) {
            return 1.0f;
        }
        if (rightOpen && !leftOpen) {
            return -1.0f;
        }
        // Свободно с обеих сторон — идём влево, устойчиво: метание из стороны в
        // сторону только мешает.
        return leftOpen ? 1.0f : 0.0f;
    }

    /** Свободно ли в этом направлении на уровне ног и головы. */
    private boolean sideOpen(Minecraft mc, double dirX, double dirZ) {
        var base = mc.player.blockPosition();
        int x = base.getX() + (int) Math.round(dirX);
        int z = base.getZ() + (int) Math.round(dirZ);
        for (int dy = 0; dy <= 1; dy++) {
            var p = new net.minecraft.core.BlockPos(x, base.getY() + dy, z);
            if (!mc.level.getBlockState(p).getCollisionShape(mc.level, p).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private boolean shouldSprint(Minecraft mc, boolean usingItem) {
        if (!this.sprintAllowed || usingItem || mc.player == null) {
            return false;
        }
        // В воде спринт не работает: ваниль его всё равно сбрасывает, а на
        // поверхности он мешает всплывать.
        if (mc.player.isInWater()) {
            return false;
        }
        if (mc.player.getFoodData().getFoodLevel() <= 6) {
            return false;
        }
        // Решаем по оставшейся ДЛИНЕ маршрута, а не по числу узлов: после
        // струнной стяжки узлы разрежены, и прямая на тридцать блоков — это два
        // узла. Старая проверка «узлов впереди больше трёх» запрещала бег на
        // большей части пути.
        return this.path != null && this.remainingDistance() > 12.0;
    }

    /**
     * Оставшаяся длина маршрута в блоках.
     *
     * <p>Считается с ранним выходом и ограничением по числу отрезков: точное
     * значение дальше порога не нужно, а полный обход длинного пути каждый тик
     * — лишняя работа.
     */
    private double remainingDistance() {
        if (this.path == null || this.path.index >= this.path.nodes.size()) {
            return 0.0;
        }
        PathNode prev = this.path.current();
        if (prev == null) {
            return 0.0;
        }
        double total = 0.0;
        int limit = Math.min(this.path.nodes.size() - 1, this.path.index + 40);
        for (int i = this.path.index + 1; i <= limit; i++) {
            PathNode node = this.path.nodes.get(i);
            double dx = node.centerX() - prev.centerX();
            double dy = node.centerY() - prev.centerY();
            double dz = node.centerZ() - prev.centerZ();
            total += Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (total > 12.0) {
                break;
            }
            prev = node;
        }
        return total;
    }

    /**
     * Прыжок только там, где он нужен.
     *
     * <p>Раньше здесь была проверка «впереди край — прыгаем», и она срабатывала
     * даже когда прыгать было некуда: персонаж сигал в яму, а на дне — снова,
     * потому что край оставался впереди. Теперь прыжок заказывает только поиск
     * пути: он уже проверил, что за провалом есть куда приземлиться.
     */
    private boolean shouldJump(Minecraft mc, PathNode node, float absErr) {
        if (mc.player == null) {
            return false;
        }

        // В воде «прыжок» — это всплытие. Именно так из неё и выбираются:
        // зажать пробел и плыть к берегу. Без этого персонаж тонул и бился
        // о дно, потому что единственное доступное движение — вниз.
        if (mc.player.isInWater()) {
            return true;
        }

        if (!mc.player.onGround()) {
            return false;
        }

        double dy = node.centerY() - mc.player.getY();

        // Мы ниже маршрута — значит провалились мимо. Прыгать бессмысленно,
        // нужно перестраивать путь, а не подпрыгивать на дне.
        if (dy < -0.8) {
            return false;
        }

        double dx = node.centerX() - mc.player.getX();
        double dz = node.centerZ() - mc.player.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        // Упёрлись в препятствие — пробуем перепрыгнуть.
        //
        // Проверка стоит ДО разбора типа узла намеренно. Раньше она была в конце,
        // и любой узел типа «спуск» или «подъём» её обходил: бот упирался в
        // ступеньку, тип узла не совпадал, прыжка не было — и он толкался в стену,
        // пока сторож не выбрасывал узел из маршрута. Со стороны это «тупой бот
        // стоит и не идёт». Живой игрок в такой момент просто прыгает.
        //
        // Но если впереди раскоп — НЕ прыгает: стену, которую надо проломить,
        // перепрыгнуть нельзя, и подскоки у неё — это ровно то «сломал блок и
        // запрыгивает», на которое жаловались. Ждём, пока подойдём и прорубим.
        if (this.humanizer.blockedTicks() >= 3 && !this.diggingPath) {
            return true;
        }

        if (node.type == MovementType.JUMP_GAP) {
            // Прыгать через провал можно только с ЕГО уровня. Если мы уже ниже
            // точки приземления, мы в яме, и любой прыжок — просто подскок на дне.
            if (Math.abs(dy) > 0.6) {
                return false;
            }
            // Отталкиваемся от края: ждать точку приземления поздно, там обрыв.
            PathNode previous = this.previousNode();
            if (previous != null) {
                double px = previous.centerX() - mc.player.getX();
                double pz = previous.centerZ() - mc.player.getZ();
                if (px * px + pz * pz <= 1.4 * 1.4) {
                    return true;
                }
            }
            return horizontal <= 3.2;
        }

        if (node.type == MovementType.ASCEND) {
            // Подъём — только если цель действительно выше нас.
            return dy > 0.3 && horizontal <= 1.9;
        }

        // Прыжок на бегу. Это не украшение: в Minecraft прыжок сохраняет скорость
        // бега и не тратит её на трение о землю, поэтому «бег с подскоками»
        // реально быстрее обычного бега. Живые игроки так и делают, когда спешат.
        //
        // Прыгаем на КАЖДОМ приземлении, а не по таймеру. Таймер давал ровно то,
        // на что была жалоба: бежит — прыгнул — добежал до приземления и ещё
        // немного пробежал — снова прыгнул. Рваный ритм. Живой игрок в Minecraft
        // держит пробел, и персонаж прыгает сразу, как только коснулся земли:
        // движение выходит ровным и быстрым.
        //
        // Но живой игрок ещё и СМОТРИТ, где прыгает: перед поворотом подскоки
        // прекращаются (в воздухе управление слабое, пролёт мимо линии — это
        // зигзаг-коррекция), в низкий потолок никто не прыгает (удар головой —
        // потеря скорости), у края обрыва — тоже. Те же три проверки здесь.
        //
        // onGround здесь уже проверен выше, так что «прыгать всегда» безопасно:
        // ваниль просто не даст прыгнуть в воздухе.
        if (this.sprintingNow && Math.abs(dy) < 0.7 && absErr < STRAFE_ASSIST_OFF
                && !this.diggingPath && this.hopCornerFar(mc) && this.hopCorridorClear(mc)) {
            double speedSq = mc.player.getDeltaMovement().horizontalDistanceSqr();
            // Если мы ещё не разогнались, прыгать не будем: иначе, стоя на
            // месте в ожидании поворота, будем подпрыгивать на месте.
            return speedSq > 0.02;
        }

        return false;
    }

    /**
     * Далеко ли ещё угол, в который мы поворачиваем.
     *
     * <p>Спринт-прыжок прямо перед поворотом — это пролёт мимо маршрута:
     * в воздухе руление слабое, и приземлившись, персонаж уходит в сторону и
     * потом зигзагом возвращается. Игрок так не делает — он гасит подскоки
     * заранее.
     */
    private boolean hopCornerFar(Minecraft mc) {
        PathNode corner = this.path != null ? this.path.current() : null;
        if (corner == null || mc.player == null) {
            return false;
        }
        double dx = corner.centerX() - mc.player.getX();
        double dz = corner.centerZ() - mc.player.getZ();
        return dx * dx + dz * dz > HOP_CORNER_DISTANCE * HOP_CORNER_DISTANCE;
    }

    /**
     * Есть ли впереди место для прыжка: пол под ногами и потолок не ниже
     * четырёх блоков на четыре блока вперёд по курсу.
     *
     * <p>Прыжок головой в потолок обрывает спринт, прыжок за край обрыва —
     * падение с маршрута. Обе ситуации живой игрок читает глазами заранее и
     * просто не прыгает.
     */
    private boolean hopCorridorClear(Minecraft mc) {
        if (mc.player == null) {
            return false;
        }
        double yawRad = Math.toRadians(this.rotation.yaw());
        double dirX = -Math.sin(yawRad);
        double dirZ = Math.cos(yawRad);
        int feet = (int) Math.floor(mc.player.getY());
        double px = mc.player.getX();
        double pz = mc.player.getZ();
        for (double d = 0.5; d <= 4.0; d += 0.5) {
            int x = (int) Math.floor(px + dirX * d);
            int z = (int) Math.floor(pz + dirZ * d);
            if (!this.clearAt(mc, x, feet, z) || !this.clearAt(mc, x, feet + 1, z)
                    || !this.clearAt(mc, x, feet + 2, z) || !this.clearAt(mc, x, feet + 3, z)
                    || !this.hasFloorAt(mc, x, feet - 1, z)) {
                return false;
            }
        }
        return true;
    }

    private PathNode previousNode() {
        if (this.path == null) {
            return null;
        }
        int index = this.path.index - 1;
        if (index < 0 || index >= this.path.nodes.size()) {
            return null;
        }
        return this.path.nodes.get(index);
    }

    private void trackStuck(Minecraft mc) {
        // На раскопе «стоит на месте» — это норма, а не застревание: копание по
        // определению неподвижно. Но и слепым быть нельзя: если узел раскопа
        // оказался вне руки и дойти до него нечем, бот встанет навсегда. Поэтому
        // на раскопе порог не отключается, а растягивается: настоящая работа
        // укладывается в него с запасом, а вечное стояние — нет.
        int limit = this.diggingPath ? DIG_STUCK_TICKS : REPATH_AFTER_STUCK_TICKS;

        // Смотрим не на флаг «упёрлись», а на реальное смещение.
        //
        // Флаг приходит из Humanizer, а тот обнуляется на спринте: бот,
        // бегущий в стену или склон, спринт не терял, счётчик постоянно
        // сбрасывался, и сторож не срабатывал никогда. Координаты не врут:
        // если за тик не сдвинулись ни на сантиметр — мы не идём, а упираемся.
        double x = mc.player.getX();
        double z = mc.player.getZ();
        boolean moved;
        if (Double.isNaN(this.lastTrackX)) {
            moved = true;
        } else {
            double dx = x - this.lastTrackX;
            double dz = z - this.lastTrackZ;
            moved = dx * dx + dz * dz > 1.0E-4;
        }
        this.lastTrackX = x;
        this.lastTrackZ = z;

        if (!moved) {
            this.stuckTicks++;
        } else {
            this.stuckTicks = 0;
        }
        if (this.stuckTicks > limit) {
            this.repathRequested = true;
            this.stuckTicks = 0;
        }
    }

    /** Предел терпения для стояния на месте во время раскопа. */
    private static final int DIG_STUCK_TICKS = 140;

    /**
     * Есть ли впереди узел раскопа.
     *
     * <p>Смотрим несколько узлов вперёд, а не только текущий: между двумя
     * раскопами бывает обычный шаг, и на нём счётчик застревания успевал
     * сработать.
     */
    private boolean hasDigAhead() {
        if (this.path == null) {
            return false;
        }
        int limit = Math.min(this.path.nodes.size() - 1, this.path.index + 4);
        for (int i = this.path.index; i <= limit; i++) {
            if (this.path.nodes.get(i).type == MovementType.DIG) {
                return true;
            }
        }
        return false;
    }

    /**
     * Прорубает ход к узлу {@link MovementType#DIG}.
     *
     * <p>Порядок как у игрока: подобрать инструмент, навести взгляд, сломать
     * блок в ногах, потом в голове. Копать головой вперёд нельзя — застрянешь.
     *
     * @return {@code true}, если исполнитель занят копанием и идти не должен
     */
    private boolean applyDig(Minecraft mc, EventInput event, PathNode node) {
        BlockPos feet = node.pos();
        BlockPos head = feet.above();

        boolean feetClear = this.clearAt(mc, feet.getX(), feet.getY(), feet.getZ());
        boolean headClear = this.clearAt(mc, head.getX(), head.getY(), head.getZ());
        if (feetClear && headClear) {
            // Уже прорублено (например, другой игрок постарался) — просто идём.
            this.digNodeKey = 0L;
            this.digTicks = 0;
            return false;
        }

        // Блок для работы — тот, что ещё цел.
        BlockPos target = feetClear ? head : feet;
        if (!BlockInteraction.inReach(mc, target)) {
            // Ещё далеко: пусть обычная ходьба подведёт ближе.
            //
            // Но если ближе не становится (стена, обрыв, вода), это тупик, а не
            // прогулка. Раньше ветка просто возвращала false, счётчики не росли,
            // и оба сторожа молчали: узловой на раскопе не следит, а застревание
            // сбрасывал спринт. Бот вечно упирался в склон рядом с недостижимой
            // клеткой раскопа. Теперь тупик копится и заканчивается пересчётом.
            long farKey = node.key();
            if (farKey != this.digNodeKey) {
                this.digNodeKey = farKey;
                this.digTicks = 0;
            }
            if (++this.digTicks > DIG_UNREACHABLE_TICKS) {
                this.digNodeKey = 0L;
                this.digTicks = 0;
                this.repathRequested = true;
            }
            return false;
        }

        // Счётчик — на конкретный узел: перешли к следующему, счёт заново.
        long key = node.key();
        if (key != this.digNodeKey) {
            this.digNodeKey = key;
            this.digTicks = 0;
        }

        // Предел терпения. Обсидиан без нужной кирки не поддаётся вообще, и без
        // этого предела исполнитель буравил бы его вечно, не сдвинув маршрут.
        if (++this.digTicks > DIG_TIMEOUT_TICKS) {
            this.digNodeKey = 0L;
            this.digTicks = 0;
            BlockInteraction.stop(mc);
            this.repathRequested = true;
            return true;
        }

        // Инструмент — каждый тик: предмет мог сломаться, а следующий блок может
        // требовать другой инструмент.
        ToolSelector.equipFor(mc, target);

        // Встаём на месте и смотрим на блок.
        this.stopMovement(event);
        this.jumpRequested = false;

        this.intentYaw = this.yawToBlock(mc, target);
        this.intentPitch = this.pitchToBlock(mc, target);
        this.rotation.setTarget(this.intentYaw, this.intentPitch);
        this.rotation.update();
        mc.player.setYRot(this.rotation.yaw());
        mc.player.setXRot(this.rotation.pitch());

        BlockInteraction.breakBlock(mc, target);
        return true;
    }

    /** Курс на центр блока. */
    private float yawToBlock(Minecraft mc, BlockPos pos) {
        double dx = (pos.getX() + 0.5) - mc.player.getX();
        double dz = (pos.getZ() + 0.5) - mc.player.getZ();
        if (dx * dx + dz * dz < 0.0001) {
            return mc.player.getYRot();
        }
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /** Наклон на центр блока. Положительный — вниз, как в Minecraft. */
    private float pitchToBlock(Minecraft mc, BlockPos pos) {
        double dx = (pos.getX() + 0.5) - mc.player.getX();
        double dz = (pos.getZ() + 0.5) - mc.player.getZ();
        double dy = (pos.getY() + 0.5) - mc.player.getEyeY();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        return (float) -Math.toDegrees(Math.atan2(dy, horizontal));
    }

    private void stopMovement(EventInput event) {
        event.setForward(0.0f);
        event.setStrafe(0.0f);
        event.setJump(false);
        event.setSprint(false);
    }

    private int debugTicks;

    /** Диагностика исполнителя: включается {@code VESENCE_PATHDEBUG=1}. */
    private void debugTick(Minecraft mc, PathNode node, boolean sprinting) {
        if (System.getenv("VESENCE_PATHDEBUG") == null) {
            return;
        }
        if (++this.debugTicks % 20 != 0) {
            return;
        }
        System.out.println("[exec] idx=" + this.path.index + "/" + this.path.nodes.size()
                + " node=" + node.type + "(" + node.x + "," + node.y + "," + node.z + ")"
                + " pos=" + String.format("%.1f/%.1f/%.1f",
                        mc.player.getX(), mc.player.getY(), mc.player.getZ())
                + " yaw=" + String.format("%.1f", mc.player.getYRot())
                + " intent=" + String.format("%.1f", this.intentYaw)
                + " jump=" + this.jumpRequested
                + " sprint=" + sprinting
                + " onGround=" + mc.player.onGround());
    }

    public boolean isJumpRequested() {
        return this.jumpRequested;
    }

    public Vec3 playerPos(Minecraft mc) {
        return mc.player == null ? Vec3.ZERO : mc.player.position();
    }
}
