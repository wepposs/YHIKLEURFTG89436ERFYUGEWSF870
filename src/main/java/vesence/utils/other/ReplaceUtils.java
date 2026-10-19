package vesence.utils.other;

import java.util.regex.Pattern;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.text.MutableText;
import net.minecraft.text.PlainTextContent.Literal;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextContent;

/**
 * Заменяет подстроку (без учёта регистра) внутри дерева {@link Text},
 * сохраняя стили каждого сегмента. Порт из RelevantPremiumpp4
 * (ru.whylol.api.utils.replace.ReplaceUtils) для NameProtect/Streamer Mode.
 */
@Environment(EnvType.CLIENT)
public class ReplaceUtils {

    public static Text replace(Text input, String target, String replacement) {
        if (input != null && target != null && replacement != null) {
            MutableText result = Text.empty().setStyle(input.getStyle());
            appendReplaced(result, input, target, replacement);
            return result;
        } else {
            return input;
        }
    }

    private static void appendReplaced(MutableText result, Text current, String target, String replacement) {
        TextContent content = current.getContent();
        Style style = current.getStyle();
        if (content instanceof Literal literal) {
            Pattern pattern = Pattern.compile(Pattern.quote(target), Pattern.CASE_INSENSITIVE);
            String replaced = pattern.matcher(literal.string()).replaceAll(java.util.regex.Matcher.quoteReplacement(replacement));
            result.append(Text.literal(replaced).setStyle(style));
        }

        for (Text sibling : current.getSiblings()) {
            appendReplaced(result, sibling, target, replacement);
        }
    }
}
