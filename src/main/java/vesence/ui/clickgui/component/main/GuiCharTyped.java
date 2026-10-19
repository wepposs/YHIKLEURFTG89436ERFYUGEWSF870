package vesence.ui.clickgui.component.main;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.ui.clickgui.GuiScreen;

@Environment(EnvType.CLIENT)
public class GuiCharTyped extends GuiScreen {
   public static boolean charTyped(char codePoint, int modifiers) {
      if (GuiScreen.activeStringSetting != null) {
         if (codePoint == '\b') {
            if (!GuiScreen.activeStringSetting.input.isEmpty()) {
               GuiScreen.activeStringSetting.input = GuiScreen.activeStringSetting.input.substring(0, GuiScreen.activeStringSetting.input.length() - 1);

            }

            return true;
         }

         if (codePoint >= ' ' && codePoint != 127) {
            if (GuiScreen.textSelectAll) {
               GuiScreen.activeStringSetting.input = "";
               GuiScreen.textSelectAll = false;
            }
            if (GuiScreen.activeStringSetting.input.length() < 16) {
               GuiScreen.activeStringSetting.input = GuiScreen.activeStringSetting.input + codePoint;
               vesence.utils.render.utils.SoundUtil.playUi("searchtyping", 0.1F);
            }

            return true;
         }
      }

      if (GuiScreen.activeSearch) {
         if (codePoint == '\b') {
            return true;
         }

         if (codePoint >= ' '
            && codePoint != 127
            && (codePoint >= 'a' && codePoint <= 'z' || codePoint >= 'A' && codePoint <= 'Z' || codePoint >= '0' && codePoint <= '9' || codePoint == ' ')) {
            if (GuiScreen.textSelectAll) {
               GuiScreen.searchText = "";
               GuiScreen.textSelectAll = false;
            }
            if (GuiScreen.searchText.length() < 50) {
               GuiScreen.searchText = GuiScreen.searchText + codePoint;
            }

            return true;
         }
      }

      return false;
   }
}
