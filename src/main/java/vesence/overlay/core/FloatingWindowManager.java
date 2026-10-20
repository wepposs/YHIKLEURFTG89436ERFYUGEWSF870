package vesence.overlay.core;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import vesence.overlay.browser.BrowserWindow;
import vesence.overlay.discord.DiscordWindow;
import vesence.overlay.telegram.TelegramWindow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FloatingWindowManager {
    private static final FloatingWindowManager INSTANCE = new FloatingWindowManager();
    private final List<FloatingWindow> windows = new ArrayList<>();
    private FloatingWindow focused;

    public static FloatingWindowManager get() { return INSTANCE; }
    public List<FloatingWindow> getAll() { return Collections.unmodifiableList(windows); }
    public FloatingWindow getFocusedWindow() { return focused; }

    public void toggleBrowser() { toggle(BrowserWindow.class, BrowserWindow::new); }
    public void toggleTelegram() { toggle(TelegramWindow.class, TelegramWindow::new); }
    public void toggleDiscord() { toggle(DiscordWindow.class, DiscordWindow::new); }

    public void openBrowser() { open(BrowserWindow.class, BrowserWindow::new); }
    public void openTelegram() { open(TelegramWindow.class, TelegramWindow::new); }
    public void openDiscord() { open(DiscordWindow.class, DiscordWindow::new); }
    public void close(Class<? extends FloatingWindow> type) {
        for (FloatingWindow window : new ArrayList<>(windows)) {
            if (type.isInstance(window)) { windows.remove(window); window.close(); }
        }
    }

    private <T extends FloatingWindow> void toggle(Class<T> type, java.util.function.Function<Vec3, T> factory) {
        for (FloatingWindow window : new ArrayList<>(windows)) {
            if (type.isInstance(window)) { windows.remove(window); window.close(); return; }
        }
        open(type, factory);
    }

    private <T extends FloatingWindow> void open(Class<T> type, java.util.function.Function<Vec3, T> factory) {
        for (FloatingWindow window : windows) if (type.isInstance(window)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Vec3 pos = mc.player.getEyePosition(1.0f).add(mc.player.getViewVector(1.0f).scale(3.0));
        windows.add(factory.apply(pos));
    }

    public void tick() { for (FloatingWindow window : new ArrayList<>(windows)) window.tick(); }

    public void closeAll() {
        for (FloatingWindow window : windows) window.close();
        windows.clear(); focused = null;
    }
}
