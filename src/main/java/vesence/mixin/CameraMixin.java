package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.event.EventManager;
import vesence.event.player.EventRotation;
import vesence.module.impl.movement.FreecamState;
import vesence.module.impl.visuals.InterpolateF5;

@Environment(EnvType.CLIENT)
@Mixin({Camera.class})
public abstract class CameraMixin {
   @Unique
   private EventRotation rotationEvent;
   @Unique
   private float originalYaw;
   @Unique
   private float originalPitch;
   @Unique
   private float vesence$currentTickProgress;

   @Shadow
   private Entity entity;

   @Shadow
   protected abstract void setRotation(float var1, float var2);

   @Shadow
   protected abstract void setPosition(Vec3 var1);

   @Shadow
   protected abstract void move(float surge, float heave, float sway);

   @Inject(
      method = {"alignWithEntity(F)V"},
      at = {@At("HEAD")}
   )
   private void onUpdateHead(float tickProgress, CallbackInfo ci) {
      this.vesence$currentTickProgress = tickProgress;
      Entity focusedEntity = this.entity;
      if (focusedEntity != null) {
         this.originalYaw = focusedEntity.getViewYRot(tickProgress);
         this.originalPitch = focusedEntity.getViewXRot(tickProgress);
         this.rotationEvent = new EventRotation(this.originalYaw, this.originalPitch, tickProgress);
         EventManager.call(this.rotationEvent);
      } else {
         this.rotationEvent = null;
      }
   }

   @Redirect(
      method = {"alignWithEntity(F)V"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/Camera;setRotation(FF)V"
      )
   )
   private void redirectSetRotation(Camera instance, float yaw, float pitch) {
      if (this.rotationEvent == null
         || this.rotationEvent.getYaw() == this.originalYaw && this.rotationEvent.getPitch() == this.originalPitch) {
         this.setRotation(yaw, pitch);
      } else {
         this.setRotation(this.rotationEvent.getYaw(), this.rotationEvent.getPitch());
      }
   }

   @Inject(
      method = {"alignWithEntity(F)V"},
      at = {@At("RETURN")}
   )
   private void onUpdateReturn(float tickProgress, CallbackInfo ci) {
      this.rotationEvent = null;
      if (FreecamState.pos != null) {
         Vec3 renderPos = FreecamState.prevPos != null
            ? FreecamState.prevPos.lerp(FreecamState.pos, tickProgress)
            : FreecamState.pos;
         this.setPosition(renderPos);

         float renderYaw = FreecamState.prevYaw + (FreecamState.yaw - FreecamState.prevYaw) * tickProgress;
         float renderPitch = FreecamState.prevPitch + (FreecamState.pitch - FreecamState.prevPitch) * tickProgress;
         this.setRotation(renderYaw, renderPitch);
      }
   }

   @ModifyArg(
      method = {"alignWithEntity(F)V"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"
      )
   )
   private float vesence$modifyClipDistance(float distance) {
      if (!InterpolateF5.isActive()) return distance;
      InterpolateF5 module = InterpolateF5.getInstance();
      if (module == null) return distance;
      boolean thirdPerson = !net.minecraft.client.Minecraft.getInstance().options.getCameraType().isFirstPerson();
      if (!thirdPerson) return distance;
      return module.getInterpolatedDistance(this.vesence$currentTickProgress);
   }

   @Redirect(
      method = {"alignWithEntity(F)V"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/Camera;move(FFF)V"
      )
   )
   private void vesence$redirectMoveBy(Camera instance, float surge, float heave, float sway) {
      float adjustedHeave = heave;
      if (InterpolateF5.isActive()) {
         InterpolateF5 module = InterpolateF5.getInstance();
         boolean thirdPerson = !net.minecraft.client.Minecraft.getInstance().options.getCameraType().isFirstPerson();
         if (module != null && thirdPerson) {
            adjustedHeave += module.getInterpolatedHeightOffset(this.vesence$currentTickProgress);
         }
      }
      this.move(surge, adjustedHeave, sway);
   }
}
