package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.AbstractScrollArea;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vesence.module.impl.misc.BetterMinecraft;

/**
 * Adds smooth scrolling to every scrollable widget (settings lists, world/server lists, etc.).
 * Scroll-wheel input is redirected onto an animation target that {@code tickSmoothScroll} eases the
 * real scroll position toward each frame.
 */
@Environment(EnvType.CLIENT)
@Mixin(AbstractScrollArea.class)
public abstract class ScrollableWidgetMixin {

   @org.spongepowered.asm.mixin.gen.Accessor("scrollbarSettings")
   protected abstract net.minecraft.client.gui.components.AbstractScrollArea.ScrollbarSettings vesence$scrollbarSettings();

   @Shadow protected abstract boolean scrollable();

   @Shadow public abstract double scrollAmount();

   @Shadow public abstract int maxScrollAmount();

   @Inject(method = "mouseScrolled(DDDD)Z", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
   private void vesence$smoothScroll(double mouseX, double mouseY, double horizontalAmount, double verticalAmount,
                                     CallbackInfoReturnable<Boolean> cir) {
      if (!BetterMinecraft.smoothScrollEnabled() || !scrollable()
              || !((AbstractWidget) (Object) this).isMouseOver(mouseX, mouseY)) {
         return;
      }
      double delta = -verticalAmount * vesence$scrollbarSettings().scrollRate();
      BetterMinecraft.addScrollDelta((AbstractScrollArea) (Object) this, delta, scrollAmount(), maxScrollAmount());
      cir.setReturnValue(true);
   }

   @Inject(method = "mouseDragged", at = @At("HEAD"), require = 0, expect = 0)
   private void vesence$clearOnDrag(MouseButtonEvent click, double offsetX, double offsetY, CallbackInfoReturnable<Boolean> cir) {
      // A scrollbar drag sets the position directly; drop any in-flight smoothing to avoid fighting it.
      BetterMinecraft.clearScroll((AbstractScrollArea) (Object) this);
   }
}
