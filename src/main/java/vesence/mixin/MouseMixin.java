package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Mouse;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.event.EventManager;
import vesence.event.input.MouseUpdateEvent;
import vesence.event.player.EventLook;

@Environment(EnvType.CLIENT)
@Mixin({Mouse.class})
public abstract class MouseMixin {
   @Inject(
      method = {"updateMouse"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void cancelCameraMovement(CallbackInfo ci) {
      MouseUpdateEvent event = new MouseUpdateEvent(Minecraft.getInstance());
      EventManager.call(event);
      if (event.isCancelled()) {
         ci.cancel();
      }
   }

   @Redirect(
      method = {"updateMouse"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/network/LocalPlayer;changeLookDirection(DD)V"
      )
   )
   private void onLook(LocalPlayer player, double yaw, double pitch) {
      Minecraft client = Minecraft.getInstance();
      if (client != null && client.player != null) {
         EventLook event = new EventLook(yaw, pitch);
         EventManager.call(event);
         if (!event.isCancelled()) {
            player.changeLookDirection(event.yaw, event.pitch);
         }
      }
   }
}
