package vesence.mixin.optimization;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vesence.optimization.Opt;

/**
 * Имена дальних сущностей не готовятся к отрисовке: на дистанции LOD табличка имени
 * превращается в пару размытых пикселей, но её layout всё равно собирается каждый кадр.
 */
@Environment(EnvType.CLIENT)
@Mixin(EntityRenderer.class)
public class EntityRendererLodMixin {

   @Inject(method = "shouldShowName", at = @At("HEAD"), cancellable = true)
   private void vesence$hideFarNameTags(Entity entity, double distanceSq, CallbackInfoReturnable<Boolean> cir) {
      if (entity == null || !Opt.on()) {
         return;
      }
      boolean far = entity instanceof net.minecraft.world.entity.player.Player
              ? Opt.playerFar(distanceSq)
              : Opt.entityFar(distanceSq);
      if (far) {
         cir.setReturnValue(false);
      }
   }
}
