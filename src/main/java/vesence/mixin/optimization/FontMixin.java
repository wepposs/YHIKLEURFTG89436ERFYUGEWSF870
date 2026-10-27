package vesence.mixin.optimization;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vesence.optimization.TextLayoutCache;

/**
 * Кэш layout'а текста: одна и та же строка в одном и том же месте кадра не
 * пересобирается заново. На HUD и scoreboard это десятки повторных разборов
 * глифов, которые происходят каждый кадр.
 *
 * <p>Кэш отдаёт только сам {@code PreparedText} — неизменяемый результат раскладки;
 * вызывающий его лишь обходит в {@code visit}.
 */
@Environment(EnvType.CLIENT)
@Mixin(Font.class)
public class FontMixin {

   @Inject(method = "prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;",
           at = @At("HEAD"), cancellable = true)
   private void vesence$cachedLayout(FormattedCharSequence text, float x, float y, int color,
                                     boolean dropShadow, boolean seeThrough, int backgroundColor,
                                     CallbackInfoReturnable<Font.PreparedText> cir) {
      if (!TextLayoutCache.enabled()) {
         return;
      }
      Font.PreparedText cached = TextLayoutCache.get(text, x, y, color, dropShadow, seeThrough, backgroundColor);
      if (cached != null) {
         cir.setReturnValue(cached);
      }
   }

   @Inject(method = "prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;",
           at = @At("RETURN"))
   private void vesence$storeLayout(FormattedCharSequence text, float x, float y, int color,
                                   boolean dropShadow, boolean seeThrough, int backgroundColor,
                                   CallbackInfoReturnable<Font.PreparedText> cir) {
      if (!TextLayoutCache.enabled()) {
         return;
      }
      TextLayoutCache.put(text, x, y, color, dropShadow, seeThrough, backgroundColor, cir.getReturnValue());
   }
}
