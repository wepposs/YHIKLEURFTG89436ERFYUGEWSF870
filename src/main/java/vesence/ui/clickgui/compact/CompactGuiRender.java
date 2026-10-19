package vesence.ui.clickgui.compact;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import vesence.Vesence;
import vesence.module.api.Category;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.impl.misc.ClickGui;
import vesence.renderengine.render.Renderer2D;
import vesence.ui.clickgui.GuiScreen;
import vesence.ui.clickgui.component.render.GuiRenderModePopup;
import vesence.ui.clickgui.compact.setting.CompactGuiRenderSetting;
import vesence.utils.render.BorderRadius;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.text.FontRegistry;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Environment(EnvType.CLIENT)
public class CompactGuiRender {

    private static final int TEXT_COLOR = ColorUtil.getColor(255, 255, 255, 220);
    private static final int SUBTEXT_COLOR = ColorUtil.getColor(180, 180, 180, 180);
    private static final int DIVIDER_COLOR = ColorUtil.getColor(255, 255, 255, 20);
    private static final int SEARCH_BG = ColorUtil.getColor(255, 255, 255, 12);
    private static final int SEARCH_ACTIVE_BG = ColorUtil.getColor(255, 255, 255, 22);
    private static final int LEFT_PANEL_BG = ColorUtil.getColor(15, 255);
    private static final int RIGHT_PANEL_BG = ColorUtil.getColor(15, 255);

    private static final float CPAD = 4;
    private static final int COLS = 2;

    private static final int THEME_COLS = 3;
    private static final float THEME_CELL_H = 26f;
    private static final float THEME_CELL_GAP = 6f;
    private static final float THEME_ROW_GAP = 6f;

    static float[] themeCellRect(int i, float cX, float cY, float cW, float scrollOff) {
        float cellW = (cW - THEME_CELL_GAP * (THEME_COLS - 1)) / THEME_COLS;
        int col = i % THEME_COLS;
        int row = i / THEME_COLS;
        float cellX = cX + col * (cellW + THEME_CELL_GAP);
        float cellY = cY + row * (THEME_CELL_H + THEME_ROW_GAP) + scrollOff;
        return new float[]{cellX, cellY, cellW, THEME_CELL_H};
    }

    public static List<Module> getCurrentModules() {
        List<Module> base = !CompactGuiScreen.searchText.isEmpty()
                ? CompactGuiScreen.getAllModulesForSearch()
                : CompactGuiScreen.getModulesForCategory(CompactGuiScreen.selectedCategory);

        if (CompactModuleWindow.anyOpen()) {
            List<Module> out = new ArrayList<>(base);
            out.removeIf(CompactModuleWindow::isExtracted);
            return out;
        }

        return base;
    }

    private static final java.util.Map<Module, float[]> CARD_POS = new java.util.HashMap<>();
    private static final java.util.Set<Module> lastSeenModules = new java.util.HashSet<>();

    private static float[] getRightPanelBounds(float gx, float gy, float gh) {
        float px = gx + CompactGuiScreen.LEFT_PANEL_W + CompactGuiScreen.LEFT_PANEL_GAP * 2;
        float py = gy + CompactGuiScreen.LEFT_PANEL_GAP;
        float pw = CompactGuiScreen.RIGHT_PANEL_W;
        float ph = gh - CompactGuiScreen.LEFT_PANEL_GAP * 2;
        return new float[]{px, py, pw, ph};
    }

    private static float getContentWidth(float panelW) {
        return panelW - CPAD * 2;
    }

    private static float getColumnWidth(float contentW) {
        return (contentW - CompactGuiScreen.MODULE_COL_GAP) / COLS;
    }

    public static float getSettingHeight(Renderer2D renderer, Setting<?> setting, float width) {
        return CompactGuiRenderSetting.getSettingHeight(renderer, setting, width);
    }

    public static void render(Renderer2D renderer, int mouseX, int mouseY, float mainAlpha) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client == null) {
            return;
        }

        float sw = client.getWindow().getScaledWidth();
        float sh = client.getWindow().getScaledHeight();

        CompactGuiScreen.setGuiScaleTarget(
                vesence.module.impl.misc.ClickGui.guiScale.get()
        );

        CompactGuiScreen.x = sw / 2f - CompactGuiScreen.GUI_W / 2f;
        CompactGuiScreen.y = sh / 2f - CompactGuiScreen.GUI_H / 2f;

        CompactGuiScreen.currentMouseX =
                (int) CompactGuiScreen.toGuiX(mouseX);

        CompactGuiScreen.currentMouseY =
                (int) CompactGuiScreen.toGuiY(mouseY);

        CompactGuiScreen.openAnim.update();
        CompactGuiScreen.openAnimY.update();
        CompactGuiScreen.categorySwitchAnim.update();
        CompactGuiScreen.categoryIndicatorY.update();
        CompactGuiScreen.searchAnim.update();
        CompactGuiScreen.updateModuleListTransition();

        float openVal =
                (float) CompactGuiScreen.openAnim.getValue();

        float openValY =
                (float) CompactGuiScreen.openAnimY.getValue();

        if (openVal < 0.01f || openValY < 0.01f) {
            return;
        }

        float alpha = mainAlpha * openVal;

        float gx = CompactGuiScreen.x;
        float gy = CompactGuiScreen.y;
        float gw = CompactGuiScreen.GUI_W;
        float gh = CompactGuiScreen.GUI_H;

        float userScale =
                (float) Math.max(
                        0.0001,
                        CompactGuiScreen.guiScale
                );

        boolean scaled =
                Math.abs(userScale - 1.0f) > 1.0E-4f;

        if (scaled) {
            renderer.pushScale(
                    userScale,
                    gx + gw / 2f,
                    gy + gh / 2f
            );
        }

        renderer.pushScale(
                alpha,
                gx + gw / 2f,
                gy + gh / 2f
        );

        if (!ClickGui.squircleGui.get()) {

            if (ClickGui.blurGui.get()) {
                renderer.blur(
                        gx,
                        gy,
                        gw,
                        gh,
                        ClickGui.blurStrengthGui.get().floatValue(),
                        ClickGui.getGuiCorner(),
                        alpha
                );
            }

            renderer.gradient(
                    gx,
                    gy,
                    gw,
                    gh,
                    ClickGui.getGuiCorner(),
                    ColorUtil.multAlpha(
                            ColorUtil.multDark(
                                    Renderer2D.ColorUtil.getClientColor1(),
                                    0.10f
                            ),
                            ClickGui.guiAlpha.get().floatValue() * alpha
                    ),
                    ColorUtil.multAlpha(
                            ColorUtil.multDark(
                                    Renderer2D.ColorUtil.getClientColor2(),
                                    0.10f
                            ),
                            ClickGui.guiAlpha.get().floatValue() * alpha
                    ),
                    ColorUtil.multAlpha(
                            ColorUtil.multDark(
                                    Renderer2D.ColorUtil.getClientColor1(),
                                    0.10f
                            ),
                            ClickGui.guiAlpha.get().floatValue() * alpha
                    ),
                    ColorUtil.multAlpha(
                            ColorUtil.multDark(
                                    Renderer2D.ColorUtil.getClientColor2(),
                                    0.10f
                            ),
                            ClickGui.guiAlpha.get().floatValue() * alpha
                    ),
                    true
            );

            renderer.gradientOutline(
                    gx,
                    gy,
                    gw,
                    gh,
                    ClickGui.getGuiCorner(),
                    ColorUtil.multAlpha(
                            ColorUtil.multDark(
                                    Renderer2D.ColorUtil.getClientColor(),
                                    1
                            ),
                            ClickGui.guiAlpha.get().floatValue() * alpha
                    ),
                    ColorUtil.multAlpha(
                            ColorUtil.multDark(
                                    Renderer2D.ColorUtil.getClientColor(),
                                    1
                            ),
                            ClickGui.guiAlpha.get().floatValue() * alpha
                    ),
                    ColorUtil.multAlpha(
                            ColorUtil.BLACK,
                            (int) (0 * alpha)
                    ),
                    ColorUtil.multAlpha(
                            ColorUtil.BLACK,
                            (int) (0 * alpha)
                    ),
                    1f,
                    true
            );

        } else {

            if (ClickGui.blurGui.get()) {
                renderer.blurSquircle(
                        gx,
                        gy,
                        gw,
                        gh,
                        ClickGui.blurStrengthGui.get().floatValue(),
                        ClickGui.getGuiSquirt(),
                        BorderRadius.all(
                                ClickGui.getGuiCorner()
                        ),
                        alpha
                );
            }

            renderer.drawSquircleGradient(
                    gx,
                    gy,
                    gw,
                    gh,
                    ClickGui.getGuiSquirt(),
                    BorderRadius.all(
                            ClickGui.getGuiCorner()
                    ),
                    ColorUtil.multAlpha(
                            ColorUtil.multDark(
                                    Renderer2D.ColorUtil.getClientColor(),
                                    0.08f
                            ),
                            ClickGui.guiAlpha.get().floatValue() * alpha
                    ),
                    ColorUtil.multAlpha(
                            ColorUtil.multDark(
                                    Renderer2D.ColorUtil.getClientColor(),
                                    0.08f
                            ),
                            ClickGui.guiAlpha.get().floatValue() * alpha
                    ),
                    ColorUtil.multAlpha(
                            ColorUtil.multDark(
                                    Renderer2D.ColorUtil.getClientColor(),
                                    0.1f
                            ),
                            ClickGui.guiAlpha.get().floatValue() * alpha
                    ),
                    ColorUtil.multAlpha(
                            ColorUtil.multDark(
                                    Renderer2D.ColorUtil.getClientColor(),
                                    0.1f
                            ),
                            ClickGui.guiAlpha.get().floatValue() * alpha
                    )
            );

            renderer.drawSquircleGradientOutline(
                    gx,
                    gy,
                    gw,
                    gh,
                    ClickGui.getGuiSquirt(),
                    BorderRadius.all(
                            ClickGui.getGuiCorner()
                    ),
                    ColorUtil.multAlpha(
                            ColorUtil.multDark(
                                    Renderer2D.ColorUtil.getClientColor(),
                                    1
                            ),
                            ClickGui.guiAlpha.get().floatValue() * alpha
                    ),
                    ColorUtil.multAlpha(
                            ColorUtil.multDark(
                                    Renderer2D.ColorUtil.getClientColor(),
                                    1
                            ),
                            ClickGui.guiAlpha.get().floatValue() * alpha
                    ),
                    ColorUtil.multAlpha(
                            ColorUtil.BLACK,
                            (int) (0 * alpha)
                    ),
                    ColorUtil.multAlpha(
                            ColorUtil.BLACK,
                            (int) (0 * alpha)
                    ),
                    2.5f
            );
        }

        renderLeftPanel(
                renderer,
                gx,
                gy,
                gh,
                alpha
        );

        renderRightPanel(
                renderer,
                gx,
                gy,
                gh,
                alpha
        );

        CompactModuleWindow.render(
                renderer,
                CompactGuiScreen.currentMouseX,
                CompactGuiScreen.currentMouseY,
                alpha
        );

        renderer.popScale();

        if (scaled) {
            renderer.popScale();
        }

        if (GuiScreen.activeModeSetting != null
                && GuiScreen.activeModeSetting.opened) {

            renderer.pushAlpha(mainAlpha);

            GuiRenderModePopup.renderModePopup(
                    renderer,
                    mouseX,
                    mouseY,
                    mainAlpha
            );

            renderer.popAlpha();
        }
    }

    private static void renderLeftPanel(
            Renderer2D renderer,
            float gx,
            float gy,
            float gh,
            float alpha
    ) {
        float px =
                gx + CompactGuiScreen.LEFT_PANEL_GAP;

        float py =
                gy + CompactGuiScreen.LEFT_PANEL_GAP + 25;

        float pw =
                CompactGuiScreen.LEFT_PANEL_W + 5;

        float ph =
                gh - CompactGuiScreen.LEFT_PANEL_GAP * 2;

        CompactGuiScreen.searchAnim.run(
                CompactGuiScreen.searchActive
                        ? 1.0
                        : 0.0,
                0.2,
                Easings.CUBIC_OUT
        );

        float sx =
                px + CompactGuiScreen.LEFT_PADDING;

        float sy =
                py + CompactGuiScreen.LEFT_PADDING;

        float sw =
                CompactGuiScreen.SEARCH_W;

        float sh =
                CompactGuiScreen.LEFT_SEARCH_H;

        int sBg =
                ColorUtil.overCol(
                        ColorUtil.getColor(
                                255,
                                (int) (7 * alpha)
                        ),
                        ColorUtil.getColor(
                                255,
                                (int) (12 * alpha)
                        ),
                        CompactGuiScreen.searchAnim.get()
                );

        int sOutline =
                ColorUtil.overCol(
                        ColorUtil.getColor(
                                255,
                                (int) (11 * alpha)
                        ),
                        ColorUtil.getColor(
                                255,
                                (int) (15 * alpha)
                        ),
                        CompactGuiScreen.searchAnim.get()
                );

        renderer.rect(
                sx,
                sy - 7,
                sw,
                sh,
                5,
                sBg
        );

        renderer.rectOutline(
                sx,
                sy - 7,
                sw,
                sh,
                5,
                sOutline,
                0.5f
        );

        renderer.text(
                FontRegistry.NUC,
                sx + sw - 13.5f,
                sy + sh / 2f + 3.5f - 7,
                14,
                "n",
                ColorUtil.overCol(
                        ColorUtil.getColor(
                                255,
                                (int) (35 * alpha)
                        ),
                        ColorUtil.getColor(
                                255,
                                (int) (75 * alpha)
                        ),
                        CompactGuiScreen.searchAnim.get()
                )
        );

        /*
         * Название клиента.
         */
        renderer.textCenter(
                FontRegistry.MONTSERRAT,
                px + pw / 2f,
                gy + 22.5f - 4.5f,
                20,
                "weppos",
                new int[]{
                        Renderer2D.ColorUtil.replAlpha(
                                Renderer2D.ColorUtil.getClientColor(),
                                (int) (255 * alpha)
                        ),
                        Renderer2D.ColorUtil.replAlpha(
                                Renderer2D.ColorUtil.getClientColor(),
                                (int) (200 * alpha)
                        )
                },
                2
        );

        renderer.pushClipRect(
                sx,
                sy - 7,
                sw - 19,
                sh
        );

        String displayText =
                CompactGuiScreen.searchText.isEmpty()
                        && !CompactGuiScreen.searchActive
                        ? "Search for modules..."
                        : CompactGuiScreen.searchText;

        int sTextCol =
                ColorUtil.overCol(
                        ColorUtil.getColor(
                                255,
                                (int) (125 * alpha)
                        ),
                        ColorUtil.getColor(
                                255,
                                (int) (255 * alpha)
                        ),
                        CompactGuiScreen.searchAnim.get()
                );

        if (CompactGuiScreen.searchActive
                && CompactGuiScreen.searchSelectAll
                && !CompactGuiScreen.searchText.isEmpty()) {

            float selW =
                    renderer.measureText(
                            FontRegistry.MONTSERRAT,
                            CompactGuiScreen.searchText,
                            CompactGuiScreen.SEARCH_FONT
                    ).width;

            renderer.rect(
                    sx + 4,
                    sy + sh / 2f - 7 - 5.5f,
                    selW + 2,
                    12,
                    2,
                    ColorUtil.replAlpha(
                            Renderer2D.ColorUtil.getClientColor(),
                            (int) (70 * alpha)
                    )
            );
        }

        renderer.text(
                FontRegistry.MONTSERRAT,
                sx + 5,
                sy + sh / 2f + 3 - 7,
                CompactGuiScreen.SEARCH_FONT,
                displayText,
                sTextCol
        );

        if (CompactGuiScreen.searchActive
                && (System.currentTimeMillis() / 500) % 2 == 0) {

            float cx =
                    sx
                            + 5
                            + renderer.measureText(
                            FontRegistry.MONTSERRAT,
                            CompactGuiScreen.searchText,
                            CompactGuiScreen.SEARCH_FONT
                    ).width;

            renderer.text(
                    FontRegistry.MONTSERRAT,
                    cx,
                    sy + sh / 2f + 2.5f - 7,
                    8,
                    "|",
                    ColorUtil.replAlpha(
                            TEXT_COLOR,
                            (int) (255 * alpha)
                    )
            );
        }

        renderer.popClipRect();

        float catStartY =
                sy + sh + 2.5f;

        Category[] cats =
                Category.values();

        float indicatorY =
                catStartY
                        + (float) CompactGuiScreen.categoryIndicatorY.getValue();

        int indicatorBg =
                ColorUtil.replAlpha(
                        Renderer2D.ColorUtil.getClientColor1(),
                        (int) (20 * alpha)
                );

        int indicatorBg2 =
                ColorUtil.replAlpha(
                        Renderer2D.ColorUtil.getClientColor2(),
                        (int) (20 * alpha)
                );

        int indicatorOutline =
                ColorUtil.replAlpha(
                        Renderer2D.ColorUtil.getClientColor1(),
                        (int) (20 * alpha)
                );

        int indicatorOutline2 =
                ColorUtil.replAlpha(
                        Renderer2D.ColorUtil.getClientColor2(),
                        (int) (20 * alpha)
                );

        // Два блока с заголовками СНАРУЖИ рамок: BASICS (категории) и
        // OTHER (Display + вкладки).
        int displayIdx = CompactGuiScreen.getCategoryIndex(Category.DISPLAY);

        float basicsTop =
                catStartY
                        + CompactGuiScreen.GROUP_HEADER_H
                        - 4.5f;
        float basicsBottom =
                catStartY
                        + CompactGuiScreen.GROUP_HEADER_H
                        + displayIdx * (CompactGuiScreen.LEFT_CATEGORY_H + CompactGuiScreen.LEFT_CATEGORY_GAP)
                        - CompactGuiScreen.LEFT_CATEGORY_GAP
                        + 4.5f;

        float otherTop =
                catStartY
                        + CompactGuiScreen.getCategoryYOffset(displayIdx)
                        - 3f;

        float otherBottom =
                catStartY
                        + CompactGuiScreen.getClientYOffset()
                        + CompactGuiScreen.LEFT_CATEGORY_H
                        + 4.5f;

        for (float[] box : new float[][]{{basicsTop, basicsBottom}, {otherTop, otherBottom}}) {
            renderer.rect(
                    gx + CompactGuiScreen.LEFT_PANEL_GAP + 2.5f,
                    box[0],
                    sw + 1,
                    box[1] - box[0],
                    5,
                    ColorUtil.replAlpha(
                            -1,
                            (int) (4 * alpha)
                    )
            );

            renderer.gradientOutline(
                    gx + CompactGuiScreen.LEFT_PANEL_GAP + 2.5f,
                    box[0],
                    sw + 1,
                    box[1] - box[0],
                    5,
                    ColorUtil.replAlpha(
                            -1,
                            (int) (16 * alpha)
                    ),
                    ColorUtil.replAlpha(
                            -1,
                            (int) (3 * alpha)
                    ),
                    ColorUtil.replAlpha(
                            -1,
                            (int) (16 * alpha)
                    ),
                    ColorUtil.replAlpha(
                            -1,
                            (int) (3 * alpha)
                    ),
                    1,
                    true
            );
        }

        // Заголовки групп — над рамками, не внутри.
        int headerCol = ColorUtil.replAlpha(TEXT_COLOR, (int) (90 * alpha));
        renderer.text(
                FontRegistry.MONTSERRAT,
                sx + 4,
                basicsTop - 8.5f,
                14,
                "BASICS",
                headerCol
        );
        renderer.text(
                FontRegistry.MONTSERRAT,
                sx + 4,
                (basicsBottom + otherTop) / 2f + 4f,
                14,
                "OTHER",
                headerCol
        );

        renderer.gradient(
                sx + 3,
                indicatorY,
                sw - 6,
                CompactGuiScreen.LEFT_CATEGORY_H,
                4,
                indicatorBg,
                indicatorBg,
                indicatorBg2,
                indicatorBg2,
                true
        );

        renderer.gradient(
                sx + 5.5f,
                indicatorY + 3,
                3,
                CompactGuiScreen.LEFT_CATEGORY_H - 6,
                0.9f,
                ColorUtil.replAlpha(
                        Renderer2D.ColorUtil.getClientColor1(),
                        (int) (255 * alpha)
                ),
                ColorUtil.replAlpha(
                        Renderer2D.ColorUtil.getClientColor1(),
                        (int) (255 * alpha)
                ),
                ColorUtil.replAlpha(
                        Renderer2D.ColorUtil.getClientColor2(),
                        (int) (255 * alpha)
                ),
                ColorUtil.replAlpha(
                        Renderer2D.ColorUtil.getClientColor2(),
                        (int) (255 * alpha)
                ),
                true
        );

        renderer.gradientOutline(
                sx + 3,
                indicatorY,
                sw - 6,
                CompactGuiScreen.LEFT_CATEGORY_H,
                4,
                indicatorOutline,
                indicatorOutline,
                indicatorOutline2,
                indicatorOutline2,
                1,
                true
        );

        for (int i = 0; i < cats.length; i++) {

            Category cat = cats[i];

            Animation2 hAnim =
                    CompactGuiScreen.getCategoryHoverAnim(cat);

            Animation2 sAnim =
                    CompactGuiScreen.getCategorySelectAnim(cat);

            hAnim.update();
            sAnim.update();

            float cy =
                    catStartY
                            + CompactGuiScreen.getCategoryYOffset(i);

            boolean hov =
                    isHovered(
                            CompactGuiScreen.currentMouseX,
                            CompactGuiScreen.currentMouseY,
                            sx,
                            cy,
                            sw,
                            CompactGuiScreen.LEFT_CATEGORY_H
                    );

            boolean sel =
                    CompactGuiScreen.isCategoryVisuallySelected(cat);

            hAnim.run(
                    hov ? 1.0 : 0.0,
                    0.2,
                    Easings.CUBIC_OUT
            );

            sAnim.run(
                    sel ? 1.0 : 0.0,
                    0.25,
                    Easings.CUBIC_OUT
            );

            float hv =
                    (float) hAnim.getValue();

            float sv =
                    (float) sAnim.getValue();

            int iCol =
                    ColorUtil.overCol(
                            ColorUtil.replAlpha(
                                    -1,
                                    (int) (65 * alpha)
                            ),
                            ColorUtil.replAlpha(
                                    Renderer2D.ColorUtil.getClientColor1(),
                                    (int) (255 * alpha)
                            ),
                            sv
                    );

            int iCol2 =
                    ColorUtil.overCol(
                            ColorUtil.replAlpha(
                                    -1,
                                    (int) (65 * alpha)
                            ),
                            ColorUtil.replAlpha(
                                    Renderer2D.ColorUtil.getClientColor2(),
                                    (int) (255 * alpha)
                            ),
                            sv
                    );

            renderer.textRight(
                    FontRegistry.MON,
                    sx + sw,
                    cy
                            + CompactGuiScreen.LEFT_CATEGORY_H / 2f
                            + 4,
                    CompactGuiScreen.LEFT_CATEGORY_ICON_FONT + 3,
                    cat.getIcon() + "  ",
                    new int[]{iCol, iCol2},
                    6
            );

            renderer.text(
                    FontRegistry.MONTSERRAT,
                    sx + 6 + sv * 8,
                    cy
                            + CompactGuiScreen.LEFT_CATEGORY_H / 2f
                            + 3.25f,
                    12.5f,
                    cat.getName(),
                    new int[]{
                            ColorUtil.overCol(
                                    ColorUtil.replAlpha(
                                            -1,
                                            (int) (65 * alpha)
                                    ),
                                    ColorUtil.replAlpha(
                                            Renderer2D.ColorUtil.getClientColor1(),
                                            (int) (255 * alpha)
                                    ),
                                    sv
                            ),
                            ColorUtil.overCol(
                                    ColorUtil.replAlpha(
                                            -1,
                                            (int) (45 * alpha)
                                    ),
                                    ColorUtil.replAlpha(
                                            Renderer2D.ColorUtil.getClientColor2(),
                                            (int) (255 * alpha)
                                    ),
                                    sv
                            )
                    },
                    5
            );
        }

        {
            float cy =
                    catStartY
                            + CompactGuiScreen.getThemeYOffset();

            boolean hov =
                    isHovered(
                            CompactGuiScreen.currentMouseX,
                            CompactGuiScreen.currentMouseY,
                            sx,
                            cy,
                            sw,
                            CompactGuiScreen.LEFT_CATEGORY_H
                    );

            boolean sel =
                    CompactGuiScreen.isThemeVisuallySelected();

            CompactGuiScreen.themeHoverAnim.update();
            CompactGuiScreen.themeSelectAnim.update();

            CompactGuiScreen.themeHoverAnim.run(
                    hov ? 1.0 : 0.0,
                    0.2,
                    Easings.CUBIC_OUT
            );

            CompactGuiScreen.themeSelectAnim.run(
                    sel ? 1.0 : 0.0,
                    0.25,
                    Easings.CUBIC_OUT
            );

            float sv =
                    (float) CompactGuiScreen.themeSelectAnim
                            .getValue();

            int iCol =
                    ColorUtil.overCol(
                            ColorUtil.replAlpha(
                                    -1,
                                    (int) (65 * alpha)
                            ),
                            ColorUtil.replAlpha(
                                    Renderer2D.ColorUtil.getClientColor(),
                                    (int) (215 * alpha)
                            ),
                            sv
                    );

            int iCol2 =
                    ColorUtil.overCol(
                            ColorUtil.replAlpha(
                                    -1,
                                    (int) (65 * alpha)
                            ),
                            ColorUtil.replAlpha(
                                    Renderer2D.ColorUtil.getClientColor(),
                                    (int) (125 * alpha)
                            ),
                            sv
                    );

            renderer.textRight(
                    FontRegistry.MON,
                    sx + sw - 6,
                    cy
                            + CompactGuiScreen.LEFT_CATEGORY_H / 2f
                            + 3.75f,
                    CompactGuiScreen.LEFT_CATEGORY_ICON_FONT + 3,
                    "f",
                    new int[]{iCol, iCol2}
            );

            renderer.text(
                    FontRegistry.MONTSERRAT,
                    sx + 6 + sv * 8,
                    cy
                            + CompactGuiScreen.LEFT_CATEGORY_H / 2f
                            + 3.25f,
                    12.5f,
                    "Theme",
                    new int[]{iCol, iCol2}
            );
        }

        {
            float cy =
                    catStartY
                            + CompactGuiScreen.getClientYOffset();

            boolean hov =
                    isHovered(
                            CompactGuiScreen.currentMouseX,
                            CompactGuiScreen.currentMouseY,
                            sx,
                            cy,
                            sw,
                            CompactGuiScreen.LEFT_CATEGORY_H
                    );

            boolean sel =
                    CompactGuiScreen.isClientVisuallySelected();

            CompactGuiScreen.clientHoverAnim.update();
            CompactGuiScreen.clientSelectAnim.update();

            CompactGuiScreen.clientHoverAnim.run(
                    hov ? 1.0 : 0.0,
                    0.2,
                    Easings.CUBIC_OUT
            );

            CompactGuiScreen.clientSelectAnim.run(
                    sel ? 1.0 : 0.0,
                    0.25,
                    Easings.CUBIC_OUT
            );

            float sv =
                    (float) CompactGuiScreen.clientSelectAnim
                            .getValue();

            int iCol =
                    ColorUtil.overCol(
                            ColorUtil.replAlpha(
                                    -1,
                                    (int) (65 * alpha)
                            ),
                            ColorUtil.replAlpha(
                                    Renderer2D.ColorUtil.getClientColor(),
                                    (int) (215 * alpha)
                            ),
                            sv
                    );

            int iCol2 =
                    ColorUtil.overCol(
                            ColorUtil.replAlpha(
                                    -1,
                                    (int) (65 * alpha)
                            ),
                            ColorUtil.replAlpha(
                                    Renderer2D.ColorUtil.getClientColor(),
                                    (int) (125 * alpha)
                            ),
                            sv
                    );

            renderer.textRight(
                    FontRegistry.MON,
                    sx + sw - 6,
                    cy
                            + CompactGuiScreen.LEFT_CATEGORY_H / 2f
                            + 3.75f,
                    CompactGuiScreen.LEFT_CATEGORY_ICON_FONT + 3,
                    "w",
                    new int[]{iCol, iCol2}
            );

            renderer.text(
                    FontRegistry.MONTSERRAT,
                    sx + 6 + sv * 8,
                    cy
                            + CompactGuiScreen.LEFT_CATEGORY_H / 2f
                            + 3.25f,
                    12.5f,
                    "Client",
                    new int[]{iCol, iCol2}
            );
        }

        float brandY =
                py
                        + ph
                        - CompactGuiScreen.BRAND_H
                        - 5;
    }

    private static void renderRightPanel(
            Renderer2D renderer,
            float gx,
            float gy,
            float gh,
            float alpha
    ) {
        float[] b =
                getRightPanelBounds(
                        gx,
                        gy,
                        gh
                );

        float px = b[0];
        float py = b[1];
        float pw = b[2];
        float ph = b[3];

        if (CompactGuiScreen.themeSelected
                && CompactGuiScreen.searchText.isEmpty()) {

            renderThemeGrid(
                    renderer,
                    px,
                    py,
                    pw,
                    ph,
                    alpha
            );

            return;
        }

        if (CompactGuiScreen.clientSelected
                && CompactGuiScreen.searchText.isEmpty()) {

            renderClientSettings(
                    renderer,
                    px,
                    py,
                    pw,
                    ph,
                    alpha
            );

            return;
        }

        List<Module> modules =
                getCurrentModules();

        float cX =
                px + CPAD;

        float cY =
                py + CPAD;

        float cW =
                getContentWidth(pw);

        float cH =
                ph - CPAD * 2;

        float colW =
                getColumnWidth(cW);

        float[] heights =
                new float[modules.size()];

        for (int i = 0;
             i < modules.size();
             i++) {

            heights[i] =
                    computeCardHeight(
                            renderer,
                            modules.get(i),
                            colW
                    );
        }

        float totalH = 0;

        float[] tmpColY =
                new float[COLS];

        for (int i = 0;
             i < modules.size();
             i++) {

            int sc = 0;

            for (int c = 1;
                 c < COLS;
                 c++) {

                if (tmpColY[c] < tmpColY[sc]) {
                    sc = c;
                }
            }

            tmpColY[sc] +=
                    heights[i]
                            + CompactGuiScreen.MODULE_ROW_GAP;
        }

        for (int c = 0;
             c < COLS;
             c++) {

            if (tmpColY[c] > totalH) {
                totalH = tmpColY[c];
            }
        }

        CompactGuiScreen.moduleScroll.setSpeed(26);
        CompactGuiScreen.moduleScroll.setMax(
                totalH,
                cH
        );

        boolean hov =
                isHovered(
                        CompactGuiScreen.currentMouseX,
                        CompactGuiScreen.currentMouseY,
                        px,
                        py,
                        pw,
                        ph
                );

        CompactGuiScreen.moduleScroll.setEnabled(
                hov && totalH > cH
        );

        CompactGuiScreen.moduleScroll.update();

        float scrollOff =
                CompactGuiScreen.moduleScroll.getScroll();

        float listAlpha =
                (float) CompactGuiScreen.moduleListAlpha
                        .getValue();

        boolean fadingOut =
                CompactGuiScreen.isModuleFadingOut();

        CompactGuiScreen.scrollBarAlpha.update();

        CompactGuiScreen.scrollBarAlpha.run(
                hov && totalH > cH
                        ? 1.0
                        : 0.0,
                0.25,
                Easings.CUBIC_OUT
        );

        float sbAlpha =
                (float) CompactGuiScreen.scrollBarAlpha
                        .getValue();

        renderer.pushClipRect(
                (int) cX,
                (int) cY,
                (int) cW,
                (int) cH
        );

        float[] colY =
                new float[COLS];

        Module newHovered = null;

        java.util.HashSet<Module> nowSeenModules =
                new java.util.HashSet<>();

        for (int i = 0;
             i < modules.size();
             i++) {

            Module mod =
                    modules.get(i);

            float cardH =
                    heights[i];

            int sc = 0;

            for (int c = 1;
                 c < COLS;
                 c++) {

                if (colY[c] < colY[sc]) {
                    sc = c;
                }
            }

            float targetX =
                    cX
                            + sc
                            * (
                            colW
                                    + CompactGuiScreen.MODULE_COL_GAP
                    );

            float targetYNo =
                    cY + colY[sc];

            colY[sc] +=
                    cardH
                            + CompactGuiScreen.MODULE_ROW_GAP;

            float[] p =
                    CARD_POS.computeIfAbsent(
                            mod,
                            k ->
                                    new float[]{
                                            targetX,
                                            targetYNo
                                    }
                    );

            nowSeenModules.add(mod);

            if (!lastSeenModules.contains(mod)) {
                p[0] = targetX;
                p[1] = targetYNo;
            } else {
                p[0] +=
                        (targetX - p[0])
                                * 0.14f;

                p[1] +=
                        (targetYNo - p[1])
                                * 0.14f;
            }

            float cardX = p[0];
            float cardY =
                    p[1] + scrollOff;

            if (cardY + cardH < cY - 5
                    || cardY > cY + cH + 5) {
                continue;
            }

            Animation2 enableAnim =
                    CompactGuiScreen.getModuleEnableAnim(
                            mod
                    );

            Animation2 hoverAnim =
                    CompactGuiScreen.getModuleHoverAnim(
                            mod
                    );

            enableAnim.update();
            hoverAnim.update();

            enableAnim.run(
                    mod.enable
                            ? 1.0
                            : 0.0,
                    0.3,
                    Easings.QUINT_OUT
            );

            boolean isCardHovered =
                    isHovered(
                            CompactGuiScreen.currentMouseX,
                            CompactGuiScreen.currentMouseY,
                            cardX,
                            cardY,
                            colW,
                            cardH
                    );

            if (isCardHovered) {
                newHovered = mod;
            }

            hoverAnim.run(
                    isCardHovered
                            ? 1.0
                            : 0.0,
                    0.15,
                    Easings.CUBIC_OUT
            );

            float enableVal =
                    (float) enableAnim.getValue();

            float cardAlpha;
            float cardSlideY;

            if (fadingOut) {

                cardAlpha =
                        alpha * listAlpha;

                cardSlideY =
                        (1.0f - listAlpha) * 12f;

            } else {

                float staggerA =
                        CompactGuiScreen.computeStaggerAlpha(
                                i,
                                CompactGuiScreen.moduleStaggerStart
                        );

                cardAlpha =
                        alpha
                                * listAlpha
                                * staggerA;

                cardSlideY =
                        CompactGuiScreen.computeStaggerOffset(
                                i,
                                12,
                                CompactGuiScreen.moduleStaggerStart
                        );
            }

            int bgColor =
                    ColorUtil.overCol(
                            ColorUtil.replAlpha(
                                    -1,
                                    (int) (7 * cardAlpha)
                            ),
                            ColorUtil.replAlpha(
                                    Renderer2D.ColorUtil.getClientColor2(),
                                    (int) (15 * cardAlpha)
                            ),
                            enableVal
                    );

            int bgColor2 =
                    ColorUtil.overCol(
                            ColorUtil.replAlpha(
                                    -1,
                                    (int) (0 * cardAlpha)
                            ),
                            ColorUtil.replAlpha(
                                    Renderer2D.ColorUtil.getClientColor1(),
                                    (int) (35 * cardAlpha)
                            ),
                            enableVal
                    );

            renderer.gradient(
                    cardX + cardSlideY,
                    cardY,
                    colW,
                    cardH,
                    6,
                    bgColor,
                    bgColor,
                    bgColor2,
                    bgColor2,
                    true
            );

            renderer.rectOutline(
                    cardX + cardSlideY,
                    cardY,
                    colW,
                    cardH,
                    6,
                    ColorUtil.replAlpha(
                            -1,
                            (int) (10 * cardAlpha)
                    ),
                    0.35f
            );

            renderer.pushRoundedClipRect(
                    cardX + 1 + cardSlideY,
                    cardY,
                    colW - 2,
                    cardH - 2,
                    4,
                    4,
                    4,
                    4
            );

            renderModuleCard(
                    renderer,
                    mod,
                    cardX + cardSlideY,
                    cardY,
                    colW,
                    cardH,
                    cardAlpha,
                    enableVal
            );

            renderer.popClipRect();
        }

        renderer.popClipRect();

        lastSeenModules.clear();
        lastSeenModules.addAll(nowSeenModules);

        if (newHovered != CompactGuiScreen.hoveredModule) {
            CompactGuiScreen.hoveredModule = newHovered;

            if (newHovered != null) {
                CompactGuiScreen.tooltipDelay = 0f;
            }
        }
    }

    private static final java.util.Map<
            vesence.module.Theme,
            Animation2
            > THEME_SELECT_ANIMS =
            new java.util.HashMap<>();

    private static Animation2 themeSelectAnim(
            vesence.module.Theme t,
            boolean selected
    ) {
        Animation2 a =
                THEME_SELECT_ANIMS.computeIfAbsent(
                        t,
                        k -> {
                            Animation2 an =
                                    new Animation2();

                            an.setValue(
                                    selected
                                            ? 1.0
                                            : 0.0
                            );

                            return an;
                        }
                );

        a.update();

        a.run(
                selected
                        ? 1.0
                        : 0.0,
                0.3,
                Easings.CUBIC_OUT,
                true
        );

        return a;
    }

    private static void renderClientSettings(
            Renderer2D renderer,
            float px,
            float py,
            float pw,
            float ph,
            float alpha
    ) {
        float cX =
                px + CPAD;

        float cY =
                py + CPAD;

        float cW =
                pw - CPAD * 2 - 4;

        float cH =
                ph - CPAD * 2;

        java.util.List<
                vesence.module.api.setting.Setting<?>
                > settings =
                vesence.ui.clickgui.compact.client.ClientSettings.getSettings();

        float totalH = 0f;

        for (vesence.module.api.setting.Setting<?> s :
                settings) {

            if ((boolean) s.hidden.get()) {
                continue;
            }

            totalH +=
                    vesence.ui.clickgui.compact.client.CompactGuiRenderClientSetting
                            .getSettingHeight(
                                    renderer,
                                    s,
                                    cW
                            )
                            + 2.5f;
        }

        boolean hov =
                isHovered(
                        CompactGuiScreen.currentMouseX,
                        CompactGuiScreen.currentMouseY,
                        px,
                        py,
                        pw,
                        ph
                );

        CompactGuiScreen.clientScroll.setSpeed(26);

        CompactGuiScreen.clientScroll.setMax(
                totalH,
                cH
        );

        CompactGuiScreen.clientScroll.setEnabled(
                hov && totalH > cH
        );

        CompactGuiScreen.clientScroll.update();

        float scrollOff =
                CompactGuiScreen.clientScroll.getScroll();

        float listAlpha =
                (float) CompactGuiScreen.moduleListAlpha
                        .getValue();

        boolean fadingOut =
                CompactGuiScreen.isModuleFadingOut();

        renderer.pushClipRect(
                (int) cX - 2,
                (int) cY - 2,
                (int) cW + 4,
                (int) cH + 4
        );

        float curY =
                cY + scrollOff;

        int idx = 0;

        for (vesence.module.api.setting.Setting<?> setting :
                settings) {

            if ((boolean) setting.hidden.get()) {
                continue;
            }

            float sH =
                    vesence.ui.clickgui.compact.client.CompactGuiRenderClientSetting
                            .getSettingHeight(
                                    renderer,
                                    setting,
                                    cW
                            );

            float rowAlpha;
            float slideY;

            if (fadingOut) {

                rowAlpha =
                        alpha * listAlpha;

                slideY =
                        (1.0f - listAlpha) * 12f;

            } else {

                float staggerA =
                        CompactGuiScreen.computeStaggerAlpha(
                                idx,
                                CompactGuiScreen.moduleStaggerStart
                        );

                rowAlpha =
                        alpha * listAlpha * staggerA;

                slideY =
                        CompactGuiScreen.computeStaggerOffset(
                                idx,
                                12,
                                CompactGuiScreen.moduleStaggerStart
                        );
            }

            if (curY + sH >= cY - 5
                    && curY <= cY + cH + 5) {

                vesence.ui.clickgui.compact.client.CompactGuiRenderClientSetting
                        .renderSetting(
                                renderer,
                                setting,
                                cX + slideY,
                                curY,
                                cW,
                                CompactGuiScreen.currentMouseX,
                                CompactGuiScreen.currentMouseY,
                                rowAlpha
                        );
            }

            curY += sH + 2.5f;
            idx++;
        }

        renderer.popClipRect();
    }

    private static void renderThemeGrid(
            Renderer2D renderer,
            float px,
            float py,
            float pw,
            float ph,
            float alpha
    ) {
        float cX =
                px + CPAD;

        float cY =
                py + CPAD;

        float cW =
                pw - CPAD * 2 - 4;

        float cH =
                ph - CPAD * 2;

        vesence.module.Theme[] themes =
                vesence.module.Theme.values();

        vesence.module.Theme current =
                vesence.module.impl.visuals.ThemeModule
                        .getCurrentTheme();

        int rows =
                (themes.length
                        + THEME_COLS
                        - 1)
                        / THEME_COLS;

        float totalH =
                rows
                        * (
                        THEME_CELL_H
                                + THEME_ROW_GAP
                );

        boolean hov =
                isHovered(
                        CompactGuiScreen.currentMouseX,
                        CompactGuiScreen.currentMouseY,
                        px,
                        py,
                        pw,
                        ph
                );

        CompactGuiScreen.moduleScroll.setSpeed(26);

        CompactGuiScreen.moduleScroll.setMax(
                totalH,
                cH
        );

        CompactGuiScreen.moduleScroll.setEnabled(
                hov && totalH > cH
        );

        CompactGuiScreen.moduleScroll.update();

        float scrollOff =
                CompactGuiScreen.moduleScroll.getScroll();

        float listAlpha =
                (float) CompactGuiScreen.moduleListAlpha
                        .getValue();

        boolean fadingOut =
                CompactGuiScreen.isModuleFadingOut();

        renderer.pushClipRect(
                (int) cX - 2,
                (int) cY - 2,
                (int) cW + 4,
                (int) cH + 1
        );

        for (int i = 0;
             i < themes.length;
             i++) {

            vesence.module.Theme theme =
                    themes[i];

            float[] r =
                    themeCellRect(
                            i,
                            cX,
                            cY,
                            cW,
                            scrollOff
                    );

            float cellX = r[0];
            float cellY = r[1];
            float cellW = r[2];
            float cellH = r[3];

            if (cellY + cellH < cY - 5
                    || cellY > cY + cH + 5) {
                continue;
            }

            boolean selected =
                    theme == current;

            boolean cellHov =
                    isHovered(
                            CompactGuiScreen.currentMouseX,
                            CompactGuiScreen.currentMouseY,
                            cellX,
                            cellY,
                            cellW,
                            cellH
                    );

            float cellAlpha;
            float slideY;

            if (fadingOut) {

                cellAlpha =
                        alpha * listAlpha;

                slideY =
                        (1.0f - listAlpha) * 12f;

            } else {

                float staggerA =
                        CompactGuiScreen.computeStaggerAlpha(
                                i,
                                CompactGuiScreen.moduleStaggerStart
                        );

                cellAlpha =
                        alpha * listAlpha * staggerA;

                slideY =
                        CompactGuiScreen.computeStaggerOffset(
                                i,
                                12,
                                CompactGuiScreen.moduleStaggerStart
                        );
            }

            float cx2 = cellX;
            float cy2 = cellY + slideY;

            Animation2 hovA =
                    CompactGuiScreen.getThemeCellAnim(
                            theme
                    );

            hovA.update();

            hovA.run(
                    (selected || cellHov)
                            ? 1.0
                            : 0.0,
                    0.2,
                    Easings.CUBIC_OUT
            );

            float hv =
                    (float) hovA.getValue();

            float sel =
                    (float) themeSelectAnim(
                            theme,
                            selected
                    ).getValue();

            float ca = cellAlpha;

            int cellBg =
                    ColorUtil.overCol(
                            ColorUtil.replAlpha(
                                    -1,
                                    (int) (7 * ca)
                            ),
                            ColorUtil.replAlpha(
                                    Renderer2D.ColorUtil.getClientColor(),
                                    (int) (14 * ca)
                            ),
                            hv
                    );

            renderer.rect(
                    cx2,
                    cy2,
                    cellW,
                    cellH,
                    6,
                    cellBg
            );

            int outlineCol =
                    ColorUtil.overCol(
                            ColorUtil.replAlpha(
                                    -1,
                                    (int) (10 * ca)
                            ),
                            ColorUtil.replAlpha(
                                    theme.getMain().getRGB(),
                                    (int) (210 * ca)
                            ),
                            sel
                    );

            renderer.gradientOutline(
                    cx2,
                    cy2,
                    cellW,
                    cellH,
                    6,
                    outlineCol,
                    outlineCol,
                    ColorUtil.multDark(
                            outlineCol,
                            0.65f
                    ),
                    ColorUtil.multDark(
                            outlineCol,
                            0.6f
                    ),
                    1,
                    true
            );

            int textCol =
                    ColorUtil.overCol(
                            ColorUtil.replAlpha(
                                    TEXT_COLOR,
                                    (int) (130 * ca)
                            ),
                            ColorUtil.replAlpha(
                                    -1,
                                    (int) (255 * ca)
                            ),
                            Math.max(hv, sel)
                    );

            renderer.text(
                    FontRegistry.MONTSERRAT,
                    cx2 + 6,
                    cy2 + cellH / 2f + 3.5f,
                    12.5f,
                    theme.getName(),
                    textCol
            );

            float prevW = 22f;
            float prevH = 11f;

            float prevX =
                    cx2
                            + cellW
                            - prevW
                            - 6f;

            float prevY =
                    cy2
                            + (
                            cellH
                                    - prevH
                    ) / 2f;

            int c1 =
                    theme.getMain().getRGB();

            int c2 =
                    theme.getMain2().getRGB();

            renderer.gradient(
                    prevX,
                    prevY,
                    prevW,
                    prevH,
                    3.5f,
                    ColorUtil.replAlpha(
                            c1,
                            (int) (255 * ca)
                    ),
                    ColorUtil.replAlpha(
                            c2,
                            (int) (255 * ca)
                    ),
                    ColorUtil.replAlpha(
                            c2,
                            (int) (255 * ca)
                    ),
                    ColorUtil.replAlpha(
                            c1,
                            (int) (255 * ca)
                    )
            );

            renderer.rectOutline(
                    prevX,
                    prevY,
                    prevW,
                    prevH,
                    3.5f,
                    ColorUtil.getColor(
                            0,
                            (int) (60 * ca)
                    ),
                    1f
            );
        }

        renderer.popClipRect();

        CompactGuiScreen.scrollBarAlpha.update();

        CompactGuiScreen.scrollBarAlpha.run(
                hov && totalH > cH
                        ? 1.0
                        : 0.0,
                0.25,
                Easings.CUBIC_OUT
        );
    }

    private static void renderModuleCard(
            Renderer2D renderer,
            Module mod,
            float cardX,
            float cardY,
            float cardW,
            float cardH,
            float alpha,
            float enableVal
    ) {
        float pad =
                CompactGuiScreen.MODULE_CARD_PADDING;

        float cx =
                cardX + pad;

        float cy =
                cardY + pad;

        float innerW =
                cardW - pad * 2;

        int nameCol =
                ColorUtil.overCol(
                        ColorUtil.replAlpha(
                                TEXT_COLOR,
                                (int) (165 * alpha)
                        ),
                        ColorUtil.replAlpha(
                                Renderer2D.ColorUtil.getClientColor1(),
                                (int) (255 * alpha)
                        ),
                        enableVal
                );

        int nameCol2 =
                ColorUtil.overCol(
                        ColorUtil.replAlpha(
                                TEXT_COLOR,
                                (int) (125 * alpha)
                        ),
                        ColorUtil.replAlpha(
                                Renderer2D.ColorUtil.getClientColor2(),
                                (int) (255 * alpha)
                        ),
                        enableVal
                );

        float togW = 16;
        float togH = 8;

        float togX =
                cardX
                        + cardW
                        - pad
                        - togW;

        float togY =
                cardY
                        + pad
                        + 1.5f;

        int togTrackBg =
                ColorUtil.overCol(
                        ColorUtil.replAlpha(
                                -1,
                                (int) (24 * alpha)
                        ),
                        ColorUtil.replAlpha(
                                Renderer2D.ColorUtil.getClientColor(),
                                (int) (100 * alpha)
                        ),
                        enableVal
                );

        float dotX =
                togX
                        + 2
                        + enableVal
                        * (
                        togW
                                - togH
                                - 2
                );

        float dotY =
                togY
                        + togH / 2f;

        renderer.text(
                FontRegistry.MONTSERRAT,
                cx + 3,
                cy + 10,
                14.75f,
                mod.name,
                new int[]{
                        nameCol,
                        nameCol2
                },
                0,
                -0.1f
        );

        cy += 18;

        String desc =
                mod.description != null
                        ? mod.description
                        : "";

        if (!desc.isEmpty()) {

            List<String> lines =
                    wrapText(
                            renderer,
                            desc,
                            CompactGuiScreen.MODULE_DESC_FONT,
                            innerW
                    );

            int dCol =
                    ColorUtil.replAlpha(
                            SUBTEXT_COLOR,
                            (int) (120 * alpha)
                    );

            for (String line : lines) {

                if (cy
                        + CompactGuiScreen.MODULE_DESC_FONT
                        + 1
                        > cardY + cardH - pad) {
                    break;
                }

                renderer.text(
                        FontRegistry.MONTSERRAT,
                        cx + 3,
                        cy
                                + CompactGuiScreen.MODULE_DESC_FONT
                                - 8.5f,
                        CompactGuiScreen.MODULE_DESC_FONT,
                        line,
                        dCol
                );

                cy +=
                        CompactGuiScreen.MODULE_DESC_FONT
                                - 3f;
            }
        }

        List<Setting<?>> settings =
                mod.getSettingsForGUI();

        cy +=
                CompactGuiScreen.MODULE_DIVIDER_GAP;

        renderer.rect(
                cardX - 2,
                cy,
                cardW + 4,
                0.5f,
                ColorUtil.overCol(
                        ColorUtil.replAlpha(
                                -1,
                                (int) (10 * alpha)
                        ),
                        ColorUtil.replAlpha(
                                Renderer2D.ColorUtil.getClientColor(),
                                (int) (10 * alpha)
                        ),
                        enableVal
                )
        );

        cy += 3;

        {
            String bLabel =
                    "Module bind...";

            String bText =
                    mod.binding
                            ? "..."
                            : mod.bindLabel();

            float ktw =
                    renderer.measureText(
                            FontRegistry.MONTSERRAT,
                            bText,
                            11
                    ).width;

            float bw =
                    ktw + 7;

            float bx =
                    cardX
                            + cardW
                            - bw
                            - 3;

            renderer.rect(
                    bx,
                    cy + 0.75f,
                    bw,
                    11,
                    2.5f,
                    ColorUtil.replAlpha(
                            -1,
                            (int) (15 * alpha)
                    )
            );

            renderer.textRight(
                    FontRegistry.MONTSERRAT,
                    bx + bw - 3.5f,
                    cy + 8.75f,
                    11,
                    bText,
                    bText.contains("NONE")
                            ? ColorUtil.replAlpha(
                            -1,
                            (int) (100 * alpha)
                    )
                            : ColorUtil.overCol(
                            ColorUtil.replAlpha(
                                    -1,
                                    (int) (155 * alpha)
                            ),
                            ColorUtil.replAlpha(
                                    -1,
                                    alpha
                            ),
                            enableVal
                    )
            );

            renderer.text(
                    FontRegistry.MONTSERRAT,
                    cx + 3,
                    cy + 9,
                    13,
                    bLabel,
                    new int[]{
                            ColorUtil.overCol(
                                    ColorUtil.replAlpha(
                                            -1,
                                            (int) (100 * alpha)
                                    ),
                                    ColorUtil.replAlpha(
                                            -1,
                                            (int) (200 * alpha)
                                    ),
                                    enableVal
                            ),
                            ColorUtil.overCol(
                                    ColorUtil.replAlpha(
                                            -1,
                                            (int) (65 * alpha)
                                    ),
                                    ColorUtil.replAlpha(
                                            -1,
                                            (int) (125 * alpha)
                                    ),
                                    enableVal
                            )
                    }
            );

            cy += 13;
        }
    }

    public static float computeCardHeight(
            Renderer2D renderer,
            Module mod,
            float cardW
    ) {
        float pad =
                CompactGuiScreen.MODULE_CARD_PADDING * 2;

        float h =
                pad + 18;

        String desc =
                mod.description != null
                        ? mod.description
                        : "";

        if (!desc.isEmpty()) {

            List<String> lines =
                    wrapText(
                            renderer,
                            desc,
                            CompactGuiScreen.MODULE_DESC_FONT,
                            cardW - pad
                    );

            h +=
                    lines.size()
                            * (
                            CompactGuiScreen.MODULE_DESC_FONT
                                    - 3f
                    );
        }

        h +=
                CompactGuiScreen.MODULE_DIVIDER_GAP
                        + 3
                        + 13;

        return Math.max(
                h,
                CompactGuiScreen.MODULE_CARD_MIN_H
        );
    }

    public static boolean isHovered(
            float mouseX,
            float mouseY,
            float x,
            float y,
            float w,
            float h
    ) {
        return mouseX >= x
                && mouseY >= y
                && mouseX < x + w
                && mouseY < y + h;
    }

    static List<String> wrapText(
            Renderer2D renderer,
            String text,
            float fontSize,
            float maxWidth
    ) {
        List<String> result =
                new ArrayList<>();

        if (text == null || text.isEmpty()) {
            result.add("");
            return result;
        }

        String[] words =
                text.split(" ");

        StringBuilder current =
                new StringBuilder();

        for (String word : words) {

            if (current.length() == 0) {
                current.append(word);
            } else {

                String test =
                        current
                                + " "
                                + word;

                float w =
                        renderer.measureText(
                                FontRegistry.MONTSERRAT,
                                test,
                                fontSize
                        ).width;

                if (w > maxWidth
                        && current.length() > 0) {

                    result.add(
                            current.toString()
                    );

                    current =
                            new StringBuilder(
                                    word
                            );

                } else {

                    current
                            .append(" ")
                            .append(word);
                }
            }
        }

        if (current.length() > 0) {
            result.add(
                    current.toString()
            );
        }

        if (result.isEmpty()) {
            result.add("");
        }

        return result;
    }
}
