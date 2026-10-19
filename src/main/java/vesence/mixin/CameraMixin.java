package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
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
   protected abstract void setRotation(float var1, float var2);

   @Shadow
   protected abstract void setPos(Vec3 var1);

   @Shadow
   protected abstract void moveBy(float surge, float heave, float sway);

   @Inject(
      method = {"update"},
      at = {@At("HEAD")}
   )
   private void onUpdateHead(Level area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickProgress, CallbackInfo ci) {
      this.vesence$currentTickProgress = tickProgress;
      if (focusedEntity != null) {
         this.originalYaw = focusedEntity.getYaw(tickProgress);
         this.originalPitch = focusedEntity.getPitch(tickProgress);
         this.rotationEvent = new EventRotation(this.originalYaw, this.originalPitch, tickProgress);
         EventManager.call(this.rotationEvent);
      } else {
         this.rotationEvent = null;
      }
   }

   @Redirect(
      method = {"update"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V"
      )
   )
   private void redirectSetRotation(Camera instance, float yaw, float pitch) {
      if (this.rotationEvent == null
         || this.rotationEvent.getYRot() == this.originalYaw && this.rotationEvent.getXRot() == this.originalPitch) {
         this.setRotation(yaw, pitch);
      } else {
         this.setRotation(this.rotationEvent.getYRot(), this.rotationEvent.getXRot());
      }
   }

   @Inject(
      method = {"update"},
      at = {@At("RETURN")}
   )
   private void onUpdateReturn(Level area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickProgress, CallbackInfo ci) {
      this.rotationEvent = null;
      if (FreecamState.pos != null) {
         Vec3 renderPos = FreecamState.prevPos != null
            ? FreecamState.prevPos.lerp(FreecamState.pos, tickProgress)
            : FreecamState.pos;
         this.setPos(renderPos);

         float renderYaw = FreecamState.prevYaw + (FreecamState.yaw - FreecamState.prevYaw) * tickProgress;
         float renderPitch = FreecamState.prevPitch + (FreecamState.pitch - FreecamState.prevPitch) * tickProgress;
         this.setRotation(renderYaw, renderPitch);
      }
   }

   @ModifyArg(
      method = {"update"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/Camera;clipToSpace(F)F"
      )
   )
   private float vesence$modifyClipDistance(float distance) {
      if (!InterpolateF5.isActive()) return distance;
      InterpolateF5 module = InterpolateF5.getInstance();
      if (module == null) return distance;
      boolean thirdPerson = !net.minecraft.client.Minecraft.getInstance().options.getPerspective().isFirstPerson();
      if (!thirdPerson) return distance;
      return module.getInterpolatedDistance(this.vesence$currentTickProgress);
   }

   @Redirect(
      method = {"update"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/Camera;moveBy(FFF)V"
      )
   )
   private void vesence$redirectMoveBy(Camera instance, float surge, float heave, float sway) {
      float adjustedHeave = heave;
      if (InterpolateF5.isActive()) {
         InterpolateF5 module = InterpolateF5.getInstance();
         boolean thirdPerson = !net.minecraft.client.Minecraft.getInstance().options.getPerspective().isFirstPerson();
         if (module != null && thirdPerson) {
            adjustedHeave += module.getInterpolatedHeightOffset(this.vesence$currentTickProgress);
         }
      }
      this.moveBy(surge, adjustedHeave, sway);
   }
}
