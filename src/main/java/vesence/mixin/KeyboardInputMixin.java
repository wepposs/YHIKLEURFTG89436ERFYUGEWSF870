package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.PlayerInput;
import net.minecraft.client.input.KeyboardInput;
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
           @At(value = "FIELD", target = "Lnet/minecraft/client/input/KeyboardInput;playerInput:Lnet/minecraft/util/PlayerInput;", opcode = 181, shift = Shift.AFTER) })
   private void onTickAfterPlayerInput(CallbackInfo ci) {
      KeyboardInput self = (KeyboardInput) (Object) this;
      PlayerInput current = self.playerInput;

      float f = getMovementMultiplier(current.forward(), current.backward());
      float g = getMovementMultiplier(current.left(), current.right());
      EventInput inputEvent = new EventInput(f, g, current.jump(), current.sneak(), current.sprint(), 0.3);
      EventManager.call(inputEvent);

      boolean forward = inputEvent.getForward() > 0.0F;
      boolean backward = inputEvent.getForward() < 0.0F;
      boolean left = inputEvent.getStrafe() > 0.0F;
      boolean right = inputEvent.getStrafe() < 0.0F;
      boolean sprint = inputEvent.isSprint();
      if (inputEvent.getForward() == 0.0F && inputEvent.getStrafe() == 0.0F) {
         sprint = false;
      }

      PlayerInput modifiedInput = new PlayerInput(
              forward,
              backward,
              left,
              right,
              inputEvent.isJump(),
              inputEvent.isSneak(),
              sprint);
      self.playerInput = modifiedInput;
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
