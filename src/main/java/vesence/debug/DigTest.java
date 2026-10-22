package vesence.debug;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import vesence.pathing.MovementType;
import vesence.pathing.Path;
import vesence.pathing.VesenceBaritone;

/**
 * Живая проверка копания: цель под землёй, дороги в обход нет.
 *
 * <p>Отдельно от {@link PathTest} намеренно. Тот идёт по поверхности и проверяет
 * ходьбу, прыжки и повороты; если смешать это с раскопом, при падении одного
 * невозможно понять, что именно сломалось.
 *
 * <p>Включается {@code VESENCE_DIGTEST=1}.
 */
@Environment(EnvType.CLIENT)
public final class DigTest {

    private static final int WARMUP_TICKS = 160;
    private static final int RUN_TICKS = 700;

    /** Насколько глубоко целимся. Двенадцать блоков — заведомо сквозь породу. */
    private static final int DEPTH = 12;

    /**
     * Режим дальней цели: {@code VESENCE_DIGTEST=2}.
     *
     * <p>Проверяет то, на что была жалоба: цель в шестидесяти блоках не должна
     * дробиться на переходы и не должна заваливать чат сообщениями.
     */
    private static final boolean FAR_MODE = "2".equals(System.getenv("VESENCE_DIGTEST"));

    private static final int FAR_DISTANCE = 60;

    private static boolean ready;
    private static boolean started;
    private static int ticks;
    private static int runTicks;
    private static int digTicks;
    private static BlockPos target;
    private static Vec3 startPos;
    private static double bestDistance = Double.MAX_VALUE;
    private static boolean finished;

    private DigTest() {
    }

    public static void init() {
        if (System.getenv("VESENCE_DIGTEST") == null) {
            return;
        }
        ready = false;
        started = false;
        ticks = 0;
        runTicks = 0;
        digTicks = 0;
        bestDistance = Double.MAX_VALUE;
        finished = false;
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK
                .register(DigTest::tick);
        System.out.println("[digtest] включён");
    }

    public static void tick(Minecraft mc) {
        if (System.getenv("VESENCE_DIGTEST") == null) {
            return;
        }
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (!ready) {
            // Ждём именно загруженных чанков, а не onGround: игрок может
            // появиться в воде или в воздухе, и тогда ожидание зависнет.
            BlockPos pos = mc.player.blockPosition();
            if (!mc.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                return;
            }
            ready = true;
        }

        if (++ticks < WARMUP_TICKS) {
            return;
        }

        if (!started) {
            started = true;
            start(mc);
            return;
        }

        runTicks++;

        VesenceBaritone engine = VesenceBaritone.get();
        Path path = engine.currentPath();
        if (path != null && !path.finished()) {
            var node = path.current();
            if (node != null && node.type == MovementType.DIG) {
                digTicks++;
            }
        }

        double distance = Math.sqrt(mc.player.blockPosition().distSqr(target));
        if (distance < bestDistance) {
            bestDistance = distance;
        }

        if (finished) {
            return;
        }

        if (runTicks % 20 == 0) {
            var node = path == null ? null : path.current();
            System.out.println("[digtest] t=" + runTicks
                    + " pos=" + fmt(mc.player.position())
                    + " dist=" + String.format("%.1f", distance)
                    + " idx=" + (path == null ? -1 : path.index) + "/" + (path == null ? 0 : path.size())
                    + " node=" + (node == null ? "нет" : node.type + node.pos().toShortString())
                    + " путь=" + (path == null ? "нет" : "есть")
                    + " процесс=" + (engine.process() == null ? "нет" : engine.process().name()));
        }

        if (runTicks >= RUN_TICKS || !engine.isPathing()) {
            finished = true;
            report(mc, distance);
        }
    }

    private static void start(Minecraft mc) {
        BlockPos origin = mc.player.blockPosition();

        // Режим 2 — дальняя цель по поверхности.
        //
        // Ровно тот случай, на который была жалоба: цель в шестидесяти блоках,
        // а бот вставал и сыпал в чат «иду к цели, осталось N метров». Шестьдесят
        // блоков — это заведомо ближе границы загруженных чанков, значит никаких
        // переходов тут быть не должно: один прямой маршрут и один расчёт.
        if (FAR_MODE) {
            int tx = origin.getX() + FAR_DISTANCE;
            int tz = origin.getZ() + FAR_DISTANCE;
            // Целимся на ПОВЕРХНОСТЬ в той точке, а не на свою высоту.
            //
            // Прошлый заход оставил игрока на дне прокопа, и цель «в шестидесяти
            // блоках на моей высоте» оказывалась внутри породы — тест мерил не
            // дальний маршрут, а то, что закопаться в стену нельзя.
            target = new BlockPos(tx, surfaceY(mc, tx, tz), tz);
            startPos = mc.player.position();
            enableModule();
            long began = System.nanoTime();
            VesenceBaritone.get().setGoalAndPath(target);
            System.out.println("[digtest] FAR start=" + fmt(startPos) + " target=" + target
                    + " расчёт_мс=" + (System.nanoTime() - began) / 1_000_000.0);
            return;
        }

        // Ищем настоящую поверхность под игроком.
        //
        // Спавн бывает какой угодно: крона дерева, крыша, край обрыва. Цель,
        // посчитанная «на двенадцать ниже ног», в таком мире попадает куда-то в
        // воздух, и тест проверяет не прокоп, а случайность точки старта.
        int ground = origin.getY();
        for (int dy = 0; dy >= -24; dy--) {
            var state = mc.level.getBlockState(origin.offset(0, dy, 0));
            if (!state.getCollisionShape(mc.level, origin.offset(0, dy, 0)).isEmpty()) {
                ground = origin.getY() + dy + 1;
                break;
            }
        }

        target = new BlockPos(origin.getX(), ground - DEPTH, origin.getZ());
        startPos = mc.player.position();
        enableModule();

        VesenceBaritone.get().setGoalAndPath(target);
        System.out.println("[digtest] start=" + fmt(startPos) + " ground=" + ground
                + " target=" + target + " (глубина " + DEPTH + ")");
    }

    private static void enableModule() {
        var module = vesence.Vesence.get.getManager()
                .get(vesence.module.impl.movement.Baritone.class);
        if (module != null && !module.enable) {
            module.toggle();
        }
    }

    /**
     * Уровень поверхности в указанном столбце.
     *
     * <p>Через карту высот, а не перебором сверху вниз: перебор находил крышу
     * пещеры или дно ущелья и ставил цель в тридцати блоках под землёй. Тест
     * после этого честно сообщал «путь не найден», хотя проверял он совсем не то.
     */
    private static int surfaceY(Minecraft mc, int x, int z) {
        return mc.level.getHeight(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
    }

    private static void report(Minecraft mc, double distance) {
        System.out.println("[digtest] === RESULT ===");
        System.out.println("[digtest] start=" + fmt(startPos));
        System.out.println("[digtest] end=" + fmt(mc.player.position()));
        System.out.println("[digtest] target=" + target + " bestDist=" + String.format("%.1f", bestDistance));
        System.out.println("[digtest] ticks=" + runTicks + " digTicks=" + digTicks);
        System.out.println("[digtest] dropped=" + String.format("%.1f",
                startPos.y - mc.player.position().y));
        System.out.println("[digtest] reached=" + (distance <= 2.5));
        // Флаг снимаем: иначе отчёт печатался бы каждый тик до закрытия игры.
        System.setProperty("vesence.digtest.done", "true");
    }

    private static String fmt(Vec3 vec) {
        return String.format("%.1f/%.1f/%.1f", vec.x, vec.y, vec.z);
    }
}
