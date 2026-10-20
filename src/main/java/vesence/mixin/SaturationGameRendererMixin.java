package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.module.impl.visuals.Ambience;
import vesence.renderengine.render.pipeline.SaturationPipeline;

/**
 * Насыщенность мира (Ambience → «Насыщенность»).
 *
 * <p>Порт 26.3: раньше это висело на {@code Gui.render} — то есть между миром и HUD.
 * В 26.3 такого метода нет: кадр собирается в {@code GameRenderer.render()} как
 * «очистка → мир → пост-эффекты → {@code guiRenderer.render()}». Поэтому цепляемся
 * к вызову {@code GuiRenderer.render} и делаем проход ПЕРЕД ним: мир уже нарисован,
 * GUI ещё нет — ровно прежняя семантика (насыщенность не трогает интерфейс).
 */
@Environment(EnvType.CLIENT)
@Mixin(value = GameRenderer.class, priority = 900)
public class SaturationGameRendererMixin {
    @Unique
    private SaturationPipeline saturationPipeline;

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V",
                    shift = At.Shift.BEFORE
            ),
            require = 0
    )
    private void vesence$applySaturationBeforeGui(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (!Ambience.isEnabled() || !Ambience.changeSaturation.get()) return;
        if (mc.player == null || mc.level == null) return;

        float saturation = Ambience.getSaturation();
        if (saturation == 1.0f) return;

        if (saturationPipeline == null) {
            saturationPipeline = new SaturationPipeline();
        }

        try {
            saturationPipeline.applySaturation(mc, saturation);
        } catch (Exception e) {
            System.err.println("[weppos] Saturation error: " + e.getMessage());
        }
    }
}
