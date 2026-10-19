package vesence.utils.render;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Порт {@code HitBubbleEffect} из Relevant под immediate-mode рендер vesence (1.21.11).
 *
 * <p>Хранит список «пузырей» — биллборд-квадратов с текстурой картинки таргета,
 * ориентированных по yaw/pitch камеры в момент спавна. Пузырь живёт {@code maxTime}
 * мс и линейно анимирует прозрачность/сдвиг/масштаб. В режиме «Осколки» рисуется
 * полигонами Вороного, разлетающимися от центра.
 *
 * <p>В отличие от оригинала ({@code RenderSystem} + {@code Tessellator}), здесь
 * вершины пишутся в готовые {@link VertexConsumer} переданных слоёв — это требование
 * pipeline'а 1.21.11.
 */
public class HitBubbleEffect {

    public enum ColorMode {
        RAINBOW,
        CLIENT
    }

    private static final int ROTATE_SPEED = 4;
    private static final long ROTATION_PERIOD_MS = 3600L / ROTATE_SPEED;

    private final List<Bubble> bubbles = new ArrayList<>();

    public void clear() {
        bubbles.clear();
    }

    public boolean isEmpty() {
        return bubbles.isEmpty();
    }

    /** Заспавнить пузырь в позиции {@code pos} с ориентацией камеры yaw/pitch. */
    public void spawn(Vec3d pos, float yaw, float pitch, float maxTimeMs) {
        bubbles.add(new Bubble(pos, yaw, pitch, maxTimeMs, System.currentTimeMillis()));
    }

    /**
     * Рендер всех живых пузырей.
     *
     * @param quadBuffer       слой для обычного textured-quad (additive)
     * @param voronoiFill      слой TRIANGLES (additive, та же текстура)
     * @param voronoiLine      слой DEBUG_LINES (additive)
     */
    public void render(MatrixStack ms,
                       Vec3d cameraPos,
                       VertexConsumer quadBuffer,
                       VertexConsumer voronoiFill,
                       VertexConsumer voronoiLine,
                       float moduleAlpha,
                       ColorMode colorMode,
                       int themeColor1,
                       int themeColor2,
                       boolean voronoiActive,
                       int voronoiPoints) {
        if (bubbles.isEmpty() || ms == null || cameraPos == null) {
            return;
        }
        if (moduleAlpha < 0.05f) {
            return;
        }

        long now = System.currentTimeMillis();

        Iterator<Bubble> it = bubbles.iterator();
        while (it.hasNext()) {
            Bubble bubble = it.next();
            float delta = bubble.getDeltaTime(now);
            if (delta >= 1.0f) {
                it.remove();
                continue;
            }
            drawBubble(ms, bubble, moduleAlpha, cameraPos, quadBuffer, voronoiFill, voronoiLine,
                    voronoiActive, voronoiPoints, colorMode, themeColor1, themeColor2, now, delta);
        }
    }

    /** Только обновить (стереть просроченные). */
    public void tick() {
        long now = System.currentTimeMillis();
        bubbles.removeIf(b -> b.getDeltaTime(now) >= 1.0f);
    }

    private void drawBubble(MatrixStack ms, Bubble bubble, float alphaPC, Vec3d cameraPos,
                            VertexConsumer quadBuffer, VertexConsumer voronoiFill, VertexConsumer voronoiLine,
                            boolean voronoiActive, int voronoiPoints,
                            ColorMode colorMode, int themeColor1, int themeColor2,
                            long now, float delta) {
        if (delta <= 0.0f) {
            return;
        }

        float aPC = (float) easeInOutQuadWave(MathHelper.clamp((delta + 0.1f) * alphaPC, 0.0f, 1.0f)) * 2.0f;
        if (aPC > 1.0f) {
            aPC = 1.0f;
        }
        if (delta > 0.5f) {
            aPC *= aPC;
        }
        aPC *= alphaPC;
        if (aPC <= 0.01f) {
            return;
        }

        double x = bubble.pos.x - cameraPos.x;
        double y = bubble.pos.y - cameraPos.y;
        double z = bubble.pos.z - cameraPos.z;

        ms.push();
        ms.translate(x, y, z);

        if (!voronoiActive) {
            ms.translate(-bubble.pitchSin * delta / 3.0f, bubble.yawSin * delta / 2.0f, -bubble.pitchCos * delta / 3.0f);
        }

        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-bubble.viewYaw));
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(bubble.viewPitch));
        ms.scale(-0.1f, -0.1f, 0.1f);

        float rBase = 12.5f;
        float r = rBase * aPC;
        float rotation = (now % ROTATION_PERIOD_MS) / 10.0f * ROTATE_SPEED;

        if (voronoiActive) {
            UVoronoiIntegration integration = bubble.getVoronoiIntegration(voronoiPoints);
            if (integration != null) {
                ms.push();
                ms.scale(rBase, rBase, 1.0f);
                integration.setMatrix(ms.peek().getPositionMatrix());
                float timePCBase = delta;
                float timePC = Math.min(delta / 0.75f, 1.0f);
                timePC = (float) easeInOutExpo(0.25f + timePC);
                float voronoiAPC = Math.max(
                        1.0f - Math.min(timePCBase * 1.5f, 1.0f) - (1.0f - Math.min(timePCBase / 0.2f, 1.0f)),
                        0.0f
                );
                // Цвет осколков берётся из настроек (Радужный / Клиент).
                // Vertex color перемножается с текстурой в шейдере — это даёт
                // окрашенные осколки с рисунком картинки таргета.
                int fill0 = stateColor(0, 1.0f, colorMode, themeColor1, themeColor2, now);
                int fill1 = stateColor(270, 1.0f, colorMode, themeColor1, themeColor2, now);
                int fill2 = stateColor(540, 1.0f, colorMode, themeColor1, themeColor2, now);
                int fill3 = stateColor(810, 1.0f, colorMode, themeColor1, themeColor2, now);
                integration.renderFillSegments(voronoiFill, true,
                        timePC * 0.8f + timePCBase * 0.2f,
                        60.0f * timePC + 40.0f * timePCBase,
                        voronoiAPC, fill0, fill1, fill2, fill3);

                // Контур осколков рисуется только если передан line-буфер.
                if (voronoiLine != null) {
                    int lineWhite = 0xFFFFFFFF;
                    integration.renderLineSegments(voronoiLine, true,
                            timePC * 0.8f + timePCBase * 0.2f,
                            60.0f * timePC + 40.0f * timePCBase,
                            voronoiAPC * timePC, lineWhite, lineWhite, lineWhite, lineWhite);
                }
                ms.pop();
                ms.pop();
                return;
            }
        }

        ms.push();
        ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotation));
        Matrix4f quadMatrix = ms.peek().getPositionMatrix();
        drawBubbleQuad(quadBuffer, quadMatrix, r, aPC, colorMode, themeColor1, themeColor2, now);
        ms.pop();

        ms.pop();
    }

    private void drawBubbleQuad(VertexConsumer buffer, Matrix4f matrix, float size, float alphaPC,
                                ColorMode colorMode, int themeColor1, int themeColor2, long now) {
        float half = size / 2.0f;
        int c1 = stateColor(0, alphaPC, colorMode, themeColor1, themeColor2, now);
        int c2 = stateColor(90, alphaPC, colorMode, themeColor1, themeColor2, now);
        int c3 = stateColor(180, alphaPC, colorMode, themeColor1, themeColor2, now);
        int c4 = stateColor(270, alphaPC, colorMode, themeColor1, themeColor2, now);

        putVertex(buffer, matrix, -half, -half, 0.0f, 0.0f, 0.0f, c1);
        putVertex(buffer, matrix, -half, half, 0.0f, 0.0f, 1.0f, c2);
        putVertex(buffer, matrix, half, half, 0.0f, 1.0f, 1.0f, c3);
        putVertex(buffer, matrix, half, -half, 0.0f, 1.0f, 0.0f, c4);
    }

    private static int stateColor(int index, float alphaPC, ColorMode colorMode,
                                  int themeColor1, int themeColor2, long now) {
        int color;
        switch (colorMode) {
            case RAINBOW:
                color = ColorUtil.rainbow(8, index, 0.85f, 1.0f, 1.0f);
                break;
            case CLIENT:
            default:
                // theme1↔theme2 «дыхание» по фазе index — мягкий перелив темы.
                float t = (float) ((Math.sin((now / 300.0) + index * 0.05) + 1.0) * 0.5);
                color = ColorUtil.interpolate(themeColor1, themeColor2, t);
                break;
        }
        int alpha = (int) (ColorUtil.alpha(color) * alphaPC);
        return ColorUtil.replAlpha(color, MathHelper.clamp(alpha, 0, 255));
    }

    private static int blendWithWhite(int color, float amount) {
        int white = ColorUtil.replAlpha(0xFFFFFFFF, ColorUtil.alpha(color));
        return ColorUtil.interpolate(color, white, amount);
    }

    private static void putVertex(VertexConsumer buffer, Matrix4f matrix,
                                  float x, float y, float z,
                                  float u, float v, int color) {
        buffer.vertex(matrix, x, y, z)
                .color(ColorUtil.red(color), ColorUtil.green(color), ColorUtil.blue(color), ColorUtil.alpha(color))
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(15728880)
                .normal(0.0f, 0.0f, 1.0f);
    }

    private static double easeInOutQuadWave(double t) {
        t = MathHelper.clamp((float) t, 0.0f, 1.0f);
        double s = Math.sin(Math.PI * t);
        return s * s;
    }

    private static double easeInOutExpo(double t) {
        t = MathHelper.clamp((float) t, 0.0f, 1.0f);
        if (t == 0.0) return 0.0;
        if (t == 1.0) return 1.0;
        return t < 0.5 ? Math.pow(2.0, 20.0 * t - 10.0) / 2.0 : (2.0 - Math.pow(2.0, -20.0 * t + 10.0)) / 2.0;
    }

    /** Один пузырь: позиция, yaw/pitch камеры при спавне, длительность, время спавна. */
    private static final class Bubble {
        private final Vec3d pos;
        private final float viewYaw;
        private final float viewPitch;
        private final float yawSin;
        private final float pitchSin;
        private final float pitchCos;
        private final long startTime;
        private final float maxTime;
        private UVoronoiIntegration voronoi;
        private int voronoiPoints;

        private Bubble(Vec3d pos, float viewYaw, float viewPitch, float maxTime, long startTime) {
            this.pos = pos;
            this.viewYaw = viewYaw;
            this.viewPitch = viewPitch;
            this.maxTime = maxTime;
            this.startTime = startTime;
            float yawRad = (float) Math.toRadians(viewYaw);
            float pitchRad = (float) Math.toRadians(viewPitch);
            this.yawSin = MathHelper.sin(yawRad);
            this.pitchSin = MathHelper.sin(pitchRad);
            this.pitchCos = MathHelper.cos(pitchRad);
        }

        private float getDeltaTime(long now) {
            if (maxTime <= 0.0f) return 1.0f;
            return (now - startTime) / maxTime;
        }

        private UVoronoiIntegration getVoronoiIntegration(int points) {
            if (voronoi == null || voronoiPoints != points) {
                // Синхронная генерация: пузырь живёт всего 1200мс и спавнится разово,
                // поэтому асинхронная инициализация не успевала бы — осколки не
                // появлялись бы вовсе. Стоимость генерации мала даже для 170 точек.
                voronoi = UVoronoiIntegration.generateDefault(points, false);
                voronoiPoints = points;
            }
            return voronoi;
        }
    }
}
