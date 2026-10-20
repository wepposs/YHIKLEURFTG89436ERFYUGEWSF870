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
 * Отрисовка HUD-слоя vesence в правильном месте кадра 26.3.
 *
 * <p>Кадр собирается так: очистка → мир → пост-эффекты → {@code GuiRenderer.render()}
 * → блит в swapchain. В 1.21.11 HUD рисовался внутри {@code InGameHud.render}, то есть
 * ДО экранов, и потому меню ESC, инвентарь, чат и отладка F3 ложились сверху.
 *
 * <p>Порт сначала рисовал HUD в пост-GUI проходе — и он оказывался поверх всего:
 * перекрывал F3 и меню паузы. Поэтому HUD перенесён сюда: сразу перед
 * {@code GuiRenderer.render()}, когда мир уже нарисован, а ванильный GUI ещё нет.
 * Порядок наложения снова как в 1.21.11.
 *
 * <p>Приоритет не задан (1000) — это больше, чем у {@link SaturationGameRendererMixin}
 * (900), поэтому наш колбэк выполняется ПОСЛЕ прохода насыщенности и HUD не попадает
 * под цветокоррекцию мира.
 */
@Environment(EnvType.CLIENT)
@Mixin(GameRenderer.class)
public class HudLayerMixin {

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V",
                    shift = At.Shift.BEFORE
            ),
            require = 0
    )
    private void vesence$drawHudBeforeGui(CallbackInfo ci) {
        try {
            Vesence.onPreGuiRender();
        } catch (Throwable t) {
            System.err.println("[weppos] HUD layer failed: " + t);
        }
    }
}
