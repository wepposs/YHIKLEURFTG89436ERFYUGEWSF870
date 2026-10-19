package vesence.mixin;

import net.minecraft.client.Keyboard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.Vesence;
import vesence.module.impl.player.LockSlot;
import vesence.ui.clickgui.GuiClient;

@Mixin(Keyboard.class)
public class KeyboardMixin {
    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void onKey(long window, int action, KeyInput input, CallbackInfo ci) {
        if (action != GLFW.GLFW_PRESS) {
            return;
        }
        int key = input.key();
        if (key == -1 || key == GLFW.GLFW_KEY_UNKNOWN) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) {
            return;
        }

        if (mc.player != null
                && mc.options.dropKey.matchesKey(input)
                && LockSlot.shouldCancelSelectedDrop()) {
            ci.cancel();
            return;
        }

        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            mc.setScreen(new GuiClient());
            if (mc.mouse != null) {
                mc.mouse.unlockCursor();
            }
            ci.cancel();
            return;
        }

        if (mc.player != null && Vesence.get != null && Vesence.get.manager != null) {
            for (var module : Vesence.get.manager.getModules()) {
                if (module instanceof vesence.module.impl.misc.Browser browser) browser.handleOpenKey(key);
                if (module instanceof vesence.module.impl.misc.Telegram telegram) telegram.handleOpenKey(key);
                if (module instanceof vesence.module.impl.misc.Discord discord) discord.handleOpenKey(key);
                if (module.bind == -1) continue;
                if (module.bind == key) {
                    module.toggle();
                }
            }
        }

        if (mc.player != null) {
            vesence.utils.macro.MacroManager.getInstance().onKeyPress(key);
        }
    }

}
