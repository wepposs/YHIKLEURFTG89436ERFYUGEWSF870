package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.ui.MainScreen;
import vesence.ui.MainScreenHelper;

/**
 * Подмена ванильных экранов на экраны vesence.
 *
 * <p>Порт 26.3: {@code Minecraft#setScreen} удалён, единственная точка постановки экрана —
 * {@link Gui#setScreen}. Стартовый экран ставится именно так:
 * {@code Gui#buildInitialScreens} собирает цепочку и вызывает {@code this.setScreen(new TitleScreen(...))}
 * напрямую, минуя {@code Minecraft#setScreenAndShow}. Поэтому прежний хук на
 * {@code setScreenAndShow} для главного меню не срабатывал вообще — игра показывала
 * ванильный TitleScreen, а MainScreen не появлялся.
 *
 * <p>Перехватываем здесь — это covers и {@code setScreenAndShow} (он сам зовёт
 * {@code gui.setScreen}), и прямые вызовы из {@code buildInitialScreens}.
 */
@Environment(EnvType.CLIENT)
@Mixin(Gui.class)
public class GuiScreenRedirectMixin {

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true, require = 0)
    private void vesence$redirectScreens(Screen screen, CallbackInfo ci) {
        if (screen == null) return;

        // Повторный вход: мы сами ставим MainScreen/MultiplayerScreen — их не трогаем.
        if (screen instanceof MainScreen || screen instanceof vesence.ui.MultiplayerScreen) return;

        boolean isTitle = screen instanceof TitleScreen;
        boolean isMultiplayer = screen instanceof JoinMultiplayerScreen;

        if (!isTitle && !isMultiplayer) return;

        if (MainScreenHelper.bypassOnce) {
            MainScreenHelper.bypassOnce = false;
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        try {
            if (isTitle) {
                ci.cancel();
                mc.gui.setScreen(new MainScreen());
            } else {
                ci.cancel();
                boolean ru = false;
                try {
                    ru = new vesence.ui.SettingsWindow().getSelectedLangMode() == 0;
                } catch (Throwable ignored) {
                }
                mc.gui.setScreen(new vesence.ui.MultiplayerScreen(new MainScreen()).setRussian(ru));
            }
        } catch (Throwable t) {
            System.err.println("[weppos] screen redirect failed: " + t);
        }
    }
}
