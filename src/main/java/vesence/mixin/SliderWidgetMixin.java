package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.AbstractSliderButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.Vesence;
import vesence.module.impl.misc.BetterMinecraft;

/**
 * Replaces the vanilla slider skin with the custom Vesence skin (a rounded panel with a themed
 * progress fill and the slider label).
 */
@Environment(EnvType.CLIENT)
@Mixin(AbstractSliderButton.class)
public abstract class SliderWidgetMixin {

   @Inject(method = "extractWidgetRenderState", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
   private void vesence$customSlider(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
      Minecraft client = Minecraft.getInstance();
      if (client != null && client.gui.screen() instanceof ChatScreen) {
         return;
      }
      if (!BetterMinecraft.customButtonsEnabled() || !Vesence.isModInitialized()) {
         return;
      }
      if (BetterMinecraft.tryReplaceSlider((AbstractWidget) (Object) this)) {
         ci.cancel();
      }
   }
}
