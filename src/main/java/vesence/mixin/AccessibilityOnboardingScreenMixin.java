package vesence.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.AccessibilityOnboardingScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.ui.MainScreen;
import vesence.ui.MainScreenHelper;

@Mixin(AccessibilityOnboardingScreen.class)
public class AccessibilityOnboardingScreenMixin {

    @Inject(method = "init", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void vesence$redirectToMainScreen(CallbackInfo ci) {
        if (MainScreenHelper.bypassOnce) {
            MainScreenHelper.bypassOnce = false;
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        ci.cancel();
        mc.setScreen(new MainScreen());
    }
}
