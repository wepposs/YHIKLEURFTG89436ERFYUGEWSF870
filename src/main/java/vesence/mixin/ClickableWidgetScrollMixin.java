package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.AbstractScrollArea;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.module.impl.misc.BetterMinecraft;

/**
 * Drives the smooth-scroll animation once per frame. {@link AbstractWidget#render} runs every
 * frame for every widget, so scrollable widgets ease their scroll position toward the target here
 * before their contents are drawn.
 */
@Environment(EnvType.CLIENT)
@Mixin(AbstractWidget.class)
public abstract class ClickableWidgetScrollMixin {

   @Inject(method = "extractRenderState", at = @At("HEAD"), require = 0, expect = 0)
   private void vesence$tickSmoothScroll(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
      if ((Object) this instanceof AbstractScrollArea scrollable && BetterMinecraft.smoothScrollEnabled()) {
         BetterMinecraft.tickSmoothScroll(scrollable);
      }
   }
}
