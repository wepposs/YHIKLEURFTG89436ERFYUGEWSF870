package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.ScrollableWidget;
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
@Mixin(ScrollableWidget.class)
public abstract class ScrollableWidgetMixin {

   @Shadow protected abstract double getDeltaYPerScroll();

   @Shadow protected abstract boolean overflows();

   @Shadow public abstract double getScrollY();

   @Shadow public abstract int getMaxScrollY();

   @Inject(method = "mouseScrolled(DDDD)Z", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
   private void vesence$smoothScroll(double mouseX, double mouseY, double horizontalAmount, double verticalAmount,
                                     CallbackInfoReturnable<Boolean> cir) {
      if (!BetterMinecraft.smoothScrollEnabled() || !overflows()
              || !((ClickableWidget) (Object) this).isMouseOver(mouseX, mouseY)) {
         return;
      }
      double delta = -verticalAmount * getDeltaYPerScroll();
      BetterMinecraft.addScrollDelta((ScrollableWidget) (Object) this, delta, getScrollY(), getMaxScrollY());
      cir.setReturnValue(true);
   }

   @Inject(method = "mouseDragged", at = @At("HEAD"), require = 0, expect = 0)
   private void vesence$clearOnDrag(Click click, double offsetX, double offsetY, CallbackInfoReturnable<Boolean> cir) {
      // A scrollbar drag sets the position directly; drop any in-flight smoothing to avoid fighting it.
      BetterMinecraft.clearScroll((ScrollableWidget) (Object) this);
   }
}
