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
      // 26.3: GLFW.glfwGetCursorPos удалён. Курсор теперь отслеживает MouseHandler
      // (обновляется в onMove); здесь берём его текущие (уже оконные) координаты.
      double[] xPos = new double[1];
      double[] yPos = new double[1];
      xPos[0] = Minecraft.getInstance().mouseHandler.xpos();
      yPos[0] = Minecraft.getInstance().mouseHandler.ypos();

      Minecraft client = Minecraft.getInstance();
      vesence.debug.InputDebug.mouse("MouseHandler.onButton", input.button(), action, xPos[0], yPos[0]);
      if (action == InputConstants.PRESS
              && client != null
              && client.gui.screen() == null
              && Vesence.get != null
              && Vesence.get.manager != null) {
         // 26.3: input.button() теперь SDL (1=ЛКМ, 2=СКМ, 3=ПКМ), а бинд vesence
         // кодируется как -100 - GLFW-индекс. MouseUtil.bind переводит одно в другое.
         int mouseKey = vesence.utils.other.MouseUtil.bind(input.button());
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
