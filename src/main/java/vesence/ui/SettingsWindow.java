package vesence.ui;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.cfg.ConfigManager;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;
import vesence.utils.math.ScrollUtil;
import vesence.utils.render.text.FontRegistry;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

@Environment(EnvType.CLIENT)
public class SettingsWindow {

    private static final float WINDOW_MIN_W = 420;
    private static final float WINDOW_MIN_H = 280;
    private static final float RESIZE_HANDLE = 6f;
    private static final float SIDEBAR_W = 165;
    private static final float SIDEBAR_TAB_H = 45;
    private static final float SIDEBAR_TAB_Y_START = 7;
    private static final float INNER_PAD = 9;
    private static final float IOS_PADDING = 16f;
    private static final float HEADER_H = 45f;
    private static final float SETTING_ROW_H = 40f;
    private static final float SETTING_OPTION_H = 32f;
    private static final float START_SETTING_Y = -12;
    private static final float SETTING_GAP = -5;
    private static final float SLIDER_ROW_H = 40f;
    private static final float SLIDER_H = 6f;
    private static final float SLIDER_HANDLE_R = 7f;

    private static final float[] MENU_SCALE_PRESETS = {0.75f, 1.0f, 1.25f, 1.5f};
    private static final String[] MENU_SCALE_LABELS = {"75%", "100%", "125%", "150%"};
    private static final int[] GUI_SCALE_PRESETS = {1, 2, 3, 4};
    private static final String[] GUI_SCALE_LABELS = {"1x", "2x", "3x", "4x"};
    private static final String[] LANG_MODES = {"RU", "ENG"};
    private static final String SETTINGS_FILE = "menu_settings.json";
    private static final String[] BG_SHADER_PATHS = {"orb_bg", "orb_bg2"};

    private static final SoundSource[] SOUND_CATS = SoundSource.values();
    private static final String[] SOUND_LABELS_RU = {
            "Общая громкость", "Музыка", "Проигрыватель", "Погода",
            "Блоки", "Враждебные", "Дружественные", "Игроки", "Окружение", "Голос", "Интерфейс"
    };
    private static final String[] SOUND_LABELS_EN = {
            "Master Volume", "Music", "Jukebox", "Weather",
            "Blocks", "Hostile", "Friendly", "Players", "Ambient", "Voice", "UI"
    };

    private boolean windowOpen = false;
    private float windowOffX = 0f, windowOffY = 0f;
    private float windowW = 750, windowH = 450;
    private boolean draggingWindow = false;
    private float dragStartMouseX = 0f, dragStartMouseY = 0f;
    private float dragStartOffX = 0f, dragStartOffY = 0f;
    private int resizingEdge = 0;
    private float resizeStartMX = 0f, resizeStartMY = 0f;
    private float resizeStartW = 0f, resizeStartH = 0f;
    private final Animation2 windowOpenAnim = new Animation2();

    private int selectedTab = 0;
    private int prevSelectedTab = 0;
    private final Animation2[] tabColorAnims = {new Animation2(), new Animation2(), new Animation2()};
    private final Animation2 tabTransitionAnim = new Animation2();

    private final Animation2 closeHoverAnim = new Animation2();
    private final Animation2 settingsIconHoverAnim = new Animation2();

    private final SettingRenderer.SelectorState menuScaleState = new SettingRenderer.SelectorState(MENU_SCALE_PRESETS.length);
    { menuScaleState.selectedIndex = 1; }
    private float menuScale = 1.0f;
    private final Animation2 scaleAnim = new Animation2();

    private final SettingRenderer.SelectorState guiScaleState = new SettingRenderer.SelectorState(GUI_SCALE_PRESETS.length);

    private final SettingRenderer.SliderState soundSliderState = new SettingRenderer.SliderState(SOUND_CATS.length);

    private final SettingRenderer.SelectorState langState = new SettingRenderer.SelectorState(LANG_MODES.length);
    private final ScrollUtil scrollUtil = new ScrollUtil();
    private boolean blurEnabled = true;
    private boolean showTime = true;
    private boolean animationsEnabled = true;
    private int selectedBgIndex = 0;
    private final Animation2 bgColorAnim = new Animation2();

    public boolean isOpen() { return windowOpen; }

    public void openFromPopup() {
        windowOpen = true;
        windowOpenAnim.run(1.0, 0.8f, Easings.QUINT_OUT);
    }

    public float getMenuScale() { return menuScale;}
    public int getSelectedLangMode() { return langState.selectedIndex; }
    public Animation2 getOpenAnim() { return windowOpenAnim; }

    public String t(String ru, String eng) {
        return langState.selectedIndex == 0 ? ru : eng;
    }

    public void toggleLanguage() {
        langState.selectedIndex = (langState.selectedIndex + 1) % LANG_MODES.length;
        saveSettings();
    }

    public void reset() {
        windowOpenAnim.set(0.0);
        windowOpen = false;
        draggingWindow = false;
        resizingEdge = 0;
        windowOffX = 0f;
        windowOffY = 0f;
        windowW = 750;
        windowH = 450;
        selectedTab = 0;
        tabColorAnims[0].set(1.0);
        tabColorAnims[1].set(0.0);
        tabColorAnims[2].set(0.0);
        tabTransitionAnim.set(1.0);
        scaleAnim.set(menuScale);
        menuScaleState.expanded = false;
        menuScaleState.expandAnim.set(0.0);
        guiScaleState.expanded = false;
        guiScaleState.expandAnim.set(0.0);
        langState.expanded = false;
        langState.expandAnim.set(0.0);
        loadSettings();
    }

    public void renderWindow(Renderer2D renderer, int sw, int sh, float lmx, float lmy, float centerX) {
        windowOpenAnim.update();
        float wT = (float) windowOpenAnim.get();
        bgColorAnim.update();
        float bgColorT = (float) bgColorAnim.get();

        if (wT <= 0.001f) return;

        float wAlpha = (float) Easings.CUBIC_OUT.ease(Math.min(wT, 1.0));
        float wSlideY = (1f - (float) Easings.QUINT_OUT.ease(Math.min(wT, 1.0))) * 30f;
        float wScale = 0.88f + 0.12f * (float) Easings.EXPO_OUT.ease(Math.min(wT, 1.0));

        float wCenterX = centerX + windowOffX;
        float wCenterY = sh / 2f + windowOffY;
        float wX = wCenterX - windowW / 2f * wScale;
        float wY = wCenterY - windowH / 2f * wScale + wSlideY;
        float wW = windowW * wScale;
        float wH = windowH * wScale;

        renderer.pushAlpha(wAlpha);

        int windowBg = ColorUtil.getColor(25);
        renderer.shadow(wX, wY, wW, wH, 8, 12, 3, ColorUtil.replAlpha(ColorUtil.getColor(255), 5));
        renderer.rect(wX, wY, wW, wH, 8, windowBg);

        float pad = INNER_PAD;
        float barH = HEADER_H;
        float headerX = wX + pad;
        float headerY = wY + pad;
        float headerW = wW - pad * 2;
        int headerBg = ColorUtil.getColor(255, 10);
        renderer.rect(headerX, headerY, headerW, barH, 5, headerBg);
        renderer.rect(headerX, headerY, barH, barH, 5,0,0,5, headerBg);
        renderer.rect(headerX + headerW - barH, headerY, barH, barH, 0,5,5,0, headerBg);

        float closeBtnSize = 28f;
        float closeBtnX = headerX + 12f;
        float closeBtnY = headerY + barH / 2f;
        boolean closeHovered = lmx >= closeBtnX - closeBtnSize / 2f && lmx <= closeBtnX + closeBtnSize / 2f
                && lmy >= closeBtnY - closeBtnSize / 2f && lmy <= closeBtnY + closeBtnSize / 2f;
        closeHoverAnim.update();
        closeHoverAnim.run(closeHovered ? 1.0 : 0.0, 0.25, Easings.CUBIC_OUT);
        float closeHT = (float) closeHoverAnim.get();
        int closeColor = ColorUtil.overCol(ColorUtil.replAlpha(-1, 120), ColorUtil.replAlpha(-1, 255), closeHT);
        renderer.text(FontRegistry.ICON, closeBtnX + 2, closeBtnY + 10, 40, "w", closeColor);

        float settingsIconX = headerX + headerW - 12f;
        float settingsIconY = headerY + barH / 2f;
        boolean settingsHovered = lmx >= settingsIconX - closeBtnSize / 2f && lmx <= settingsIconX + closeBtnSize / 2f
                && lmy >= settingsIconY - closeBtnSize / 2f && lmy <= settingsIconY + closeBtnSize / 2f;
        settingsIconHoverAnim.update();
        settingsIconHoverAnim.run(settingsHovered ? 1.0 : 0.0, 0.25, Easings.CUBIC_OUT);
        float settingsHT = (float) settingsIconHoverAnim.get();
        int settingsColor = ColorUtil.overCol(ColorUtil.replAlpha(-1, 120), ColorUtil.replAlpha(-1, 255), settingsHT);
        renderer.textRight(FontRegistry.ICON, settingsIconX - 1, settingsIconY + 10, 35, "n", settingsColor);

        float sideW = SIDEBAR_W;
        float sideX = wX + pad;
        float sideY = headerY + barH + pad;
        float sideH = wH - pad * 2 - barH - pad;
        int sideBg = ColorUtil.getColor(255, 10);
        renderer.rect(sideX, sideY, sideW, sideH, 5, sideBg);

        String[] tabNames = {t("Экран", "Display"), t("Звук", "Sound"), t("Оформление", "Appearance")};
        String[] tabIcons = {"f", "j", "c"};
        float tabYStart = sideY + SIDEBAR_TAB_Y_START;
        float tabH = SIDEBAR_TAB_H;
        float tabStepY = tabH + 9f;

        for (int t2 = 0; t2 < tabNames.length; t2++) {
            tabColorAnims[t2].update();
        }

        for (int t2 = 0; t2 < tabNames.length; t2++) {
            float tY = tabYStart + t2 * tabStepY;
            float tProgress = (float) tabColorAnims[t2].get();
            int tabTextSel = ColorUtil.getColor(230, 230, 230);
            int tabTextUnsel = ColorUtil.getColor(150, 150, 155);
            int lightColor = ColorUtil.overCol(ColorUtil.getColor(0, 15), ColorUtil.getColor(0, 35), tProgress);
            int darkColor = ColorUtil.overCol(ColorUtil.getColor(255, 10), ColorUtil.getColor(255, 25), tProgress);
            renderer.rect(sideX + 6, tY, sideW - 12, tabH, 5, ColorUtil.overCol(lightColor, darkColor, 1.0f));
            renderer.text(FontRegistry.SF_MEDIUM, sideX + 20, tY + tabH / 2f - 5f + 12, 24, tabNames[t2],
                    ColorUtil.overCol(tabTextUnsel, tabTextSel, tProgress));
            renderer.textRight(FontRegistry.ICON, sideX + sideW - 20, tY + tabH / 2f - 5f + 16, 30, tabIcons[t2],
                    ColorUtil.overCol(tabTextUnsel, tabTextSel, tProgress));
        }

        float contentX = sideX + sideW + pad;
        float contentY = sideY;
        float contentW = wW - pad * 2 - sideW - pad;
        float contentH = sideH;

        int accentLight = ColorUtil.overCol(ColorUtil.getColor(165,255,165), ColorUtil.getColor(180,160,255), bgColorT);
        int accentDark = ColorUtil.overCol(ColorUtil.getColor(80,180,80), ColorUtil.getColor(140,120,210), bgColorT);
        int accentColor = accentDark;

        int titleColor = ColorUtil.getColor(230,230,230);
        String[] tabTitles = {t("Экран / Display", "Экран / Display"), t("Звук / Sound", "Звук / Sound"), t("Оформление / Appearance", "Оформление / Appearance")};
        renderer.text(FontRegistry.SF_MEDIUM, contentX + IOS_PADDING, contentY + 25, 37, tabTitles[selectedTab], titleColor);

        float titleAreaH = 42f;
        float scrollY = contentY + titleAreaH;
        float scrollH = contentH - titleAreaH ;

        renderer.pushClipRect(contentX, scrollY - 10, contentW, scrollH + 10);

        tabTransitionAnim.update();
        float tT = (float) tabTransitionAnim.get();

        float contentHeight = getContentHeight(selectedTab);
        scrollUtil.setMax(contentHeight, scrollH);
        scrollUtil.setEnabled(contentHeight > scrollH);
        scrollUtil.update();
        float scrollOffset = scrollUtil.getScroll();

        renderer.getTransformStack().pushTranslation(0f, scrollOffset);

        if (tT > 0.01f && tT < 0.99f) {
            float p = (float) Easings.CUBIC_OUT.ease(tT);
            renderer.pushAlpha(p);
            renderer.getTransformStack().pushTranslation(0f, (1f - p) * 20f);
            renderTabContent(renderer, selectedTab, contentX, scrollY, contentW, scrollH, accentColor, lmx, lmy - scrollOffset);
            renderer.getTransformStack().pop();
            renderer.popAlpha();
        } else {
            renderTabContent(renderer, selectedTab, contentX, scrollY, contentW, scrollH, accentColor, lmx, lmy - scrollOffset);
        }

        renderer.getTransformStack().pop();

        renderer.popClipRect();

        if (contentHeight > scrollH) {
            scrollUtil.render(renderer, contentX + contentW - 4, scrollY, 3, scrollH, 1f);
        }

        if (draggingWindow) {
            windowOffX = dragStartOffX + (lmx - dragStartMouseX);
            windowOffY = dragStartOffY + (lmy - dragStartMouseY);
        }

        scaleAnim.update();
        menuScale = (float) scaleAnim.get();

        if (resizingEdge != 0) {
            float dx = lmx - resizeStartMX;
            float dy = lmy - resizeStartMY;
            if (resizingEdge == 2 || resizingEdge == 6) windowH = Math.max(WINDOW_MIN_H, resizeStartH + dy);
            if (resizingEdge == 3 || resizingEdge == 6) windowW = Math.max(WINDOW_MIN_W, resizeStartW + dx);
        }

        if (wT >= 0.95f) {
            int edgeColor = ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), 40);
            float h = RESIZE_HANDLE * 3;
            if (lmy >= wY + wH - h && lmy <= wY + wH && lmx >= wX && lmx <= wX + wW)
                renderer.rect(wX, wY + wH - RESIZE_HANDLE, wW, RESIZE_HANDLE, 0, edgeColor);
            if (lmx >= wX + wW - h && lmx <= wX + wW && lmy >= wY && lmy <= wY + wH)
                renderer.rect(wX + wW - RESIZE_HANDLE, wY, RESIZE_HANDLE, wH, 0, edgeColor);
        }

        renderer.popAlpha();
    }

    public boolean clickWindow(float lx, float ly, int fbW, int fbH) {
        if (!windowOpen) return false;
        float wT2 = (float) windowOpenAnim.get();
        if (wT2 < 0.95f) return false;

        float cX = fbW / 2f;
        float wX2 = cX + windowOffX - windowW / 2f;
        float wY2 = fbH / 2f + windowOffY - windowH / 2f;
        float wW2 = windowW;
        float wH2 = windowH;
        float pad2 = INNER_PAD;

        float barH = HEADER_H;
        float headerX2 = wX2 + pad2;
        float headerY2 = wY2 + pad2;
        float headerW2 = wW2 - pad2 * 2;

        float closeBtnSize = 28f;
        float closeBtnX = headerX2 + 12f;
        float closeBtnY = headerY2 + barH / 2f;
        if (lx >= closeBtnX - closeBtnSize / 2f && lx <= closeBtnX + closeBtnSize / 2f
                && ly >= closeBtnY - closeBtnSize / 2f && ly <= closeBtnY + closeBtnSize / 2f) {
            windowOpen = false;
            windowOpenAnim.run(0.0, 0.4, Easings.CUBIC_IN);
            return true;
        }

        if (ly >= headerY2 && ly <= headerY2 + barH && lx >= headerX2 && lx <= headerX2 + headerW2) {
            draggingWindow = true;
            dragStartMouseX = lx;
            dragStartMouseY = ly;
            dragStartOffX = windowOffX;
            dragStartOffY = windowOffY;
            return true;
        }

        float sideX2 = wX2 + pad2;
        float sideY2 = headerY2 + barH + pad2;
        float sideW2 = SIDEBAR_W;
        float tabYStart2 = sideY2 + SIDEBAR_TAB_Y_START;
        float tabH2 = SIDEBAR_TAB_H;
        float tabStepY2 = tabH2 + 9f;
        if (lx >= sideX2 && lx <= sideX2 + sideW2 && ly >= tabYStart2) {
            for (int t = 0; t < 3; t++) {
                float tY = tabYStart2 + t * tabStepY2;
                if (ly >= tY && ly <= tY + tabH2) {
                    if (selectedTab != t) {
                        prevSelectedTab = selectedTab;
                        selectedTab = t;
                        scrollUtil.reset();
                        tabColorAnims[t].run(1.0, 0.35, Easings.CUBIC_OUT);
                        for (int o = 0; o < 3; o++) { if (o != t) tabColorAnims[o].run(0.0, 0.35, Easings.CUBIC_OUT); }
                        tabTransitionAnim.set(0.0);
                        tabTransitionAnim.run(1.0, 1, Easings.QUINT_OUT);
                        menuScaleState.expanded = false;
                        menuScaleState.expandAnim.run(0.0, 0.25, Easings.CUBIC_IN);
                        guiScaleState.expanded = false;
                        guiScaleState.expandAnim.run(0.0, 0.25, Easings.CUBIC_IN);
                        langState.expanded = false;
                        langState.expandAnim.run(0.0, 0.2, Easings.CUBIC_IN);
                    }
                    return true;
                }
            }
        }

        float contX2 = sideX2 + sideW2 + pad2;
        float contY2 = sideY2;
        float contW2 = wW2 - pad2 * 2 - sideW2 - pad2;
        float contH2 = wH2 - pad2 * 2 - barH - pad2;

        float titleAreaH2 = 45f;
        float scrollY2 = contY2 + titleAreaH2;
        float scrollH2 = contH2 - titleAreaH2;

        if (lx >= contX2 && lx <= contX2 + contW2 && ly >= scrollY2 && ly <= scrollY2 + scrollH2) {
            float sly = ly - scrollUtil.getScroll();
            if (selectedTab == 0) {
                float rowY = scrollY2 + START_SETTING_Y;
                float msSelW = 80f;
                float msSelBaseH = SETTING_ROW_H - 8f;
                float msSelX = contX2 + contW2 - IOS_PADDING - msSelW;
                float msSelY = rowY + 4f;
                float msExpandH = MENU_SCALE_PRESETS.length * SETTING_OPTION_H * (float) menuScaleState.expandAnim.get();
                if (SettingRenderer.clickSelector(lx, sly, msSelX, msSelY, msSelW, msSelBaseH, msExpandH,
                        menuScaleState, MENU_SCALE_LABELS, () -> {
                            menuScale = MENU_SCALE_PRESETS[menuScaleState.selectedIndex];
                            scaleAnim.run(menuScale, 0.35, Easings.CUBIC_OUT);
                            guiScaleState.expanded = false;
                            guiScaleState.expandAnim.run(0.0, 0.25, Easings.CUBIC_IN);
                            saveSettings();
                        })) return true;

                rowY += SETTING_ROW_H + MENU_SCALE_PRESETS.length * SETTING_OPTION_H * (float) menuScaleState.expandAnim.get() + SETTING_GAP;

                float gsSelW = 80f;
                float gsSelBaseH = SETTING_ROW_H - 8f;
                float gsSelX = contX2 + contW2 - IOS_PADDING - gsSelW;
                float gsSelY = rowY + 4f;
                float gsExpandH = GUI_SCALE_PRESETS.length * SETTING_OPTION_H * (float) guiScaleState.expandAnim.get();
                if (SettingRenderer.clickSelector(lx, sly, gsSelX, gsSelY, gsSelW, gsSelBaseH, gsExpandH,
                        guiScaleState, GUI_SCALE_LABELS, () -> {
                            Minecraft mc = Minecraft.getInstance();
                            if (mc != null) {
                                try { mc.options.guiScale().set(GUI_SCALE_PRESETS[guiScaleState.selectedIndex]); mc.resizeGui(); } catch (Exception ignored) {}
                            }
                            menuScaleState.expanded = false;
                            menuScaleState.expandAnim.run(0.0, 0.25, Easings.CUBIC_IN);
                            saveSettings();
                        })) return true;
            }

            if (selectedTab == 1) {
                float rowY = scrollY2 + START_SETTING_Y;
                for (int i = 0; i < SOUND_CATS.length; i++) {
                    float sRowY = rowY + i * SLIDER_ROW_H;
                    if (sRowY + SLIDER_ROW_H > scrollY2 + scrollH2) break;
                    if (SettingRenderer.clickSlider(lx, sly, contX2, contW2, sRowY, i, soundSliderState)) return true;
                }
            }

            if (selectedTab == 2) {
                float rowY = scrollY2 + START_SETTING_Y;
                float langSelW = 80f;
                float langSelBaseH = SETTING_ROW_H - 8f;
                float langSelExpandH = LANG_MODES.length * SETTING_OPTION_H * (float) langState.expandAnim.get();
                float langSelX = contX2 + contW2 - IOS_PADDING - langSelW;
                float langSelY = rowY + 4f;
                if (SettingRenderer.clickSelector(lx, sly, langSelX, langSelY, langSelW, langSelBaseH, langSelExpandH,
                        langState, LANG_MODES, () -> saveSettings())) return true;
            }

        }

        float rh = RESIZE_HANDLE * 3;
        boolean nearBot = ly >= wY2 + wH2 - rh && ly <= wY2 + wH2;
        boolean nearRgt = lx >= wX2 + wW2 - rh && lx <= wX2 + wW2;
        boolean inWindowX = lx >= wX2 && lx <= wX2 + wW2;
        boolean inWindowY = ly >= wY2 && ly <= wY2 + wH2;
        int edge = 0;
        if (nearBot && nearRgt) edge = 6;
        else if (nearBot && inWindowX) edge = 2;
        else if (nearRgt && inWindowY) edge = 3;
        if (edge != 0) {
            resizingEdge = edge;
            resizeStartMX = lx;
            resizeStartMY = ly;
            resizeStartW = windowW;
            resizeStartH = windowH;
            return true;
        }

        return false;
    }

    public void release(float lx, float ly, int fbW, int fbH) {
        if (windowOpen && !draggingWindow && resizingEdge == 0) {
            float cX = fbW / 2f;
            float wT3 = (float) windowOpenAnim.get();
            if (wT3 >= 0.95f) {
                float wX3 = cX + windowOffX - windowW / 2f;
                float wY3 = fbH / 2f + windowOffY - windowH / 2f;
                float wW3 = windowW;
                float wH3 = windowH;
                if (!(lx >= wX3 && lx <= wX3 + wW3 && ly >= wY3 && ly <= wY3 + wH3)) {
                    windowOpen = false;
                    windowOpenAnim.run(0.0, 0.4, Easings.CUBIC_IN);
                }
            }
        }
        draggingWindow = false;
        resizingEdge = 0;
        if (soundSliderState.dragIndex >= 0) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                try { mc.options.save(); } catch (Exception ignored) {}
            }
            soundSliderState.dragIndex = -1;
        }
    }

    public boolean keyPressed(int key) {
        if (key == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE && windowOpen) {
            windowOpen = false;
            windowOpenAnim.run(0.0, 0.4, Easings.CUBIC_IN);
            return true;
        }
        return false;
    }

    public boolean shouldCloseOnEsc() {
        if (windowOpen) {
            windowOpen = false;
            windowOpenAnim.run(0.0, 0.4, Easings.CUBIC_IN);
            return false;
        }
        return true;
    }

    public void scrollWindow(double delta) {
        if (!windowOpen) return;
        scrollUtil.handleScroll(delta);
    }

    private float getContentHeight(int tab) {
        if (tab == 0) {
            float msExpandT = (float) menuScaleState.expandAnim.get();
            float h = START_SETTING_Y + SETTING_ROW_H + MENU_SCALE_PRESETS.length * SETTING_OPTION_H * msExpandT + SETTING_GAP;
            float gsExpandT = (float) guiScaleState.expandAnim.get();
            h += SETTING_ROW_H + GUI_SCALE_PRESETS.length * SETTING_OPTION_H * gsExpandT;
            return h;
        }
        if (tab == 1) {
            return START_SETTING_Y + SOUND_CATS.length * SLIDER_ROW_H;
        }
        if (tab == 2) {
            float langExpandT = (float) langState.expandAnim.get();
            return START_SETTING_Y + SETTING_ROW_H + LANG_MODES.length * SETTING_OPTION_H * langExpandT;
        }
        return 0f;
    }

    private void renderTabContent(Renderer2D renderer, int tab, float cX, float cY, float cW, float cH, int accentColor, float lmx, float lmy) {
        float rowY = cY + START_SETTING_Y;

        if (tab == 0) {
            float msH = SettingRenderer.renderSelector(renderer, cX, rowY, cW,
                    t("Масштаб Main screen", "Main screen scale"), MENU_SCALE_LABELS,
                    menuScaleState, accentColor, lmx, lmy);
            rowY += msH + SETTING_GAP;

            SettingRenderer.renderSelector(renderer, cX, rowY, cW,
                    t("Масштаб интерфейса", "GUI scale"), GUI_SCALE_LABELS,
                    guiScaleState, accentColor, lmx, lmy);
        }

        if (tab == 1) {
            for (int i = 0; i < SOUND_CATS.length; i++) {
                float sRowY = rowY + i * SLIDER_ROW_H;
                if (sRowY + SLIDER_ROW_H > cY + cH) break;
                String sLabel = langState.selectedIndex == 0 ? SOUND_LABELS_RU[i] : SOUND_LABELS_EN[i];
                if (i >= SOUND_LABELS_RU.length) sLabel = SOUND_CATS[i].getName();
                SettingRenderer.renderSlider(renderer, cX, sRowY, cW, sLabel, soundSliderState.volumeCache[i], i,
                        soundSliderState, accentColor, lmx, lmy);
            }
        }

        if (tab == 2) {
            SettingRenderer.renderSelector(renderer, cX, rowY, cW,
                    t("Язык меню", "Menu language"), LANG_MODES,
                    langState, accentColor, lmx, lmy);
        }
    }

    private void saveSettings() {
        try {
            JsonObject obj = new JsonObject();
            obj.addProperty("menuScaleIndex", menuScaleState.selectedIndex);
            obj.addProperty("guiScaleIndex", guiScaleState.selectedIndex);
            obj.addProperty("blurEnabled", blurEnabled);
            obj.addProperty("showTime", showTime);
            obj.addProperty("animationsEnabled", animationsEnabled);
            obj.addProperty("bgIndex", selectedBgIndex);
            obj.addProperty("selectedLangMode", langState.selectedIndex);
            File f = new File(ConfigManager.configDirectory, SETTINGS_FILE);
            f.getParentFile().mkdirs();
            try (FileWriter w = new FileWriter(f, java.nio.charset.StandardCharsets.UTF_8)) {
                w.write(new GsonBuilder().setPrettyPrinting().create().toJson(obj));
            }
        } catch (Exception ignored) {}
    }

    private void loadSettings() {
        try {
            File f = new File(ConfigManager.configDirectory, SETTINGS_FILE);
            if (!f.exists()) return;
            try (FileReader r = new FileReader(f, java.nio.charset.StandardCharsets.UTF_8)) {
                JsonObject obj = JsonParser.parseReader(r).getAsJsonObject();
                if (obj.has("menuScaleIndex")) {
                    menuScaleState.selectedIndex = Math.max(0, Math.min(MENU_SCALE_PRESETS.length - 1, obj.get("menuScaleIndex").getAsInt()));
                    menuScale = MENU_SCALE_PRESETS[menuScaleState.selectedIndex];
                    scaleAnim.set(menuScale);
                }
                if (obj.has("guiScaleIndex")) {
                    guiScaleState.selectedIndex = Math.max(0, Math.min(GUI_SCALE_PRESETS.length - 1, obj.get("guiScaleIndex").getAsInt()));
                }
                if (obj.has("blurEnabled")) blurEnabled = obj.get("blurEnabled").getAsBoolean();
                if (obj.has("showTime")) showTime = obj.get("showTime").getAsBoolean();
                if (obj.has("animationsEnabled")) animationsEnabled = obj.get("animationsEnabled").getAsBoolean();
                if (obj.has("bgIndex")) selectedBgIndex = Math.max(0, Math.min(BG_SHADER_PATHS.length - 1, obj.get("bgIndex").getAsInt()));
                if (obj.has("selectedLangMode")) langState.selectedIndex = Math.max(0, Math.min(LANG_MODES.length - 1, obj.get("selectedLangMode").getAsInt()));
            }
        } catch (Exception ignored) {}
    }
}
