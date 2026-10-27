package vesence.pathing;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import vesence.module.impl.visuals.Immediate;
import vesence.utils.render.ColorUtil;

import java.util.List;

/**
 * Отрисовка маршрута: светящаяся линия по узлам и светящийся блок цели.
 *
 * <p>Стиль взят у Trajectories: лента постоянной толщины плюс мягкое свечение
 * вокруг неё, всё без depth-теста — маршрут читается сквозь стены. Блок цели
 * сделан в том же духе: пульсирующая заливка и яркий каркас.
 *
 * <p>Три вещи, на которых рендер молча пропадал, и которые здесь учтены:
 *
 * <ul>
 *   <li><b>Координаты.</b> Матрица из {@code EventRender3D} содержит только
 *       поворот камеры, без сдвига на её позицию. Вершины обязаны быть в
 *       координатах ОТНОСИТЕЛЬНО камеры: с мировыми координатами геометрия
 *       уезжает за экран без единой ошибки в логе.</li>
 *   <li><b>Отправка.</b> Собирать mesh и тут же рисовать его своим
 *       {@code RenderPass} нельзя: {@code EventRender3D} приходит внутрь
 *       отрисовки мира, когда кадровый граф ещё кодируется. Работает только
 *       отложенная отправка через {@link WorldRenderer.ImmediateBuffers} —
 *       она ставит задачу в пост-GUI проход.</li>
 *   <li><b>Топология.</b> {@code DEBUG_LINES} в 26.3 не рисуется. Толщина
 *       задаётся геометрией: каждый сегмент разворачивается в квад,
 *       повёрнутый к камере.</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public final class PathRenderer {

    private static final DepthStencilState NO_DEPTH =
            new DepthStencilState(CompareOp.ALWAYS_PASS, false);

    /** Лента маршрута и заливка блока цели. */
    private static final RenderPipeline QUADS_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(new RenderPipeline.Snippet[]{RenderPipelines.MATRICES_FOG_SNIPPET})
                    .withLocation(Identifier.fromNamespaceAndPath("vesence", "pipeline/world/path_quads"))
                    .withVertexShader("core/position_color")
                    .withFragmentShader("core/position_color")
                    .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                    .withPrimitiveTopology(PrimitiveTopology.QUADS)
                    .withCull(false)
                    .withDepthStencilState(NO_DEPTH)
                    .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
                    .build());

    /** Каркас блока цели. */
    private static final RenderPipeline LINES_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(new RenderPipeline.Snippet[]{RenderPipelines.MATRICES_FOG_SNIPPET})
                    .withLocation(Identifier.fromNamespaceAndPath("vesence", "pipeline/world/path_lines"))
                    .withVertexShader("core/position_color")
                    .withFragmentShader("core/position_color")
                    .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                    .withPrimitiveTopology(PrimitiveTopology.LINES)
                    .withCull(false)
                    .withDepthStencilState(NO_DEPTH)
                    .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
                    .build());

    /**
     * Светящиеся спрайты вдоль маршрута — тот же приём, что у модуля Projectile.
     * Текстура общая (dashbloom), поэтому маршрут выглядит «той самой линией».
     */
    private static final Identifier BLOOM_TEXTURE =
            Identifier.fromNamespaceAndPath("vesence", "textures/world/dashbloom.png");

    private static final RenderPipeline BLOOM_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(new RenderPipeline.Snippet[]{RenderPipelines.MATRICES_FOG_SNIPPET})
                    .withLocation(Identifier.fromNamespaceAndPath("vesence", "pipeline/world/path_bloom"))
                    .withVertexShader("core/position_tex_color")
                    .withFragmentShader("core/position_tex_color")
                    .withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.SAMPLER0)
                    .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                    .withPrimitiveTopology(PrimitiveTopology.QUADS)
                    .withCull(false)
                    .withDepthStencilState(NO_DEPTH)
                    .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
                    .build());

    private static final RenderType QUADS_LAYER = RenderType.create(
            "vesence_path_quads", RenderSetup.builder(QUADS_PIPELINE).createRenderSetup());
    private static final RenderType LINES_LAYER = RenderType.create(
            "vesence_path_lines", RenderSetup.builder(LINES_PIPELINE).createRenderSetup());
    private static final RenderType BLOOM_LAYER = RenderType.create(
            "vesence_path_bloom",
            RenderSetup.builder(BLOOM_PIPELINE).withTexture("Sampler0", BLOOM_TEXTURE).createRenderSetup());

    /** Полутолщина ленты в блоках. */
    private static final float LINE_HALF = 0.07f;
    /** Во сколько раз свечение шире ядра. */
    private static final float GLOW_SCALE = 2.6f;
    /** Прозрачность ядра линии. */
    private static final float LINE_ALPHA = 0.92f;
    /** Прозрачность свечения вокруг линии. */
    private static final float GLOW_ALPHA = 0.13f;

    private static final boolean DEBUG = System.getenv("VESENCE_PATHDEBUG") != null;

    /** Диагностический квад перед камерой — см. {@link #drawProbe}. */
    private static final boolean PROBE = System.getenv("VESENCE_PATHBIG") != null;

    private static ByteBufferBuilder allocator;
    private static Immediate immediate;
    private static boolean failed;
    private static int draws;

    private PathRenderer() {
    }

    /**
     * Форсирует инициализацию класса — то есть регистрацию пайплайнов.
     *
     * <p>{@code RenderPipelines.register} обязан выполниться ДО того, как движок
     * соберёт свой набор пайплайнов на старте. Если класс инициализируется лениво,
     * уже во время отрисовки кадра, пайплайн регистрируется слишком поздно.
     */
    public static void init() {
        // Тело пустое намеренно: достаточно самого факта загрузки класса.
    }

    /**
     * Рисует маршрут: светящуюся линию по узлам и блок цели.
     *
     * @param cameraRotation поворот камеры — для разворота спрайтов свечения
     *                       к зрителю (как в Projectile)
     * @param nodes  оставшаяся часть маршрута (может быть и один узел — тогда
     *               рисуется только блок цели)
     * @param goalAt блок цели; {@code null} — взять последний узел маршрута
     */
    public static void draw(PoseStack matrices, Vec3 camera, org.joml.Quaternionf cameraRotation,
                            List<PathNode> nodes, BlockPos goalAt) {
        draw(matrices, camera, cameraRotation, nodes, goalAt, null);
    }

    /**
     * То же, но линия начинается из точки {@code trailFrom} — ног игрока.
     *
     * <p>Без первой точки ломаная начиналась бы со следующего узла. На
     * стянутых маршрутах это десятки блоков впереди: под ногами ничего не
     * нарисовано, линия «висит в воздухе» и начинается не оттуда, где идёт
     * персонаж.
     */
    public static void draw(PoseStack matrices, Vec3 camera, org.joml.Quaternionf cameraRotation,
                            List<PathNode> nodes, BlockPos goalAt, Vec3 trailFrom) {
        if (failed || nodes == null || nodes.isEmpty() || camera == null) {
            return;
        }
        if (DEBUG && draws++ % 200 == 0) {
            System.out.println("[baritone] draw nodes=" + nodes.size()
                    + " cam=" + String.format("%.1f/%.1f/%.1f", camera.x, camera.y, camera.z)
                    + " first=" + nodes.get(0) + " goal=" + goalAt);
        }

        // Цвет — от темы, как у остальных визуальных модулей.
        int theme = ColorUtil.fade();
        if ((theme >> 24 & 0xFF) == 0) {
            theme |= 0xFF000000;
        }
        int r = theme >> 16 & 0xFF;
        int g = theme >> 8 & 0xFF;
        int b = theme & 0xFF;

        Matrix4f matrix = matrices.last().pose();
        PathNode last = nodes.get(nodes.size() - 1);
        BlockPos goal = goalAt != null ? goalAt : last.pos();

        // Полилиния маршрута точками: ноги игрока → все узлы. Чуть приподнята
        // над полом, чтобы лента лежала на земле, а не тонула в ней.
        List<Vec3> pts = new java.util.ArrayList<>(nodes.size() + 1);
        if (trailFrom != null) {
            pts.add(new Vec3(trailFrom.x, trailFrom.y + 0.04, trailFrom.z));
        }
        for (PathNode node : nodes) {
            pts.add(new Vec3(node.centerX(), node.centerY() + 0.04, node.centerZ()));
        }

        if (DEBUG && draws % 200 == 1) {
            System.out.println("[baritone] frame " + draws + " goal=" + goal
                    + " cam=" + String.format("%.1f/%.1f/%.1f", camera.x, camera.y, camera.z));
        }

        try {
            if (immediate == null) {
                immediate = new Immediate();
            }

            VertexConsumer quads = immediate.getBuffer(QUADS_LAYER);
            if (PROBE) {
                drawProbe(quads, matrix);
            }
            drawGoalFill(quads, matrix, goal, camera, r, g, b);
            drawRibbon(quads, matrix, pts, camera, r, g, b);

            // Светящаяся тропа спрайтами — стиль Projectile поверх ленты.
            VertexConsumer bloom = immediate.getBuffer(BLOOM_LAYER);
            drawBloomTrail(bloom, matrices, camera, cameraRotation, pts, r, g, b);

            VertexConsumer lines = immediate.getBuffer(LINES_LAYER);
            drawGoalFrame(lines, matrix, goal, camera, r, g, b);

            // Отложенная отправка: реальный draw произойдёт в пост-GUI проходе.
            immediate.draw();
        } catch (Throwable t) {
            // Битый буфер не должен ронять кадр, но и молчать нельзя: без этого
            // сообщения «линия не рисуется» невозможно отличить от «линия за экраном».
            failed = true;
            System.out.println("[baritone] path render failed: " + t);
            reset();
        }
    }

    /**
     * Диагностический промах: крупный квад прямо перед камерой.
     *
     * <p>Разделяет две неисправности, которые выглядят одинаково: «геометрия не
     * доходит до экрана» и «геометрия посчитана неверно». Квад заведомо попадает
     * в кадр, поэтому его отсутствие на снимке означает проблему в отправке, а
     * присутствие — проблему в координатах маршрута.
     */
    private static void drawProbe(VertexConsumer buffer, Matrix4f matrix) {
        float z = -6.0f;
        buffer.addVertex(matrix, -2.5f, -2.5f, z).setColor(255, 0, 255, 255);
        buffer.addVertex(matrix, 2.5f, -2.5f, z).setColor(255, 0, 255, 255);
        buffer.addVertex(matrix, 2.5f, 2.5f, z).setColor(255, 0, 255, 255);
        buffer.addVertex(matrix, -2.5f, 2.5f, z).setColor(255, 0, 255, 255);
    }

    private static void reset() {
        allocator = null;
        immediate = null;
    }

    /** Длина куска ленты в блоках: каждый кусок разворачивается к камере сам. */
    private static final float RIBBON_PIECE = 0.9f;

    /**
     * Лента по полилинии: ядро плюс мягкое свечение.
     *
     * <p>Каждый сегмент нарезается на короткие куски, и КАЖДЫЙ кусок
     * разворачивается к камере по своему собственному направлению взгляда.
     * Раньше весь сегмент (после струнной стяжки — до тридцати блоков)
     * ориентировался по середине: вблизи камера видела ленту, уехавшую вбок от
     * реальной линии на полблока и больше, — маршрут выглядел кривым.
     */
    private static void drawRibbon(VertexConsumer buffer, Matrix4f matrix,
                                   List<Vec3> pts, Vec3 camera, int r, int g, int b) {
        for (int i = 0; i + 1 < pts.size(); i++) {
            Vec3 p0 = pts.get(i);
            Vec3 p1 = pts.get(i + 1);
            double dx = p1.x - p0.x;
            double dy = p1.y - p0.y;
            double dz = p1.z - p0.z;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 1.0e-4) {
                continue;
            }
            int pieces = Math.max(1, Math.min(64, (int) Math.ceil(len / RIBBON_PIECE)));
            float px = (float) (p0.x - camera.x);
            float py = (float) (p0.y - camera.y);
            float pz = (float) (p0.z - camera.z);
            for (int j = 1; j <= pieces; j++) {
                double t = (double) j / pieces;
                float nx = (float) (p0.x + dx * t - camera.x);
                float ny = (float) (p0.y + dy * t - camera.y);
                float nz = (float) (p0.z + dz * t - camera.z);
                ribbon(buffer, matrix, px, py, pz, nx, ny, nz,
                        LINE_HALF * GLOW_SCALE, r, g, b, GLOW_ALPHA);
                ribbon(buffer, matrix, px, py, pz, nx, ny, nz,
                        LINE_HALF, r, g, b, LINE_ALPHA);
                px = nx;
                py = ny;
                pz = nz;
            }
        }
    }

    /**
     * Один сегмент ленты, повёрнутый к камере.
     *
     * @return {@code false}, если сегмент вырожденный и рисовать нечего
     */
    private static boolean ribbon(VertexConsumer buffer, Matrix4f matrix,
                                  float ax, float ay, float az,
                                  float bx, float by, float bz,
                                  float half, int r, int g, int b, float alpha) {
        float dx = bx - ax;
        float dy = by - ay;
        float dz = bz - az;
        float dlen = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dlen < 1.0e-4f) {
            return false;
        }

        // Направление от камеры (она в начале относительных координат) к середине
        // сегмента. С ним лента всегда развёрнута к зрителю.
        float mx = (ax + bx) * 0.5f;
        float my = (ay + by) * 0.5f;
        float mz = (az + bz) * 0.5f;
        float mlen = (float) Math.sqrt(mx * mx + my * my + mz * mz);
        if (mlen < 1.0e-3f) {
            mx = 0.0f;
            my = 0.0f;
            mz = 1.0f;
        } else {
            mx /= mlen;
            my /= mlen;
            mz /= mlen;
        }

        // side = normalize(direction × view) * half
        float sx = dy * mz - dz * my;
        float sy = dz * mx - dx * mz;
        float sz = dx * my - dy * mx;
        float slen = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (slen < 1.0e-3f) {
            // Сегмент смотрит почти прямо в камеру — векторное произведение с
            // направлением взгляда вырождается. Раньше такой сегмент просто
            // исчезал: после струнной стяжки длинная прямая впереди — это ОДИН
            // сегмент, и при взгляде вдоль маршрута линия пропадала целиком.
            // Берём любой устойчивый перпендикуляр: сначала мировая «ось Y»,
            // затем «ось X» (для вертикальных сегментов).
            sx = -dz;
            sy = 0.0f;
            sz = dx;
            slen = (float) Math.sqrt(sx * sx + sz * sz);
            if (slen < 1.0e-3f) {
                sx = 0.0f;
                sy = dz;
                sz = -dy;
                slen = (float) Math.sqrt(sy * sy + sz * sz);
                if (slen < 1.0e-3f) {
                    return false;
                }
            }
        }
        sx = sx / slen * half;
        sy = sy / slen * half;
        sz = sz / slen * half;

        int a = (int) (alpha * 255.0f);
        buffer.addVertex(matrix, ax - sx, ay - sy, az - sz).setColor(r, g, b, a);
        buffer.addVertex(matrix, ax + sx, ay + sy, az + sz).setColor(r, g, b, a);
        buffer.addVertex(matrix, bx + sx, by + sy, bz + sz).setColor(r, g, b, a);
        buffer.addVertex(matrix, bx - sx, by - sy, bz - sz).setColor(r, g, b, a);
        return true;
    }

    /** Половинный размер спрайта свечения, блоков. */
    private static final float BLOOM_SIZE = 0.22f;
    /** Шаг между спрайтами вдоль маршрута, блоков. */
    private static final float BLOOM_STEP = 0.85f;
    /** Прозрачность спрайта: сложение LIGHTNING делает тропу яркой без передоза. */
    private static final int BLOOM_ALPHA = 40;
    /**
     * Спрайты ближе этого расстояния до камеры не рисуются.
     *
     * <p>Маршрут начинается под ногами: без отсечки ближайший спрайт занимал бы
     * пол-экрана светящейся кляксой.
     */
    private static final double BLOOM_NEAR_CULL_SQ = 2.5 * 2.5;

    /**
     * Светящаяся тропа из спрайтов вдоль маршрута — приём из Projectile.
     *
     * <p>Каждый спрайт развёрнут к камере тем же поворотом, что и камера
     * (billboard), поэтому линия одинаково читается с любого ракурса. Шаг
     * меньше размера спрайта — получается непрерывное свечение, а не пунктир.
     */
    private static void drawBloomTrail(VertexConsumer buffer, PoseStack matrices, Vec3 camera,
                                       org.joml.Quaternionf cameraRotation, List<Vec3> pts,
                                       int r, int g, int b) {
        if (pts.size() < 2 || cameraRotation == null) {
            return;
        }
        for (int i = 0; i + 1 < pts.size(); i++) {
            Vec3 p0 = pts.get(i);
            Vec3 p1 = pts.get(i + 1);
            double dx = p1.x - p0.x;
            double dy = p1.y - p0.y;
            double dz = p1.z - p0.z;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            int samples = len < 1.0e-4 ? 1 : Math.max(2, Math.min(64, (int) Math.ceil(len / BLOOM_STEP)));
            // Концы включительно: на стыках сегментов спрайты накладываются
            // друг на друга и углам достается чуть больше свечения — стыки
            // ленты читаются как узлы маршрута, а не как дырки.
            for (int j = 0; j <= samples; j++) {
                double t = (double) j / samples;
                double rx = p0.x + dx * t - camera.x;
                double ry = p0.y + dy * t - camera.y;
                double rz = p0.z + dz * t - camera.z;
                if (rx * rx + ry * ry + rz * rz < BLOOM_NEAR_CULL_SQ) {
                    continue;
                }
                matrices.pushPose();
                matrices.translate(rx, ry, rz);
                matrices.rotate(cameraRotation);
                bloomQuad(buffer, matrices.last().pose(), r, g, b);
                matrices.popPose();
            }
        }
    }

    /** Один спрайт свечения; UV и обход — как в Projectile#addBloomQuad. */
    private static void bloomQuad(VertexConsumer buffer, Matrix4f matrix, int r, int g, int b) {
        float x = -BLOOM_SIZE;
        float y = -BLOOM_SIZE;
        float x2 = BLOOM_SIZE;
        float y2 = BLOOM_SIZE;
        buffer.addVertex(matrix, x, y, 0.0f).setUv(0.0f, 0.0f).setColor(r, g, b, BLOOM_ALPHA);
        buffer.addVertex(matrix, x, y2, 0.0f).setUv(0.0f, 1.0f).setColor(r, g, b, BLOOM_ALPHA);
        buffer.addVertex(matrix, x2, y2, 0.0f).setUv(1.0f, 1.0f).setColor(r, g, b, BLOOM_ALPHA);
        buffer.addVertex(matrix, x2, y, 0.0f).setUv(1.0f, 0.0f).setColor(r, g, b, BLOOM_ALPHA);
    }

    /** Пульсирующая заливка блока цели. */
    private static void drawGoalFill(VertexConsumer buffer, Matrix4f matrix, BlockPos goal,
                                     Vec3 camera, int r, int g, int b) {
        float pulse = 0.12f + 0.07f * (float) Math.sin(System.currentTimeMillis() / 380.0);
        int alpha = (int) (pulse * 255.0f);
        if (alpha <= 0) {
            return;
        }

        float minX = (float) (goal.getX() - camera.x);
        float minY = (float) (goal.getY() - camera.y);
        float minZ = (float) (goal.getZ() - camera.z);
        float maxX = minX + 1.0f;
        float maxY = minY + 1.0f;
        float maxZ = minZ + 1.0f;

        face(buffer, matrix, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, alpha);
        face(buffer, matrix, minX, maxY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, alpha);
        face(buffer, matrix, minX, minY, minZ, maxX, minY, minZ, maxX, maxY, minZ, minX, maxY, minZ, r, g, b, alpha);
        face(buffer, matrix, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, alpha);
        face(buffer, matrix, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, alpha);
        face(buffer, matrix, maxX, minY, minZ, maxX, minY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, r, g, b, alpha);
    }

    /** Каркас блока цели. */
    private static void drawGoalFrame(VertexConsumer buffer, Matrix4f matrix, BlockPos goal,
                                      Vec3 camera, int r, int g, int b) {
        float minX = (float) (goal.getX() - camera.x) - 0.004f;
        float minY = (float) (goal.getY() - camera.y) - 0.004f;
        float minZ = (float) (goal.getZ() - camera.z) - 0.004f;
        float maxX = (float) (goal.getX() - camera.x) + 1.004f;
        float maxY = (float) (goal.getY() - camera.y) + 1.004f;
        float maxZ = (float) (goal.getZ() - camera.z) + 1.004f;
        int alpha = 255;

        edge(buffer, matrix, minX, minY, minZ, maxX, minY, minZ, r, g, b, alpha);
        edge(buffer, matrix, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, alpha);
        edge(buffer, matrix, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, alpha);
        edge(buffer, matrix, minX, minY, maxZ, minX, minY, minZ, r, g, b, alpha);
        edge(buffer, matrix, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, alpha);
        edge(buffer, matrix, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, alpha);
        edge(buffer, matrix, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, alpha);
        edge(buffer, matrix, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, alpha);
        edge(buffer, matrix, minX, minY, minZ, minX, maxY, minZ, r, g, b, alpha);
        edge(buffer, matrix, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, alpha);
        edge(buffer, matrix, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, alpha);
        edge(buffer, matrix, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, alpha);
    }

    private static void face(VertexConsumer buffer, Matrix4f matrix,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             int r, int g, int b, int alpha) {
        buffer.addVertex(matrix, ax, ay, az).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, bx, by, bz).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, cx, cy, cz).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, dx, dy, dz).setColor(r, g, b, alpha);
    }

    private static void edge(VertexConsumer buffer, Matrix4f matrix,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             int r, int g, int b, int alpha) {
        buffer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, alpha);
    }
}
