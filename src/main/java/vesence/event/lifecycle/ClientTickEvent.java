package vesence.event.lifecycle;

import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import vesence.event.Event;

@Environment(EnvType.CLIENT)
public final class ClientTickEvent extends Event {
   private final Minecraft client;

   public ClientTickEvent(Minecraft client) {
      this.client = Objects.requireNonNull(client, "client");
   }

   public Minecraft client() {
      return this.client;
   }
}
