package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vesence.module.impl.visuals.AspectRatio;

@Environment(EnvType.CLIENT)
@Mixin(GameRenderer.class)
public class GameRendererAspectMixin {

    @Inject(method = {"getBasicProjectionMatrix"}, at = {@At("RETURN")}, cancellable = true)
    private void onGetBasicProjectionMatrix(CallbackInfoReturnable<Matrix4f> cir) {
        if (!AspectRatio.isEnabled()) {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getWindow() == null) {
            return;
        }

        int fbWidth = mc.getWindow().getFramebufferWidth();
        int fbHeight = mc.getWindow().getFramebufferHeight();

        float stretch = AspectRatio.getInstance().computeAnimatedScale(fbWidth, fbHeight);
        if (stretch > 0.0f && Math.abs(stretch - 1.0f) > 1.0E-4f) {
            Matrix4f matrix = new Matrix4f(cir.getReturnValue());
            matrix.m00(matrix.m00() * stretch);
            cir.setReturnValue(matrix);
        }
    }
}
