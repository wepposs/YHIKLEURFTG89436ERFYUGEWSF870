package vesence.overlay;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.lwjgl.glfw.GLFW;
import vesence.overlay.core.FloatingWindowManager;

@Environment(EnvType.CLIENT)
public final class OverlayModule {
    private static boolean initialized;
    private static final FloatingWindowManager WINDOWS = FloatingWindowManager.get();

    private OverlayModule() { }

    public static void init() {
        if (initialized) return;
        initialized = true;
        ClientTickEvents.END_CLIENT_TICK.register(client -> WINDOWS.tick());
    }

    public static void handleKey(int key) {
        if (key == GLFW.GLFW_KEY_B) WINDOWS.toggleBrowser();
        else if (key == GLFW.GLFW_KEY_T) WINDOWS.toggleTelegram();
        else if (key == GLFW.GLFW_KEY_G) WINDOWS.toggleDiscord();
    }

}
