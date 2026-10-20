package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import vesence.Vesence;
import vesence.module.impl.visuals.NoRender;

// Порт 26.3: LevelRenderer#renderWeather/renderClouds удалены — погода и облака рисуются
// WeatherEffectRenderer/CloudRenderer, которые вызываются из LevelRenderer#executeClassicTransparency
// (и #executeOit на OIT-пути). Перехватываем именно эти точки вызова, поэтому переключатели
// NoRender «Дождь» и «Облака» работают как раньше на обоих путях.
@Environment(EnvType.CLIENT)
@Mixin(LevelRenderer.class)
public class NoRenderWorldRendererMixin {

   private static boolean vesence$noRain() {
      return Vesence.get.manager.get(NoRender.class).enable && NoRender.elements.get("Дождь");
   }

   private static boolean vesence$noClouds() {
      NoRender nr = Vesence.get.manager.get(NoRender.class);
      return nr != null && nr.enable && NoRender.elements.get("Облака");
   }

   @WrapOperation(
           method = "executeClassicTransparency",
           at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/WeatherEffectRenderer;render(Lnet/minecraft/client/renderer/state/level/WeatherRenderState;Lcom/mojang/renderpearl/api/commands/RenderPass;)V"))
   private void vesence$skipWeather(net.minecraft.client.renderer.WeatherEffectRenderer instance,
                                    net.minecraft.client.renderer.state.level.WeatherRenderState state,
                                    com.mojang.renderpearl.api.commands.RenderPass pass,
                                    Operation<Void> original) {
      if (vesence$noRain()) return;
      original.call(instance, state, pass);
   }

   @WrapOperation(
           method = "executeClassicTransparency",
           at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/CloudRenderer;render(Lnet/minecraft/client/CloudStatus;Lcom/mojang/renderpearl/api/commands/RenderPass;)V"))
   private void vesence$skipClouds(net.minecraft.client.renderer.CloudRenderer instance,
                                   net.minecraft.client.CloudStatus status,
                                   com.mojang.renderpearl.api.commands.RenderPass pass,
                                   Operation<Void> original) {
      if (vesence$noClouds()) return;
      original.call(instance, status, pass);
   }

   @WrapOperation(
           method = "executeOit",
           at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/WeatherEffectRenderer;renderOit(Lnet/minecraft/client/renderer/oit/OitStage;Lnet/minecraft/client/renderer/state/level/WeatherRenderState;Lcom/mojang/renderpearl/api/commands/RenderPass;)V"))
   private void vesence$skipWeatherOit(net.minecraft.client.renderer.WeatherEffectRenderer instance,
                                       net.minecraft.client.renderer.oit.OitStage stage,
                                       net.minecraft.client.renderer.state.level.WeatherRenderState state,
                                       com.mojang.renderpearl.api.commands.RenderPass pass,
                                       Operation<Void> original) {
      if (vesence$noRain()) return;
      original.call(instance, stage, state, pass);
   }

   @WrapOperation(
           method = "executeOit",
           at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/CloudRenderer;renderOit(Lnet/minecraft/client/CloudStatus;Lnet/minecraft/client/renderer/oit/OitStage;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Lnet/minecraft/client/renderer/oit/OitRenderPassProvider$Parameters;)V"))
   private void vesence$skipCloudsOit(net.minecraft.client.renderer.CloudRenderer instance,
                                      net.minecraft.client.CloudStatus status,
                                      net.minecraft.client.renderer.oit.OitStage stage,
                                      com.mojang.renderpearl.api.textures.GpuTextureView textureView,
                                      net.minecraft.client.renderer.oit.OitRenderPassProvider.Parameters params,
                                      Operation<Void> original) {
      if (vesence$noClouds()) return;
      original.call(instance, status, stage, textureView, params);
   }

   @Inject(method = "submitBlockEntities", at = @At("HEAD"), cancellable = true)
   private void vesence$onRenderBlockEntities(com.mojang.blaze3d.vertex.PoseStack matrices,
                                              net.minecraft.client.renderer.state.level.LevelRenderState state,
                                              net.minecraft.client.renderer.SubmitNodeCollector collector,
                                              CallbackInfo ci) {
      NoRender nr = Vesence.get.manager.get(NoRender.class);
      if (nr != null && nr.enable && NoRender.elements.get("Блок-сущности")) {
         ci.cancel();
      }
   }
}
