package vesence.utils.render;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import org.joml.Matrix4f;

/**
 * Порт {@code UVoronoiIntegration} из Relevant, адаптированный под immediate-mode
 * рендер vesence (1.21.11).
 *
 * <p>В оригинале каждый полигон рисовался отдельным {@code Tessellator.begin/draw}
 * в режиме {@code TRIANGLE_FAN}/{@code DEBUG_LINE_STRIP}. В immediate-mode все
 * вершины одного {@link net.minecraft.client.render.RenderLayer} попадают в общий
 * буфер, поэтому несколько fan'ов «слиплись» бы в один. Чтобы этого избежать:
 * <ul>
 *   <li>заливка триангулируется (fan → треугольники {@code v0,vi,vi+1}) и пишется
 *       в слой с режимом {@code TRIANGLES};</li>
 *   <li>контур разбивается на отдельные сегменты {@code (vi,vi+1)} и пишется в
 *       слой с режимом {@code DEBUG_LINES}.</li>
 * </ul>
 * Визуально результат идентичен (fan = набор тех же треугольников).
 */
public class UVoronoiIntegration {
    private VoronoiOfQuad voronoi;
    private Matrix4f matrix;

    public static UVoronoiIntegration generateDefault(int pointsCount, boolean createInitThread) {
        return new UVoronoiIntegration(new VoronoiOfQuad(-0.5f, -0.5f, 0.5f, 0.5f, pointsCount, createInitThread));
    }

    public UVoronoiIntegration(VoronoiOfQuad voronoi) {
        this.voronoi = voronoi;
    }

    public UVoronoiIntegration setMatrix(Matrix4f matrix) {
        this.matrix = matrix;
        return this;
    }

    private List<VoronoiOfQuad.Polygon> snapshotPolygons() {
        List<VoronoiOfQuad.Polygon> base = new ArrayList<>();
        for (VoronoiOfQuad.Polygon polygon : voronoi.getPolygons()) {
            base.add(polygon);
        }
        return base;
    }

    private List<VoronoiOfQuad.Polygon> getSpreadPolygons(List<VoronoiOfQuad.Polygon> base, boolean asCopied, float mulTrans, float mulRotate) {
        List<VoronoiOfQuad.Polygon> polygons;
        if (asCopied) {
            polygons = new ArrayList<>(base.size());
            for (VoronoiOfQuad.Polygon polygon : base) {
                polygons.add(polygon.copy());
            }
        } else {
            polygons = base;
        }

        if (mulTrans == 0.0f && mulRotate == 0.0f) {
            return polygons;
        }

        List<VoronoiOfQuad.Polygon> result = new ArrayList<>(polygons.size());
        for (VoronoiOfQuad.Polygon poly : polygons) {
            VoronoiOfQuad.Polygon updated = poly;
            if (mulTrans != 0.0f) {
                updated = updated.translateAwayPosLoc(voronoi.cx, voronoi.cy,
                        updated.distanceToAtCenter(voronoi.cx, voronoi.cy) * mulTrans);
            }
            if (mulRotate != 0.0f) {
                updated = updated.rotateAtYOfAngleAwayPos(voronoi.cx, voronoi.cy,
                        updated.distanceToAtCenter(voronoi.cx, voronoi.cy) * mulRotate);
            }
            result.add(updated);
        }
        return result;
    }

    /** Заливка: триангуляция fan'ов в общий {@code TRIANGLES}-буфер, 4-цветный bilinear. */
    public void renderFillSegments(VertexConsumer buffer, boolean temporalMode, float trans, float rot,
                                   float aPC, int color1, int color2, int color3, int color4) {
        if (voronoi == null) {
            return;
        }
        List<VoronoiOfQuad.Polygon> basePolygons = snapshotPolygons();
        if (basePolygons.isEmpty()) {
            return;
        }
        List<VoronoiOfQuad.Polygon> polygonsToDraw = getSpreadPolygons(basePolygons, temporalMode, trans, rot);
        if (polygonsToDraw.isEmpty()) {
            return;
        }

        int indexPoly = 0;
        for (VoronoiOfQuad.Polygon renderPolygon : polygonsToDraw) {
            if (indexPoly >= basePolygons.size()) {
                break;
            }
            VoronoiOfQuad.Polygon staticPolygon = basePolygons.get(indexPoly);
            indexPoly++;
            if (staticPolygon == null) {
                continue;
            }
            List<VoronoiOfQuad.Vec2f> staticVecList = staticPolygon.getAllVertices();
            List<VoronoiOfQuad.Vec2f> dynamicVecList = renderPolygon.getAllVertices();
            int n = Math.min(staticVecList.size(), dynamicVecList.size());
            if (n < 3) {
                continue;
            }
            // Триангуляция fan'а под DrawMode.QUADS: каждый треугольник веера
            // (v0, vi, vi+1) подаётся как вырожденный quad (v0, vi, vi+1, vi+1).
            // Заливка идёт через TEXTURED_QUADS пайплайн (как картинки таргета) —
            // это единственный путь, дающий видимую текстурированную заливку
            // осколков в pipeline'е 1.21.11. 4-я вершина дублирует 3-ю, что не
            // меняет форму треугольника, но удовлетворяет требованию QUADS
            // (кратность 4 вершины на примитив).
            for (int i = 1; i < n - 1; i++) {
                pushVertex4Color(buffer, dynamicVecList.get(0), staticVecList.get(0), color1, color2, color3, color4, aPC);
                pushVertex4Color(buffer, dynamicVecList.get(i), staticVecList.get(i), color1, color2, color3, color4, aPC);
                pushVertex4Color(buffer, dynamicVecList.get(i + 1), staticVecList.get(i + 1), color1, color2, color3, color4, aPC);
                pushVertex4Color(buffer, dynamicVecList.get(i + 1), staticVecList.get(i + 1), color1, color2, color3, color4, aPC);
            }
        }
    }

    /** Контур: сегменты (vi,vi+1) в общий {@code DEBUG_LINES}-буфер, 4-цветный bilinear. */
    public void renderLineSegments(VertexConsumer buffer, boolean temporalMode, float trans, float rot,
                                   float aPC, int color1, int color2, int color3, int color4) {
        if (voronoi == null) {
            return;
        }
        List<VoronoiOfQuad.Polygon> basePolygons = snapshotPolygons();
        if (basePolygons.isEmpty()) {
            return;
        }
        List<VoronoiOfQuad.Polygon> polygonsToDraw = getSpreadPolygons(basePolygons, temporalMode, trans, rot);
        if (polygonsToDraw.isEmpty()) {
            return;
        }

        int indexPoly = 0;
        for (VoronoiOfQuad.Polygon renderPolygon : polygonsToDraw) {
            if (indexPoly >= basePolygons.size()) {
                break;
            }
            VoronoiOfQuad.Polygon staticPolygon = basePolygons.get(indexPoly);
            indexPoly++;
            if (staticPolygon == null) {
                continue;
            }
            List<VoronoiOfQuad.Vec2f> staticVecList = staticPolygon.getAllVertices();
            List<VoronoiOfQuad.Vec2f> dynamicVecList = renderPolygon.getAllVertices();
            int n = Math.min(staticVecList.size(), dynamicVecList.size());
            if (n < 2) {
                continue;
            }
            for (int i = 0; i < n - 1; i++) {
                pushVertexColorOnly(buffer, dynamicVecList.get(i), staticVecList.get(i), color1, color2, color3, color4, aPC);
                pushVertexColorOnly(buffer, dynamicVecList.get(i + 1), staticVecList.get(i + 1), color1, color2, color3, color4, aPC);
            }
        }
    }

    private void pushVertex4Color(VertexConsumer buffer, VoronoiOfQuad.Vec2f posVec, VoronoiOfQuad.Vec2f texVec,
                                  int c1, int c2, int c3, int c4, float aPC) {
        float u = uvU(texVec);
        float v = uvV(texVec);
        int color = bilerp(c1, c2, c3, c4, u, v);
        int alpha = (int) (ColorUtil.alpha(color) * aPC);
        alpha = Math.max(0, Math.min(255, alpha));
        int r = ColorUtil.red(color);
        int g = ColorUtil.green(color);
        int b = ColorUtil.blue(color);
        if (matrix != null) {
            buffer.vertex(matrix, posVec.x, posVec.y, 0.0f)
                    .color(r, g, b, alpha)
                    .texture(u, v)
                    .overlay(OverlayTexture.DEFAULT_UV)
                    .light(15728880)
                    .normal(0.0f, 0.0f, 1.0f);
        } else {
            buffer.vertex(posVec.x, posVec.y, 0.0f)
                    .color(r, g, b, alpha)
                    .texture(u, v)
                    .overlay(OverlayTexture.DEFAULT_UV)
                    .light(15728880)
                    .normal(0.0f, 0.0f, 1.0f);
        }
    }

    /** Контур пишется в POSITION_COLOR-слой (без текстуры/overlay/normal). */
    private void pushVertexColorOnly(VertexConsumer buffer, VoronoiOfQuad.Vec2f posVec, VoronoiOfQuad.Vec2f texVec,
                                     int c1, int c2, int c3, int c4, float aPC) {
        float u = uvU(texVec);
        float v = uvV(texVec);
        int color = bilerp(c1, c2, c3, c4, u, v);
        int alpha = (int) (ColorUtil.alpha(color) * aPC);
        alpha = Math.max(0, Math.min(255, alpha));
        int r = ColorUtil.red(color);
        int g = ColorUtil.green(color);
        int b = ColorUtil.blue(color);
        if (matrix != null) {
            buffer.vertex(matrix, posVec.x, posVec.y, 0.0f).color(r, g, b, alpha);
        } else {
            buffer.vertex(posVec.x, posVec.y, 0.0f).color(r, g, b, alpha);
        }
    }

    private float uvU(VoronoiOfQuad.Vec2f texVec) {
        float width = Math.max(0.0001f, voronoi.x2 - voronoi.x);
        return clamp01((texVec.x - voronoi.x) / width);
    }

    private float uvV(VoronoiOfQuad.Vec2f texVec) {
        float height = Math.max(0.0001f, voronoi.y2 - voronoi.y);
        return clamp01((texVec.y - voronoi.y) / height);
    }

    private static int bilerp(int c1, int c2, int c3, int c4, float u, float v) {
        // c1 top-left, c2 top-right, c3 bottom-right, c4 bottom-left
        int top = ColorUtil.interpolate(c1, c2, u);
        int bottom = ColorUtil.interpolate(c4, c3, u);
        return ColorUtil.interpolate(top, bottom, v);
    }

    private static float clamp01(float v) {
        if (v < 0.0f) {
            return 0.0f;
        }
        if (v > 1.0f) {
            return 1.0f;
        }
        return v;
    }
}
