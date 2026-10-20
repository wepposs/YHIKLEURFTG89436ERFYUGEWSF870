package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.player.Input;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.event.EventManager;
import vesence.event.player.EventInput;

@Environment(EnvType.CLIENT)
@Mixin({ KeyboardInput.class })
public abstract class KeyboardInputMixin {

   @Inject(method = { "tick" }, at = {
           @At(value = "FIELD", target = "Lnet/minecraft/client/player/KeyboardInput;keyPresses:Lnet/minecraft/world/entity/player/Input;", opcode = 181, shift = Shift.AFTER) })
   private void onTickAfterPlayerInput(CallbackInfo ci) {
      KeyboardInput self = (KeyboardInput) (Object) this;
      net.minecraft.world.entity.player.Input current = self.keyPresses;

      float f = getMovementMultiplier(current.forward(), current.backward());
      float g = getMovementMultiplier(current.left(), current.right());
      EventInput inputEvent = new EventInput(f, g, current.jump(), current.shift(), current.sprint(), 0.3);
      EventManager.call(inputEvent);

      boolean forward = inputEvent.getForward() > 0.0F;
      boolean backward = inputEvent.getForward() < 0.0F;
      boolean left = inputEvent.getStrafe() > 0.0F;
      boolean right = inputEvent.getStrafe() < 0.0F;
      boolean sprint = inputEvent.isSprint();
      if (inputEvent.getForward() == 0.0F && inputEvent.getStrafe() == 0.0F) {
         sprint = false;
      }

      net.minecraft.world.entity.player.Input modifiedInput = new net.minecraft.world.entity.player.Input(
              forward,
              backward,
              left,
              right,
              inputEvent.isJump(),
              inputEvent.isSneak(),
              sprint);
      self.keyPresses = modifiedInput;
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
