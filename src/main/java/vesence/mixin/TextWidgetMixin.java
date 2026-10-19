package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.AbstractTextWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.Vesence;
import vesence.module.impl.misc.BetterMinecraft;

/**
 * Replaces single-line text widgets (screen titles and section headers) with the custom Vesence
 * font. Multi-line text widgets are left untouched to avoid clipping.
 */
@Environment(EnvType.CLIENT)
@Mixin(AbstractTextWidget.class)
public abstract class TextWidgetMixin {

   @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
   private void vesence$customHeader(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
      if (!((Object) this instanceof TextWidget)) {
         return;
      }
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && client.currentScreen instanceof ChatScreen) {
         return;
      }
      if (!BetterMinecraft.customButtonsEnabled() || !Vesence.isModInitialized()) {
         return;
      }
      if (BetterMinecraft.tryReplaceHeader((ClickableWidget) (Object) this)) {
         ci.cancel();
      }
   }
}
