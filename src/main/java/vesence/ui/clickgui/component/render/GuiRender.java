package vesence.ui.clickgui.component.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import vesence.Vesence;
import vesence.module.api.Category;
import vesence.module.api.Module;
import vesence.renderengine.render.Renderer2D;
import vesence.ui.clickgui.GuiScreen;
import vesence.ui.clickgui.compact.CompactGuiRender;
import vesence.ui.clickgui.compact.CompactGuiScreen;
import vesence.utils.render.math.ScaleHelper;

@Environment(EnvType.CLIENT)
public class GuiRender extends GuiScreen {

   public static void render(
           Renderer2D renderer2D,
           GuiGraphicsExtractor context,
           int mouseXRaw,
           int mouseYRaw,
           float tickDelta
   ) {
      Minecraft client = Minecraft.getInstance();

      if (client == null || client.getWindow() == null) {
         return;
      }

      int viewportWidth = client.getWindow().getScreenWidth();
      int viewportHeight = client.getWindow().getScreenHeight();

      if (viewportWidth <= 0 || viewportHeight <= 0) {
         return;
      }

      float actualScale = ScaleHelper.getScale();

      int mouseX = (int) (mouseXRaw / actualScale);
      int mouseY = (int) (mouseYRaw / actualScale);

      /*
       * ==========================================
       * MOUSE / GUI STATE
       * ==========================================
       */

      CompactGuiScreen.currentMouseX =
              (int) CompactGuiScreen.toGuiX(mouseX);

      CompactGuiScreen.currentMouseY =
              (int) CompactGuiScreen.toGuiY(mouseY);

      GuiScreen.currentMouseX = mouseX;
      GuiScreen.currentMouseY = mouseY;

      /*
       * ==========================================
       * ANIMATIONS
       * ==========================================
       */

      GuiScreen.alphaPC.update();
      GuiScreen.settingPC.update();
      GuiScreen.alphaPC2.update();
      GuiScreen.alphaPC3.update();

      if (GuiScreen.categories != null) {

         for (Category category : GuiScreen.categories) {

            GuiScreen
                    .getPanelScaleAnimation(category)
                    .update();

            for (Module module :
                    Vesence.get.manager.getType(category)) {

               GuiScreen
                       .getModuleSettingsAnimation(module)
                       .update();

               GuiScreen
                       .getModuleSettingsAlphaAnimation(module)
                       .update();

               GuiScreen
                       .getModuleBindAnimation(module)
                       .update();
            }
         }
      }

      /*
       * ==========================================
       * MAIN GUI ALPHA
       * ==========================================
       */

      GuiScreen.alpha.run(1.0);

      float mainAlpha =
              (float) GuiScreen.alphaPC.getValue();

      if (mainAlpha <= 0.001F) {
         return;
      }

      /*
       * ==========================================
       * RENDER GUI
       * ==========================================
       */

      float scale =
              (float) client.getWindow().getScreenWidth()
                      / client.getWindow().getGuiScaledWidth();

      renderer2D.pushScale(scale);

      try {

         CompactGuiRender.render(
                 renderer2D,
                 mouseX,
                 mouseY,
                 mainAlpha * (float) categoryAnimation.getOutput()
         );

      } finally {

         renderer2D.popTransform();
      }
   }
}