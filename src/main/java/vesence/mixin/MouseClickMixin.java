package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import com.mojang.blaze3d.platform.InputConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.Vesence;
import vesence.event.EventManager;
import vesence.event.input.CursorLockEvent;
import vesence.event.input.MouseButtonEvent;

@Environment(EnvType.CLIENT)
@Mixin({MouseHandler.class})
public class MouseClickMixin {
   @Inject(
      method = {"onButton"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void handleMenuMouseClick(long window, net.minecraft.client.input.MouseButtonInfo input, int action, CallbackInfo ci) {
      // TODO(port26): was GLFW.glfwGetCursorPos(window, ...)
      double[] xPos = new double[1];
      double[] yPos = new double[1];
      xPos[0] = Minecraft.getInstance().mouseHandler.xpos();
      yPos[0] = Minecraft.getInstance().mouseHandler.ypos();

      Minecraft client = Minecraft.getInstance();
      if (action == InputConstants.PRESS
              && client != null
              && client.gui.screen() == null
              && Vesence.get != null
              && Vesence.get.manager != null) {
         int mouseKey = -100 - (input.button() - 1); // TODO(port26): SDL buttons are 1-based; keep vesence 0-based encoding
         for (var module : Vesence.get.manager.getModules()) {
            if (module.bind == mouseKey) {
               module.toggle();
            }
         }
      }

      MouseButtonEvent event = new MouseButtonEvent(window, input.button(), action, input.modifiers(), xPos[0], yPos[0]);
      EventManager.call(event);
      if (event.isCancelled()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"grabMouse"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void preventCursorLock(CallbackInfo ci) {
      Minecraft client = Minecraft.getInstance();
      long handle = 0L;
      if (client != null && client.getWindow() != null) {
         handle = client.getWindow().handle();
      }

      CursorLockEvent event = new CursorLockEvent(client, handle);
      EventManager.call(event);
      if (event.isCancelled()) {
         ci.cancel();
      }
   }
}
