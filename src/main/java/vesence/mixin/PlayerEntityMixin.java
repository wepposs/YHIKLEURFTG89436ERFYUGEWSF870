package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vesence.Vesence;

@Environment(EnvType.CLIENT)
@Mixin({Player.class})
public abstract class PlayerEntityMixin {
   @Inject(method = { "isPushedByFluid" }, at = { @At("HEAD") }, cancellable = true)
   private void onIsPushedByFluids(CallbackInfoReturnable<Boolean> cir) {
      if (!Vesence.isModInitialized()) return;

      Player self = (Player) (Object) this;
      if (!(self instanceof LocalPlayer)) return;

      vesence.module.impl.movement.NoPush noPush = vesence.module.impl.movement.NoPush.getInstance();
      if (noPush == null || !noPush.enable) return;

      if (noPush.shouldIgnoreWater()) {
         cir.setReturnValue(false);
      }
   }

   /**
    * Хук на решение «будет ли удар критическим».
    *
    * <p>В 1.21.x это был {@code PlayerEntity#isCriticalHit} — метод-запрос. В 26.3 он
    * заменён приватным {@code Player#canCriticalAttack(Entity)}, который вызывается
    * внутри {@code Player#attack} прямо перед расчётом урона (×1.5). Сигнатура и
    * семантика те же, поэтому режим Criticals «Плавное падение» снова работает.
    */
   @Inject(method = "canCriticalAttack", at = @At("RETURN"), cancellable = true)
   private void onIsCriticalHit(Entity target, CallbackInfoReturnable<Boolean> cir) {
      if (!Vesence.isModInitialized()) return;

      Player self = (Player) (Object) this;
      if (!(self instanceof LocalPlayer)) return;

      vesence.module.impl.combat.Criticals criticals = vesence.module.impl.combat.Criticals.getInstance();
      if (criticals == null || !criticals.enable) return;

      if (criticals.isSmoothFall()) {
         cir.setReturnValue(true);
      }
   }
}
