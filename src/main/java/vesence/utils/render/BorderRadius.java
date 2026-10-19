package vesence.utils.render;

public record BorderRadius(float topLeftRadius, float topRightRadius, float bottomLeftRadius, float bottomRightRadius) {
    public static BorderRadius all(float radius) {
        return new BorderRadius(radius, radius, radius, radius);
    }
    public static BorderRadius all(float topLeftRadius, float topRightRadius, float bottomLeftRadius, float bottomRightRadius) {
        return new BorderRadius(topLeftRadius, topRightRadius, bottomLeftRadius, bottomRightRadius);
    }
}
