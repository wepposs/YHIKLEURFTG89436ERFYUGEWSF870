package vesence.pathing;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Взаимодействие с блоками: посмотреть и сломать.
 *
 * <p>Ломание идёт через клавишу атаки, а не через прямые вызовы
 * {@code MultiPlayerGameMode}.
 *
 * <p>Это не стилистическое предпочтение, а единственный работающий способ.
 * Ваниль в своём тике сама останавливает ломание, если клавиша атаки не нажата
 * ({@code Minecraft} вызывает {@code stopDestroyBlock} в двух местах). Первая
 * версия дёргала {@code startDestroyBlock}/{@code continueDestroyBlock} напрямую
 * — и каждый следующий тик ваниль обнуляла прогресс. Снаружи это выглядело так:
 * персонаж стоит вплотную к блоку, бьёт по нему, а блок не ломается никогда.
 *
 * <p>Поэтому мы делаем ровно то, что делает игрок: наводим взгляд на блок и
 * держим атаку. Всё остальное — забота ванили.
 */
@Environment(EnvType.CLIENT)
public final class BlockInteraction {

    /** Дальность действия руки, блоков. */
    public static final double REACH = 4.5;

    /**
     * Запас, который оставляем при выборе места для стоянки.
     *
     * <p>Исполнитель останавливается, не доходя до узла, а не точно в его центре.
     * Если выбирать клетку ровно на пределе досягаемости, после подхода блок
     * окажется чуть дальше руки — и процесс будет бесконечно брать новые цели,
     * ни одну не достигая. Запас в блок с лишним это и закрывает.
     */
    public static final double STAND_REACH_MARGIN = 1.2;

    /** Уже начали ломание текущего блока — значит нужен continue, а не start. */
    private static BlockPos startedOn;

    /**
     * Блок, по которому сейчас идёт работа.
     *
     * <p>Пока он задан, прицел подменяется на него, а ванильная обработка атаки
     * перехватывается (см. {@code MinecraftClientMixin}).
     */
    private static BlockPos forcedTarget;

    private BlockInteraction() {
    }

    /** Находится ли блок в пределах вытянутой руки. */
    public static boolean inReach(Minecraft mc, BlockPos pos) {
        if (mc.player == null) {
            return false;
        }
        Vec3 center = Vec3.atCenterOf(pos);
        return mc.player.getEyePosition().distanceTo(center) <= REACH;
    }

    /**
     * Дотянется ли игрок до блока, если встанет в указанную клетку.
     *
     * <p>Считаем с запасом {@link #STAND_REACH_MARGIN}: место для стоянки должно
     * оставлять руке свободу, иначе после подхода блок окажется за пределами
     * досягаемости и работа не сдвинется с места.
     *
     * <p>Нужно, чтобы заранее отбросить блоки, к которым подойти некуда: иначе
     * поиск пути строит маршрут в точку, откуда блок всё равно не достать.
     */
    public static boolean inReachFrom(BlockPos stand, BlockPos block) {
        // Глаза примерно на 1.6 над ногами — этого достаточно для проверки.
        double dx = (block.getX() + 0.5) - (stand.getX() + 0.5);
        double dy = (block.getY() + 0.5) - (stand.getY() + 1.6);
        double dz = (block.getZ() + 0.5) - (stand.getZ() + 0.5);
        double limit = REACH - STAND_REACH_MARGIN;
        return Math.sqrt(dx * dx + dy * dy + dz * dz) <= limit;
    }

    /**
     * Виден ли блок из указанной клетки — то есть не закрыт ли он другим.
     *
     * <p>Проверка обязательна, потому что ломание идёт через клавишу атаки: ваниль
     * бьёт по тому, что под прицелом. Если нужный блок закрыт соседним, ваниль
     * будет ломать соседний, а наш прогресс не сдвинется — снаружи это выглядит
     * как «стоит вплотную, бьёт и не ломает».
     *
     * <p>Луч пускаем из глаз в точке стоянки, а не из текущих глаз: важно знать,
     * будет ли блок виден ТАМ, куда мы собираемся прийти.
     */
    public static boolean visibleFrom(Minecraft mc, BlockPos stand, BlockPos block) {
        if (mc.level == null || mc.player == null) {
            return false;
        }
        Vec3 eye = new Vec3(stand.getX() + 0.5, stand.getY() + 1.62, stand.getZ() + 0.5);
        return canSee(mc, eye, block);
    }

    /**
     * Виден ли блок из текущего положения глаз.
     *
     * <p>В отличие от {@link #visibleFrom}, отвечает на вопрос «если навести
     * взгляд сейчас, попадём ли мы в нужный блок», а не «попадём ли, когда
     * придём на место».
     */
    public static boolean visibleFromHere(Minecraft mc, BlockPos block) {
        if (mc.level == null || mc.player == null) {
            return false;
        }
        return canSee(mc, mc.player.getEyePosition(), block);
    }

    private static boolean canSee(Minecraft mc, Vec3 eye, BlockPos block) {
        Vec3 target = Vec3.atCenterOf(block);
        ClipContext context = new ClipContext(eye, target,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player);
        BlockHitResult hit = mc.level.clip(context);
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(block);
    }

    /**
     * Поворачивает игрока на центр блока.
     *
     * <p>Без этого ваниль не считает блок доступным для ломания: она проверяет,
     * что игрок смотрит примерно в его сторону.
     */
    public static void lookAt(Minecraft mc, BlockPos pos) {
        if (mc.player == null) {
            return;
        }
        Vec3 eye = mc.player.getEyePosition();
        Vec3 target = Vec3.atCenterOf(pos);
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        // Тангаж в Minecraft положительный вниз, поэтому знак обратный.
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));

        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
    }

    /**
     * Ломает блок. Вызывать каждый тик, пока блок не исчезнет.
     *
     * @return {@code true}, когда блок уже сломан
     */
    public static boolean breakBlock(Minecraft mc, BlockPos pos) {
        if (mc.level == null || mc.player == null || mc.gameMode == null) {
            return false;
        }
        if (mc.level.getBlockState(pos).isAir()) {
            // Блок уже сломан — работа окончена, кнопку отпускаем.
            reset();
            mc.options.keyAttack.setDown(false);
            return true;
        }

        lookAt(mc, pos);
        forcedTarget = pos;

        // Прицел задаём сами: ваниль пересчитывает его до нас и иначе бьёт по
        // соседнему блоку.
        Direction face = faceToward(mc, pos);
        mc.hitResult = new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false);

        // Держим кнопку атаки нажатой.
        //
        // Это оказалось решающим. Ваниль строит ломание только в обработчике
        // нажатой атаки: он и начинает блок, и продолжает его, и накапливает
        // прогресс. Прямые вызовы {@code startDestroyBlock} /
        // {@code continueDestroyBlock} мимо этого обработчика прогресс не давали —
        // блок не ломался никогда, сколько ни бей. Снаружи это выглядело так:
        // персонаж стоит вплотную, машет рукой, а стена цела.
        //
        // Кнопка ставится программно и снимается в {@link #stop}: для игрока
        // ничего не меняется, а ваниль получает ровно то состояние, в котором
        // ломание работает само.
        mc.options.keyAttack.setDown(true);

        if (!pos.equals(startedOn)) {
            mc.gameMode.startDestroyBlock(pos, face);
            startedOn = pos;
        } else {
            mc.gameMode.continueDestroyBlock(pos, face);
        }

        return mc.level.getBlockState(pos).isAir();
    }

    /**
     * Идёт ли сейчас ломание блока баритоном.
     *
     * <p>Нужно миксину: пока это правда, ванильное {@code stopDestroyBlock}
     * блокируется. Ваниль зовёт его каждый тик (кнопка атаки не нажата или мышь
     * не захвачена), и каждый такой вызов обнулял накопленный прогресс. Снаружи
     * это выглядело так: персонаж стоит вплотную к блоку, бьёт по нему, а блок
     * не ломается никогда.
     */
    public static boolean isBreaking() {
        return forcedTarget != null;
    }

    /**
     * Подменяет прицел на блок, который мы копаем. Вызывается из миксина после
     * ванильного пересчёта прицела.
     *
     * <p>Только когда действительно копаем: в остальное время прицел должен
     * оставаться настоящим, иначе пострадают удары по сущностям и установка
     * блоков.
     */
    public static void overridePick(Minecraft mc) {
        BlockPos target = forcedTarget;
        if (target == null || mc.player == null || mc.level == null) {
            return;
        }
        if (mc.level.getBlockState(target).isAir() || !inReach(mc, target)) {
            // Цель уже сломана или отошли — прицел больше не подменяем, и кнопку
            // атаки отпускаем: иначе ваниль начнёт бить по тому, что случайно
            // оказалось под настоящим прицелом.
            forcedTarget = null;
            mc.options.keyAttack.setDown(false);
            return;
        }
        mc.hitResult = new BlockHitResult(Vec3.atCenterOf(target), faceToward(mc, target), target, false);
    }

    /** Грань блока, обращённая к игроку — по ней и бьём. */
    private static Direction faceToward(Minecraft mc, BlockPos pos) {
        Vec3 eye = mc.player == null ? Vec3.atCenterOf(pos) : mc.player.getEyePosition();
        double dx = eye.x - (pos.getX() + 0.5);
        double dy = eye.y - (pos.getY() + 0.5);
        double dz = eye.z - (pos.getZ() + 0.5);
        double ax = Math.abs(dx);
        double ay = Math.abs(dy);
        double az = Math.abs(dz);
        if (ax >= ay && ax >= az) {
            return dx > 0 ? Direction.EAST : Direction.WEST;
        }
        if (ay >= az) {
            return dy > 0 ? Direction.UP : Direction.DOWN;
        }
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /** Прекращает ломание — при отмене процесса или переходе к другому блоку. */
    public static void stop(Minecraft mc) {
        reset();
        if (mc != null) {
            // Кнопку атаки обязательно отпускаем: она наша, программная, и
            // оставленная нажатой она продолжила бы ломать всё подряд уже без
            // всякого маршрута.
            mc.options.keyAttack.setDown(false);
            if (mc.gameMode != null) {
                mc.gameMode.stopDestroyBlock();
            }
        }
    }

    private static void reset() {
        forcedTarget = null;
        startedOn = null;
    }
}
