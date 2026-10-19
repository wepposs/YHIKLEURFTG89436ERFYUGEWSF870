package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.Vesence;
import vesence.module.impl.visuals.NoRender;

@Environment(EnvType.CLIENT)
@Mixin(GameRenderer.class)
public class NoRenderGameRendererMixin {
   @Inject(method = "showFloatingItem", at = @At("HEAD"), cancellable = true)
   private void onShowFloatingItem(net.minecraft.item.ItemStack floatingItem, CallbackInfo ci) {
      if (Vesence.get.manager.get(NoRender.class).enable && NoRender.elements.get("Анимация тотема")) {
         ci.cancel();
      }
   }

   @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
   private void onTiltViewWhenHurt(net.minecraft.client.util.math.MatrixStack matrices, float tickDelta, CallbackInfo ci) {
      if (Vesence.get.manager.get(NoRender.class).enable && NoRender.elements.get("Тряску камеры")) {
         ci.cancel();
      }
   }
}
