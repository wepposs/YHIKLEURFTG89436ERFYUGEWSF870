package vesence.mixin.optimization;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vesence.optimization.Opt;

/**
 * LOD дальних сущностей: дальним мобам и игрокам не нужны детали, которые на таком
 * расстоянии занимают меньше пикселя — броня, предметы в руках, плащ, внешний слой скина.
 *
 * <p>В 26.3 все эти слои висят на одном {@code shouldRenderLayers(state)}, а
 * {@code distanceToCameraSq} ванильный extract уже посчитал — остаётся сравнить
 * с дистанцией из настроек.
 */
@Environment(EnvType.CLIENT)
@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererLodMixin {

   @Inject(method = "shouldRenderLayers", at = @At("HEAD"), cancellable = true)
   private void vesence$hideFarLayers(LivingEntityRenderState state, CallbackInfoReturnable<Boolean> cir) {
      if (state == null || !Opt.on()) {
         return;
      }
      boolean player = state instanceof AvatarRenderState;
      boolean far = player ? Opt.playerFar(state.distanceToCameraSq) : Opt.entityFar(state.distanceToCameraSq);
      if (far) {
         cir.setReturnValue(false);
      }
   }
}
