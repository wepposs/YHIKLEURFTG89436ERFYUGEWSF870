package vesence.utils.render.math;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;

@Environment(EnvType.CLIENT)
public class ScaledResolution {
   private final double scaledWidthD;
   private final double scaledHeightD;
   private int scaledWidth;
   private int scaledHeight;
   private static int scaleFactor;

   public ScaledResolution(MinecraftClient mc) {
      if (mc != null && mc.getWindow() != null) {
         int fbWidth = mc.getWindow().getFramebufferWidth();
         int fbHeight = mc.getWindow().getFramebufferHeight();
         scaleFactor = mc.getWindow().getScaleFactor();
         this.scaledWidthD = (double)fbWidth / scaleFactor;
         this.scaledHeightD = (double)fbHeight / scaleFactor;
         this.scaledWidth = MathHelper.ceil(this.scaledWidthD);
         this.scaledHeight = MathHelper.ceil(this.scaledHeightD);
      } else {
         this.scaledWidth = 1920;
         this.scaledHeight = 1080;
         scaleFactor = 1;
         this.scaledWidthD = this.scaledWidth;
         this.scaledHeightD = this.scaledHeight;
      }
   }

   public int getWidth() {
      return this.scaledWidth;
   }

   public int getHeight() {
      return this.scaledHeight;
   }

   public double getScaledWidth_double() {
      return this.scaledWidthD;
   }

   public double getScaledHeight_double() {
      return this.scaledHeightD;
   }

   public static int getScaleFactor() {
      return scaleFactor;
   }
}
