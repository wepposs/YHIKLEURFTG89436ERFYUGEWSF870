package vesence.ui.clickgui.component.main;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.ui.clickgui.GuiScreen;
import vesence.ui.clickgui.component.render.GuiRenderMain;

@Environment(EnvType.CLIENT)
public class GuiMouseScrolled extends GuiScreen {
   public static boolean mouseScrolled(double pMouseX, double pMouseY, double pScrollX, double pScrollY) {
      float mouseX = (float)(pMouseX);
      float mouseY = (float)(pMouseY);
      if (!GuiScreen.exit) {
         int panels = GuiScreen.categories.length;
         for (int p = 0; p < panels; p++) {
            float panelX = GuiScreen.x + p * (panelWidth + panelGap);
            float contentY = GuiScreen.y + panelHeaderH;
            float contentH = panelHeight - panelHeaderH;
            if (GuiRenderMain.isHovered(mouseX, mouseY, panelX, contentY, panelWidth, contentH)) {
               vesence.module.api.Category category = GuiScreen.categories[p];

               if (GuiScreen.getCategorySettingsState(category).openedModule != null) {
                  GuiScreen.getModuleSettingsPanelScroll(category).handleScroll(pScrollY);
               } else if (GuiScreen.getCategorySettingsState(category).isAnimatingOut()) {
                  GuiScreen.getModuleSettingsPanelScroll(category).handleScroll(pScrollY);
               } else {
                  GuiScreen.getPanelScrollUtil(category).handleScroll(pScrollY);
               }
               return true;
            }
         }
      }

      return false;
   }
}
