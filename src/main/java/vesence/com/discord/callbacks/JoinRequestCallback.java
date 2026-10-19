package vesence.com.discord.callbacks;

import com.sun.jna.Callback;
import vesence.com.discord.DiscordUser;

public interface JoinRequestCallback extends Callback {
   void apply(DiscordUser p0);
}
