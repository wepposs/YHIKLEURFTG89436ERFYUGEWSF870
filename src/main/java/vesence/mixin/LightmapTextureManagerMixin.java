package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import vesence.Vesence;
import vesence.module.impl.visuals.FullBright;

@Environment(EnvType.CLIENT)
@Mixin({LightmapTextureManager.class})
public class LightmapTextureManagerMixin {
   @Redirect(
      method = {"update"},
      at = @At(
         value = "INVOKE",
         target = "Ljava/lang/Double;floatValue()F",
         ordinal = 1
      )
   )
   private float getGammaValue(Double instance) {
      if (Vesence.get != null && Vesence.get.manager != null) {
         FullBright module = Vesence.get.manager.get(FullBright.class);
         if (module != null && module.enable && module.mode.is("Р“Р°РјРјР°")) {
            return 200.0F;
         }
      }

      return instance.floatValue();
   }
}
