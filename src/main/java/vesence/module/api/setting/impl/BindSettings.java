package vesence.module.api.setting.impl;

import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import vesence.module.api.setting.Setting;

@Environment(EnvType.CLIENT)
public class BindSettings extends Setting<Integer> {
   public int key;
   public String description;
   public boolean active;

   public BindSettings(String name, int key) {
      this.name = name;
      this.key = key;
   }

   @Override
   public Integer get() {
      return this.key;
   }

   public void set(int key) {
      this.key = key;
   }

   public BindSettings hidden(Supplier<Boolean> hidden) {
      this.hidden = hidden;
      return this;
   }

   public boolean isKeyDown(int keyCode) {
      return InputUtil.isKeyPressed(Minecraft.getInstance().getWindow(), keyCode);
   }

   public String label() {
      return vesence.utils.render.utils.KeyUtil.getKey(this.key);
   }

   public boolean isPressed() {
      if (this.key == -1) return false;
      return isDown(this.key);
   }

   private boolean isDown(int k) {
      if (k == -1) return false;
      if (k <= -100) {
         int button = -k - 100;
         long handle = Minecraft.getInstance().getWindow().getHandle();
         return GLFW.glfwGetMouseButton(handle, button) == GLFW.GLFW_PRESS;
      }
      return InputUtil.isKeyPressed(Minecraft.getInstance().getWindow(), k);
   }
}
