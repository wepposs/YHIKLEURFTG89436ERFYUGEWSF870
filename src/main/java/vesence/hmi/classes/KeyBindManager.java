package vesence.hmi.classes;

import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;

public class KeyBindManager {
    KeyMapping key;

    public boolean isKeyPressed(int keyCode) {
        if (keyCode != 0) {
            // 26.3: GLFW.glfwGetKey(windowHandle, keyCode) удалён вместе с GLFW.
            // InputConstants.isKeyDown(int) — его SDL-эквивалент (scancode).
            return InputConstants.isKeyDown(keyCode);
        }
        return false;
    }
}
