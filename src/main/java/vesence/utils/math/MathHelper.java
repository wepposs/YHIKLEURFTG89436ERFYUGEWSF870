package vesence.utils.math;

public class MathHelper {
    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static float interpolate(float target, float current, double speed) {
        return (float) (current + (target - current) * speed);
    }

    public static float round(float value, float increment) {
        if (increment <= 0) return value;
        return Math.round(value / increment) * increment;
    }
}
