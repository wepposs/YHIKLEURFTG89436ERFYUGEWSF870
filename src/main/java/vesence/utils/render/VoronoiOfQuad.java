package vesence.utils.render;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Порт {@code VoronoiOfQuad} из Relevant. Чистая 2D-математика без MC-зависимостей.
 *
 * <p>Строит диаграмму Вороного на прямоугольнике {@code [x..x2] × [y..y2]} для
 * набора точек. Каждая ячейка — выпуклый полигон ({@link Vec2f}). Используется
 * для «осколочного» эффекта HitBubble.
 */
public class VoronoiOfQuad {
    private List<Polygon> polygons = new ArrayList<>();
    private List<Polygon> polygonsDontTouch = new ArrayList<>();
    public float x;
    public float y;
    public float x2;
    public float y2;
    public float cx;
    public float cy;
    private static final Random RANDOM = new Random();
    private static final AtomicInteger UNIQUE_COUNTER = new AtomicInteger();
    private final ArrayList<Polygon> tempListPolygon = new ArrayList<>();

    public VoronoiOfQuad(float x, float y, float x2, float y2, List<Vec2f> points, boolean createInitThread) {
        this.x = x;
        this.y = y;
        this.x2 = x2;
        this.y2 = y2;
        this.cx = this.x + (this.x2 - this.x) / 2.0f;
        this.cy = this.y + (this.y2 - this.y) / 2.0f;
        if (createInitThread) {
            CompletableFuture.runAsync(() -> {
                this.polygons = getVoronoiPolygons(x, y, x2, y2, points);
                this.polygonsDontTouch = copyList(polygons);
            });
        } else {
            this.polygons = getVoronoiPolygons(x, y, x2, y2, points);
            this.polygonsDontTouch = copyList(polygons);
        }
    }

    public VoronoiOfQuad(float x, float y, float x2, float y2, int countOfPoints, boolean createInitThread) {
        this(x, y, x2, y2, new ArrayList<>(), createInitThread);
        List<Vec2f> points = genPointsInBounds(x, y, x2, y2, countOfPoints);
        if (createInitThread) {
            CompletableFuture.runAsync(() -> {
                this.polygons = getVoronoiPolygons(x, y, x2, y2, points);
                this.polygonsDontTouch = copyList(polygons);
            });
        } else {
            this.polygons = getVoronoiPolygons(x, y, x2, y2, points);
            this.polygonsDontTouch = copyList(polygons);
        }
    }

    public List<Polygon> getPolygons() {
        return this.polygons;
    }

    public List<Polygon> getPolygonsDontTouch() {
        return this.polygonsDontTouch;
    }

    private List<Polygon> copyList(List<Polygon> src) {
        List<Polygon> list = new ArrayList<>();
        for (Polygon p : src) {
            list.add(p.copy());
        }
        return list;
    }

    private List<Vec2f> genPointsInBounds(float x, float y, float x2, float y2, int countOfPoints) {
        List<Vec2f> list = new ArrayList<>();
        for (int i = 0; i < countOfPoints; i++) {
            float px = x + RANDOM.nextFloat() * (x2 - x);
            float py = y + RANDOM.nextFloat() * (y2 - y);
            list.add(new Vec2f(px, py));
        }
        return list;
    }

    public List<Polygon> getVoronoiPolygons(float x, float y, float x2, float y2, List<Vec2f> points) {
        tempListPolygon.clear();

        List<Vec2f> boundsRectVecList = Arrays.asList(
                new Vec2f(x, y),
                new Vec2f(x2, y),
                new Vec2f(x2, y2),
                new Vec2f(x, y2)
        );
        int countOfPoints = points.size();

        for (int iLevel0 = 0; iLevel0 < countOfPoints; ++iLevel0) {
            List<Vec2f> current = new ArrayList<>(boundsRectVecList);

            for (int iLevel1 = 0; iLevel1 < countOfPoints; ++iLevel1) {
                if (iLevel1 != iLevel0) {
                    float a = 2.0f * (points.get(iLevel1).x - points.get(iLevel0).x);
                    float b = 2.0f * (points.get(iLevel1).y - points.get(iLevel0).y);
                    float c = points.get(iLevel0).x * points.get(iLevel0).x + points.get(iLevel0).y * points.get(iLevel0).y
                            - (points.get(iLevel1).x * points.get(iLevel1).x + points.get(iLevel1).y * points.get(iLevel1).y);
                    current = clipPolygon(current, a, b, c);
                    if (current.isEmpty()) {
                        break;
                    }
                }
            }

            if (!current.isEmpty()) {
                tempListPolygon.add(new Polygon(this, new ArrayList<>(current), UNIQUE_COUNTER.incrementAndGet()));
            }
        }

        return new ArrayList<>(tempListPolygon);
    }

    private List<Vec2f> clipPolygon(List<Vec2f> polygon, float a, float b, float c) {
        List<Vec2f> tempListVec2f = new ArrayList<>();
        int n = polygon.size();

        for (int i = 0; i < n; ++i) {
            Vec2f p1 = polygon.get(i);
            Vec2f p2 = polygon.get((i + 1) % n);
            boolean s1 = isInside(p1, a, b, c);
            boolean s2 = isInside(p2, a, b, c);
            if (s1) {
                if (!s2) {
                    Vec2f intersect = findIntersection(p1, p2, a, b, c);
                    if (intersect != null) {
                        tempListVec2f.add(intersect);
                    }
                } else {
                    tempListVec2f.add(p2);
                }
            } else if (s2) {
                Vec2f intersect = findIntersection(p1, p2, a, b, c);
                if (intersect != null) {
                    tempListVec2f.add(intersect);
                }
                tempListVec2f.add(p2);
            }
        }

        return tempListVec2f;
    }

    private boolean isInside(Vec2f p, float a, float b, float c) {
        return a * p.x + b * p.y + c <= 0.0f;
    }

    private Vec2f findIntersection(Vec2f p1, Vec2f p2, float a, float b, float c) {
        float dx = p2.x - p1.x;
        float dy = p2.y - p1.y;
        float denom = a * dx + b * dy;
        if (Math.abs(denom) < 1.0E-4) {
            return null;
        }
        float num = -(a * p1.x + b * p1.y + c);
        float t = num / denom;
        if (t >= 0.0f && t <= 1.0f) {
            return new Vec2f(p1.x + t * dx, p1.y + t * dy);
        }
        return null;
    }

    public static class Vec2f {
        public float x;
        public float y;

        public Vec2f(float x, float y) {
            this.x = x;
            this.y = y;
        }

        public Vec2f copy() {
            return new Vec2f(x, y);
        }
    }

    public static class Polygon {
        private final VoronoiOfQuad owner;
        private final List<Vec2f> vertices;
        public final int uniqueInt;

        public Polygon(VoronoiOfQuad owner, List<Vec2f> vertices, int uniqueInt) {
            this.owner = owner;
            this.vertices = vertices;
            this.uniqueInt = uniqueInt;
        }

        public List<Vec2f> getAllVertices() {
            return vertices;
        }

        public Polygon copy() {
            List<Vec2f> copy = new ArrayList<>();
            for (Vec2f v : vertices) {
                copy.add(v.copy());
            }
            return new Polygon(owner, copy, uniqueInt);
        }

        public float distanceToAtCenter(float cx, float cy) {
            Vec2f center = getCenter();
            float dx = center.x - cx;
            float dy = center.y - cy;
            return (float) Math.sqrt(dx * dx + dy * dy);
        }

        public Polygon translateAwayPosLoc(float cx, float cy, float amount) {
            if (amount == 0.0f) {
                return this;
            }
            Vec2f center = getCenter();
            float dx = center.x - cx;
            float dy = center.y - cy;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 1.0E-4f) {
                return this;
            }
            float nx = dx / len;
            float ny = dy / len;
            for (Vec2f v : vertices) {
                v.x += nx * amount;
                v.y += ny * amount;
            }
            return this;
        }

        public Polygon rotateAtYOfAngleAwayPos(float cx, float cy, float angle) {
            if (angle == 0.0f) {
                return this;
            }
            double rad = Math.toRadians(angle);
            float cos = (float) Math.cos(rad);
            float sin = (float) Math.sin(rad);
            for (Vec2f v : vertices) {
                float px = v.x - cx;
                float py = v.y - cy;
                float rx = px * cos - py * sin;
                float ry = px * sin + py * cos;
                v.x = rx + cx;
                v.y = ry + cy;
            }
            return this;
        }

        private Vec2f getCenter() {
            if (vertices.isEmpty()) {
                return new Vec2f(owner.cx, owner.cy);
            }
            float sx = 0.0f;
            float sy = 0.0f;
            for (Vec2f v : vertices) {
                sx += v.x;
                sy += v.y;
            }
            float inv = 1.0f / vertices.size();
            return new Vec2f(sx * inv, sy * inv);
        }
    }
}
