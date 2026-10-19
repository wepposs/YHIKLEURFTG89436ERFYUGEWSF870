package vesence.ui.clickgui.component.main;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.module.api.Category;
import vesence.ui.clickgui.GuiScreen;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;

@Environment(EnvType.CLIENT)
public class GuiInit extends GuiScreen {
   public static void init() {
      GuiScreen.animation.run(1.0, 0.2F);
      GuiScreen.alphaPC.set(0.0);
      GuiScreen.alphaPC.run(1.0, 0.4, Easings.CUBIC_OUT);
      GuiScreen.exit = false;
      GuiScreen.mainAnimation.reset();
      GuiScreen.alpha.run(1.0);

      GuiScreen.openedModuleSettings = null;
      GuiScreen.openedModuleCategory = null;
      GuiScreen.lastAnimatedCategory = null;
      GuiScreen.lastOpenedModule = null;
      GuiScreen.moduleSettingsPanelRotateY.setValue(-90.0);
      GuiScreen.categoryTitleAlpha.setValue(1.0);
      GuiScreen.categoryTitleY.setValue(0.0);
      GuiScreen.categoryIconAlpha.setValue(1.0);
      GuiScreen.categoryIconX.setValue(0.0);
      GuiScreen.moduleTitleAlpha.setValue(0.0);
      GuiScreen.moduleTitleY.setValue(10.0);
      GuiScreen.modulesContentAlpha.setValue(1.0);
      GuiScreen.modulesContentX.setValue(0.0);
      GuiScreen.settingsContentX.setValue(20.0);
      GuiScreen.settingsContentAlpha.setValue(0.0);

      GuiScreen.categorySettingsStates.clear();
      GuiScreen.settingsOpenOrder.clear();

      GuiScreen.isOpening = true;
      GuiScreen.animationStartTime = System.currentTimeMillis();
      GuiScreen.panelAnimationDelays.clear();
      GuiScreen.isStaggerYActive = true;
      GuiScreen.moduleStaggerStartTime = System.currentTimeMillis();

      if (GuiScreen.categories != null) {
         for (Category category : GuiScreen.categories) {
            Animation2 panelAnim = GuiScreen.getPanelScaleAnimation(category);
            panelAnim.setStart(0L);
            panelAnim.setDuration(0.0);
            panelAnim.setValue(0.0);
            panelAnim.setFromValue(0.0);
            panelAnim.setToValue(0.0);
         }
      }
   }
}
