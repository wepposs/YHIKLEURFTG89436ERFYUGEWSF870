package vesence.ui.clickgui.component.main;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.module.api.Category;
import vesence.ui.clickgui.GuiScreen;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;

@Environment(EnvType.CLIENT)
public class GuiShouldCloseOnEsc extends GuiScreen {
   public static boolean shouldCloseOnEsc() {

      if (!GuiScreen.settingsOpenOrder.isEmpty()) {
         Category lastCategory = GuiScreen.settingsOpenOrder.removeLast();
         CategorySettingsState catState = GuiScreen.getCategorySettingsState(lastCategory);
         if (catState.openedModule != null) {
            catState.rotateY.setValue(0.0);
            catState.rotateY.run(-90.0, 0.5, Easings.QUART_OUT);
            catState.catTitleAlpha.setValue(0.0);
            catState.catTitleAlpha.run(1.0, 0.3, Easings.QUINT_OUT);
            catState.catTitleY.setValue(-10.0);
            catState.catTitleY.run(0.0, 0.5, Easings.QUART_OUT);
            catState.catIconAlpha.setValue(0.0);
            catState.catIconAlpha.run(1.0, 0.3, Easings.QUINT_OUT);
            catState.catIconX.setValue(-8.0);
            catState.catIconX.run(0.0, 0.5, Easings.QUART_OUT);
            catState.modTitleAlpha.setValue(1.0);
            catState.modTitleAlpha.run(0.0, 0.3, Easings.QUINT_OUT);
            catState.modTitleY.setValue(0.0);
            catState.modTitleY.run(10.0, 0.5, Easings.QUART_OUT);
            catState.modContentAlpha.setValue(0.0);
            catState.modContentAlpha.run(1.0, 0.3, Easings.QUINT_OUT);
            catState.modContentX.setValue(-15.0);
            catState.modContentX.run(0.0, 0.5, Easings.QUART_OUT);
            catState.setContentAlpha.setValue(1.0);
            catState.setContentAlpha.run(0.0, 0.15, Easings.SINE_IN);
            catState.setContentX.setValue(0.0);
            catState.setContentX.run(20.0, 0.5, Easings.QUART_OUT);
            catState.staggerXActive = true;
            catState.staggerXOpening = false;
            catState.staggerXStartTime = System.currentTimeMillis();
            catState.settingsStaggerActive = true;
            catState.settingsStaggerOpening = false;
            catState.settingsStaggerStartTime = System.currentTimeMillis();
            catState.openedModule = null;
         }
         return false;
      }

      if (!GuiScreen.exit && (float)GuiScreen.alphaPC.getValue() > 0.0F) {
         GuiScreen.alphaPC.run(0.0, 0.25F, Easings.CUBIC_IN);
         GuiScreen.exit = true;

         GuiScreen.isOpening = false;
         GuiScreen.animationStartTime = System.currentTimeMillis();

         if (GuiScreen.categories != null) {
            for (Category category : GuiScreen.categories) {
               Animation2 panelAnim = GuiScreen.getPanelScaleAnimation(category);
               panelAnim.setStart(0L);
               panelAnim.setDuration(0.0);
            }
         }
      }

      return false;
   }
}
