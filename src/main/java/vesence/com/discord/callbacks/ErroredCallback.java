package vesence.com.discord.callbacks;

import com.sun.jna.Callback;

public interface ErroredCallback extends Callback {
   void apply(int p0, String p1);
}
