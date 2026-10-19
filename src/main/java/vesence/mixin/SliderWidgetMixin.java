package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
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
@Mixin(SliderWidget.class)
public abstract class SliderWidgetMixin {

   @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
   private void vesence$customSlider(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && client.currentScreen instanceof ChatScreen) {
         return;
      }
      if (!BetterMinecraft.customButtonsEnabled() || !Vesence.isModInitialized()) {
         return;
      }
      if (BetterMinecraft.tryReplaceSlider((ClickableWidget) (Object) this)) {
         ci.cancel();
      }
   }
}
