package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import vesence.module.impl.misc.NameProtect;

/**
 * Streamer Mode: глобальная подмена никнейма (и грифер/серверных слов) во ВСЁМ
 * строковом тексте, который рисуется через {@link DrawContext} — чат, actionbar,
 * подсказки и т.п. Порт из RelevantPremiumpp4 (DrawContextNameProtectMixin).
 *
 * <p>В 1.21.11 {@code drawText}/{@code drawTextWithShadow} возвращают void
 * (в 1.21.4 возвращали int) — дескрипторы обновлены соответственно.
 */
@Environment(EnvType.CLIENT)
@Mixin(DrawContext.class)
public class DrawContextNameProtectMixin {

    @ModifyVariable(
        method = "drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;III)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private String vesence$patchStringShadow(String text) {
        return this.vesence$patch(text);
    }

    @ModifyVariable(
        method = "drawText(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;IIIZ)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private String vesence$patchString(String text) {
        return this.vesence$patch(text);
    }

    private String vesence$patch(String text) {
        if (text == null || text.isEmpty() || vesence$shouldBypass(text)) {
            return text;
        }
        NameProtect np = NameProtect.getInstance();
        return np != null && np.enable ? np.patchIncomingText(text) : text;
    }

    /**
     * Не трогаем короткие/служебные overlay-строки ванильного GUI:
     * stack count, durability/count overlays, чисто числовой текст и т.п.
     * Иначе глобальный NameProtect может ломать рендер цифр в инвентаре.
     */
    private static boolean vesence$shouldBypass(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return true;
        }
        if (trimmed.length() <= 4 && vesence$isOverlayLike(trimmed)) {
            return true;
        }
        return vesence$isNumericLike(trimmed);
    }

    private static boolean vesence$isNumericLike(String text) {
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isDigit(ch)) continue;
            if (Character.isWhitespace(ch)) continue;
            if (ch == '+' || ch == '-' || ch == '.' || ch == ',' || ch == ':' || ch == '/' || ch == '%') continue;
            return false;
        }
        return true;
    }

    private static boolean vesence$isOverlayLike(String text) {
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isDigit(ch)) continue;
            if (Character.isLetter(ch)) continue;
            if (ch == 'x' || ch == 'X' || ch == '/' || ch == '.') continue;
            return false;
        }
        return true;
    }
}
