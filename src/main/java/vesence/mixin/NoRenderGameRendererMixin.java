package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.Vesence;
import vesence.module.impl.visuals.NoRender;

@Environment(EnvType.CLIENT)
@Mixin(GameRenderer.class)
public class NoRenderGameRendererMixin {
   // Порт 26.3: GameRenderer#showFloatingItem удалён — активация тотема переехала в
   // LocalPlayer#displayItemActivation и рисуется ScreenEffectRenderer#renderItemActivationAnimation.
   // Хук на этот метод живёт в InGameOverlayRendererMixin (тот же целевой класс, что и
   // остальные экранные оверлеи), поэтому здесь ничего не остаётся.

   @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
   private void onTiltViewWhenHurt(net.minecraft.client.renderer.state.level.CameraRenderState cameraRenderState,
                                   com.mojang.blaze3d.vertex.PoseStack matrices, CallbackInfo ci) {
      if (Vesence.get.manager.get(NoRender.class).enable && NoRender.elements.get("Тряску камеры")) {
         ci.cancel();
      }
   }
}
