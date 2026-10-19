package vesence.utils.modules.esp;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.LinkedHashMap;
import java.util.Map;

@Environment(EnvType.CLIENT)
public final class RwPrefix {

    private RwPrefix() {}

    private static final Map<String, String> SYMBOLS = new LinkedHashMap<>();

    static {
        SYMBOLS.put("ꔀ", "PLAYER");
        SYMBOLS.put("ꔄ", "HERO");
        SYMBOLS.put("ꔈ", "TITAN");
        SYMBOLS.put("ꔒ", "AVENGER");
        SYMBOLS.put("ꔖ", "OVERLORD");
        SYMBOLS.put("ꔠ", "MAGISTER");
        SYMBOLS.put("ꔤ", "IMPERATOR");
        SYMBOLS.put("ꔨ", "DRAGON");
        SYMBOLS.put("ꔲ", "BULL");
        SYMBOLS.put("ꔶ", "TIGER");
        SYMBOLS.put("ꕀ", "HYDRA");
        SYMBOLS.put("ꕄ", "DRACULA");
        SYMBOLS.put("ꕈ", "COBRA");
        SYMBOLS.put("ꕒ", "RABBIT");
        SYMBOLS.put("ꕖ", "BUNNY");
        SYMBOLS.put("ꕠ", "D.HELPER");
        SYMBOLS.put("ꔉ", "HELPER");
        SYMBOLS.put("ꔓ", "ML.MODER");
        SYMBOLS.put("ꔗ", "MODER");
        SYMBOLS.put("ꔡ", "MODER+");
        SYMBOLS.put("ꔥ", "ST.MODER");
        SYMBOLS.put("ꔩ", "GL.MODER");
        SYMBOLS.put("ꕉ", "PEGAS");
        SYMBOLS.put("ꔳ", "ML.ADMIN");
        SYMBOLS.put("ꔷ", "ADMIN");
        SYMBOLS.put("ꔁ", "MEDIA");
        SYMBOLS.put("ꕁ", "GOD");
        SYMBOLS.put("ꔅ", "YT");
    }

    public static final class ParsedName {
        public final String prefix;
        public final String name;
        public final String clan;

        public ParsedName(String prefix, String name, String clan) {
            this.prefix = prefix;
            this.name = name;
            this.clan = clan;
        }
    }

    public static String replaceSymbols(String string) {
        if (string == null || string.isEmpty()) return "";
        String s = string;
        for (Map.Entry<String, String> e : SYMBOLS.entrySet()) {
            if (s.contains(e.getKey())) {
                s = s.replace(e.getKey(), e.getValue());
            }
        }
        return s
                .replace("🔥", "₼").replace("⚡", "₪").replace("★", "₥")
                .replace("ᴀ", "a").replace("ʙ", "b").replace("ᴄ", "c")
                .replace("ᴅ", "d").replace("ᴇ", "e").replace("ғ", "f")
                .replace("ɢ", "g").replace("ʜ", "h").replace("ɪ", "i")
                .replace("ᴊ", "j").replace("ᴋ", "k").replace("ʟ", "l")
                .replace("ᴍ", "m").replace("ɴ", "n").replace("ᴏ", "o")
                .replace("ᴘ", "p").replace("ǫ", "q").replace("ʀ", "r")
                .replace("ᴛ", "t").replace("ᴜ", "u").replace("ᴠ", "v")
                .replace("ᴡ", "w").replace("ʏ", "y").replace("ᴢ", "z");
    }

    public static String stripFormatting(String text) {
        if (text == null || text.isEmpty()) return "";

        String s = text.replaceAll("\\$\\{[^}]*}", "");
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u00a7' && i + 1 < s.length()) {
                i++;
            } else if (c >= 32 || c == ' ') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static ParsedName parseDisplayName(String displayName) {
        String clean = stripFormatting(replaceSymbols(displayName)).trim();
        if (clean.isEmpty()) return new ParsedName("", "", "");

        String prefix = "";
        String clan = "";
        String name = clean;

        String[] tokens = clean.split("\\s+");
        if (tokens.length >= 2) {
            String first = tokens[0];
            if (SYMBOLS.containsValue(first.toUpperCase()) || isRankLike(first)) {
                prefix = first;
                StringBuilder rest = new StringBuilder();
                for (int i = 1; i < tokens.length; i++) {
                    if (rest.length() > 0) rest.append(' ');
                    rest.append(tokens[i]);
                }
                name = rest.toString();
            }
        }

        int br = name.lastIndexOf('[');
        if (br > 0 && name.endsWith("]")) {
            clan = name.substring(br).trim();
            name = name.substring(0, br).trim();
        }

        return new ParsedName(prefix, name, clan);
    }

    private static boolean isRankLike(String token) {

        if (token.length() < 2) return false;
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (!(Character.isUpperCase(c) || c == '.' || c == '+')) return false;
        }
        return true;
    }
}
