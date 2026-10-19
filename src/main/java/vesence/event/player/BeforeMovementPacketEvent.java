package vesence.event.player;

import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import vesence.event.Event;

@Environment(EnvType.CLIENT)
public final class BeforeMovementPacketEvent extends Event {
   private final Minecraft client;
   private final LocalPlayer player;
   private final ClientLevel world;

   public BeforeMovementPacketEvent(Minecraft client, LocalPlayer player, ClientLevel world) {
      this.client = Objects.requireNonNull(client, "client");
      this.player = Objects.requireNonNull(player, "player");
      this.world = Objects.requireNonNull(world, "world");
   }

   public Minecraft client() {
      return this.client;
   }

   public LocalPlayer player() {
      return this.player;
   }

   public ClientLevel world() {
      return this.world;
   }
}
