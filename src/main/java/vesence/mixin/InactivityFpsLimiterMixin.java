package vesence.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Убирает ванильный лимит 60 FPS в меню/вне мира.
 *
 * <p>Порт 26.3: {@code net.minecraft.client.option.InactivityFpsLimiter} превратился
 * в простой enum {@code InactivityFpsLimit} (он лишь выбирает режим AFK-лимита), а
 * сами числа лимита переехали в
 * {@code com.mojang.blaze3d.platform.FramerateLimitTracker#getFramerateLimit()},
 * где ветка {@code OUT_OF_LEVEL_MENU} возвращает жёстко зашитые 60. Подменяем эту
 * константу на настроенный пользователем лимит — ровно то, что делал старый миксин
 * на {@code InactivityFpsLimiter#update}.
 */
@Environment(EnvType.CLIENT)
@Mixin({FramerateLimitTracker.class})
public abstract class InactivityFpsLimiterMixin {
   @Shadow
   private int framerateLimit;

   @ModifyConstant(
      method = {"getFramerateLimit"},
      constant = {@Constant(
         intValue = 60
      )}
   )
   private int vesence$replaceMenuFps(int original) {
      return this.framerateLimit;
   }
}
