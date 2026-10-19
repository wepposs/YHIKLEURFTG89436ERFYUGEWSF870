package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.module.impl.misc.BetterMinecraft;

/**
 * Smooth, non-jerky chat scrolling.
 *
 * <p>Vanilla selects visible chat lines from the integer {@code scrolledLines}, so animating a large
 * pixel offset while rendering the final line set makes a whole line pop in and then slide (a jerk).
 * Instead we render at the <b>rounded</b> animated position and translate only by the <b>fractional</b>
 * sub-line remainder (at most half a line), so the text slides continuously without ever jumping.
 *
 * <p>The animation only follows genuine user scrolling; when {@code scrolledLines} changes because a
 * new message arrived (vanilla keeps the view anchored), the animated position is shifted by the same
 * amount so nothing moves.
 */
@Environment(EnvType.CLIENT)
@Mixin(ChatComponent.class)
public abstract class ChatHudMixin {

   @Shadow
   private int scrolledLines;

   @Shadow
   protected abstract int getLineHeight();

   @Shadow
   protected abstract double getChatScale();

   @Unique
   private double vesence$displayed;

   @Unique
   private int vesence$lastScrolled = Integer.MIN_VALUE;

   @Unique
   private boolean vesence$userScrolled;

   @Unique
   private long vesence$lastTime;

   @Unique
   private boolean vesence$pushed;

   @Unique
   private int vesence$realScrolled;

   // ── Гарантия видимости чата (см. vesence$clampChatOpacity/restoreChatOpacity) ──
   @Unique
   private boolean vesence$opacityClamped;
   @Unique
   private double vesence$savedChatOpacity;

   @Inject(method = "scroll(I)V", at = @At("RETURN"), require = 0, expect = 0)
   private void vesence$onUserScroll(int amount, CallbackInfo ci) {
      vesence$userScrolled = true;
   }

   @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/font/Font;IIIZZ)V", at = @At("HEAD"), require = 0, expect = 0)
   private void vesence$pushSmoothScroll(GuiGraphics context, Font font, int currentTick, int mouseX, int mouseY, boolean focused, boolean flag, CallbackInfo ci) {
      // Гарантия видимости чата: если chatOpacity занулена — временно рисуем видимо.
      vesence$clampChatOpacity();

      vesence$pushed = false;
      int target = scrolledLines;

      if (!BetterMinecraft.smoothScrollEnabled()) {
         vesence$displayed = target;
         vesence$lastScrolled = target;
         return;
      }

      if (vesence$lastScrolled == Integer.MIN_VALUE) {
         vesence$lastScrolled = target;
         vesence$displayed = target;
      }

      int delta = target - vesence$lastScrolled;
      if (delta != 0 && !vesence$userScrolled) {
         // Not a user scroll (new message / reset): keep the view anchored, do not animate.
         vesence$displayed += delta;
      }
      vesence$lastScrolled = target;
      vesence$userScrolled = false;

      long now = System.currentTimeMillis();
      double dt = vesence$lastTime == 0L ? 0.0 : Math.min(0.1, (now - vesence$lastTime) / 1000.0);
      vesence$lastTime = now;

      double factor = 1.0 - Math.exp(-dt * 18.0);
      vesence$displayed += (target - vesence$displayed) * factor;

      if (Math.abs(vesence$displayed - target) < 0.01) {
         // Converged: render normally at the target with no offset.
         vesence$displayed = target;
         return;
      }

      int rounded = (int) Math.round(vesence$displayed);
      if (rounded < 0) {
         rounded = 0;
      }
      double frac = vesence$displayed - rounded;
      double chatScale = getChatScale();
      double offset = frac * getLineHeight() * chatScale;

      // Рисуем набор строк для округлённой позиции со сдвигом на дробный остаток строки.
      // ФИКС 1.21.11: БОЛЬШЕ НЕ вызываем context.enableScissor вокруг ChatComponent.render.
      // Под слоёной (deferred) отрисовкой GuiGraphics в 1.21.11 scissor вокруг рендера
      // чата «вырезал» сам ТЕКСТ сообщений — чат пропадал, оставался только скролл-
      // индикатор. Для плавности достаточно сдвига матрицы на дробную часть (< 1 строки);
      // клиппинг краёв не нужен (в худшем случае строка на краю чуть выходит за область
      // ТОЛЬКО во время активного скролла — это безопасно и незаметно).
      vesence$realScrolled = target;
      scrolledLines = rounded;

      context.getMatrices().pushMatrix();
      context.getMatrices().translate(0f, (float) offset);
      vesence$pushed = true;
   }

   @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/font/Font;IIIZZ)V", at = @At("RETURN"), require = 0, expect = 0)
   private void vesence$popSmoothScroll(GuiGraphics context, Font font, int currentTick, int mouseX, int mouseY, boolean focused, boolean flag, CallbackInfo ci) {
      // Восстанавливаем исходную непрозрачность чата (значение не сохраняется на диск).
      vesence$restoreChatOpacity();

      if (vesence$pushed) {
         scrolledLines = vesence$realScrolled;
         context.getMatrices().popMatrix();
         vesence$pushed = false;
      }
   }

   /**
    * Гарантия того, что клиент НИКОГДА не прячет чат из-за занулённой непрозрачности.
    *
    * <p>Если {@code chatOpacity} в настройках MC ≈ 0 (например, применён FPS/«чистый»
    * пресет опций — тогда текст сообщений рисуется на ~10% и визуально пропадает,
    * а виден только скролл-индикатор, т.к. он не умножается на chatOpacity),
    * ВРЕМЕННО, только на время отрисовки чата, поднимаем непрозрачность до 1.0.
    * Значение восстанавливается в {@link #vesence$restoreChatOpacity} сразу после
    * рендера и НЕ пишется в options.txt — настройка пользователя не меняется.
    * Порог 0.1 не трогает осознанно приглушённый (но видимый) чат.
    */
   @Unique
   private void vesence$clampChatOpacity() {
      vesence$opacityClamped = false;
      try {
         Minecraft mc = Minecraft.getInstance();
         if (mc == null || mc.options == null) return;
         double op = mc.options.getChatOpacity().getValue();
         if (op < 0.1) {
            vesence$savedChatOpacity = op;
            mc.options.getChatOpacity().setValue(1.0);
            vesence$opacityClamped = true;
         }
      } catch (Throwable ignored) {
      }
   }

   @Unique
   private void vesence$restoreChatOpacity() {
      if (!vesence$opacityClamped) return;
      vesence$opacityClamped = false;
      try {
         Minecraft mc = Minecraft.getInstance();
         if (mc != null && mc.options != null) {
            mc.options.getChatOpacity().setValue(vesence$savedChatOpacity);
         }
      } catch (Throwable ignored) {
      }
   }
}
