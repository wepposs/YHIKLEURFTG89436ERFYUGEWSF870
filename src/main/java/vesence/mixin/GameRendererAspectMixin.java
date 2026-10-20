package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.module.impl.visuals.AspectRatio;

/**
 * Растяжение картинки под выбранное соотношение сторон (модуль Screen Ratio).
 *
 * <p>Порт 26.3: {@code GameRenderer#getBasicProjectionMatrix} удалён. Матрица проекции
 * теперь считается в extract-фазе и складывается в
 * {@code GameRenderer.gameRenderState().levelRenderState.cameraRenderState.projectionMatrix},
 * а render-фаза только читает её. Поэтому правим готовую матрицу на выходе
 * {@code GameRenderer.extract} — до того, как её прочитает {@code render()}.
 */
@Environment(EnvType.CLIENT)
@Mixin(GameRenderer.class)
public class GameRendererAspectMixin {

    @Inject(method = "extract", at = @At("RETURN"), require = 0)
    private void vesence$applyAspectStretch(CallbackInfo ci) {
        if (!AspectRatio.isEnabled()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) {
            return;
        }

        GameRenderer self = (GameRenderer) (Object) this;
        var renderState = self.gameRenderState();
        if (renderState == null || renderState.levelRenderState == null
                || renderState.levelRenderState.cameraRenderState == null) {
            return;
        }
        Matrix4f projection = renderState.levelRenderState.cameraRenderState.projectionMatrix;
        if (projection == null) {
            return;
        }

        int fbWidth = mc.getWindow().getScreenWidth();
        int fbHeight = mc.getWindow().getScreenHeight();

        float stretch = AspectRatio.getInstance().computeAnimatedScale(fbWidth, fbHeight);
        if (stretch > 0.0f && Math.abs(stretch - 1.0f) > 1.0E-4f) {
            projection.m00(projection.m00() * stretch);
        }
    }
}
