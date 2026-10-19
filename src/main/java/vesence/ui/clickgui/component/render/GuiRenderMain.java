package vesence.ui.clickgui.component.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.util.math.MatrixStack;
import vesence.Vesence;
import vesence.module.api.Category;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.HueSetting;
import vesence.module.impl.misc.ClickGui;
import vesence.renderengine.render.Renderer2D;
import vesence.ui.clickgui.GuiScreen;
import vesence.ui.clickgui.component.setting.GuiRenderSetting;
import vesence.utils.render.BorderRadius;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.math.animation.Direction;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;
import vesence.utils.render.text.ColorFormat;
import vesence.utils.render.text.FontRegistry;
import vesence.utils.render.utils.KeyUtil;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

@Environment(EnvType.CLIENT)
public class GuiRenderMain extends GuiScreen {
   public static void renderMain(Renderer2D renderer2D, MatrixStack pose, int mouseX, int mouseY, float mainAlpha) {
      int outlineColor = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getOutLineColor(1, 1), (int)(20.4F * mainAlpha));
      int mainColor = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), (int)(255.0F * mainAlpha));
      int mainColor6 = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), (int)(15.3F * mainAlpha));
      int mainColor40 = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), (int)(102.0F * mainAlpha));
      int textColor = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getTextColor(1, 1), (int)(255.0F * mainAlpha));
      int backGroundOneColor = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getBackGroundColor(1, 1), (int)(178.5F * mainAlpha));
      int clientColor = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), (int)(255.0F * mainAlpha));
      Color mainColorGlow35 = Renderer2D.ColorUtil.getColor(Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), (int)(56.0F * mainAlpha)));

      float groupX = GuiScreen.x;
      float groupY = GuiScreen.y;
      int panels = GuiScreen.categories.length;
      float pw = panelWidth;
      float ph = panelHeight;
      float pg = panelGap;

      float contentH = ph - panelHeaderH;

      for (Category cat : GuiScreen.categories) {
         CategorySettingsState state = GuiScreen.getCategorySettingsState(cat);
         state.rotateY.update();
         state.catTitleAlpha.update();
         state.catTitleY.update();
         state.catIconAlpha.update();
         state.catIconX.update();
         state.modTitleAlpha.update();
         state.modTitleY.update();
         state.modContentAlpha.update();
         state.modContentX.update();
         state.setContentX.update();
         state.setContentAlpha.update();
      }

      for (int p = 0; p < panels; p++) {
         Category category = GuiScreen.categories[p];
         float panelX = groupX + p * (pw + pg);
         float panelY = groupY;

         Animation2 panelAlphaAnim = GuiScreen.getPanelScaleAnimation(category);
         panelAlphaAnim.update();

         float delay = GuiScreen.getPanelAnimationDelay(category);
         long currentTime = System.currentTimeMillis();
         long elapsedTime = currentTime - GuiScreen.animationStartTime;
         float timeSinceDelay = (elapsedTime - delay * 1000) / 1000.0f;

         if (timeSinceDelay >= 0 && panelAlphaAnim.getDuration() == 0.0) {
            if (GuiScreen.isOpening) {
               panelAlphaAnim.run(1.0, 0.4, Easings.CUBIC_OUT);
            } else {
               panelAlphaAnim.run(0.0, 0.6, Easings.CUBIC_OUT);
            }
         }

         float panelAnimValue = (float) panelAlphaAnim.getValue();
         if (panelAnimValue < 0.01f) continue;

         float panelAlpha = mainAlpha * panelAnimValue;
         float panelYOffset = (1.0f - panelAnimValue) * 20.0f;
         float animatedPanelY = panelY + panelYOffset;

         int scaledOutlineColor = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getOutLineColor(1, 1), (int)(20.4F * panelAlpha));
         int scaledMainColor = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), (int)(255.0F * panelAlpha));
         int scaledMainColor6 = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), (int)(15.3F * panelAlpha));
         int scaledMainColor40 = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), (int)(102.0F * panelAlpha));
         int scaledTextColor = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getTextColor(1, 1), (int)(255.0F * panelAlpha));
         int scaledClientColor = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), (int)(255.0F * panelAlpha));

         renderCategoryPanel(renderer2D, pose, mouseX, mouseY, category, panelX, animatedPanelY, pw, ph, contentH, panelAlpha, scaledOutlineColor, scaledMainColor, scaledMainColor6, scaledMainColor40, scaledTextColor, scaledClientColor);
      }

      if (GuiScreen.activeColorPicker != null && GuiScreen.activeColorPicker instanceof HueSetting) {
         GuiRenderColorPicker.renderColorPickerWindow(
                 renderer2D,
                 (HueSetting)GuiScreen.activeColorPicker,
                 mouseX,
                 mouseY,
                 ColorUtil.multAlpha(outlineColor, (float)GuiScreen.animation15.getOutput()),
                 ColorUtil.multAlpha(backGroundOneColor, (float)GuiScreen.animation15.getOutput()),
                 ColorUtil.multAlpha(mainColor40, (float)GuiScreen.animation15.getOutput()),
                 mainAlpha * (float)GuiScreen.animation15.getOutput()
         );
      }

      if (GuiScreen.activeModeSetting != null && GuiScreen.activeModeSetting.opened) {
         GuiRenderModePopup.renderModePopup(renderer2D, mouseX, mouseY, mainAlpha);
      }
   }

   public static boolean isHovered(float mouseX, float mouseY, float x, float y, float width, float height) {
      return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
   }

   private static void renderCategoryPanelModulesOnly(Renderer2D renderer2D, MatrixStack pose, int mouseX, int mouseY, Category category, float panelX, float panelY, float pw, float ph, float contentH, float panelAlpha, int scaledOutlineColor, int scaledMainColor, int scaledMainColor6, int scaledMainColor40, int scaledTextColor, int scaledClientColor, float modAlpha, float modXOffset, CategorySettingsState catState, boolean renderSettings) {
      float contentAreaY = panelY + panelHeaderH + 13;
      float visibleContentH = contentH - 17;
      renderer2D.pushRoundedClipRect(panelX, contentAreaY, pw, visibleContentH, 11, 11, 11, 11);

      List<Module> catModules = getSortedModules(category, renderer2D);

      float settingsContentWMeasure = pw - 20F;
      int moduleCount = catModules.size();
      List<List<Setting<?>>> moduleSettingsList = new ArrayList<>(moduleCount);
      float[] moduleSettingsHeights = new float[moduleCount];

      float measureY = 7.0F;
      for (int mi = 0; mi < moduleCount; mi++) {
         Module moduleMeasure = catModules.get(mi);
         List<Setting<?>> settings = moduleMeasure.getSettingsForGUI();
         moduleSettingsList.add(settings);
         float mSettingsH = 0.0F;
         for (Setting<?> setting : settings) {
            mSettingsH += GuiRenderSetting.getSettingHeight(renderer2D, setting, settingsContentWMeasure) + 1.0F;
         }
         moduleSettingsHeights[mi] = mSettingsH;
         float mExpand = (float) GuiScreen.getModuleSettingsAnimation(moduleMeasure).getValue();
         float extraH = 22.5F * mExpand;
         float mCardH = moduleHeight + mSettingsH * mExpand + extraH;
         measureY += mCardH + cardGap;
      }
      measureY += 7.0F;

      boolean hovered = isHovered(mouseX, mouseY, panelX, contentAreaY, pw, visibleContentH);
      vesence.utils.math.ScrollUtil panelScroll = GuiScreen.getPanelScrollUtil(category);
      panelScroll.setSpeed(25);
      panelScroll.setMax(measureY, visibleContentH);
      boolean shouldEnable = measureY > visibleContentH;
      panelScroll.setEnabled(shouldEnable);
      panelScroll.update();
      float scrollOffset = panelScroll.getScroll();

      float rawYCursor = panelHeaderH + 7 + scrollOffset;
      float settingsContentW = pw - 20;

      int moduleIndex = 0;
      for (Module module : catModules) {
         float settingsFullH = moduleSettingsHeights[moduleIndex];
         List<Setting<?>> moduleSettings = moduleSettingsList.get(moduleIndex);

         if (renderSettings) {
            GuiScreen.getModuleSettingsAnimation(module).update();
            GuiScreen.getModuleSettingsAlphaAnimation(module).update();
            boolean isOpen = GuiScreen.openSettingsModules.contains(module) || module.open || catState.openedModule == module;
            GuiScreen.getModuleSettingsAnimation(module).run(isOpen ? 1.0 : 0.0, 0.4F, Easings.QUINT_OUT);
            GuiScreen.getModuleSettingsAlphaAnimation(module).run(isOpen ? 1.0 : 0.0, 0.24F, Easings.CUBIC_OUT);
         }
         float expand = (float) GuiScreen.getModuleSettingsAnimation(module).getValue();
         float expandAlpha = renderSettings ? (float) GuiScreen.getModuleSettingsAlphaAnimation(module).getValue() : 1.0f;
         float expandedH = settingsFullH * expand;
         float extraH = 22.5F * expand;
         float cardH = moduleHeight + expandedH + extraH;

         String animKey = category.name() + "_" + module.name;
         Animation2 yAnim = GuiScreen.getModuleYAnimation(animKey);
         yAnim.update();
         yAnim.run(rawYCursor, 0.25, Easings.CUBIC_OUT);
         float yCursor = (float) yAnim.get();

         boolean isSettingsMode = catState.openedModule != null;

         float staggerYOff = (!isSettingsMode && GuiScreen.isStaggerYActive)
                 ? GuiScreen.computeStaggerOffset(moduleIndex, GuiScreen.STAGGER_Y_OFFSET, false)
                 : 0.0F;

         float effectiveModXOffset = modXOffset;
         float staggerAlpha = 1.0F;

         if (!isSettingsMode && catState.staggerXActive) {
            effectiveModXOffset = GuiScreen.computeStaggerOffset(moduleIndex, GuiScreen.STAGGER_X_OFFSET, true, catState.staggerXStartTime, catState.staggerXOpening);
            staggerAlpha = GuiScreen.computeStaggerAlpha(moduleIndex, catState.staggerXStartTime, catState.staggerXOpening);
         }
         if (!isSettingsMode && GuiScreen.isStaggerYActive) {
            staggerAlpha = Math.min(staggerAlpha, GuiScreen.computeStaggerAlpha(moduleIndex));
         }
         float effectiveModAlpha = modAlpha * staggerAlpha;

         float modY = panelY + yCursor + 5 + staggerYOff;
         if (staggerAlpha <= 0.0F || modY + cardH < contentAreaY || modY > contentAreaY + visibleContentH) {
            rawYCursor += cardH + cardGap;
            moduleIndex++;
            continue;
         }

         module.animation.update();
         module.animation.run(module.enable ? 1.0 : 0.0, 0.3F, Easings.CUBIC_BOTH, true);
         module.animation1.setDirection(module.enable ? Direction.FORWARDS : Direction.BACKWARDS);
         float animPC = module.animation.get();

         float headerY = modY + moduleCardInnerPadding;
         float nameY = headerY;
         float rectY = nameY - panelAlpha * 35 + 35;

         boolean hasSettings = !moduleSettings.isEmpty();

         boolean isModuleHovered = isHovered(mouseX, mouseY, panelX + 4 + effectiveModXOffset, rectY, pw - 8, moduleHeight);
         boolean isModuleSettingsHover = hasSettings && isModuleHovered;

         module.textScaleAnim.update();
         module.textScaleAnim.run(isModuleHovered ? 0.9 : 1.0, 0.3F, Easings.QUAD_OUT, true);
         float textScale = (float) module.textScaleAnim.getValue();

         float settingsIconX = panelX + pw - (moduleHeight) - 4 + effectiveModXOffset;
         boolean isSettingsIconHovered = hasSettings && isHovered(mouseX, mouseY, settingsIconX, rectY, moduleHeight, moduleHeight);

         module.settingsIconHoverAnim.update();
         module.settingsIconHoverAnim.run(isModuleSettingsHover ? 1.0 : 0.0, 0.2F, Easings.CUBIC_OUT);
         float settingsIconHoverValue = (float) module.settingsIconHoverAnim.getValue();

         int rectColor = ColorUtil.overCol(ColorUtil.getColor(255, (int)(7.65 * panelAlpha * effectiveModAlpha)),
                 ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), (int)(20.4 * panelAlpha * effectiveModAlpha)), animPC);

         int OutlineColor = ColorUtil.overCol(ColorUtil.getColor(255, (int)(5.1 * panelAlpha * effectiveModAlpha)),
                 ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), (int)(10.2 * panelAlpha * effectiveModAlpha)), animPC);

         int rectColor2 = ColorUtil.overCol(ColorUtil.getColor(255, (int)(0 * panelAlpha * effectiveModAlpha)),
                 ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), (int)(255 * panelAlpha * effectiveModAlpha)), animPC);

         renderer2D.rect(panelX + 8 + effectiveModXOffset, rectY, pw - 16, cardH, 3, rectColor);
         renderer2D.rectOutline(panelX + 8 + effectiveModXOffset, rectY, pw - 16, cardH + 0.5F, 3, OutlineColor, 1);
         renderer2D.rect(panelX + 8 + effectiveModXOffset, rectY, 2.5F, cardH, 3, 0, 0, 3, rectColor2);

         String test = module.binding ? "Module bind: " + ColorFormat.color(255, 255, 255, (int) (38 * expandAlpha))  + "[" + ColorFormat.reset() + "NONE" + ColorFormat.color(255, 255, 255, (int) (38 * expandAlpha)) + "]" : "Module bind: " + ColorFormat.color(255, 255, 255, (int) (38 * expandAlpha))  + "[" + ColorFormat.reset() + module.bindLabel() + ColorFormat.color(255, 255, 255, (int) (38 * expandAlpha)) + "]";

         float textX = panelX + 12.5F + effectiveModXOffset + animPC * 3.5F;
         float textY = nameY + 11 - panelAlpha * 35 + 35;
         float textSize = 13;

         renderer2D.text(
                 FontRegistry.SF_MEDIUM,
                 textX,
                 textY,
                 textSize,
                 module.name,
                 ColorUtil.overCol(ColorUtil.getColor(255, (int)(89.25 * panelAlpha * effectiveModAlpha)),
                         ColorUtil.replAlpha(ColorUtil.getColor(255), (int)(255 * panelAlpha * effectiveModAlpha)), animPC));

         if (expand > 0.001F) {
            float bindBlockStartY = headerY + moduleHeaderHeight;
            float bindBlockH = 22.5F * expand;

            float bindRectPadTop = 4.5F * expand;
            float bindRectH = 13 * expand;
            float bindRectY = bindBlockStartY + bindRectPadTop;

            float bindTextY = bindRectY + bindRectH / 2 + textSize / 2 - 2;

            renderer2D.rect(panelX + 8 + effectiveModXOffset, bindRectY, pw - 16, bindRectH, 0,
                    OutlineColor);

            renderer2D.text(
                    FontRegistry.SF_MEDIUM,
                    textX,
                    bindTextY - 2,
                    textSize,
                    test,
                    ColorUtil.overCol(ColorUtil.getColor(255, (int)(89.25 * panelAlpha * effectiveModAlpha * expandAlpha)),
                            ColorUtil.replAlpha(ColorUtil.getColor(255), (int)(255 * panelAlpha * effectiveModAlpha * expandAlpha)), animPC));
         }

         if (hasSettings) {
            int settingsIconAlpha = (int)(65 * panelAlpha * effectiveModAlpha + settingsIconHoverValue * 100 * panelAlpha * effectiveModAlpha);
            renderer2D.text(
                    FontRegistry.SF_MEDIUM,
                    panelX + pw - 17.5f + effectiveModXOffset,
                    textY - 2,
                    textSize,
                    "...",
                    ColorUtil.replAlpha(-1, settingsIconAlpha),
                    -0.25f
            );
         }

         if (renderSettings && expand > 0.001F) {
            float bindBlockH = 22.5F * expand;
            float settingsStartY = headerY + moduleHeaderHeight + bindBlockH;
            float settingsX = panelX + 10 + effectiveModXOffset;
            float totalSettingsH = 0.0F;
            for (Setting<?> setting : moduleSettings) {
               float settingH = GuiRenderSetting.renderSetting(
                       renderer2D, setting, settingsX, settingsStartY + totalSettingsH,
                       settingsContentW, GuiScreen.currentMouseX, GuiScreen.currentMouseY,
                       scaledOutlineColor, scaledMainColor, scaledMainColor6, scaledMainColor40, scaledTextColor,
                       panelAlpha * expandAlpha * effectiveModAlpha
               );
               totalSettingsH += settingH;
            }
         }

         rawYCursor += cardH + cardGap;
         moduleIndex++;
      }

      renderer2D.popClipRect();
   }

   private static void renderCategoryPanel(Renderer2D renderer2D, MatrixStack pose, int mouseX, int mouseY, Category category, float panelX, float panelY, float pw, float ph, float contentH, float panelAlpha, int scaledOutlineColor, int scaledMainColor, int scaledMainColor6, int scaledMainColor40, int scaledTextColor, int scaledClientColor) {
      renderer2D.pushAlpha(panelAlpha);
      renderer2D.blurSquircle(panelX, panelY, pw, ph, 25, 5, BorderRadius.all(20), 1);
      renderer2D.drawSquircle(panelX, panelY, pw, ph, 5, BorderRadius.all(20), ColorUtil.getColor(15,155));
      String catName = category.getName();
      String catIcon = category.getIcon();
      renderer2D.pushClipRect(panelX, panelY, pw, ph);
      renderer2D.textCenter(FontRegistry.SF_MEDIUM, panelX + pw / 2f + 5, panelY + 15.5f, 18, catName, ColorUtil.replAlpha(-1, panelAlpha));
      renderer2D.textCenter(FontRegistry.ICON, panelX + pw / 2f - (renderer2D.measureText(FontRegistry.SF_MEDIUM, catName, 16).width / 2f) - 6, panelY + 16, 14.5f, catIcon, scaledClientColor);
      renderer2D.popClipRect();
      renderer2D.popAlpha();

      CategorySettingsState catStateForPanel = GuiScreen.getCategorySettingsState(category);
      renderCategoryPanelModulesOnly(renderer2D, pose, mouseX, mouseY, category, panelX, panelY, pw, ph, contentH, panelAlpha, scaledOutlineColor, scaledMainColor, scaledMainColor6, scaledMainColor40, scaledTextColor, scaledClientColor, 1.0f, 0.0f, catStateForPanel, true);
   }

   private static ClickGui cachedClickGui = null;
   private static final HashMap<Category, List<Module>> sortedModulesCache = new HashMap<>();
   private static String lastSortMode = null;

   private static ClickGui getClickGuiModule() {
      if (cachedClickGui == null) {
         cachedClickGui = (ClickGui) Vesence.get.manager.module.stream()
                 .filter(m -> m instanceof ClickGui).findFirst().orElse(null);
      }
      return cachedClickGui;
   }

   private static List<Module> getSortedModules(Category category, Renderer2D renderer2D) {
      String currentSortMode = ClickGui.sort.is("По длине") ? "length" : "alpha";
      boolean sortChanged = !currentSortMode.equals(lastSortMode);
      if (sortChanged) {
         sortedModulesCache.clear();
         lastSortMode = currentSortMode;
      }

      List<Module> cached = sortedModulesCache.get(category);
      if (cached != null) return cached;

      List<Module> list = new ArrayList<>(Vesence.get.manager.getType(category));
      if ("length".equals(currentSortMode)) {
         list.sort(Comparator.comparingDouble((Module m) -> {
            return renderer2D.measureText(FontRegistry.SF_MEDIUM, m.name, 14f).width;
         }).reversed());
      } else {
         list.sort(Comparator.comparing(m -> m.name));
      }
      sortedModulesCache.put(category, list);
      return list;
   }

   public static void invalidateCache() {
      sortedModulesCache.clear();
      lastSortMode = null;
      cachedClickGui = null;
   }
}
