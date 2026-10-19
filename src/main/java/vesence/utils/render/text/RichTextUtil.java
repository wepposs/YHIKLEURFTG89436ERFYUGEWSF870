package vesence.utils.render.text;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
public final class RichTextUtil {

    private RichTextUtil() {}

    public static String toColorFormat(Text text, int defaultRgb, int alpha) {
        if (text == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        Text translated = PrefixHelper.translate(text);
        int dr = (defaultRgb >> 16) & 0xFF;
        int dg = (defaultRgb >> 8) & 0xFF;
        int db = defaultRgb & 0xFF;

        translated.visit((style, segment) -> {
            if (segment == null || segment.isEmpty()) {
                return java.util.Optional.empty();
            }
            Integer styleColor = style.getColor() != null ? style.getColor().getRgb() : null;
            Integer curColor = styleColor;
            StringBuilder pending = new StringBuilder();
            int i = 0;
            while (i < segment.length()) {
                char c = segment.charAt(i);
                if (c == '\u00a7' && i + 1 < segment.length()) {
                    flushColored(result, pending, curColor, dr, dg, db, alpha);
                    net.minecraft.util.Formatting fmt = net.minecraft.util.Formatting.byCode(segment.charAt(i + 1));
                    if (fmt != null) {
                        if (fmt == net.minecraft.util.Formatting.RESET) {
                            curColor = styleColor;
                        } else if (fmt.isColor() && fmt.getColorValue() != null) {
                            curColor = fmt.getColorValue();
                        }
                    }
                    i += 2;
                } else {
                    pending.append(c);
                    i++;
                }
            }
            flushColored(result, pending, curColor, dr, dg, db, alpha);
            return java.util.Optional.empty();
        }, Style.EMPTY);
        return result.toString();
    }

    private static void flushColored(StringBuilder out, StringBuilder pending, Integer color,
                                     int dr, int dg, int db, int alpha) {
        if (pending.length() == 0) {
            return;
        }
        String replaced = replaceSymbols(stripFormatting(pending.toString()));
        pending.setLength(0);
        if (replaced.isEmpty()) {
            return;
        }
        if (color != null) {
            out.append(ColorFormat.color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, alpha));
        } else {
            out.append(ColorFormat.color(dr, dg, db, alpha));
        }
        out.append(replaced);
    }

    public static String itemName(ItemStack stack, int alpha) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        String formatted = toColorFormat(stack.getName(), 0xFFFFFF, alpha);
        if (formatted.isEmpty()) {

            return ColorFormat.color(255, 255, 255, alpha)
                    + replaceSymbols(stripFormatting(stack.getName().getString()));
        }
        return formatted;
    }

    public static String replaceSymbols(String string) {
        if (string == null || string.isEmpty()) {
            return "";
        }
        return string
                .replace("ꔀ", "PLAYER").replace("ꔄ", "HERO").replace("ꔈ", "TITAN")
                .replace("ꔒ", "AVENGER").replace("ꔖ", "OVERLORD").replace("ꔠ", "MAGISTER")
                .replace("ꔤ", "IMPERATOR").replace("ꔨ", "DRAGON").replace("ꔲ", "BULL")
                .replace("ꔶ", "TIGER").replace("ꕀ", "HYDRA").replace("ꕄ", "DRACULA")
                .replace("ꕈ", "COBRA").replace("ꕒ", "RABBIT").replace("ꕖ", "BUNNY")
                .replace("ꕠ", "D.HELPER").replace("ꔉ", "HELPER").replace("ꔓ", "ML.MODER")
                .replace("ꔗ", "MODER").replace("ꔡ", "MODER+").replace("ꔥ", "ST.MODER")
                .replace("ꔩ", "GL.MODER").replace("ꕉ", "PEGAS").replace("ꔳ", "ML.ADMIN")
                .replace("ꔷ", "ADMIN").replace("ꔁ", "MEDIA").replace("ꕁ", "GOD")
                .replace("ꔅ", "YT")

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
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00a7' && i + 1 < text.length()) {
                i++;
            } else if (c >= 32) {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
