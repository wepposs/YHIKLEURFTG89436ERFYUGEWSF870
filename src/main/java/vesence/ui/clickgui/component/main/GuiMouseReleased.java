package vesence.ui.clickgui.component.main;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.ui.clickgui.GuiScreen;

@Environment(EnvType.CLIENT)
public class GuiMouseReleased extends GuiScreen {
   public static void mouseReleased() {

      GuiScreen.pickingSaturationBrightness = false;
      GuiScreen.pickingHue = false;
      GuiScreen.pickingAlpha = false;
      if (GuiScreen.activeHueSetting != null) {
         GuiScreen.activeHueSetting.draggingBar = -1;
         GuiScreen.activeHueSetting.pickingHue = false;
         GuiScreen.activeHueSetting.pickingSaturationBrightness = false;
      }
      GuiScreen.activeSliderSetting = null;
      vesence.ui.clickgui.compact.CompactGuiScreen.unfreezeScale();
      if (GuiScreen.activeRangeSetting != null) {
         GuiScreen.activeRangeSetting.draggingThumb = 0;
         GuiScreen.activeRangeSetting = null;
      }
      GuiScreen.sliderX = 0.0F;
      GuiScreen.sliderY = 0.0F;
      GuiScreen.sliderWidth = 0.0F;
   }
}
