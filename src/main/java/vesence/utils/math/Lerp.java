package vesence.utils.math;

public class Lerp {
    public static double calc(double input, double target, double step) {
        return input + step * (target - input);
    }

    public static float calc(float input, float target, double step) {
        return (float) (input + step * (target - input));
    }

    public static int calc(int input, int target, double step) {
        return (int) (input + step * (target - input));
    }
}
