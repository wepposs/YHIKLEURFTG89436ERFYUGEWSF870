package vesence.utils.render.ttf;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

import java.awt.Font;
import java.io.InputStream;
import java.util.Optional;

@Environment(EnvType.CLIENT)
public final class TtfFontUtil {

   private TtfFontUtil() {
   }

   public static Font load(Identifier resource, float size) {
      try {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc == null || mc.getResourceManager() == null) return null;
         Optional<Resource> r = mc.getResourceManager().getResource(resource);
         if (r.isEmpty()) return null;
         try (InputStream in = r.get().getInputStream()) {
            Font base = Font.createFont(Font.TRUETYPE_FONT, in);
            return base.deriveFont(size);
         }
      } catch (Exception e) {
         e.printStackTrace();
         return null;
      }
   }
}
