package vesence.hmi.classes;

import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;

public class KeyBindManager {
    KeyMapping key;

    public boolean isKeyPressed(int keyCode) {
        if (keyCode != 0) {
            // TODO(port26): was GLFW.glfwGetKey(windowHandle, keyCode); 26.3 uses SDL scancodes
            return InputConstants.isKeyDown(keyCode);
        }
        return false;
    }
}
