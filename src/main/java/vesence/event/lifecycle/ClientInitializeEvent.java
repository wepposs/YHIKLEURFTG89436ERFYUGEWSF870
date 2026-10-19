package vesence.event.lifecycle;

import java.time.Instant;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import vesence.event.Event;

@Environment(EnvType.CLIENT)
public final class ClientInitializeEvent extends Event {
   private final Minecraft client;
   private final Instant timestamp;

   public ClientInitializeEvent(Minecraft client) {
      this(client, Instant.now());
   }

   public ClientInitializeEvent(Minecraft client, Instant timestamp) {
      this.client = client;
      this.timestamp = Objects.requireNonNull(timestamp, "timestamp");
   }

   public Minecraft client() {
      return this.client;
   }

   public Instant timestamp() {
      return this.timestamp;
   }
}
