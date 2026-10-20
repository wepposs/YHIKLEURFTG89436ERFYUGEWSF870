package vesence.module.api.setting.impl;

import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.InputConstants;
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
      return InputConstants.isKeyDown(keyCode);
   }

   public String label() {
      return vesence.utils.render.utils.KeyUtil.getKey(this.key);
   }

   public boolean isPressed() {
      if (this.key == -1) return false;
      return isDown(this.key);
   }

   public boolean isDown() {
      return isPressed();
   }

   private boolean isDown(int k) {
      if (k == -1) return false;
      if (k <= -100) {
         int button = -k - 100;
         // TODO(port26): was GLFW.glfwGetMouseButton(handle, button); SDL-era API only exposes named buttons
         net.minecraft.client.MouseHandler mouse = Minecraft.getInstance().mouseHandler;
         return switch (button) {
            case 0 -> mouse.isLeftPressed();
            case 1 -> mouse.isRightPressed();
            case 2 -> mouse.isMiddlePressed();
            default -> false;
         };
      }
      return InputConstants.isKeyDown(k);
   }
}
