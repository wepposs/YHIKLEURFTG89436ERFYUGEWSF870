package vesence.renderengine.render;

public final class ColorUtility {
    public static int red(int c) {
        return c >> 16 & 0xFF;
    }

    public static int green(int c) {
        return c >> 8 & 0xFF;
    }

    public static int blue(int c) {
        return c & 0xFF;
    }

    public static int alpha(int c) {
        return c >> 24 & 0xFF;
    }

    public static float redf(int c) {
        return (float)ColorUtility.red(c) / 255.0f;
    }

    public static float greenf(int c) {
        return (float)ColorUtility.green(c) / 255.0f;
    }

    public static float bluef(int c) {
        return (float)ColorUtility.blue(c) / 255.0f;
    }

    public static float alphaf(int c) {
        return (float)ColorUtility.alpha(c) / 255.0f;
    }

    public static int[] getRGBA(int c) {
        return new int[]{ColorUtility.red(c), ColorUtility.green(c), ColorUtility.blue(c), ColorUtility.alpha(c)};
    }

    public static int[] getRGB(int c) {
        return new int[]{ColorUtility.red(c), ColorUtility.green(c), ColorUtility.blue(c)};
    }

    public static float[] getRGBAf(int c) {
        return new float[]{ColorUtility.redf(c), ColorUtility.greenf(c), ColorUtility.bluef(c), ColorUtility.alphaf(c)};
    }

    public static float[] getRGBf(int c) {
        return new float[]{ColorUtility.redf(c), ColorUtility.greenf(c), ColorUtility.bluef(c)};
    }

    private ColorUtility() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
