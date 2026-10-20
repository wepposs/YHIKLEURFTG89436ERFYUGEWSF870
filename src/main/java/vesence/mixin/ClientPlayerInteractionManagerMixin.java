package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vesence.Vesence;
import vesence.event.EventManager;
import vesence.event.impl.BlockBreakingEvent;
import vesence.event.impl.ClickSlotEvent;
import vesence.event.player.AttackEvent;
import vesence.module.impl.player.NoEntityTrace;

@Environment(EnvType.CLIENT)
@Mixin({MultiPlayerGameMode.class})
public abstract class ClientPlayerInteractionManagerMixin {
   @Inject(
      method = {"attack"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onAttackEntity(Player player, Entity target, CallbackInfo ci) {
      AttackEvent event = new AttackEvent(target);
      EventManager.call(event);
      if (event.isCancelled()) {
         ci.cancel();
         return;
      }
      // Living Hands (режим "Новый"): универсальная точка детекции крита — срабатывает
      // и для ручных атак, и для Aura (которая бьёт через attackEntity, минуя doAttack).
      // Кулдаун здесь ещё не сброшен (attack() вызовется ниже по методу).
      vesence.module.impl.visuals.LivingHands.notifyAttackCrit(
         vesence.hmi.CritDetector.isCriticalAttack(player, target));
   }

    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void onInteractEntity(Player player, Entity entity, net.minecraft.world.phys.EntityHitResult hitResult, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
       if (!Vesence.isModInitialized()) return;
       if (hand != InteractionHand.MAIN_HAND) return;

       NoEntityTrace noEntityTrace = NoEntityTrace.getInstance();
       if (noEntityTrace == null || !noEntityTrace.enable) return;
       if (!noEntityTrace.shouldIgnoreEntityTrace()) return;

       Minecraft mc = Minecraft.getInstance();
       if (mc.player == null || mc.level == null || mc.getConnection() == null) return;

       Vec3 eyePos = mc.player.getEyePosition();
       Vec3 lookVec = mc.player.getViewVector(1.0F);
       Vec3 endPos = eyePos.add(lookVec.scale(5.0));

       BlockHitResult blockHit = mc.level.clip(
          new ClipContext(eyePos, endPos,
             ClipContext.Block.COLLIDER,
             ClipContext.Fluid.NONE,
             mc.player));

       if (blockHit != null && blockHit.getType() == HitResult.Type.BLOCK) {
          mc.getConnection().send(
             new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, blockHit, 0));
          mc.player.swing(hand, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
       }

       cir.setReturnValue(InteractionResult.FAIL);
    }

   @Inject(method = "continueDestroyBlock", at = @At("HEAD"))
   private void onBlockBreakingProgress(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
      EventManager.call(new BlockBreakingEvent(pos, direction));
   }

   @Inject(method = "handleContainerInput", at = @At("HEAD"), cancellable = true)
   private void onClickSlot(int containerId, int slotId, int button, ContainerInput actionType, Player player, CallbackInfo ci) {
      ClickSlotEvent event = new ClickSlotEvent(containerId, slotId, button, actionType);
      EventManager.call(event);
      if (event.isCancelled()) ci.cancel();
   }
}
