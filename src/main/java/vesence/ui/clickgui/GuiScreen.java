package vesence.ui.clickgui;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import vesence.module.api.Category;
import vesence.module.api.Module;
import vesence.module.Theme;
import vesence.module.api.setting.impl.BindSettings;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.HueSetting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.module.api.setting.impl.StringSetting;
import vesence.utils.math.ScrollUtil;
import vesence.utils.render.math.animation.Animation;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim2.Easing;
import vesence.utils.render.math.animation.impl.EaseInOutQuad;

@Environment(EnvType.CLIENT)
public class GuiScreen {
   public static MinecraftClient mc = MinecraftClient.getInstance();
   public static Animation mainAnimation = new EaseInOutQuad(200, 1.0);
   public static Animation categoryAnimation = new EaseInOutQuad(500, 1.0);
   public static Animation moduleAnimation = new EaseInOutQuad(500, 1.0);
   public static Animation animation15 = new EaseInOutQuad(1000, 1.0);
   public static vesence.utils.render.math.animation.anim2.Animation alpha = new vesence.utils.render.math.animation.anim2.Animation(
           Easing.EASE_OUT_SINE, 1500L
   );
   public static vesence.utils.render.math.animation.anim.util.Animation2 animation = new vesence.utils.render.math.animation.anim.util.Animation2();
   public static Animation2 alphaPC = new Animation2();
   public static Animation2 categoryIndicatorOffset = new Animation2();
   public static Animation2 settingPC = new Animation2();
   public static Animation2 alphaPC2 = new Animation2();
   public static Animation2 alphaPC3 = new Animation2();
   public static HueSetting activeColorPicker = null;
   public static float colorPickerX = 0.0F;

   public static float colorPickerY = 0.0F;
   public static boolean pickingSaturationBrightness = false;
   public static boolean pickingHue = false;
   public static boolean pickingAlpha = false;
   public static HueSetting activeHueSetting = null;
   public static float huePickerX = 0.0F;
   public static float huePickerY = 0.0F;
   public static float huePickerWidth = 0.0F;
   public static BindSettings activeBindSetting = null;
   public static StringSetting activeStringSetting = null;
   public static SliderSetting activeSliderSetting = null;

   public static SliderSetting editingSliderSetting = null;
   public static vesence.module.api.setting.impl.RangeSliderSetting activeRangeSetting = null;
   public static ModeSetting activeModeSetting = null;
   public static float modePopupX = 0.0F;
   public static float modePopupY = 0.0F;
   public static Module activeModuleBind = null;
   public static float sliderX = 0.0F;
   public static float sliderY = 0.0F;
   public static float sliderWidth = 0.0F;
   public static String searchText = "";
   public static boolean activeSearch = false;
   /**
    * Флаг "выделить всё" (Ctrl+A) для текстовых полей, обслуживаемых классическими
    * обработчиками: classic-поиск, StringSetting и числовой буфер слайдера. Одно
    * поле в фокусе за раз, поэтому одного флага достаточно. Compact-поиск использует
    * собственный {@code CompactGuiScreen.searchSelectAll}.
    */
   public static boolean textSelectAll = false;
   public static float searchWidth = 124.04F;
   public static float searchHeight = 21.325F;
   public static float searchYOffset = 6.185F;
   public static float searchAnchorX = 338.555F;
   public static float searchGapRight = 5.0F;
   public static float searchIconInsetX = 7.985F;
   public static float searchTextInsetX = 22.89F;
   public static float searchIconYOffset = 21.085F;
   public static float searchTextYOffset = 19.195F;
   public static long lastBackspaceTime = 0L;
   public static boolean backspaceHeld = false;
   public static long firstBackspacePressTime = 0L;
   public static boolean showClientSettingsPopup = false;
   public static BooleanSetting clientBlurSetting = new BooleanSetting("Blur?", true);
   public static boolean exit = false;
   public static float x;
   public static float y;
   public static float width;
   public static float height;

   public static final float panelWidth = 105;
   public static final float panelHeight = 210;
   public static final float moduleHeight = 16;
   public static final float panelGap = 6;
   public static final float panelHeaderH = 8;
   public static final float panelRadius = 6.5F;
   public static final float moduleHeaderHeight = 13;
   public static final float moduleCardInnerPadding = 2.0F;
   public static final float cardGap = 5;
   public static int currentMouseX = 0;
   public static int currentMouseY = 0;
   public static Category[] categories;
   public static Category selectedCategories;
   public static List<Module> modules;
   public static Module selectedModule;
   private static ScrollUtil scrollUtil;
   private static ScrollUtil settingsScrollUtil;
   public static Set<Module> openSettingsModules = new HashSet<>();
   public static Map<Module, Animation2> moduleSettingsAnimations = new HashMap<>();
   public static Map<Module, Animation2> moduleSettingsAlphaAnimations = new HashMap<>();
   public static Map<Module, Animation2> moduleBindAnimations = new HashMap<>();
   public static Map<SliderSetting, Animation2> sliderAnimations = new HashMap<>();
   public static Map<Category, ScrollUtil> panelScrolls = new HashMap<>();

   public static Map<Category, Animation2> panelScaleAnimations = new HashMap<>();
   public static Map<Category, Float> panelAnimationDelays = new HashMap<>();
   public static boolean isOpening = true;
   public static long animationStartTime = 0L;

   public static class CategorySettingsState {
      public Module openedModule = null;
      public Module lastOpenedModule = null;
      public Animation2 rotateY = new Animation2();
      public Animation2 catTitleAlpha = new Animation2();
      public Animation2 catTitleY = new Animation2();
      public Animation2 catIconAlpha = new Animation2();
      public Animation2 catIconX = new Animation2();
      public Animation2 modTitleAlpha = new Animation2();
      public Animation2 modTitleY = new Animation2();
      public Animation2 modContentAlpha = new Animation2();
      public Animation2 modContentX = new Animation2();
      public Animation2 setContentX = new Animation2();
      public Animation2 setContentAlpha = new Animation2();
      public long staggerXStartTime = 0L;
      public boolean staggerXActive = false;
      public boolean staggerXOpening = false;
      public long settingsStaggerStartTime = 0L;
      public boolean settingsStaggerActive = false;
      public boolean settingsStaggerOpening = false;

      public CategorySettingsState() {
         rotateY.set(-90.0);
         catTitleAlpha.set(1.0);
         catTitleY.set(0.0);
         catIconAlpha.set(1.0);
         catIconX.set(0.0);
         modTitleAlpha.set(0.0);
         modTitleY.set(10.0);
         modContentAlpha.set(1.0);
         modContentX.set(0.0);
         setContentX.set(20.0);
         setContentAlpha.set(0.0);
      }

      public boolean shouldShowSettings() {
         if (openedModule != null) return true;
         return rotateY.get() > -89.0 || setContentAlpha.get() > 0.01;
      }

      public boolean isAnimatingOut() {
         return openedModule == null && shouldShowSettings();
      }
   }

   public static Map<Category, CategorySettingsState> categorySettingsStates = new HashMap<>();
   public static LinkedList<Category> settingsOpenOrder = new LinkedList<>();

   public static CategorySettingsState getCategorySettingsState(Category category) {
      return categorySettingsStates.computeIfAbsent(category, k -> new CategorySettingsState());
   }

   public static Module openedModuleSettings = null;
   public static Category openedModuleCategory = null;
   public static Category lastAnimatedCategory = null;
   public static Module lastOpenedModule = null;
   public static Animation2 moduleSettingsPanelRotateY = new Animation2();
   public static Animation2 categoryTitleAlpha = new Animation2();
   public static Animation2 categoryTitleY = new Animation2();
   public static Animation2 categoryIconAlpha = new Animation2();
   public static Animation2 categoryIconX = new Animation2();
   public static Animation2 moduleTitleAlpha = new Animation2();
   public static Animation2 moduleTitleY = new Animation2();
   public static Animation2 modulesContentAlpha = new Animation2();
   public static Animation2 modulesContentX = new Animation2();
   public static Animation2 settingsContentX = new Animation2();
   public static Animation2 settingsContentAlpha = new Animation2();
   public static Map<Category, ScrollUtil> moduleSettingsPanelScrolls = new HashMap<>();
   public static Map<Module, Animation2> moduleNoSettingsFlash = new HashMap<>();
   public static Map<String, Animation2> moduleYAnimations = new HashMap<>();
   public static long moduleStaggerStartTime = 0L;
   public static boolean isStaggerYActive = false;
   public static final long STAGGER_DELAY_MS = 35L;
   public static final float STAGGER_Y_OFFSET = 20.0F;
   public static final float STAGGER_X_OFFSET = 15.0F;
   public static final long STAGGER_ANIM_DURATION_MS = 250L;

   public static Animation2 getModuleYAnimation(String key) {
      return moduleYAnimations.computeIfAbsent(key, k -> new Animation2());
   }

   public static float computeStaggerOffset(int moduleIndex, float maxOffset, boolean negative, long startTime, boolean reverse) {
      long delay = moduleIndex * STAGGER_DELAY_MS;
      long elapsed = System.currentTimeMillis() - startTime - delay;
      if (elapsed < 0) return reverse ? 0.0F : (negative ? -maxOffset : maxOffset);
      float progress = Math.min(1.0F, (float) elapsed / STAGGER_ANIM_DURATION_MS);
      float eased = 1.0F - (1.0F - progress) * (1.0F - progress) * (1.0F - progress);
      if (reverse) {
         float offset = maxOffset * eased;
         return negative ? -offset : offset;
      } else {
         float offset = maxOffset * (1.0F - eased);
         return negative ? -offset : offset;
      }
   }

   public static float computeStaggerAlpha(int moduleIndex, long startTime, boolean reverse) {
      long delay = moduleIndex * STAGGER_DELAY_MS;
      long elapsed = System.currentTimeMillis() - startTime - delay;
      if (elapsed < 0) return reverse ? 1.0F : 0.0F;
      float progress = Math.min(1.0F, (float) elapsed / STAGGER_ANIM_DURATION_MS);
      float eased = 1.0F - (1.0F - progress) * (1.0F - progress) * (1.0F - progress);
      return reverse ? (1.0F - eased) : eased;
   }

   public static float computeStaggerOffset(int moduleIndex, float maxOffset, boolean negative) {
      return computeStaggerOffset(moduleIndex, maxOffset, negative, moduleStaggerStartTime, false);
   }

   public static float computeStaggerAlpha(int moduleIndex) {
      return computeStaggerAlpha(moduleIndex, moduleStaggerStartTime, false);
   }

   public static Animation2 getModuleNoSettingsFlash(Module module) {
      return moduleNoSettingsFlash.computeIfAbsent(module, k -> new Animation2());
   }

   public static ScrollUtil getScrollUtil() {
      if (scrollUtil == null) {
         scrollUtil = new ScrollUtil();
      }

      return scrollUtil;
   }

   public static ScrollUtil getSettingsScrollUtil() {
      if (settingsScrollUtil == null) {
         settingsScrollUtil = new ScrollUtil();
      }
      return settingsScrollUtil;
   }

   public static ScrollUtil getModuleSettingsPanelScroll(Category category) {
      return moduleSettingsPanelScrolls.computeIfAbsent(category, k -> new ScrollUtil());
   }

   public static ScrollUtil getPanelScrollUtil(Category category) {
      return panelScrolls.computeIfAbsent(category, k -> new ScrollUtil());
   }

   public static Animation2 getModuleSettingsAnimation(Module module) {
      return moduleSettingsAnimations.computeIfAbsent(module, k -> new Animation2());
   }

   public static Animation2 getModuleSettingsAlphaAnimation(Module module) {
      return moduleSettingsAlphaAnimations.computeIfAbsent(module, k -> new Animation2());
   }

   public static Animation2 getModuleBindAnimation(Module module) {
      Animation2 anim = moduleBindAnimations.computeIfAbsent(module, k -> new Animation2());
      if (module.bind != -1 && anim.getDuration() == 0.0 && anim.getValue() == 0.0) {
         anim.setValue(1.0);
      }

      return anim;
   }

   public static Animation2 getSliderAnimation(SliderSetting slider) {
      return sliderAnimations.computeIfAbsent(slider, k -> {
         Animation2 newAnim = new Animation2();
         float targetProgress = (float) ((slider.current - slider.minimum) / (slider.maximum - slider.minimum));
         newAnim.setValue(targetProgress);
         return newAnim;
      });
   }

   public static Animation2 getPanelScaleAnimation(Category category) {
      return panelScaleAnimations.computeIfAbsent(category, k -> new Animation2());
   }

   public static float getPanelAnimationDelay(Category category) {
      if (!panelAnimationDelays.containsKey(category)) {
         int totalPanels = categories.length;
         int panelIndex = -1;
         for (int i = 0; i < categories.length; i++) {
            if (categories[i] == category) {
               panelIndex = i;
               break;
            }
         }

         if (panelIndex != -1) {
            float centerIndex = (totalPanels - 1) / 2.0f;
            float distanceFromCenter = Math.abs(panelIndex - centerIndex);
            float delay = distanceFromCenter * 0.1f;
            panelAnimationDelays.put(category, delay);
         } else {
            panelAnimationDelays.put(category, 0.0f);
         }
      }
      return panelAnimationDelays.get(category);
   }

   public static float getGlobalScaleAnimation() {
      if (categories == null || categories.length == 0) {
         return 1.0f;
      }

      float totalScale = 0.0f;
      for (Category category : categories) {
         Animation2 anim = getPanelScaleAnimation(category);
         totalScale += (float) anim.getValue();
      }

      return totalScale / categories.length;
   }
}
