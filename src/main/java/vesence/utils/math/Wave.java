package vesence.utils.math;

import vesence.utils.other.Mathf;
import vesence.utils.render.animation.util.Easing;

public class Wave {
    public static float sinWave(double value, double delayMS, Easing easing) {
        return (float) (Mathf.clamp01(easing.ease((Math.sin(System.currentTimeMillis() / delayMS) + 1F) / 2F)) * value);
    }

    public static float cosWave(double value, double delayMS, Easing easing) {
        return (float) (Mathf.clamp01(easing.ease((Math.cos(System.currentTimeMillis() / delayMS) + 1F) / 2F)) * value);
    }

    public static float sinWave(double from, double to, double delayMS, Easing easing) {
        return (float) (from + sinWave(to, delayMS, easing));
    }

    public static float cosWave(double from, double to, double delayMS, Easing easing) {
        return (float) (from + cosWave(to, delayMS, easing));
    }
}
