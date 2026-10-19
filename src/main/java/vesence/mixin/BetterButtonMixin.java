package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.Vesence;
import vesence.module.impl.misc.BetterMinecraft;

/**
 * Replaces the vanilla skin of every pressable widget (plain buttons and cycling option buttons)
 * with the custom Vesence skin. Icon-only widgets (e.g. the lock button) keep their vanilla look so
 * their icon stays visible.
 */
@Environment(EnvType.CLIENT)
@Mixin(PressableWidget.class)
public abstract class BetterButtonMixin {

   @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
   private void vesence$customButton(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && client.currentScreen instanceof ChatScreen) {
         return;
      }
      if (!BetterMinecraft.customButtonsEnabled() || !Vesence.isModInitialized()) {
         return;
      }
      // Queue for the custom skin painted after the GuiRenderer flush; keep vanilla (icon) when the
      // widget has no text label. If queued, cancel the vanilla rendering so only the custom skin
      // remains.
      if (BetterMinecraft.tryReplaceButton((ClickableWidget) (Object) this)) {
         ci.cancel();
      }
   }
}
