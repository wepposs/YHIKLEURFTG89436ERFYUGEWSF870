package vesence.ui.clickgui.compact;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.module.api.Category;
import vesence.module.api.Module;
import vesence.utils.math.ScrollUtil;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;

import java.util.*;

@Environment(EnvType.CLIENT)
public class CompactGuiScreen {

    public static final float GUI_W = 400;
    public static final float GUI_H = 270;
    public static final float ROUNDING = 8;

    public static final float LEFT_PANEL_W = 95;
    public static final float LEFT_PANEL_GAP = 4;
    public static final float LEFT_SEARCH_H = 18;
    public static final float LEFT_CATEGORY_H = 17;
    public static final float LEFT_CATEGORY_GAP = 4;
    public static final float LEFT_CATEGORY_ICON_SIZE = 12;
    public static final float LEFT_CATEGORY_ICON_FONT = 12;
    public static final float LEFT_CATEGORY_NAME_FONT = 10;
    public static final float GROUP_HEADER_H = 16;
    public static final float OTHER_GROUP_GAP = 8;
    /** Зазор-отбивка между категориями и вкладками Theme/Client. */
    public static final float TAB_SECTION_GAP = 6;
    public static final float LEFT_PADDING = 3;

    public static final float RIGHT_PANEL_W = GUI_W - LEFT_PANEL_W - LEFT_PANEL_GAP * 2;
    public static final float MODULE_COL_GAP = 4;
    public static final float MODULE_ROW_GAP = 4;
    public static final float MODULE_CARD_MIN_H = 32;
    public static final float MODULE_CARD_PADDING = 5;
    public static final float MODULE_NAME_FONT = 13;
    public static final float MODULE_DESC_FONT = 11;
    public static final float MODULE_BIND_FONT = 9;
    public static final float MODULE_DIVIDER_GAP = 3;

    public static final float SEARCH_W = LEFT_PANEL_W - LEFT_PADDING * 2 + 4;
    public static final float SEARCH_FONT = 12;

    public static final float BRAND_H = 18;
    public static final float BRAND_FONT = 8;

    public static final float TOOLTIP_W = 140;
    public static final float TOOLTIP_MAX_H = 60;
    public static final float TOOLTIP_FONT_SIZE = 9;
    public static final float TOOLTIP_PAD = 4;

    public static float x, y;
    public static Category selectedCategory = Category.COMBAT;
    public static Module selectedModule = null;
    public static String searchText = "";
    public static boolean searchActive = false;
    /** Флаг "выделить всё" (Ctrl+A) для строки поиска compact-меню. */
    public static boolean searchSelectAll = false;
    public static int currentMouseX, currentMouseY;

    public static double guiScale = 1.0;
    public static double guiScaleTarget = 1.0;
    public static final Animation2 guiScaleAnim = new Animation2();
    private static boolean guiScaleInit = false;

    public static void setGuiScaleTarget(double target) {
        target = Math.max(0.0001, target);
        if (!guiScaleInit) {
            guiScaleInit = true;
            guiScaleTarget = target;
            guiScale = target;
            guiScaleAnim.set(target);
            return;
        }
        if (Math.abs(target - guiScaleTarget) > 1.0E-6) {
            guiScaleTarget = target;
            guiScaleAnim.run(target, 0.3, vesence.utils.render.math.animation.anim.util.Easings.CUBIC_OUT);
        }
        guiScaleAnim.update();
        guiScale = guiScaleAnim.get();
    }

    public static vesence.module.api.setting.impl.SliderSetting scrollCapturedSlider = null;
    public static long scrollCaptureTime = 0L;
    public static final long SCROLL_CAPTURE_WINDOW_MS = 400L;

    private static boolean scaleFrozen = false;
    private static double frozenScale = 1.0;

    public static void freezeScale() {
        if (!scaleFrozen) {
            scaleFrozen = true;
            frozenScale = guiScale;
        }
    }

    public static void unfreezeScale() {
        scaleFrozen = false;
    }

    private static double activeScale() {
        return Math.max(0.0001, scaleFrozen ? frozenScale : guiScale);
    }

    public static float toGuiX(double rawX) {
        float cx = x + GUI_W / 2f;
        float s = (float) activeScale();
        return cx + (float) ((rawX - cx) / s);
    }

    public static float toGuiY(double rawY) {
        float cy = y + GUI_H / 2f;
        float s = (float) activeScale();
        return cy + (float) ((rawY - cy) / s);
    }

    public static boolean themeSelected = false;
    public static final Animation2 themeHoverAnim = new Animation2();
    public static final Animation2 themeSelectAnim = new Animation2();
    public static final Map<vesence.module.Theme, Animation2> themeCellAnims = new HashMap<>();

    public static boolean clientSelected = false;
    public static final Animation2 clientHoverAnim = new Animation2();
    public static final Animation2 clientSelectAnim = new Animation2();
    public static final ScrollUtil clientScroll = new ScrollUtil();



    public static Animation2 getThemeCellAnim(vesence.module.Theme t) {
        return themeCellAnims.computeIfAbsent(t, k -> new Animation2());
    }

    public static float getThemeYOffset() {
        return getCategoriesEndYOffset() + TAB_SECTION_GAP;
    }

    public static float getClientYOffset() {
        return getThemeYOffset() + LEFT_CATEGORY_H + LEFT_CATEGORY_GAP;
    }



    public static void selectThemeTab() {
        if (themeSelected && pendingCategory == null && !pendingThemeTab && !pendingClientTab) return;
        categoryIndicatorY.run(getThemeYOffset(), 0.25, Easings.CUBIC_OUT);
        if (moduleFadingOut) {
            pendingThemeTab = true;
            pendingClientTab = false;
            pendingCategory = null;
            return;
        }
        if (themeSelected) return;

        pendingThemeTab = true;
        pendingClientTab = false;
        pendingCategory = null;
        moduleFadingOut = true;
        moduleListAlpha.set(1.0);
        moduleListAlpha.run(0.0, 0.25, Easings.BACK_OUT, true);
        categorySwitchAnim.set(0.0);
        categorySwitchAnim.run(1.0, 0.3, Easings.CUBIC_OUT);
        moduleScroll.reset();
    }

    public static void selectClientTab() {
        if (clientSelected && pendingCategory == null && !pendingThemeTab && !pendingClientTab) return;
        categoryIndicatorY.run(getClientYOffset(), 0.25, Easings.CUBIC_OUT);
        if (moduleFadingOut) {
            pendingClientTab = true;
            pendingThemeTab = false;
            pendingCategory = null;
            return;
        }
        if (clientSelected) return;

        pendingClientTab = true;
        pendingThemeTab = false;
        pendingCategory = null;
        moduleFadingOut = true;
        moduleListAlpha.set(1.0);
        moduleListAlpha.run(0.0, 0.25, Easings.BACK_OUT, true);
        categorySwitchAnim.set(0.0);
        categorySwitchAnim.run(1.0, 0.3, Easings.CUBIC_OUT);
        clientScroll.reset();
    }



    public static final Animation2 openAnim = new Animation2();
    public static final Animation2 openAnimY = new Animation2();
    public static final Animation2 categorySwitchAnim = new Animation2();
    public static final Animation2 moduleListAnim = new Animation2();
    public static final Animation2 searchAnim = new Animation2();

    public static final Animation2 categoryIndicatorY = new Animation2();

    public static final Map<Category, Animation2> categoryHoverAnims = new HashMap<>();
    public static final Map<Category, Animation2> categorySelectAnims = new HashMap<>();

    public static final Map<Module, Animation2> moduleEnableAnims = new HashMap<>();
    public static final Map<Module, Animation2> moduleHoverAnims = new HashMap<>();

    public static final ScrollUtil moduleScroll = new ScrollUtil();

    public static Module hoveredModule = null;
    public static final Animation2 tooltipAnim = new Animation2();
    public static float tooltipDelay = 0f;
    public static final float TOOLTIP_DELAY_SEC = 0.4f;

    public static final Animation2 scrollBarAlpha = new Animation2();

    public static long moduleStaggerStart = 0;
    public static boolean moduleStaggerActive = false;

    public static final Animation2 moduleListAlpha = new Animation2();
    private static boolean moduleFadingOut = false;
    private static Category pendingCategory = null;

    private static boolean pendingThemeTab = false;
    private static boolean pendingClientTab = false;


    public static int getCategoryIndex(Category cat) {
        Category[] cats = Category.values();
        for (int i = 0; i < cats.length; i++) {
            if (cats[i] == cat) return i;
        }
        return 0;
    }

    /**
     * Две группы с заголовками: «BASICS» (Combat..Misc) и «OTHER»
     * (Display и вкладки ниже). Между группами — заголовок и зазор.
     */
    public static float getCategoryYOffset(int index) {
        float y = GROUP_HEADER_H + index * (LEFT_CATEGORY_H + LEFT_CATEGORY_GAP);
        int displayIdx = getCategoryIndex(Category.DISPLAY);
        if (index >= displayIdx) {
            y += GROUP_HEADER_H + OTHER_GROUP_GAP;
        }
        return y;
    }

    /** Смещение сразу за последней категорией — конец блока категорий. */
    public static float getCategoriesEndYOffset() {
        Category[] cats = Category.values();
        return getCategoryYOffset(cats.length - 1) + LEFT_CATEGORY_H + LEFT_CATEGORY_GAP;
    }

    public static void animateCategoryIndicator(Category cat) {
        categoryIndicatorY.run(getCategoryYOffset(getCategoryIndex(cat)), 0.25, Easings.CUBIC_OUT);
    }

    public static boolean isCategoryVisuallySelected(Category cat) {
        if (pendingThemeTab || pendingClientTab) return false;
        if (pendingCategory != null) return cat == pendingCategory;
        if (themeSelected || clientSelected) return false;
        return cat == selectedCategory;
    }

    public static boolean isThemeVisuallySelected() {
        if (pendingThemeTab) return true;
        if (pendingClientTab || pendingCategory != null) return false;
        return themeSelected;
    }

    public static boolean isClientVisuallySelected() {
        if (pendingClientTab) return true;
        if (pendingThemeTab || pendingCategory != null) return false;
        return clientSelected;
    }



    public static Animation2 getCategoryHoverAnim(Category cat) {
        return categoryHoverAnims.computeIfAbsent(cat, k -> new Animation2());
    }

    public static Animation2 getCategorySelectAnim(Category cat) {
        return categorySelectAnims.computeIfAbsent(cat, k -> {
            Animation2 a = new Animation2();
            if (cat == selectedCategory) a.set(1.0);
            return a;
        });
    }

    public static Animation2 getModuleEnableAnim(Module mod) {
        return moduleEnableAnims.computeIfAbsent(mod, k -> {
            Animation2 a = new Animation2();
            a.set(mod.enable ? 1.0 : 0.0);
            return a;
        });
    }

    public static Animation2 getModuleHoverAnim(Module mod) {
        return moduleHoverAnims.computeIfAbsent(mod, k -> new Animation2());
    }

    public static void init() {
        openAnim.set(0.0);
        openAnim.run(1.0, 0.25, Easings.SINE_OUT);
        openAnimY.set(0.0);
        openAnimY.run(1.0, 0.35, Easings.QUAD_OUT);
        categorySwitchAnim.set(1.0);
        categoryIndicatorY.set(getCategoryYOffset(getCategoryIndex(selectedCategory)));
        moduleListAnim.set(1.0);
        searchAnim.set(searchActive ? 1.0 : 0.0);
        moduleStaggerStart = System.currentTimeMillis();
        moduleStaggerActive = true;
        moduleListAlpha.set(1.0);

    }

    public static void selectCategory(Category cat) {
        if (themeSelected || pendingThemeTab || clientSelected || pendingClientTab) {

            animateCategoryIndicator(cat);
            if (moduleFadingOut) {
                pendingCategory = cat;
                pendingThemeTab = false;
                pendingClientTab = false;
                return;
            }
            pendingCategory = cat;
            pendingThemeTab = false;
            pendingClientTab = false;
            moduleFadingOut = true;
            moduleListAlpha.set(1.0);
            moduleListAlpha.run(0.0, 0.25, Easings.BACK_OUT, true);
            categorySwitchAnim.set(0.0);
            categorySwitchAnim.run(1.0, 0.3, Easings.CUBIC_OUT);
            moduleScroll.reset();
            return;
        }
        if (cat == selectedCategory && pendingCategory == null) return;
        animateCategoryIndicator(cat);
        if (moduleFadingOut) {
            pendingCategory = cat;
            return;
        }
        if (cat == selectedCategory) return;
        pendingCategory = cat;
        moduleFadingOut = true;
        moduleListAlpha.set(1.0);
        moduleListAlpha.run(0.0, 0.25, Easings.BACK_OUT, true);
        categorySwitchAnim.set(0.0);
        categorySwitchAnim.run(1.0, 0.3, Easings.CUBIC_OUT);
        moduleStaggerStart = System.currentTimeMillis();
        moduleStaggerActive = true;
        moduleScroll.reset();
    }

    public static void updateModuleListTransition() {
        moduleListAlpha.update();
        if (moduleFadingOut && (float) moduleListAlpha.getValue() < 0.02) {
            moduleFadingOut = false;
            if (pendingThemeTab) {
                themeSelected = true;
                clientSelected = false;
                pendingThemeTab = false;
            } else if (pendingClientTab) {
                clientSelected = true;
                themeSelected = false;
                pendingClientTab = false;
            } else if (pendingCategory != null) {
                themeSelected = false;
                clientSelected = false;
                selectedCategory = pendingCategory;
            }
            pendingCategory = null;
            selectedModule = null;
            moduleListAlpha.set(0.0);
            moduleListAlpha.run(1.0, 0.25, Easings.BACK_OUT, true);
            moduleStaggerStart = System.currentTimeMillis();
            moduleStaggerActive = true;
        }
    }

    public static boolean isModuleFadingOut() {
        return moduleFadingOut;
    }

    public static void selectModule(Module mod) {
        if (mod == selectedModule) {
            selectedModule = null;
            return;
        }
        selectedModule = mod;
    }

    public static float computeStaggerOffset(int index, float maxOffset, long startTime) {
        long delay = index * 25L;
        long elapsed = System.currentTimeMillis() - startTime - delay;
        if (elapsed < 0) return maxOffset;
        float progress = Math.min(1.0f, (float) elapsed / 200L);
        float eased = 1.0f - (1.0f - progress) * (1.0f - progress);
        return maxOffset * (1.0f - eased);
    }

    public static float computeStaggerAlpha(int index, long startTime) {
        long delay = index * 25L;
        long elapsed = System.currentTimeMillis() - startTime - delay;
        if (elapsed < 0) return 0.0f;
        float progress = Math.min(1.0f, (float) elapsed / 200L);
        float eased = 1.0f - (1.0f - progress) * (1.0f - progress);
        return eased;
    }

    public static List<Module> getModulesForCategory(Category cat) {
        List<Module> list = new ArrayList<>(vesence.Vesence.get.manager.getType(cat));

        list.removeIf(m -> m.hiddenFromGui);
        list.removeIf(m -> m instanceof vesence.module.impl.visuals.Hud);
        list.removeIf(m -> m instanceof vesence.module.impl.visuals.ThemeModule);
        if (!searchText.isEmpty()) {
            String lower = searchText.toLowerCase();
            list.removeIf(m -> !m.name.toLowerCase().contains(lower));
        }
        sortModules(list);
        return list;
    }

    public static List<Module> getAllModulesForSearch() {
        List<Module> all = new ArrayList<>();
        for (Category cat : Category.values()) {
            all.addAll(vesence.Vesence.get.manager.getType(cat));
        }

        all.removeIf(m -> m.hiddenFromGui);
        all.removeIf(m -> m instanceof vesence.module.impl.visuals.Hud);
        all.removeIf(m -> m instanceof vesence.module.impl.visuals.ThemeModule);
        if (!searchText.isEmpty()) {
            String lower = searchText.toLowerCase();
            all.removeIf(m -> !m.name.toLowerCase().contains(lower));
        }
        sortModules(all);
        return all;
    }

    private static void sortModules(List<Module> list) {
        if (vesence.module.impl.misc.ClickGui.sort.is("По длине")) {
            list.sort(Comparator.comparingInt((Module m) -> m.name.length()).thenComparing(m -> m.name));
        } else {
            list.sort(Comparator.comparing(m -> m.name));
        }
    }
}
