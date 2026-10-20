package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.Vesence;

/**
 * Точка отрисовки кастомного UI/HUD.
 *
 * <p>В 1.21.11 здесь был {@code RenderSystem.flipFrame} — самый конец кадра, прямо перед
 * swap'ом. В 26.3 flipFrame удалён, а кадр собирается так:
 * {@code Minecraft.renderFrame} → {@code GameRenderer.extract()} (сбор render state) →
 * {@code GameRenderer.render()} (очистка таргета, мир, {@code GuiRenderer.render()}) →
 * {@code windowSurface.blitFromTexture()} → {@code present()}.
 *
 * <p>Поэтому RETURN {@code GameRenderer.render()} — ровно то место, где раньше был flipFrame:
 * ванильный GUI уже нарисован, main render target ещё не ушёл в swapchain. Если рисовать
 * раньше (в extract, как это делали хуки после порта) — картинку стирает
 * {@code clearColorAndDepthTextures} в начале {@code render()}, что и давало чёрный экран.
 */
@Environment(EnvType.CLIENT)
@Mixin({GameRenderer.class})
public class RenderSystemMixin {
   @Inject(
      method = {"render()V"},
      at = {@At("RETURN")}
   )
   private void vesence$onPostGuiRender(CallbackInfo ci) {
      Vesence.onPostGuiRender();
   }
}
