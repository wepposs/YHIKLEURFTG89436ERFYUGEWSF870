package vesence.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.phys.AABB;
import net.minecraft.client.Minecraft;
import net.minecraft.server.dialog.Input;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vesence.event.EventManager;
import vesence.event.impl.EventUpdate;
import vesence.event.player.EventMotion;
import vesence.event.player.SlowWalkingEvent;
import vesence.event.player.UsingItemEvent;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.FreeLookUtil;

@Environment(EnvType.CLIENT)
@Mixin({ LocalPlayer.class })
public abstract class ClientPlayerEntityMixin {
   @Shadow
   public net.minecraft.client.player.ClientInput input;

   @Inject(method = { "tick" }, at = { @At("HEAD") })
   private void onTickHead(CallbackInfo ci) {
      EventManager.call(new EventUpdate());
   }

   @Redirect(method = {
           "applyInput" }, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getXRot()F"))
   private float redirectRenderPitchUpdate(LocalPlayer instance) {
      return FreeLookUtil.active ? Minecraft.getInstance().gameRenderer.mainCamera().xRot()
              : instance.getXRot();
   }

   /**
    * Замедление при использовании предмета.
    *
    * <p>В 1.21.x это был {@code ModifyExpressionValue} на {@code isUsingItem()} внутри
    * {@code applyMovementSpeedFactors}. В 26.3 такого метода нет — решение принимает
    * приватный {@code LocalPlayer#isSlowDueToUsingItem()}, вызываемый из {@code modifyInput}.
    * Хук на него даёт то же поведение: отмена {@link UsingItemEvent} убирает замедление.
    */
   @Inject(method = "isSlowDueToUsingItem", at = @At("RETURN"), cancellable = true)
   private void usingItemHook(CallbackInfoReturnable<Boolean> cir) {
      if (!cir.getReturnValueZ()) {
         return;
      }
      UsingItemEvent event = new UsingItemEvent((byte) 1);
      EventManager.call(event);
      if (event.isCancelled()) {
         cir.setReturnValue(false);
      }
   }

   @Redirect(method = {
           "applyInput" }, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getYRot()F"))
   private float redirectRenderYawUpdate(LocalPlayer instance) {
      return FreeLookUtil.active ? Minecraft.getInstance().gameRenderer.mainCamera().yRot() : instance.getYRot();
   }

   @Inject(method = { "aiStep" }, at = { @At("HEAD") })
   private void onUpdateWalkingPlayer(CallbackInfo ci) {
      LocalPlayer player = (LocalPlayer) (Object) this;
      if (player != null) {
         AABB axisalignedbb = player.getBoundingBox();
         EventMotion eventPre = new EventMotion(
                 player.getYRot(),
                 player.getXRot(),
                 player.getX(),
                 player.getY(),
                 player.getZ(),
                 player.onGround(),
                 axisalignedbb,
                 null);
         EventManager.call(eventPre);
         if (!eventPre.isCancelled()) {
            if (eventPre.getYaw() != player.getYRot() || eventPre.getPitch() != player.getXRot()) {
               player.setYRot(eventPre.getYaw());
               player.setXRot(eventPre.getPitch());
            }

            if (eventPre.getPosX() != player.getX() || eventPre.getPosY() != player.getY()
                    || eventPre.getPosZ() != player.getZ()) {
               player.absSnapTo(eventPre.getPosX(), eventPre.getPosY(), eventPre.getPosZ(),
                       eventPre.getYaw(), eventPre.getPitch());
            }

            if (eventPre.isOnGround() != player.onGround()) {
               player.setOnGround(eventPre.isOnGround());
            }
         }
      }
   }

   /**
    * SlowWalking: гасит ванильное замедление при использовании предмета (еда/зелья).
    *
    * <p>Порт 26.3: {@code LivingEntity#applyMovementSpeedFactors(Vec2f)} удалён, а его
    * логика переехала в {@code LocalPlayer#modifyInput(Vec2)}, где множитель берётся из
    * {@code LocalPlayer#itemUseSpeedMultiplier()} (значение {@code UseEffects#speedMultiplier()},
    * по умолчанию 0.2). Подменяем именно этот вызов на 1.0 — тогда масштабируется только
    * «предметный» множитель, а ванильные sneak-множители остаются нетронутыми.
    */
   @ModifyExpressionValue(
           method = "modifyInput",
           at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;itemUseSpeedMultiplier()F"),
           require = 0
   )
   private float vesence$preventItemUseSlowdown(float original, net.minecraft.world.phys.Vec2 input) {
      LocalPlayer player = (LocalPlayer) (Object) this;
      if (original == 0.2F && player.isUsingItem() && !player.isPassenger()) {
         SlowWalkingEvent event = new SlowWalkingEvent(input.y, input.x);
         EventManager.call(event);
         event.hook();
         if (event.isCancelled()) {
            return 1.0F;
         }
      }

      return original;
   }

   @Inject(method = "moveTowardsClosestSpace", at = @At("HEAD"), cancellable = true)
   private void onPushOutOfBlocks(double x, double z, CallbackInfo ci) {
      if (!vesence.Vesence.isModInitialized()) return;
      vesence.module.impl.movement.NoPush noPush = vesence.module.impl.movement.NoPush.getInstance();
      if (noPush != null && noPush.enable && noPush.shouldIgnoreBlocks()) {
         ci.cancel();
      }
   }

   @Unique
   private static float getMovementMultiplier(boolean positive, boolean negative) {
      if (positive == negative) {
         return 0.0F;
      } else {
         return positive ? 1.0F : -1.0F;
      }
   }
}
