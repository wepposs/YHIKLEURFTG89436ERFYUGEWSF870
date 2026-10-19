package vesence.com.discord;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

public final class DiscordNativeLoader {
   private static volatile boolean loaded;
   private static volatile String extractedPath;

   private DiscordNativeLoader() {
   }

   public static synchronized String ensureExtractedPath() {
      if (extractedPath != null) {
         return extractedPath;
      }
      try {
         String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
         String resourcePath;
         String suffix;
         if (os.contains("win")) {
            resourcePath = "/win32-x86-64/discord-rpc.dll";
            suffix = ".dll";
         } else if (os.contains("mac")) {
            resourcePath = "/darwin/libdiscord-rpc.dylib";
            suffix = ".dylib";
         } else {
            resourcePath = "/linux-x86-64/libdiscord-rpc.so";
            suffix = ".so";
         }

         Path tmp = Files.createTempFile("discord-rpc-", suffix);
         tmp.toFile().deleteOnExit();
         try (InputStream in = DiscordNativeLoader.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
               throw new IllegalStateException("Discord RPC native resource not found: " + resourcePath);
            }
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
         }
         extractedPath = tmp.toAbsolutePath().toString();
         return extractedPath;
      } catch (IOException e) {
         throw new RuntimeException("Failed to load discord-rpc native library", e);
      }
   }

   public static synchronized void ensureLoaded() {
      if (loaded) {
         return;
      }
      System.load(ensureExtractedPath());
      loaded = true;
   }
}
