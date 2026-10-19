package vesence.utils.other;

import java.util.regex.Pattern;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.text.MutableText;
import net.minecraft.text.PlainTextContent.Literal;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.text.TextContent;

/**
 * Заменяет подстроку (без учёта регистра) внутри дерева {@link Component},
 * сохраняя стили каждого сегмента. Порт из RelevantPremiumpp4
 * (ru.whylol.api.utils.replace.ReplaceUtils) для NameProtect/Streamer Mode.
 */
@Environment(EnvType.CLIENT)
public class ReplaceUtils {

    public static Component replace(Component input, String target, String replacement) {
        if (input != null && target != null && replacement != null) {
            MutableText result = Component.empty().setStyle(input.getStyle());
            appendReplaced(result, input, target, replacement);
            return result;
        } else {
            return input;
        }
    }

    private static void appendReplaced(MutableText result, Component current, String target, String replacement) {
        TextContent content = current.getContent();
        Style style = current.getStyle();
        if (content instanceof Literal literal) {
            Pattern pattern = Pattern.compile(Pattern.quote(target), Pattern.CASE_INSENSITIVE);
            String replaced = pattern.matcher(literal.string()).replaceAll(java.util.regex.Matcher.quoteReplacement(replacement));
            result.append(Component.literal(replaced).setStyle(style));
        }

        for (Component sibling : current.getSiblings()) {
            appendReplaced(result, sibling, target, replacement);
        }
    }
}
