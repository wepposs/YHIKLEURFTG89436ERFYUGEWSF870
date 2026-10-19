package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
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
@Mixin({ClientPlayerInteractionManager.class})
public abstract class ClientPlayerInteractionManagerMixin {
   @Inject(
      method = {"attackEntity"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onAttackEntity(PlayerEntity player, Entity target, CallbackInfo ci) {
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

    @Inject(method = "interactEntity", at = @At("HEAD"), cancellable = true)
    private void onInteractEntity(PlayerEntity player, Entity entity, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
       if (!Vesence.isModInitialized()) return;
       if (hand != Hand.MAIN_HAND) return;

       NoEntityTrace noEntityTrace = NoEntityTrace.getInstance();
       if (noEntityTrace == null || !noEntityTrace.enable) return;
       if (!noEntityTrace.shouldIgnoreEntityTrace()) return;

       MinecraftClient mc = MinecraftClient.getInstance();
       if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return;

       Vec3d eyePos = mc.player.getEyePos();
       Vec3d lookVec = mc.player.getRotationVec(1.0F);
       Vec3d endPos = eyePos.add(lookVec.multiply(5.0));

       BlockHitResult blockHit = mc.world.raycast(
          new RaycastContext(eyePos, endPos,
             RaycastContext.ShapeType.COLLIDER,
             RaycastContext.FluidHandling.NONE,
             mc.player));

       if (blockHit != null && blockHit.getType() == HitResult.Type.BLOCK) {
          mc.getNetworkHandler().sendPacket(
             new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, blockHit, 0));
          mc.player.swingHand(hand);
       }

       cir.setReturnValue(ActionResult.FAIL);
    }

   @Inject(method = "updateBlockBreakingProgress", at = @At("HEAD"))
   private void onBlockBreakingProgress(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
      EventManager.call(new BlockBreakingEvent(pos, direction));
   }

   @Inject(method = "clickSlot", at = @At("HEAD"), cancellable = true)
   private void onClickSlot(int syncId, int slotId, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
      ClickSlotEvent event = new ClickSlotEvent(syncId, slotId, button, actionType);
      EventManager.call(event);
      if (event.isCancelled()) ci.cancel();
   }
}
