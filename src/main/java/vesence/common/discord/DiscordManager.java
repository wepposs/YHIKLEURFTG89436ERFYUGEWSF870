package vesence.common.discord;

import net.minecraft.resources.Identifier;

/**
 * DiscordManager intentionally does not initialize Discord RPC.
 *
 * The client uses vesence.module.impl.misc.DiscordRCP as the single
 * Rich Presence owner. Keeping a second Discord_Initialize call here
 * would overwrite the presence and cause two different Application IDs
 * to fight with each other.
 */
public class DiscordManager {

   private boolean running = false;
   private DiscordInfo info = new DiscordInfo("Unknown", "", "");
   private Identifier avatarId;

   public void init() {
      // RPC is managed exclusively by DiscordRCP.
      this.running = false;
   }

   public void stopRPC() {
      // DiscordRCP owns the native RPC lifecycle.
      this.running = false;
   }

   public void pushPresence() {
      // Intentionally empty: prevents this manager from overwriting DiscordRCP.
   }

   public boolean isRunning() {
      return this.running;
   }

   public DiscordInfo getInfo() {
      return this.info;
   }

   public Identifier getAvatarId() {
      return this.avatarId;
   }

   public record DiscordInfo(String userName, String avatarUrl, String userId) {
   }
}
