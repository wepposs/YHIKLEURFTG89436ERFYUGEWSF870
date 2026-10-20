package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MouseHandler;
import com.mojang.blaze3d.platform.InputConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.event.EventManager;
import vesence.event.input.MouseScrollEvent;

@Environment(EnvType.CLIENT)
@Mixin({MouseHandler.class})
public class MouseScrollMixin {
   @Inject(
      method = {"onScroll"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void handleMenuMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
      // 26.3: GLFW.glfwGetCursorPos удалён; MouseHandler хранит актуальные координаты курсора.
      double[] cursorX = new double[1];
      double[] cursorY = new double[1];
      cursorX[0] = net.minecraft.client.Minecraft.getInstance().mouseHandler.xpos();
      cursorY[0] = net.minecraft.client.Minecraft.getInstance().mouseHandler.ypos();
      MouseScrollEvent event = new MouseScrollEvent(window, horizontal, vertical, cursorX[0], cursorY[0]);
      EventManager.call(event);
      if (event.isCancelled()) {
         ci.cancel();
      }
   }
}
