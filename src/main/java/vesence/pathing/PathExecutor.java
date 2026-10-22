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

    private final Humanizer humanizer = new Humanizer();
    private final RotationSmoother rotation = new RotationSmoother(0.0f, 0.0f);

    private Path path;
    private int stuckTicks;

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

    /** Были в воде в прошлом тике — чтобы перестроить путь после выхода на берег. */
    private boolean wasInWater;

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
        this.path = path;
        this.stuckTicks = 0;
        this.repathRequested = false;
        this.jumpRequested = false;
        this.wasInWater = false;
        this.humanizer.reset();

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
        this.wasInWater = false;
        this.digTicks = 0;
        this.digNodeKey = 0L;
        this.hasHeading = false;
        this.movingNow = false;
        this.busyNow = false;
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

        // По умолчанию считаем, что стоим: ниже любой выход по раннему
        // возврату оставит это значение, и взгляд сам вернётся вперёд.
        this.movingNow = false;
        this.busyNow = false;

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
            this.busyNow = true;
            this.applySwim(mc, event);
            return;
        }

        // Выбрались на берег. Пересчитывать путь НЕ надо: узлы впереди и так
        // лежат на суше, а новый расчёт от берега легко снова уводил в воду —
        // со стороны это выглядело как «вылез и опять побежал в воду».
        // Просто сдвигаем индекс за водные узлы и идём дальше.
        if (this.wasInWater) {
            this.wasInWater = false;
            // A brief surface jump does not mean the entire crossing was completed.
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

        // Целимся не в текущий узел, а в самый дальний, до которого можно дойти
        // по прямой. Это и есть главное отличие от «шаг за шагом по центрам».
        PathNode node = this.smoothedTarget(mc);
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
            this.busyNow = true;
            if (this.applyDig(mc, event, node)) {
                return;
            }
        }
        this.busyNow = false;
        this.movingNow = true;

        this.intentYaw = this.headingWithDeadband(this.headingFor(mc, node));
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
        //
        // Осматривание по сторонам добавляется уже поверх этого угла и только
        // для камеры — курс движения оно не трогает.
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

        // Тело уже смотрит на узел, поэтому «вперёд» — это ровно нужное
        // направление. Никакой раскладки по WASD не требуется.
        event.setForward(1.0f);
        event.setStrafe(this.unstickStrafe(mc));

        this.jumpRequested = this.shouldJump(mc, node);
        event.setJump(this.jumpRequested);
        event.setSprint(sprinting);
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
            this.cameraYaw = travelYaw;
            this.bodyYaw = travelYaw;
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
     * <p>Сорок градусов — это уже не изгиб маршрута, а смена направления
     * движения. Всё, что меньше, картинка переживает неподвижно.
     */
    private static final float CAMERA_FOLLOW_ANGLE = 40.0f;

    /** Скорость подтягивания камеры, градусов в секунду. Медленно — нарочно. */
    private static final float CAMERA_SPEED = 45.0f;

    /** Когда камера разворачивается сразу: персонаж почти убежал из кадра. */
    private static final float CAMERA_SNAP = 150.0f;

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

    /** Идём ли в этом тике — нужно осматриванию, чтобы не вертеться на месте. */
    private boolean movingNow;

    /** Заняты ли делом, при котором смотреть по сторонам вредно. */
    private boolean busyNow;

    /**
     * Поведение в воде: плыть к сухой части маршрута.
     *
     * <p>Раньше здесь искался «ближайший берег» кольцами вокруг игрока. Это была
     * ошибка: ближайший сухой блок — не обязательно тот, куда нам надо. Персонаж
     * выбирался на мыс в стороне, пересчитывал путь, тот снова шёл через воду —
     * и он прыгал обратно. Отсюда «плавает вдоль берега и лезет в воду».
     *
     * <p>Правило простое и без исключений: плывём в сторону цели, всплываем
     * пробелом, вылезаем на первую же кромку, которая выше воды. Ни одного пути,
     * ведущего к остановке: раньше здесь можно было запросить пересчёт и встать
     * в воде, если рядом не нашлось берега.
     */
    private void applySwim(Minecraft mc, EventInput event) {
        // Follow adjacent route nodes, not a distant bank through intervening walls.
        if (this.path.finished()) {
            this.stopMovement(event);
            this.repathRequested = true;
            return;
        }
        PathNode target = this.path.current();
        double aimX = target.centerX();
        double aimZ = target.centerZ();
        this.movingNow = true;
        this.humanizer.tick(true, false, false);
        // Refresh observations in water too: previously they stayed frozen forever.
        if (this.humanizer.blockedTicks() >= 40) {
            this.repathRequested = true;
        }
        double dx = aimX - mc.player.getX();
        double dz = aimZ - mc.player.getZ();
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

        event.setForward(1.0f);
        event.setStrafe(0.0f);
        // Пробел в воде — всплытие. Держим зажатым: он и поднимает к поверхности,
        // и позволяет зацепиться за кромку берега, когда доплыли.
        event.setJump(true);
        event.setSprint(false);
        event.setSneak(false);

        this.jumpRequested = true;
        this.sprintingNow = false;
        this.wasInWater = true;

        this.debugSwim(mc, yaw, target != null ? String.valueOf(target) : "берег/цель");
    }

    /**
     * Ищет кромку берега, к которой можно пристать.
     *
     * <p>Условия кромки жёсткие, иначе персонаж плывёт к тому, на что не
     * забраться:
     * <ul>
     *   <li>блок сухой и проходимый, под ним твёрдая опора (не вода);</li>
     *   <li>рядом с ним по горизонтали есть вода — иначе это не берег, а суша
     *       в глубине, и до неё всё равно не добраться вплавь;</li>
     *   <li>высота не выше двух блоков над ногами — иначе не выпрыгнуть.</li>
     * </ul>
     *
     * <p>Выбирается не просто ближайшая кромка, а ближайшая В СТОРОНУ ЦЕЛИ.
     * Это принципиально: без поправки на цель персонаж выбирался на тот берег,
     * с которого только что сошёл, пересчитывал путь, тот снова вёл через воду —
     * и он прыгал обратно. Со стороны это выглядело как «плавает вдоль берега
     * и лезет в воду».
     *
     * @return пара {@code {dx, dz}} от игрока до центра кромки либо {@code null}
     */
    private double[] findExitBank(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            return null;
        }
        int px = mc.player.blockPosition().getX();
        int py = mc.player.blockPosition().getY();
        int pz = mc.player.blockPosition().getZ();

        double bestScore = Double.MAX_VALUE;
        double[] best = null;

        final int radius = 14;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double distSq = (double) dx * dx + (double) dz * dz;
                if (distSq < 0.5) {
                    continue;
                }
                for (int dy = -1; dy <= 2; dy++) {
                    int x = px + dx;
                    int y = py + dy;
                    int z = pz + dz;
                    if (!this.isBank(mc, x, y, z)) {
                        continue;
                    }
                    // Цена = путь до кромки + штраф за удалённость от цели.
                    // Множитель подобран так, чтобы кромка «за спиной» проигрывала
                    // кромке впереди даже если до неё плыть чуть дольше.
                    double score = distSq + this.goalHeuristic(x, y, z) * 2.0;
                    if (score < bestScore) {
                        bestScore = score;
                        best = new double[]{dx + 0.5, dz + 0.5};
                    }
                    break;
                }
            }
        }
        return best;
    }

    /** Оценка расстояния до цели — чтобы выбирать берег в её сторону. */
    private double goalHeuristic(int x, int y, int z) {
        if (this.path == null || this.path.goal == null) {
            return 0.0;
        }
        return this.path.goal.heuristic(x, y, z);
    }

    /** Сухая кромка, к которой можно пристать из воды. */
    private boolean isBank(Minecraft mc, int x, int y, int z) {
        var pos = new net.minecraft.core.BlockPos(x, y, z);
        var at = mc.level.getBlockState(pos);
        var below = mc.level.getBlockState(pos.below());

        // Сам блок должен быть проходим, а опора под ним — твёрдой и сухой.
        if (!at.getCollisionShape(mc.level, pos).isEmpty() || at.liquid()) {
            return false;
        }
        if (below.liquid() || below.getCollisionShape(mc.level, pos.below()).isEmpty()) {
            return false;
        }
        if (!mc.level.getBlockState(pos.above()).getCollisionShape(mc.level, pos.above()).isEmpty()) {
            return false;
        }

        // И рядом должна быть вода: иначе это суша в глубине, до неё не доплыть.
        return mc.level.getBlockState(pos.north()).liquid()
                || mc.level.getBlockState(pos.south()).liquid()
                || mc.level.getBlockState(pos.east()).liquid()
                || mc.level.getBlockState(pos.west()).liquid();
    }

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

    /** Пропускает узлы, оставшиеся в воде: мы уже вышли, плыть к ним незачем. */
    private void skipWaterNodes(Minecraft mc) {
        if (this.path == null) {
            return;
        }
        while (!this.path.finished()) {
            PathNode node = this.path.current();
            if (node == null || !this.isWaterAt(mc, node.x, node.y, node.z)) {
                return;
            }
            this.path.index++;
        }
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
     * Мёртвая зона курса: на сколько градусов должна отличаться новая цель, чтобы
     * вообще стоило поворачивать тело.
     *
     * <p>Зачем. Узлы маршрута стоят по сетке, и на диагональном участке курс к
     * следующему узлу гуляет на десять-двадцать градусов туда-обратно каждый тик.
     * Сглаживание это сглаживает, но не убирает: персонаж мелко поворачивается
     * всё время, пока бежит, — со стороны «вертится». Живой игрок так не делает:
     * он держит направление, а мелкие поправки делает ногами.
     *
     * <p>Девяти градусов достаточно, чтобы погасить дрожание сетки, и мало, чтобы
     * персонаж ушёл с маршрута: поправка всё равно возвращается, как только
     * отклонение перерастёт порог.
     */
    private static final float HEADING_DEADBAND = 9.0f;

    /**
     * Курс тела с мёртвой зоной.
     *
     * @return угол, который следует держать: прежний, если новый отличается
     *         незначительно, иначе новый
     */
    private float headingWithDeadband(float desired) {
        if (!this.hasHeading) {
            this.hasHeading = true;
            this.heading = desired;
            return desired;
        }
        if (Math.abs(net.minecraft.util.Mth.wrapDegrees(desired - this.heading)) < HEADING_DEADBAND) {
            return this.heading;
        }
        this.heading = desired;
        return desired;
    }

    private float heading;
    private boolean hasHeading;

    /** Куда смотреть: на подъёме чуть вверх, на спуске чуть вниз. */
    private float pitchFor(PathNode node) {
        return switch (node.type) {
            case ASCEND, JUMP_GAP -> 2.0f;
            case DESCEND, FALL -> 14.0f;
            default -> WALK_PITCH;
        };
    }

    /**
     * Куда смотреть и куда идти.
     *
     * <p>Обычный случай — направление на центр узла. Но есть два положения, где
     * так считать нельзя, и оба давали «крутится на месте»:
     *
     * <ul>
     *   <li>стоим почти ровно над узлом, который ниже нас (перед обрывом) —
     *       горизонтальная разница близка к нулю, и {@code atan2} выдаёт мусор:
     *       голова и курс скачут на случайный угол каждый тик;</li>
     *   <li>летим вниз — управлять направлением всё равно нельзя.</li>
     * </ul>
     *
     * <p>В обоих случаях сохраняем направление участка, по которому пришли:
     * человек, дойдя до края, продолжает шагать вперёд, а не разворачивается.
     */
    private float headingFor(Minecraft mc, PathNode node) {
        double dx = node.centerX() - mc.player.getX();
        double dz = node.centerZ() - mc.player.getZ();
        double dy = node.centerY() - mc.player.getY();
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        boolean aboveLowerNode = horizontal < 0.9 && dy < -0.8;
        if (!aboveLowerNode && horizontal >= 0.05) {
            return (float) Math.toDegrees(Math.atan2(-dx, dz));
        }

        // Направление предыдущего участка: от узла, где мы были, к текущему.
        PathNode previous = this.previousNode();
        if (previous != null) {
            double pdx = node.centerX() - previous.centerX();
            double pdz = node.centerZ() - previous.centerZ();
            if (pdx * pdx + pdz * pdz > 0.01) {
                return (float) Math.toDegrees(Math.atan2(-pdx, pdz));
            }
        }
        return this.intentYaw;
    }

    /** Насколько далеко вперёд смотрим при сглаживании, узлов. */
    private static final int MAX_SMOOTH_AHEAD = 24;

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
        double best = Double.MAX_VALUE;
        int limit = Math.min(this.path.nodes.size() - 1, this.path.index + 8);
        for (int i = this.path.index; i <= limit; i++) {
            PathNode node = this.path.nodes.get(i);
            double dx = node.centerX() - mc.player.getX();
            double dz = node.centerZ() - mc.player.getZ();
            double dy = node.centerY() - mc.player.getY();
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d < best) {
                best = d;
            }
        }
        return best > OFF_PATH_DISTANCE;
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

            boolean closeHorizontally = dx * dx + dz * dz <= NODE_REACH * NODE_REACH;

            // Узел выше нас требует реального подъёма: пока не забрались — не
            // считаем его пройденным, иначе персонаж будет «доходить» до вершины,
            // стоя у её подножия.
            //
            // Узел ниже нас считается достигнутым сразу: мы стоим над ним, и
            // спуск приведёт туда сам. Но допуск ограничен четырьмя блоками —
            // иначе при падении в глубокую воду весь маршрут разом помечался
            // пройденным, и персонаж оставался в воде без цели.
            //
            // Для узла раскопа допуска нет вовсе: он лежит ВНУТРИ блока, и «мы
            // висим над ним в четырёх блоках» — это не «мы его прошли». С четырёх-
            // блочным допуском индекс перескакивал несколько ступеней разом,
            // текущим становился узел, до которого не дотянуться рукой, и бот
            // замирал в яме, глядя в стену.
            boolean surfaceSwim = mc.player.isInWater() && node.type == MovementType.SWIM;
            boolean reached = surfaceSwim
                    ? closeHorizontally && mc.player.getY() >= node.y - 0.6
                    : node.type == MovementType.DIG
                    ? closeHorizontally && dy <= 1.0 && dy >= -1.0
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
            return;
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
        // Решаем по длине ОСТАВШЕГОСЯ пути, а не по расстоянию до текущего узла:
        // узлы стоят через блок, и «далеко ли до узла» почти всегда false.
        return this.path != null && this.path.nodes.size() - this.path.index > 3;
    }

    /**
     * Прыжок только там, где он нужен.
     *
     * <p>Раньше здесь была проверка «впереди край — прыгаем», и она срабатывала
     * даже когда прыгать было некуда: персонаж сигал в яму, а на дне — снова,
     * потому что край оставался впереди. Теперь прыжок заказывает только поиск
     * пути: он уже проверил, что за провалом есть куда приземлиться.
     */
    private boolean shouldJump(Minecraft mc, PathNode node) {
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
        if (this.humanizer.blockedTicks() >= 3) {
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
        // onGround здесь уже проверен выше, так что «прыгать всегда» безопасно:
        // ваниль просто не даст прыгнуть в воздухе.
        if (this.sprintingNow && Math.abs(dy) < 0.7) {
            double speedSq = mc.player.getDeltaMovement().horizontalDistanceSqr();
            return speedSq > 0.02;
        }

        return false;
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
        if (this.humanizer.isBlocked()) {
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
