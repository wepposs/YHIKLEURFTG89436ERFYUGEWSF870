package vesence.com.discord;

import com.sun.jna.Library;
import com.sun.jna.Native;

public interface DiscordRPC extends Library {
   DiscordRPC INSTANCE = Native.load(DiscordNativeLoader.ensureExtractedPath(), DiscordRPC.class);

   void Discord_UpdateHandlers(DiscordEventHandlers var1);

   void Discord_UpdatePresence(DiscordRichPresence p0);

   void Discord_Respond(String var1, int var2);

   void Discord_Register(String var1, String var2);

   void Discord_Shutdown();

   void Discord_UpdateConnection();

   void Discord_RegisterSteamGame(String var1, String var2);

   void Discord_RunCallbacks();

   void Discord_Initialize(String p0, DiscordEventHandlers p1, boolean p2, String p3);

   void Discord_ClearPresence();
}
