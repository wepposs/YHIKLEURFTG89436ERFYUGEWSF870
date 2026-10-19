package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.Vesence;
import vesence.module.impl.visuals.NoRender;

@Environment(EnvType.CLIENT)
@Mixin(WorldRenderer.class)
public class NoRenderWorldRendererMixin {
   @Inject(method = "renderWeather", at = @At("HEAD"), cancellable = true)
   private void onRenderWeather(CallbackInfo ci) {
      if (Vesence.get.manager.get(NoRender.class).enable && NoRender.elements.get("Дождь")) {
         ci.cancel();
      }
   }

   @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
   private void vesence$onRenderClouds(CallbackInfo ci) {
      NoRender nr = Vesence.get.manager.get(NoRender.class);
      if (nr != null && nr.enable && NoRender.elements.get("Облака")) {
         ci.cancel();
      }
   }

   @Inject(method = "renderBlockEntities", at = @At("HEAD"), cancellable = true)
   private void vesence$onRenderBlockEntities(CallbackInfo ci) {
      NoRender nr = Vesence.get.manager.get(NoRender.class);
      if (nr != null && nr.enable && NoRender.elements.get("Блок-сущности")) {
         ci.cancel();
      }
   }
}
