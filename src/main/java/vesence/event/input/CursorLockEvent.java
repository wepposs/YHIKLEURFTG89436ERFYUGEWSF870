package vesence.event.input;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import vesence.event.Event;

@Environment(EnvType.CLIENT)
public final class CursorLockEvent extends Event {
   private final Minecraft client;
   private final long windowHandle;

   public CursorLockEvent(Minecraft client, long windowHandle) {
      this.client = client;
      this.windowHandle = windowHandle;
   }

   public Minecraft client() {
      return this.client;
   }

   public long windowHandle() {
      return this.windowHandle;
   }
}
