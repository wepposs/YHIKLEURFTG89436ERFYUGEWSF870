package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.Vesence;
import vesence.module.impl.visuals.FullBright;

// 26.3: Lightmap.update() удалён — яркость считается в LightmapRenderStateExtractor.extract,
// результат лежит в LightmapRenderState.brightness.
@Environment(EnvType.CLIENT)
@Mixin({LightmapRenderStateExtractor.class})
public class LightmapTextureManagerMixin {
   @Inject(method = {"extract"}, at = {@At("RETURN")})
   private void vesence$forceGamma(LightmapRenderState state, float delta, CallbackInfo ci) {
      if (Vesence.get != null && Vesence.get.manager != null) {
         FullBright module = Vesence.get.manager.get(FullBright.class);
         if (module != null && module.enable && module.mode.is("Р“Р°РјРјР°")) {
            state.brightness = 200.0F;
         }
      }
   }
}
