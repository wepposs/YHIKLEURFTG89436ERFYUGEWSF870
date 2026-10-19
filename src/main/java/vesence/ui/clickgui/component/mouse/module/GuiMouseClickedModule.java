package vesence.ui.clickgui.component.mouse.module;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.Vesence;
import vesence.module.api.Category;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.HueSetting;
import vesence.module.impl.misc.ClickGui;
import vesence.renderengine.render.Renderer2D;
import vesence.ui.clickgui.GuiScreen;
import vesence.ui.clickgui.component.mouse.setting.GuiMouseClickedSetting;
import vesence.ui.clickgui.component.render.GuiRenderMain;
import vesence.ui.clickgui.component.setting.GuiRenderSetting;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;
import vesence.utils.render.text.FontRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Environment(EnvType.CLIENT)
public class GuiMouseClickedModule extends GuiScreen {

   private static List<Module> getSortedModules(Category category, Renderer2D renderer2D) {
      List<Module> list = new ArrayList<>(Vesence.get.manager.getType(category));
      ClickGui clickGui = (ClickGui) Vesence.get.manager.module.stream()
              .filter(m -> m instanceof ClickGui).findFirst().orElse(null);
      if (clickGui != null && ClickGui.sort.is("По длине")) {
         list.sort(Comparator.comparingDouble((Module m) -> {
            return renderer2D.measureText(FontRegistry.SF_MEDIUM, m.name, 14f).width;
         }).reversed());
      } else {
         list.sort(Comparator.comparing(m -> m.name));
      }
      return list;
   }

   public static boolean mouseClickedModule(Renderer2D renderer2D, int mouseX, int mouseY, int pButton) {
      float groupX = GuiScreen.x;
      float groupY = GuiScreen.y;
      int panels = GuiScreen.categories.length;
      float pw = panelWidth;
      float ph = panelHeight;
      float pg = panelGap;
      float contentH = ph - panelHeaderH;

      for (int p = 0; p < panels; p++) {
         Category category = GuiScreen.categories[p];
         float panelX = groupX + p * (pw + pg);

         Animation2 panelAlphaAnim = GuiScreen.getPanelScaleAnimation(category);
         panelAlphaAnim.update();
         float panelAnimValue = (float) panelAlphaAnim.getValue();
         if (panelAnimValue < 0.01f) continue;

         float panelYOffset = (1.0f - panelAnimValue) * 20.0f;
         float animatedPanelY = groupY + panelYOffset;
         float panelAlpha = (float) GuiScreen.mainAnimation.getOutput() * panelAnimValue;

         float contentAreaY = animatedPanelY + panelHeaderH + 13;
         float contentAreaH = contentH - 17;

         if (!GuiRenderMain.isHovered(mouseX, mouseY, panelX, contentAreaY, pw, contentAreaH)) {
            continue;
         }

         CategorySettingsState catState = GuiScreen.getCategorySettingsState(category);
         boolean isSettingsMode = catState.openedModule != null;

         List<Module> catModules = getSortedModules(category, renderer2D);
         float settingsContentWMeasure = pw - 20F;

         int moduleCount = catModules.size();
         List<List<Setting<?>>> moduleSettingsList = new ArrayList<>(moduleCount);
         float[] moduleSettingsHeights = new float[moduleCount];
         float measureY = 7.0F;

         for (int mi = 0; mi < moduleCount; mi++) {
            Module m = catModules.get(mi);
            List<Setting<?>> settings = m.getSettingsForGUI();
            moduleSettingsList.add(settings);
            float mSettingsH = 0.0F;
            for (Setting<?> s : settings) {
               mSettingsH += GuiRenderSetting.getSettingHeight(renderer2D, s, settingsContentWMeasure) + 1.0F;
            }
            moduleSettingsHeights[mi] = mSettingsH;
            float mExpand = (float) GuiScreen.getModuleSettingsAnimation(m).getValue();

            float extraH = (mExpand > 0.001F) ? 22.5F : 0.0F;
            float mCardH = moduleHeight + mSettingsH * mExpand + extraH;
            measureY += mCardH + cardGap;
         }
         measureY += 7.0F;

         vesence.utils.math.ScrollUtil panelScroll = GuiScreen.getPanelScrollUtil(category);
         panelScroll.setMax(measureY, contentAreaH);
         panelScroll.update();
         float scrollOffset = panelScroll.getScroll();

         float settingsContentW = pw - 20;

         for (int mi = 0; mi < moduleCount; mi++) {
            Module module = catModules.get(mi);
            float settingsFullH = moduleSettingsHeights[mi];
            List<Setting<?>> moduleSettings = moduleSettingsList.get(mi);

            float expand = (float) GuiScreen.getModuleSettingsAnimation(module).getValue();
            float expandedH = settingsFullH * expand;

            float extraH = (expand > 0.001F) ? 22.5F : 0.0F;
            float cardH = moduleHeight + expandedH + extraH;

            String animKey = category.name() + "_" + module.name;
            Animation2 yAnim = GuiScreen.getModuleYAnimation(animKey);
            yAnim.update();
            float yCursor = (float) yAnim.get();

            float staggerYOff = (!isSettingsMode && GuiScreen.isStaggerYActive)
                    ? GuiScreen.computeStaggerOffset(mi, GuiScreen.STAGGER_Y_OFFSET, false)
                    : 0.0F;

            float effectiveModXOffset = 0.0F;
            float staggerAlpha = 1.0F;
            if (!isSettingsMode && catState.staggerXActive) {
               effectiveModXOffset = GuiScreen.computeStaggerOffset(mi, GuiScreen.STAGGER_X_OFFSET, true, catState.staggerXStartTime, catState.staggerXOpening);
               staggerAlpha = GuiScreen.computeStaggerAlpha(mi, catState.staggerXStartTime, catState.staggerXOpening);
            }
            if (!isSettingsMode && GuiScreen.isStaggerYActive) {
               staggerAlpha = Math.min(staggerAlpha, GuiScreen.computeStaggerAlpha(mi));
            }

            if (staggerAlpha <= 0.0F) {
               continue;
            }

            float modY = animatedPanelY + yCursor + 5 + staggerYOff;

            if (modY + cardH < contentAreaY || modY > contentAreaY + contentAreaH) {
               continue;
            }

            float headerY = modY + moduleCardInnerPadding;
            float rectY = headerY - panelAlpha * 35 + 35;

            float modX = panelX + 4 + effectiveModXOffset;
            float modW = pw - 8;
            float modH = moduleHeight;

            if (expand > 0.01F && !moduleSettings.isEmpty()) {
               float settingsStartY = headerY + moduleHeaderHeight + 22.5F;
               float settingsX = panelX + 10 + effectiveModXOffset;
               float totalSettingsH = 0.0F;
               for (Setting<?> setting : moduleSettings) {
                  float settingH = GuiRenderSetting.getSettingHeight(renderer2D, setting, settingsContentW);
                  float settingY = settingsStartY + totalSettingsH;
                  if (GuiRenderMain.isHovered(mouseX, mouseY, settingsX, settingY, settingsContentW, settingH)) {
                     if (GuiMouseClickedSetting.handleSettingClick(renderer2D, setting, settingsX, settingY, settingsContentW, mouseX, mouseY, pButton)) {
                        return true;
                     }
                  }
                  totalSettingsH += settingH;
               }
            }

            if (GuiRenderMain.isHovered(mouseX, mouseY, modX, rectY, modW, modH)) {
               boolean hasSettings = !moduleSettings.isEmpty();

               if (module.binding && pButton >= 0) {
                  int mouseKey = -100 - pButton;
                  module.bind = mouseKey;
                  module.binding = false;
                  GuiScreen.activeModuleBind = null;
                  GuiScreen.getModuleBindAnimation(module).run(1.0, 0.2, Easings.SINE_OUT);
                  if (Vesence.get.configManager != null) Vesence.get.configManager.autoSave();
                  return true;
               }

               if (pButton == 0) {
                  if (GuiScreen.activeModeSetting != null) {
                     GuiScreen.activeModeSetting.opened = false;
                     GuiScreen.activeModeSetting = null;
                  }
                  module.toggle();
                  return true;
               } else if (pButton == 1) {
                  if (GuiScreen.activeModeSetting != null) {
                     GuiScreen.activeModeSetting.opened = false;
                     GuiScreen.activeModeSetting = null;
                  }
                  if (hasSettings) {
                     boolean willOpen = !GuiScreen.openSettingsModules.contains(module);
                     module.open = willOpen;

                     if (willOpen) {
                        GuiScreen.openSettingsModules.add(module);
                        catState.openedModule = module;
                     } else {
                        GuiScreen.openSettingsModules.remove(module);
                        if (catState.openedModule == module) catState.openedModule = null;
                     }

                     GuiScreen.getModuleSettingsAnimation(module).run(willOpen ? 1.0 : 0.0, 0.4F, Easings.QUINT_OUT);
                     GuiScreen.getModuleSettingsAlphaAnimation(module).run(willOpen ? 1.0 : 0.0, 0.24F, Easings.CUBIC_OUT);
                     return true;
                  }
                  return false;
               } else if (pButton == 2) {
                  if (GuiScreen.activeModeSetting != null) {
                     GuiScreen.activeModeSetting.opened = false;
                     GuiScreen.activeModeSetting = null;
                  }
                  if (module.binding) {
                     module.binding = false;
                     GuiScreen.activeModuleBind = null;
                     GuiScreen.getModuleBindAnimation(module).run(0.0, 1.0, Easings.SINE_OUT);
                  } else {
                     if (GuiScreen.activeModuleBind != null) {
                        GuiScreen.activeModuleBind.binding = false;
                        GuiScreen.getModuleBindAnimation(GuiScreen.activeModuleBind).run(0.0, 1.0, Easings.SINE_OUT);
                     }
                     GuiScreen.activeModuleBind = module;
                     module.binding = true;
                     GuiScreen.getModuleBindAnimation(module).run(1.0, 1.0, Easings.SINE_OUT);
                  }
                  return true;
               }
            }
         }
      }

      if (GuiScreen.activeModeSetting != null) {
         GuiScreen.activeModeSetting.opened = false;
         GuiScreen.activeModeSetting = null;
      }
      return false;
   }

   public static boolean mouseScrolledModule(int mouseX, int mouseY, double delta) {
      float groupX = GuiScreen.x;
      float groupY = GuiScreen.y;
      int panels = GuiScreen.categories.length;
      float pw = panelWidth;
      float ph = panelHeight;
      float pg = panelGap;
      float contentH = ph - panelHeaderH;

      for (int p = 0; p < panels; p++) {
         Category category = GuiScreen.categories[p];
         float panelX = groupX + p * (pw + pg);

         Animation2 panelAlphaAnim = GuiScreen.getPanelScaleAnimation(category);
         float panelAnimValue = (float) panelAlphaAnim.getValue();
         if (panelAnimValue < 0.01f) continue;

         float panelYOffset = (1.0f - panelAnimValue) * 20.0f;
         float animatedPanelY = groupY + panelYOffset;

         float contentAreaY = animatedPanelY + panelHeaderH + 13;
         float contentAreaH = contentH - 17;

         if (!GuiRenderMain.isHovered(mouseX, mouseY, panelX, contentAreaY, pw, contentAreaH)) {
            continue;
         }

         vesence.utils.math.ScrollUtil panelScroll = GuiScreen.getPanelScrollUtil(category);
         panelScroll.handleScroll(delta);
         return true;
      }
      return false;
   }

   public static float[] findColorPickerPosition(Renderer2D renderer2D, HueSetting hueSetting) {
      if (hueSetting == null) return null;

      float groupX = GuiScreen.x;
      float groupY = GuiScreen.y;
      int panels = GuiScreen.categories.length;
      float pw = panelWidth;
      float pg = panelGap;

      for (int p = 0; p < panels; p++) {
         Category category = GuiScreen.categories[p];
         float panelX = groupX + p * (pw + pg);

         List<Module> catModules = getSortedModules(category, renderer2D);

         float scrollOffset = GuiScreen.getPanelScrollUtil(category).getScroll();
         float settingsContentW = pw - 20;

         for (Module module : catModules) {
            float settingsFullH = 0.0F;
            for (Setting<?> setting : module.getSettingsForGUI()) {
               settingsFullH += GuiRenderSetting.getSettingHeight(renderer2D, setting, settingsContentW) + 1.0F;
            }
            float expand = (float) GuiScreen.getModuleSettingsAnimation(module).getValue();
            float expandedH = settingsFullH * expand;
            float extraH = (expand > 0.001F) ? 22.5F : 0.0F;
            float cardH = moduleHeight + expandedH + extraH;

            String animKey = category.name() + "_" + module.name;
            float animatedYCursor = (float) GuiScreen.getModuleYAnimation(animKey).get();

            CategorySettingsState catState = GuiScreen.getCategorySettingsState(category);
            boolean isSettingsMode = catState.openedModule != null;
            float staggerYOff = (!isSettingsMode && GuiScreen.isStaggerYActive)
                    ? GuiScreen.computeStaggerOffset(catModules.indexOf(module), GuiScreen.STAGGER_Y_OFFSET, false)
                    : 0.0F;

            float modY = groupY + animatedYCursor + 5 + staggerYOff;
            float headerY = modY + moduleCardInnerPadding;
            float settingsStartY = headerY + moduleHeaderHeight + 22.5F;
            float settingsX = panelX + 10;

            float totalSettingsH = 0.0F;
            for (Setting<?> setting : module.getSettingsForGUI()) {
               if (setting == hueSetting) {
                  float pickerX = settingsX + settingsContentW - 15.0F;
                  float pickerY = settingsStartY + totalSettingsH - 5.0F;
                  return new float[]{pickerX, pickerY};
               }
               totalSettingsH += GuiRenderSetting.getSettingHeight(renderer2D, setting, settingsContentW) + 1.0F;
            }
         }
      }

      return null;
   }
}
