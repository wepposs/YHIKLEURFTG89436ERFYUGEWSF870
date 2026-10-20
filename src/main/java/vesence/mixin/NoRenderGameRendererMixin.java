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
   // TODO(port26): GameRenderer#showFloatingItem removed in 26.3 — totem/floating-item activation
   // moved to LocalPlayer#displayItemActivation + PlayerRenderState.itemActivation rendered by
   // ScreenEffectRenderer#renderItemActivationAnimation. Hook point lives in another class, so the
   // «Анимация тотема» cancel is stubbed here.
   // @Inject(method = "showFloatingItem", at = @At("HEAD"), cancellable = true)
   // private void onShowFloatingItem(net.minecraft.world.item.ItemStack floatingItem, CallbackInfo ci) { ... }

   @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
   private void onTiltViewWhenHurt(net.minecraft.client.renderer.state.level.CameraRenderState cameraRenderState,
                                   com.mojang.blaze3d.vertex.PoseStack matrices, CallbackInfo ci) {
      if (Vesence.get.manager.get(NoRender.class).enable && NoRender.elements.get("Тряску камеры")) {
         ci.cancel();
      }
   }
}
