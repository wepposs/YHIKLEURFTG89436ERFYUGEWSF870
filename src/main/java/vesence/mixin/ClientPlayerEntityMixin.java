package vesence.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.math.Vector2f;
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
   protected Input input;

   @Inject(method = { "tick" }, at = { @At("HEAD") })
   private void onTickHead(CallbackInfo ci) {
      EventManager.call(new EventUpdate());
   }

   @Redirect(method = {
           "tickMovementInput" }, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/LocalPlayer;getPitch()F"))
   private float redirectRenderPitchUpdate(LocalPlayer instance) {
      return FreeLookUtil.active ? Minecraft.getInstance().gameRenderer.getCamera().getXRot()
              : instance.getXRot();
   }

   @ModifyExpressionValue(method = { "applyMovementSpeedFactors" }, at = {
           @At(value = "INVOKE", target = "Lnet/minecraft/client/network/LocalPlayer;isUsingItem()Z") })
   private boolean usingItemHook(boolean original) {
      if (original) {
         UsingItemEvent event = new UsingItemEvent((byte) 1);
         EventManager.call(event);
         if (event.isCancelled()) {
            return false;
         }
      }

      return original;
   }

   @Redirect(method = {
           "tickMovementInput" }, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/LocalPlayer;getYaw()F"))
   private float redirectRenderYawUpdate(LocalPlayer instance) {
      return FreeLookUtil.active ? Minecraft.getInstance().gameRenderer.getCamera().getYRot() : instance.getYRot();
   }

   @Inject(method = { "tickMovement" }, at = { @At("HEAD") })
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
            if (eventPre.getYRot() != player.getYRot() || eventPre.getXRot() != player.getXRot()) {
               player.setYRot(eventPre.getYRot());
               player.setXRot(eventPre.getXRot());
            }

            if (eventPre.getPosX() != player.getX() || eventPre.getPosY() != player.getY()
                    || eventPre.getPosZ() != player.getZ()) {
               player.refreshPositionAndAngles(eventPre.getPosX(), eventPre.getPosY(), eventPre.getPosZ(),
                       eventPre.getYRot(), eventPre.getXRot());
            }

            if (eventPre.onGround() != player.onGround()) {
               player.setOnGround(eventPre.onGround());
            }
         }
      }
   }

   @Redirect(method = {
           "applyMovementSpeedFactors" }, at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/Vector2f;multiply(F)Lnet/minecraft/util/math/Vector2f;", ordinal = 1))
   private Vector2f preventSlowdownMultiply(Vector2f vec, float multiplier) {
      LocalPlayer player = (LocalPlayer) (Object) this;
      if (multiplier == 0.2F && player.isUsingItem() && !player.hasVehicle()) {
         float moveForward = vec.y;
         float moveStrafe = vec.x;
         SlowWalkingEvent event = new SlowWalkingEvent(moveForward, moveStrafe);
         EventManager.call(event);
         event.hook();
         if (event.isCancelled()) {
            return vec;
         }
      }

      return vec.multiply(multiplier);
   }

   @Inject(method = "pushOutOfBlocks", at = @At("HEAD"), cancellable = true)
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
