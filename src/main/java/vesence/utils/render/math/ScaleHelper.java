package vesence.utils.render.math;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.opengl.GL11;

@Environment(EnvType.CLIENT)
public class ScaleHelper {
   private MinecraftClient mc = MinecraftClient.getInstance();
   public static float size = 2.0F;

   public static float getScale() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc != null && mc.getWindow() != null) {
         return mc.getWindow().getScaleFactor();
      }
      return size;
   }

   public static void scale_pre() {
      ScaledResolution scaledRes = new ScaledResolution(MinecraftClient.getInstance());
      double scale = ScaledResolution.getScaleFactor() / Math.pow(ScaledResolution.getScaleFactor(), 2.0);
      GL11.glPushMatrix();
      GL11.glScaled(scale * size, scale * size, scale * size);
   }

   public static void scale_post() {
      GL11.glScaled(size, size, size);
      GL11.glPopMatrix();
   }

   public static void scaleStart(float x, float y, float scale) {
      MatrixStack poseStack = new MatrixStack();
      poseStack.push();
      poseStack.translate(x, y, 0.0F);
      poseStack.scale(scale, scale, 1.0F);
      poseStack.translate(-x, -y, 0.0F);
   }

   public static void scaleEnd() {
      MatrixStack poseStack = new MatrixStack();
      poseStack.pop();
   }

   public static int calc(int value) {
      float sf = getScale();
      return (int)(value * sf / sf);
   }

   public static int calc(float value) {
      float sf = getScale();
      return (int)(value * sf / sf);
   }

   public static float[] calc(float mouseX, float mouseY) {
      float sf = getScale();
      mouseX = mouseX * sf / sf;
      mouseY = mouseY * sf / sf;
      return new float[]{mouseX, mouseY};
   }

   public static void scaleNonMatrix(float x, float y, float scale) {
      MatrixStack poseStack = new MatrixStack();
      poseStack.translate(x, y, 0.0F);
      poseStack.scale(scale, scale, 1.0F);
      poseStack.translate(-x, -y, 0.0F);
   }
}
