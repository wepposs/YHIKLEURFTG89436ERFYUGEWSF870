package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vesence.module.impl.visuals.AspectRatio;

@Environment(EnvType.CLIENT)
@Mixin(GameRenderer.class)
public class GameRendererAspectMixin {

    // TODO(port26): в 26.3 проекция — класс net.minecraft.client.renderer.Projection
    // (setupPerspective), метода getBasicProjectionMatrix больше нет. Хук отключён.
    // @Inject(method = {"getBasicProjectionMatrix"}, at = {@At("RETURN")}, cancellable = true)
    private void onGetBasicProjectionMatrix(CallbackInfoReturnable<Matrix4f> cir) {
        if (!AspectRatio.isEnabled()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) {
            return;
        }

        int fbWidth = mc.getWindow().getScreenWidth();
        int fbHeight = mc.getWindow().getScreenHeight();

        float stretch = AspectRatio.getInstance().computeAnimatedScale(fbWidth, fbHeight);
        if (stretch > 0.0f && Math.abs(stretch - 1.0f) > 1.0E-4f) {
            Matrix4f matrix = new Matrix4f(cir.getReturnValue());
            matrix.m00(matrix.m00() * stretch);
            cir.setReturnValue(matrix);
        }
    }
}
