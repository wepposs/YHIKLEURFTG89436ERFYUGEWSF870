package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vesence.Vesence;

@Environment(EnvType.CLIENT)
@Mixin({PlayerEntity.class})
public abstract class PlayerEntityMixin {
   @Inject(method = { "isPushedByFluids" }, at = { @At("HEAD") }, cancellable = true)
   private void onIsPushedByFluids(CallbackInfoReturnable<Boolean> cir) {
      if (!Vesence.isModInitialized()) return;

      PlayerEntity self = (PlayerEntity) (Object) this;
      if (!(self instanceof ClientPlayerEntity)) return;

      vesence.module.impl.movement.NoPush noPush = vesence.module.impl.movement.NoPush.getInstance();
      if (noPush == null || !noPush.enable) return;

      if (noPush.shouldIgnoreWater()) {
         cir.setReturnValue(false);
      }
   }

   @Inject(method = "isCriticalHit", at = @At("RETURN"), cancellable = true)
   private void onIsCriticalHit(Entity target, CallbackInfoReturnable<Boolean> cir) {
      if (!Vesence.isModInitialized()) return;

      PlayerEntity self = (PlayerEntity) (Object) this;
      if (!(self instanceof ClientPlayerEntity)) return;

      vesence.module.impl.combat.Criticals criticals = vesence.module.impl.combat.Criticals.getInstance();
      if (criticals == null || !criticals.enable) return;

      if (criticals.isSmoothFall()) {
         cir.setReturnValue(true);
      }
   }
}
