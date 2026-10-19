package vesence.utils.render.text;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Environment(EnvType.CLIENT)
public final class PrefixHelper {

    private static final Map<String, String> PREFIX_MAP = new LinkedHashMap<>();

    static {

        PREFIX_MAP.put("ꔀ", "PLAYER");
        PREFIX_MAP.put("ꔄ", "HERO");
        PREFIX_MAP.put("ꔈ", "TITAN");
        PREFIX_MAP.put("ꔒ", "AVENGER");
        PREFIX_MAP.put("ꔖ", "OVERLORD");
        PREFIX_MAP.put("ꔠ", "MAGISTER");
        PREFIX_MAP.put("ꔤ", "IMPERATOR");
        PREFIX_MAP.put("ꔨ", "DRAGON");
        PREFIX_MAP.put("ꔲ", "BULL");
        PREFIX_MAP.put("ꔶ", "TIGER");
        PREFIX_MAP.put("ꕀ", "HYDRA");
        PREFIX_MAP.put("ꕄ", "DRACULA");
        PREFIX_MAP.put("ꕈ", "COBRA");
        PREFIX_MAP.put("ꕒ", "RABBIT");
        PREFIX_MAP.put("ꕖ", "BUNNY");

        PREFIX_MAP.put("ꕠ", "D.HELPER");
        PREFIX_MAP.put("ꔉ", "HELPER");
        PREFIX_MAP.put("ꔓ", "ML.MODER");
        PREFIX_MAP.put("ꔗ", "MODER");
        PREFIX_MAP.put("ꔡ", "MODER+");
        PREFIX_MAP.put("ꔥ", "ST.MODER");
        PREFIX_MAP.put("ꔩ", "GL.MODER");
        PREFIX_MAP.put("ꕉ", "PEGAS");
        PREFIX_MAP.put("ꔳ", "ML.ADMIN");
        PREFIX_MAP.put("ꔷ", "ADMIN");
        PREFIX_MAP.put("ꔁ", "MEDIA");
        PREFIX_MAP.put("ꕁ", "GOD");
        PREFIX_MAP.put("ꔅ", "YT");
    }

    private static final Set<String> PREFIX_SYMBOLS = PREFIX_MAP.keySet();

    private static final Set<String> DONAT_TEXTURES = Set.of(
            "player", "hero", "avenger", "overlord", "magister", "imperator", "dragon", "bull",
            "hydra", "dracula", "cobra", "rabbit", "bunny",
            "d.helper", "helper", "ml.moder", "moder", "moder+", "st.moder", "gl.moder",
            "ml.admin", "admin", "media", "yt"
    );

    public static boolean isPrefixSymbol(String ch) {
        return ch != null && PREFIX_MAP.containsKey(ch);
    }

    public static String getReplacement(String ch) {
        return ch == null ? null : PREFIX_MAP.get(ch);
    }

    public static net.minecraft.util.Identifier getDonatTexture(String ch) {
        String replacement = getReplacement(ch);
        if (replacement == null) {
            return null;
        }
        String file = replacement.toLowerCase(java.util.Locale.ROOT);
        if (!DONAT_TEXTURES.contains(file)) {
            return null;
        }
        return net.minecraft.util.Identifier.of("vesence", "textures/donat/" + file + ".png");
    }

    public static net.minecraft.util.Identifier findDonatTexture(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        for (String symbol : PREFIX_SYMBOLS) {
            if (raw.contains(symbol)) {
                return getDonatTexture(symbol);
            }
        }
        return null;
    }

    public static Text translate(Text component) {
        if (component == null) return Text.empty();

        String string = component.getString();
        boolean hasPrefix = false;
        for (String symbol : PREFIX_SYMBOLS) {
            if (string.contains(symbol)) {
                hasPrefix = true;
                break;
            }
        }
        if (!hasPrefix) return component;

        MutableText result = Text.empty();
        component.visit((style, segment) -> {
            translateSegment(segment, style, result);
            return java.util.Optional.empty();
        }, Style.EMPTY);
        return result;
    }

    private static void translateSegment(String segment, Style style, MutableText result) {
        int i = 0;
        while (i < segment.length()) {
            int cp = segment.codePointAt(i);
            int cpLen = Character.charCount(cp);
            String ch = segment.substring(i, i + cpLen);

            String replacement = PREFIX_MAP.get(ch);
            if (replacement != null) {

                result.append(Text.literal(replacement).setStyle(style));
                i += cpLen;
            } else {

                int start = i;
                while (i < segment.length()) {
                    cp = segment.codePointAt(i);
                    cpLen = Character.charCount(cp);
                    ch = segment.substring(i, i + cpLen);
                    if (PREFIX_MAP.containsKey(ch)) break;
                    i += cpLen;
                }
                String text = segment.substring(start, i);
                if (!text.isEmpty()) {
                    result.append(Text.literal(text).setStyle(style));
                }
            }
        }
    }
}
