package vesence.utils.player;

import net.minecraft.client.Minecraft;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import vesence.utils.other.IMinecraft;

@Environment(EnvType.CLIENT)
public class MovementManager implements IMinecraft {
   private static final MovementManager INSTANCE = new MovementManager();
   public final Set<String> lockRequests = new HashSet<>();

   private MovementManager() {
   }

   public static MovementManager getInstance() {
      return INSTANCE;
   }

   public void lockMovement(String moduleName) {
      if (mc.player != null && mc.player.isAlive() && mc.level != null) {
         this.lockRequests.add(moduleName);
         this.setMovementKeys(false);
      }
   }

   public void unlockMovement(String moduleName) {
      if (mc.player != null && mc.player.isAlive() && mc.level != null) {
         this.lockRequests.remove(moduleName);
         if (this.lockRequests.isEmpty() && mc.gui.screen() == null) {
            this.setMovementKeys(true);
         }
      }
   }

   public boolean isMovementLocked() {
      return !this.lockRequests.isEmpty();
   }

   private void setMovementKeys(boolean state) {
      KeyMapping[] movementKeys = new KeyMapping[]{
         mc.options.keyUp,
         mc.options.keyDown,
         mc.options.keyLeft,
         mc.options.keyRight,
         mc.options.keyJump,
         mc.options.keySprint
      };

      for (KeyMapping keyBinding : movementKeys) {
         boolean pressed = state && InputConstants.isKeyDown(keyBinding.getDefaultKey().getValue());
         keyBinding.setDown(pressed);
      }
   }
}
