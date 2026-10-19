package vesence.utils.render.text;

import net.minecraft.text.MutableText;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.List;

/**
 * Хелпер для разбора legacy §/&/$-цвет-кодов и извлечения «сырого» цветного
 * текста из дерева {@link Component}. Порт из RelevantPremiumpp4
 * (ru.whylol.api.utils.render.LegacyTextHelper), используется кастомным
 * scoreboard ({@code ScoreboardElement}) для разбиения строк на цветные
 * сегменты и учёта цвета из стилей {@link Component}.
 *
 * <p>Класс самодостаточен (зависит только от net.minecraft.text.* и
 * {@link ChatFormatting}), поэтому перенесён без изменений API.
 */
public final class LegacyTextHelper {

    private static final char LEGACY_PREFIX_SECTION = '\u00A7';
    private static final char LEGACY_PREFIX_AMP     = '&';
    private static final char LEGACY_PREFIX_DOLLAR  = '$';

    public static Component resolve(Component text, int defaultColor) {
        if (text == null) return Component.empty();
        int baseColor = defaultColor & 0xFFFFFF;
        String raw = extractRaw(text);
        if (containsLegacyCodes(raw)) return parse(raw, baseColor);
        if (hasExplicitColor(text)) return text;
        return applyDefaultColor(text, baseColor);
    }

    public static List<ColoredSegment> parseSegments(String input, int defaultColor) {
        List<ColoredSegment> segments = new ArrayList<>();
        if (input == null || input.isEmpty()) return segments;
        StringBuilder buffer = new StringBuilder();
        int currentColor = 0xFF000000 | (defaultColor & 0xFFFFFF);
        int baseColor    = currentColor;
        int length = input.length();
        for (int i = 0; i < length; i++) {
            char c = input.charAt(i);
            if (c == '<' || c == '{') {
                char end = c == '<' ? '>' : '}';
                if (isWrappedHexSequence(input, i, end)) {
                    flushSegment(buffer, segments, currentColor);
                    int rgb = parseHexColorPlain(input, i + 2);
                    currentColor = 0xFF000000 | rgb;
                    i += 8;
                    continue;
                }
            }
            if (isLegacyPrefix(c) && i + 1 < length) {
                char code = Character.toLowerCase(input.charAt(i + 1));
                if (code == 'x' && isValidHexSequence(input, i)) {
                    flushSegment(buffer, segments, currentColor);
                    int rgb = parseHexColorLegacy(input, i);
                    currentColor = 0xFF000000 | rgb;
                    i += 13;
                    continue;
                }
                if (code == '#' && isHexSequence(input, i + 2, 6)) {
                    flushSegment(buffer, segments, currentColor);
                    int rgb = parseHexColorPlain(input, i + 2);
                    currentColor = 0xFF000000 | rgb;
                    i += 7;
                    continue;
                }
                ChatFormatting formatting = ChatFormatting.byCode(code);
                if (formatting != null) {
                    flushSegment(buffer, segments, currentColor);
                    if (formatting == ChatFormatting.RESET) {
                        currentColor = baseColor;
                    } else if (formatting.isColor()) {
                        Integer rgb = formatting.getColorValue();
                        if (rgb != null) currentColor = 0xFF000000 | rgb;
                    }
                    i++;
                    continue;
                }
            }
            buffer.append(c);
        }
        flushSegment(buffer, segments, currentColor);
        return segments;
    }

    public static String extractRaw(Component text) {
        if (text == null) return "";
        StringBuilder visited = new StringBuilder();
        text.visit((style, string) -> {
            if (style != null && style.getColor() != null) {
                int rgb = style.getColor().getRgb() & 0xFFFFFF;
                String hex = Integer.toHexString(rgb);
                visited.append("&#");
                for (int i = 0; i < 6 - hex.length(); i++) visited.append('0');
                visited.append(hex);
            }
            visited.append(string);
            return java.util.Optional.empty();
        }, Style.EMPTY);
        return visited.toString();
    }

    public static boolean containsLegacyCodes(String input) {
        if (input == null || input.length() < 2) return false;
        int length = input.length();
        for (int i = 0; i < length - 1; i++) {
            char c = input.charAt(i);
            if (c == '<' || c == '{') {
                if (isWrappedHexSequence(input, i, c == '<' ? '>' : '}')) return true;
                continue;
            }
            if (!isLegacyPrefix(c)) continue;
            char code = Character.toLowerCase(input.charAt(i + 1));
            if (isLegacyCode(code)) return true;
            if (code == 'x' && isValidHexSequence(input, i)) return true;
            if (code == '#' && isHexSequence(input, i + 2, 6)) return true;
        }
        return false;
    }

    public static Component parse(String input, int defaultColor) {
        if (input == null || input.isEmpty()) return Component.empty();
        MutableText result = Component.empty();
        StringBuilder buffer = new StringBuilder();
        int baseColor = defaultColor & 0xFFFFFF;
        Style current = Style.EMPTY.withColor(TextColor.fromRgb(baseColor));
        int length = input.length();
        for (int i = 0; i < length; i++) {
            char c = input.charAt(i);
            if (c == '<' || c == '{') {
                char end = c == '<' ? '>' : '}';
                if (isWrappedHexSequence(input, i, end)) {
                    flush(buffer, result, current);
                    int rgb = parseHexColorPlain(input, i + 2);
                    current = current.withColor(TextColor.fromRgb(rgb));
                    i += 8;
                    continue;
                }
            }
            if (isLegacyPrefix(c) && i + 1 < length) {
                char code = Character.toLowerCase(input.charAt(i + 1));
                if (code == 'x' && isValidHexSequence(input, i)) {
                    flush(buffer, result, current);
                    int rgb = parseHexColorLegacy(input, i);
                    current = current.withColor(TextColor.fromRgb(rgb));
                    i += 13;
                    continue;
                }
                if (code == '#' && isHexSequence(input, i + 2, 6)) {
                    flush(buffer, result, current);
                    int rgb = parseHexColorPlain(input, i + 2);
                    current = current.withColor(TextColor.fromRgb(rgb));
                    i += 7;
                    continue;
                }
                ChatFormatting formatting = ChatFormatting.byCode(code);
                if (formatting != null) {
                    flush(buffer, result, current);
                    if (formatting == ChatFormatting.RESET) {
                        current = Style.EMPTY.withColor(TextColor.fromRgb(baseColor));
                    } else if (formatting.isColor()) {
                        current = Style.EMPTY.withFormatting(formatting);
                    } else {
                        current = current.withFormatting(formatting);
                    }
                    i++;
                    continue;
                }
            }
            buffer.append(c);
        }
        flush(buffer, result, current);
        return result;
    }

    private static void flush(StringBuilder buffer, MutableText result, Style style) {
        if (buffer.length() == 0) return;
        result.append(Component.literal(buffer.toString()).setStyle(style));
        buffer.setLength(0);
    }

    private static void flushSegment(StringBuilder buffer, List<ColoredSegment> segments, int color) {
        if (buffer.length() == 0) return;
        segments.add(new ColoredSegment(buffer.toString(), color));
        buffer.setLength(0);
    }

    private static boolean isLegacyPrefix(char c) {
        return c == LEGACY_PREFIX_SECTION || c == LEGACY_PREFIX_AMP || c == LEGACY_PREFIX_DOLLAR;
    }

    private static boolean isLegacyCode(char code) {
        return (code >= '0' && code <= '9')
            || (code >= 'a' && code <= 'f')
            || code == 'k' || code == 'l' || code == 'm'
            || code == 'n' || code == 'o' || code == 'r';
    }

    private static boolean isValidHexSequence(String input, int startIndex) {
        int length = input.length();
        if (startIndex + 13 >= length) return false;
        for (int i = 0; i < 6; i++) {
            int base = startIndex + 2 + i * 2;
            char prefix = input.charAt(base);
            char hex    = input.charAt(base + 1);
            if (!isLegacyPrefix(prefix) || !isHexDigit(hex)) return false;
        }
        return true;
    }

    private static int parseHexColorLegacy(String input, int startIndex) {
        StringBuilder hex = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            int base = startIndex + 2 + i * 2;
            hex.append(input.charAt(base + 1));
        }
        return Integer.parseInt(hex.toString(), 16);
    }

    private static int parseHexColorPlain(String input, int startIndex) {
        return Integer.parseInt(input.substring(startIndex, startIndex + 6), 16);
    }

    private static boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static boolean isHexSequence(String input, int start, int length) {
        if (start < 0 || start + length > input.length()) return false;
        for (int i = 0; i < length; i++) {
            if (!isHexDigit(input.charAt(start + i))) return false;
        }
        return true;
    }

    private static boolean isWrappedHexSequence(String input, int startIndex, char endChar) {
        if (startIndex + 8 >= input.length()) return false;
        if (input.charAt(startIndex + 1) != '#') return false;
        if (!isHexSequence(input, startIndex + 2, 6)) return false;
        return input.charAt(startIndex + 8) == endChar;
    }

    private static Component applyDefaultColor(Component text, int defaultColor) {
        if (hasExplicitColor(text)) return text;
        Style base = Style.EMPTY.withColor(TextColor.fromRgb(defaultColor));
        return Component.literal("").setStyle(base).append(text);
    }

    private static boolean hasExplicitColor(Component text) {
        final boolean[] hasColor = {false};
        text.visit((style, string) -> {
            if (style.getColor() != null) {
                hasColor[0] = true;
                return java.util.Optional.of(true);
            }
            return java.util.Optional.empty();
        }, Style.EMPTY);
        return hasColor[0];
    }

    private LegacyTextHelper() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public record ColoredSegment(String text, int color) {}
}
