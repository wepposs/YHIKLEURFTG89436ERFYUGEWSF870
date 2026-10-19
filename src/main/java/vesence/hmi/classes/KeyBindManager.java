package vesence.hmi.classes;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

public class KeyBindManager {
    KeyBinding key;

    public boolean isKeyPressed(int keyCode) {
        if (keyCode != 0) {
            long windowHandle = MinecraftClient.getInstance().getWindow().getHandle();
            return GLFW.glfwGetKey((long)windowHandle, (int)keyCode) == 1;
        }
        return false;
    }
}

