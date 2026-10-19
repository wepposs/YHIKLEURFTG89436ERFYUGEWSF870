package vesence.utils.render.text;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.regex.Pattern;

@Environment(EnvType.CLIENT)
public final class ColorFormat {

    public static final Pattern PATTERN = Pattern.compile(
            "\\$\\{(rgba|rgb)\\((\\d{1,3}),(\\d{1,3}),(\\d{1,3})(?:,(\\d{1,3}))?\\)}|\\$\\{reset}",
            Pattern.CASE_INSENSITIVE
    );

    private ColorFormat() {}

    public static String color(int red, int green, int blue) {
        return String.format("${rgb(%d,%d,%d)}", red, green, blue);
    }

    public static String color(int red, int green, int blue, int alpha) {
        return String.format("${rgba(%d,%d,%d,%d)}", red, green, blue, alpha);
    }

    public static String color(int packed) {
        return String.format("${rgba(%d,%d,%d,%d)}",
                (packed >>> 16) & 0xFF,
                (packed >>> 8) & 0xFF,
                packed & 0xFF,
                (packed >>> 24) & 0xFF);
    }

    public static String color(int packed, int alpha) {
        return String.format("${rgba(%d,%d,%d,%d)}",
                (packed >>> 16) & 0xFF,
                (packed >>> 8) & 0xFF,
                packed & 0xFF,
                alpha);
    }

    public static String reset() {
        return "${reset}";
    }

    public static String strip(String text) {
        return PATTERN.matcher(text).replaceAll("");
    }
}
