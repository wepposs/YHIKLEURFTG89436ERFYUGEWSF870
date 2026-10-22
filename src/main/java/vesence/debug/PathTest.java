package vesence.debug;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import vesence.pathing.BlockWorld;
import vesence.pathing.Path;
import vesence.pathing.PathFinder;
import vesence.pathing.PathNode;
import vesence.pathing.VesenceBaritone;
import vesence.pathing.goal.GoalBlock;
import vesence.pathing.goal.GoalNear;
import vesence.pathing.goal.GoalXZ;

import java.util.ArrayList;
import java.util.List;

/**
 * Автотест движка пути. Включается {@code VESENCE_PATHTEST=1}.
 *
 * <p>Проверяет три вещи, каждая из которых ломается независимо:
 * <ol>
 *   <li><b>поиск</b> — находит ли A* путь в четырёх направлениях на разных
 *       дистанциях, сколько узлов раскрывает и за какое число тиков;</li>
 *   <li><b>исполнение</b> — действительно ли игрок идёт, с какой скоростью и
 *       за сколько тиков доходит;</li>
 *   <li><b>камера</b> — не вращается ли вид во время движения.</li>
 * </ol>
 */
@Environment(EnvType.CLIENT)
public final class PathTest {

    private static final boolean ACTIVE = System.getenv("VESENCE_PATHTEST") != null;

    private static final int WARMUP_TICKS = 160;
    private static final int WALK_TICKS = 600;

    private static int ticks;
    private static int walkTicks;
    private static boolean probeDone;

    /** Мир загружен — выставляется один раз, на первом пригодном тике. */
    private static boolean ready;

    /** Сколько раз проверяли готовность мира — только для диагностики. */
    private static int readyWaits;
    private static boolean walkStarted;
    private static boolean finished;
    private static int shotAttempts;
    private static int bestPathPixels;

    private static Vec3 startPos;
    private static BlockPos target;
    private static double bestDistance;
    private static int arrivalTicks = -1;
    private static int jumpTicks;

    private PathTest() {
    }

    public static void init() {
        if (!ACTIVE) {
            return;
        }
        // Синтетические проверки поиска — до входа в мир, им мир не нужен.
        PathSelfTest.run();

        ClientTickEvents.END_CLIENT_TICK.register(PathTest::tick);
        vesence.event.EventManager.register(new RotationSampler());
        vesence.event.EventManager.register(new ProjectionProbe());
        System.out.println("[pathtest] harness active");
    }

    /**
     * Замер плавности поворота камеры.
     *
     * <p>Слушает {@code EventRotation} — он приходит на КАЖДОМ кадре, а не на
     * тике, поэтому меряет именно то, что видит глаз. Робот поворачивает
     * ступеньками по 30–40° за кадр; плавный поворот — единицы градусов.
     */
    public static final class RotationSampler {

        private float lastYaw;
        private boolean hasLast;
        private int frames;

        @vesence.event.EventInit
        public void onRotation(vesence.event.player.EventRotation event) {
            if (!walkStarted || finished) {
                return;
            }
            float yaw = event.getYaw();
            if (this.hasLast) {
                float delta = Math.abs(net.minecraft.util.Mth.wrapDegrees(yaw - this.lastYaw));
                if (delta > maxFrameYawDelta) {
                    maxFrameYawDelta = delta;
                }
                frameYawSum += delta;
                frameYawSamples++;
            }
            this.lastYaw = yaw;
            this.hasLast = true;
            this.frames++;
        }
    }

    private static float maxFrameYawDelta;
    private static double frameYawSum;

    /**
     * Проверка, что узлы маршрута вообще попадают в кадр.
     *
     * <p>Это главный числовой признак исправности отрисовки: если координаты
     * вершин посчитаны в неверной системе (например, в мировых вместо
     * относительных к камере), геометрия уезжает за экран — и в логе нет ни
     * одной ошибки. Здесь узлы проецируются той же матрицей и проекцией, что и
     * при отрисовке, и считается, сколько из них оказалось внутри NDC.
     *
     * <p>Печатается один раз за прогон: дальше картина уже понятна.
     */
    public static final class ProjectionProbe {

        private boolean done;

        @vesence.event.EventInit
        public void onRender3D(vesence.event.render.EventRender3D event) {
            if (this.done || !walkStarted || finished) {
                return;
            }
            var engine = VesenceBaritone.get();
            var path = engine.currentPath();
            if (path == null) {
                return;
            }
            var nodes = path.remaining();
            if (nodes.size() < 2) {
                return;
            }
            this.done = true;

            org.joml.Matrix4f view = event.getMatrixStack().last().pose();
            org.joml.Matrix4f proj = event.getProjectionMatrix();
            Vec3 cam = event.getCameraPos();

            int inside = 0;
            double maxAbsX = 0.0;
            double maxAbsY = 0.0;
            double minW = Double.MAX_VALUE;
            for (PathNode node : nodes) {
                org.joml.Vector4f clip = new org.joml.Vector4f(
                        (float) (node.centerX() - cam.x),
                        (float) (node.centerY() + 0.1 - cam.y),
                        (float) (node.centerZ() - cam.z),
                        1.0f);
                view.transform(clip);
                proj.transform(clip);
                if (clip.w <= 1.0e-4f) {
                    continue;
                }
                double ndcX = clip.x / clip.w;
                double ndcY = clip.y / clip.w;
                maxAbsX = Math.max(maxAbsX, Math.abs(ndcX));
                maxAbsY = Math.max(maxAbsY, Math.abs(ndcY));
                minW = Math.min(minW, clip.w);
                if (Math.abs(ndcX) <= 1.0 && Math.abs(ndcY) <= 1.0) {
                    inside++;
                }
            }
            System.out.println("[pathtest] project nodes=" + nodes.size()
                    + " inside=" + inside
                    + " maxAbsX=" + String.format("%.2f", maxAbsX)
                    + " maxAbsY=" + String.format("%.2f", maxAbsY)
                    + " minW=" + String.format("%.2f", minW));
        }
    }

    /**
     * Мир загружен — чанк под игроком пришёл.
     *
     * <p>Проверять «стоит на твёрдой опоре» нельзя, и это выяснилось на практике:
     * игрок в этом мире появляется прямо в воде, и под ним вода, а не земля.
     * Условие «под ногами твёрдо» не выполнялось никогда, и прогон молча ждал
     * вечно — а заодно не проверял главный сценарий, ради которого всё делалось:
     * поведение в воде.
     *
     * <p>Правильный признак готовности — что чанк вообще загружен.
     */
    private static boolean worldReady(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            return false;
        }
        BlockPos feet = mc.player.blockPosition();
        boolean loaded = mc.level.hasChunk(feet.getX() >> 4, feet.getZ() >> 4);
        if (!loaded) {
            if (++readyWaits % 40 == 0) {
                System.out.println("[pathtest] ждём чанк: pos=" + fmt(mc.player.position()));
            }
            return false;
        }
        return true;
    }
    private static int frameYawSamples;

    private static void tick(Minecraft mc) {
        if (finished || mc.player == null || mc.level == null) {
            return;
        }
        // Ждём, пока мир реально загрузится — но только ОДИН раз, на старте.
        //
        // Без этого гейта проба бежала по пустым чанкам: getBlockState отдавал
        // воздух, belowSolid=false, canStand=false — и «путь не найден» на любую
        // цель. Тест мерил не баг, а собственную торопливость.
        //
        // Важно: проверять «стоит на земле» на КАЖДОМ тике нельзя. Персонаж
        // прыгает и падает, onGround на этих тиках false — и счётчик прогона
        // замирал, а вместе с ним терялся финальный отчёт. Флаг ставится один
        // раз и больше не пересчитывается.
        if (!ready) {
            if (!worldReady(mc)) {
                return;
            }
            ready = true;
        }
        ticks++;
        if (ticks < WARMUP_TICKS) {
            return;
        }

        if (!probeDone) {
            probeDone = true;
            runProbe(mc);
            return;
        }

        if (!walkStarted) {
            walkStarted = true;
            startWalk(mc);
            return;
        }
        walkTicks++;

        // Следим за прогрессом: расстояние до цели должно падать.
        VesenceBaritone engine = VesenceBaritone.get();
        if (!engine.isPathing()) {
            report(mc);
            return;
        }

        // Считаем расстояние до самой точки, а не до объекта цели: цель по ходу
        // дела ослабляется (точный блок → окрестность → только X/Z), и жёсткий
        // каст к конкретному классу ломал бы замер ровно в тот момент, когда
        // ослабление сработало.
        double distance = Math.sqrt(mc.player.blockPosition().distSqr(target));
        if (distance < bestDistance) {
            bestDistance = distance;
        }
        if (arrivalTicks < 0 && distance <= 2.5) {
            arrivalTicks = walkTicks;
        }

        // Камера не должна крутиться: RotationSampler меряет изменение рысканья
        // между кадрами. При вращении камеры это десятки градусов за кадр.

        if (engine.executor().isJumpRequested()) {
            jumpTicks++;
        }

        if (ticks % 40 == 0) {
            System.out.println("[pathtest] t=" + ticks
                    + " pos=" + fmt(mc.player.position())
                    + " dist=" + String.format("%.1f", distance)
                    + " idx=" + (engine.currentPath() == null ? -1 : engine.currentPath().index)
                    + " blocked=" + engine.executor().humanizer().isBlocked()
                    // Метрика камеры печатается и здесь, а не только в финале:
                    // прогон может оборваться, если окно потеряет фокус, и тогда
                    // финальный отчёт просто не появится.
                    + " maxYawStep=" + String.format("%.1f", maxFrameYawDelta)
                    + " avgYawStep=" + String.format("%.2f",
                    frameYawSamples == 0 ? 0.0 : frameYawSum / frameYawSamples));
        }

        // Снимок делаем, пока путь длинный: в конце маршрута остаётся пара узлов
        // прямо под камерой, они попадают внутрь near-plane и на снимке их нет.
        //
        // Важно брать кадр НЕ на первом тике пути: буфер кадра содержит уже
        // отрисованный кадр, а путь появился только что — на снимке его ещё нет.
        //
        // И делаем несколько попыток: Humanizer периодически отводит взгляд в
        // сторону, и в такой момент линия уходит из кадра. Один снимок ничего
        // не доказывает — берём максимум по трём.
        if (shotAttempts < 3 && walkTicks >= 15 && (walkTicks - 15) % 18 == 0) {
            var current = engine.currentPath();
            if (current != null && current.remaining().size() >= 5) {
                shotAttempts++;
                takeShot(mc, current.remaining().size(), shotAttempts);
            }
        }

        if (ticks > WARMUP_TICKS + WALK_TICKS) {
            report(mc);
        }
    }

    private static void takeShot(Minecraft mc, int nodeCount, int attempt) {
        try {
            new java.io.File("uitest-shots").mkdirs();
            net.minecraft.client.Screenshot.takeScreenshot(
                    mc.gameRenderer.mainRenderTarget(), image -> {
                        try {
                            int w = image.getWidth();
                            int h = image.getHeight();
                            long sum = 0;
                            int nonBlack = 0;
                            // Ищем пиксели цвета линии пути (0x4FC3F7) — так проверяем,
                            // что рендер действительно дошёл до экрана. Смотрим каждый
                            // пиксель: линия тонкая, при выборке через один её легко
                            // пропустить и получить ложный «не рисуется».
                            int pathPixels = 0;
                            for (int y = 0; y < h; y++) {
                                for (int x = 0; x < w; x++) {
                                    int argb = image.getPixel(x, y);
                                    int r = (argb >> 16) & 0xFF;
                                    int g = (argb >> 8) & 0xFF;
                                    int b = argb & 0xFF;
                                    if (b > 140 && g > 110 && b > r + 30 && g > r + 20) pathPixels++;
                                }
                            }
                            for (int y = 0; y < h; y += 2) {
                                for (int x = 0; x < w; x += 2) {
                                    int argb = image.getPixel(x, y);
                                    int r = (argb >> 16) & 0xFF;
                                    int g = (argb >> 8) & 0xFF;
                                    int b = argb & 0xFF;
                                    sum += (r + g + b) / 3;
                                    if (r + g + b > 24) nonBlack++;
                                }
                            }
                            long total = (long) (w / 2) * (h / 2);
                            if (pathPixels > bestPathPixels) {
                                bestPathPixels = pathPixels;
                            }
                            System.out.println("[pathtest] shot#" + attempt + " " + w + "x" + h
                                    + " avg=" + (sum / Math.max(1, total))
                                    + " nonBlack=" + String.format("%.1f", 100.0 * nonBlack / total) + "%"
                                    + " pathishPixels=" + pathPixels
                                    + " best=" + bestPathPixels
                                    + " nodes=" + nodeCount);

                            // Сохраняем кадр с наибольшим числом пикселей линии —
                            // его можно посмотреть глазами и прогнать через ASCII.
                            if (pathPixels > 0) {
                                try {
                                    image.writeToFile(new java.io.File(
                                            "uitest-shots/path-line.png").toPath());
                                } catch (Throwable ignored) {
                                }
                            }
                            image.close();
                        } catch (Throwable t) {
                            System.out.println("[pathtest] shot failed: " + t);
                        }
                    });
        } catch (Throwable t) {
            System.out.println("[pathtest] shot capture failed: " + t);
        }
    }

    private static void runProbe(Minecraft mc) {
        BlockPos origin = mc.player.blockPosition();
        BlockWorld world = new BlockWorld(mc.level);
        System.out.println("[pathtest] probe origin=" + origin
                + " canStand(origin)=" + world.canStand(origin.getX(), origin.getY(), origin.getZ())
                + " belowSolid=" + world.solid(origin.getX(), origin.getY() - 1, origin.getZ()));

        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        int[] distances = {8, 20, 40};

        for (int distance : distances) {
            for (int[] dir : dirs) {
                BlockPos target = origin.offset(dir[0] * distance, 0, dir[1] * distance);
                System.out.println("[pathtest] target " + target
                        + " canStand=" + world.canStand(target.getX(), target.getY(), target.getZ())
                        + " solid=" + world.solid(target.getX(), target.getY(), target.getZ())
                        + " passable=" + world.passable(target.getX(), target.getY(), target.getZ()));
                probeOne(mc, new GoalXZ(target.getX(), target.getZ()), distance, dir, "XZ");
                probeOne(mc, new GoalNear(target, 2), distance, dir, "Near2");
            }
        }
    }

    private static void probeOne(Minecraft mc, vesence.pathing.goal.Goal goal,
                                 int distance, int[] dir, String kind) {
        BlockWorld world = new BlockWorld(mc.level);
        PathFinder finder = new PathFinder(world, goal, 3, 200_000);

        int steps = 0;
        long startedAt = System.nanoTime();
        PathFinder.Status status;
        do {
            status = finder.step(2000);
            steps++;
        } while (status == PathFinder.Status.RUNNING && steps < 500);

        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
        String tag = kind + " [" + dir[0] + "," + dir[1] + "] d=" + distance;

        if (status == PathFinder.Status.SUCCESS) {
            Path path = finder.result();
            System.out.println("[pathtest] probe " + tag + " OK nodes=" + path.size()
                    + " cost=" + String.format("%.1f", path.cost)
                    + " expanded=" + finder.expanded()
                    + " steps=" + steps + " ms=" + elapsedMs);
            logTypes(path);
        } else {
            System.out.println("[pathtest] probe " + tag + " FAIL status=" + status
                    + " expanded=" + finder.expanded() + " ms=" + elapsedMs);
        }
    }

    private static void logTypes(Path path) {
        int walk = 0;
        int ascend = 0;
        int jumpGap = 0;
        int descend = 0;
        int fall = 0;
        int swim = 0;
        int climb = 0;
        for (PathNode node : path.nodes) {
            switch (node.type) {
                case WALK -> walk++;
                case ASCEND -> ascend++;
                case JUMP_GAP -> jumpGap++;
                case DESCEND -> descend++;
                case FALL -> fall++;
                case SWIM -> swim++;
                case CLIMB -> climb++;
                default -> {
                }
            }
        }
        System.out.println("[pathtest]   types walk=" + walk + " ascend=" + ascend
                + " jumpGap=" + jumpGap
                + " descend=" + descend + " fall=" + fall + " swim=" + swim + " climb=" + climb);
    }

    private static void startWalk(Minecraft mc) {
        BlockPos origin = mc.player.blockPosition();
        target = origin.offset(26, 0, 18);
        startPos = mc.player.position();
        bestDistance = Double.MAX_VALUE;
        arrivalTicks = -1;
        walkTicks = 0;

        var module = vesence.Vesence.get.getManager()
                .get(vesence.module.impl.movement.Baritone.class);
        if (module != null && !module.enable) {
            module.toggle();
        }

        // Идём ровно тем же путём, что и игрок из чата: `.goto x y z`.
        // Если тест зовёт внутренний API напрямую, он проверяет не то, чем
        // пользуется человек, и «в игре не работает» остаётся незамеченным.
        VesenceBaritone.get().setGoalAndPath(target);
        System.out.println("[pathtest] walk start=" + fmt(startPos) + " target=" + target);
    }

    private static void report(Minecraft mc) {
        finished = true;
        double travelled = startPos == null ? 0.0 : mc.player.position().distanceTo(startPos);
        System.out.println("[pathtest] === RESULT ===");
        System.out.println("[pathtest] travelled=" + String.format("%.2f", travelled)
                + " bestDist=" + String.format("%.1f", bestDistance)
                + " arrivalTicks=" + arrivalTicks
                + " jumpTicks=" + jumpTicks);
        // Средняя скорость на маршруте. Обычный шаг — 4.3 блока/с, бег — 5.6.
        // Если здесь ~1, значит персонаж где-то стоит и «тупит».
        double seconds = (arrivalTicks > 0 ? arrivalTicks : Math.max(1, walkTicks)) / 20.0;
        System.out.println("[pathtest] speed=" + String.format("%.2f", travelled / seconds) + " b/s");
        // Камера не должна вращаться: максимум изменения рысканья за прогон.
        System.out.println("[pathtest] cameraYawDrift=" + String.format("%.2f", maxFrameYawDelta) + " deg"
                + " frames=" + frameYawSamples);
        System.out.println("[pathtest] reached=" + (bestDistance <= 2.5));
        System.out.println("[pathtest] stillPathing=" + VesenceBaritone.get().isPathing());
        VesenceBaritone.get().cancel();
    }

    private static String fmt(Vec3 v) {
        return String.format("%.1f/%.1f/%.1f", v.x, v.y, v.z);
    }

    /** Не используется, но полезно при отладке: список пройденных узлов. */
    static List<BlockPos> nodePositions(Path path) {
        List<BlockPos> out = new ArrayList<>();
        if (path != null) {
            for (PathNode node : path.nodes) {
                out.add(node.pos());
            }
        }
        return out;
    }
}
