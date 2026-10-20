package vesence.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import com.mojang.blaze3d.platform.InputConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.Vesence;
import vesence.module.impl.player.LockSlot;
import vesence.ui.clickgui.GuiClient;

@Mixin(KeyboardHandler.class)
public class KeyboardMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void onKey(long window, int action, net.minecraft.client.input.KeyEvent input, CallbackInfo ci) {
        if (action != InputConstants.PRESS) {
            return;
        }
        int key = input.key();
        if (key == -1 || key == 0 /* SDL_SCANCODE_UNKNOWN */) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() != null) {
            return;
        }

        if (mc.player != null
                && mc.options.keyDrop.matches(input)
                && LockSlot.shouldCancelSelectedDrop()) {
            ci.cancel();
            return;
        }

        if (key == InputConstants.KEY_RSHIFT) {
            mc.gui.setScreen(new GuiClient());
            if (mc.mouseHandler != null) {
                mc.mouseHandler.releaseMouse();
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
