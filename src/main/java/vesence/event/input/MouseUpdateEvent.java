package vesence.event.input;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import vesence.event.Event;

@Environment(EnvType.CLIENT)
public final class MouseUpdateEvent extends Event {
   private final Minecraft client;

   public MouseUpdateEvent(Minecraft client) {
      this.client = client;
   }

   public Minecraft client() {
      return this.client;
   }
}
