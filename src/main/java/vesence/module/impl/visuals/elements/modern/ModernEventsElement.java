package vesence.module.impl.visuals.elements.modern;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import vesence.module.impl.visuals.HudElement;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;
import vesence.utils.render.text.FontObject;
import vesence.utils.render.text.FontRegistry;
import vesence.utils.render.ttf.TtfFonts;

@Environment(EnvType.CLIENT)
public class ModernEventsElement extends HudElement {

    private static final float PANEL_PAD = 6f;
    private static final float COL_GAP = 6f;
    private static final float HEADER_H = 38f;
    private static final float HEADER_GAP = 6f;
    private static final float ICON_COL_W = 35f;
    private static final float COL_PAD_V = 5f;
    private static final float LINE_HEIGHT = 23f;

    private static final float MIN_WIDTH = 225f;
    private static final float TITLE_SIZE = 33f;
    private static final float NAME_SIZE = 30.5f;
    private static final float VALUE_SIZE = 30.5f;
    private static final int PILL_ALPHA = 12;
    private static final String HEADER_GLYPH = "n";

    private static final EventRow[] ROWS = {
            new EventRow("anarchy102", "12с"),
            new EventRow("anarchy205", "1:32с"),
            new EventRow("anarchy311", "3:55с"),
            new EventRow("anarchy410", "32с"),
            new EventRow("anarchy503", "2:44с"),
            new EventRow("anarchy605", "4:02с")
    };

    private final Animation2 visibilityAnim = new Animation2();
    private final Animation2 widthAnim = new Animation2();
    private float boundsW = MIN_WIDTH;
    private float boundsH = HEADER_H + PANEL_PAD * 2f;

    public ModernEventsElement() {
        super("Ивенты", 10f, 320f);
        visibilityAnim.set(1.0);
        widthAnim.set(MIN_WIDTH);
    }

    @Override
    public void render(Renderer2D r, FontObject font, int screenWidth, int screenHeight, DrawContext ctx) {
        boolean chatOpen = MinecraftClient.getInstance().currentScreen instanceof ChatScreen;
        boolean shouldShow = chatOpen || ROWS.length > 0;

        visibilityAnim.update();
        widthAnim.update();
        visibilityAnim.run(shouldShow ? 1.0 : 0.0, 0.15, Easings.CUBIC_OUT, true);
        float alpha = (float) visibilityAnim.get();
        if (alpha < 0.005f) return;

        int theme = Renderer2D.ColorUtil.getClientColor();
        int titleColor = ColorUtil.replAlpha(-1, (int) (255 * alpha));
        int accentColor = ColorUtil.replAlpha(theme, (int) (255 * alpha));
        int iconColor = ColorUtil.replAlpha(theme, (int) (230 * alpha));
        int panelColor = ColorUtil.replAlpha(ColorUtil.BLACK, (int) (155 * alpha));
        int pillColor = ColorUtil.replAlpha(-1, (int) (PILL_ALPHA * alpha));
        int columnColor = ColorUtil.replAlpha(-1, (int) (12 * alpha));

        float maxNameW = 0f;
        float maxValueW = 0f;
        for (EventRow row : ROWS) {
            maxNameW = Math.max(maxNameW, r.measureText(FontRegistry.SF_MEDIUM, row.name(), NAME_SIZE, -0.1f).width);
            maxValueW = Math.max(maxValueW, r.measureText(FontRegistry.SF_SEMI, row.value(), VALUE_SIZE).width);
        }

        float titleW = r.measureText(FontRegistry.SF_SEMI, "Events", TITLE_SIZE, -0.15f).width;
        float accentW = r.measureText(FontRegistry.SF_SEMI, "SP", TITLE_SIZE, -0.15f).width;
        float headerPillMin = 16f + titleW + 8f + accentW + 28f;

        float nameColW = maxNameW + 24f;
        float valueColW = maxValueW + 24f;
        float rightBlockW = Math.max(headerPillMin, nameColW + COL_GAP + valueColW);
        if (rightBlockW > nameColW + COL_GAP + valueColW) {
            nameColW = rightBlockW - COL_GAP - valueColW;
        }

        float panelTarget = Math.max(MIN_WIDTH, PANEL_PAD * 2f + ICON_COL_W + COL_GAP + nameColW + COL_GAP + valueColW);
        widthAnim.run(panelTarget, 0.2, Easings.CUBIC_OUT, true);
        float panelW = (float) widthAnim.get();

        float x0 = x + PANEL_PAD;
        float iconColX = x0;
        float valueColX = x + panelW - PANEL_PAD - valueColW;
        float nameColX = iconColX + ICON_COL_W + COL_GAP;
        float nameColW2 = valueColX - COL_GAP - nameColX;

        float colsH = ROWS.length == 0 ? 0f : ROWS.length * LINE_HEIGHT + COL_PAD_V * 2f;
        float panelH = PANEL_PAD + HEADER_H + (colsH > 0f ? HEADER_GAP + colsH : 0f) + PANEL_PAD;

        r.blur(x, y, panelW, panelH, 15, 12, alpha);
        r.rect(x, y, panelW, panelH, 12, panelColor);

        float headerTop = y + PANEL_PAD;
        TtfFonts.ESSENCE.drawStringWithGlow(r, "s", iconColX + ICON_COL_W / 2f - 12f, headerTop + HEADER_H / 2f - 11f, 24f, iconColor);

        float headerPillX = nameColX;
        float headerPillW = valueColX + valueColW - headerPillX + 1f;
        r.rect(headerPillX, headerTop, headerPillW, HEADER_H, 3, 6, ROWS.length > 0 ? 3 : 6, 3, pillColor);
        float titleBaseY = headerTop + HEADER_H / 2f + 7.5f;
        r.text(FontRegistry.SF_SEMI, headerPillX + 12f, titleBaseY, TITLE_SIZE, "Events", titleColor, -0.15f);
        r.text(FontRegistry.SF_SEMI, headerPillX + 12f + titleW + 8f, titleBaseY, TITLE_SIZE, "SP", accentColor, -0.15f);
        r.textRight(FontRegistry.SF_BOLD, headerPillX + headerPillW - 12f, headerTop + HEADER_H / 2f + 6f, 30f, ":::",
                ColorUtil.replAlpha(-1, (int) (75 * alpha)), 1);

        if (colsH <= 0f) {
            boundsW = panelW;
            boundsH = panelH;
            return;
        }

        float colsTop = headerTop + HEADER_H + HEADER_GAP;
        r.rect(iconColX, colsTop, ICON_COL_W, colsH, 3, 3, 3, 6, columnColor);
        r.rect(nameColX, colsTop, nameColW2, colsH, 3, 3, 3, 3, columnColor);
        r.rect(valueColX, colsTop, valueColW, colsH, 3, 3, 6, 3, columnColor);

        float rowTop = colsTop + COL_PAD_V;
        for (EventRow row : ROWS) {
            float baseY = rowTop + LINE_HEIGHT / 2f + NAME_SIZE * 0.33f - 2.5f;
            TtfFonts.ESSENCE.drawCenteredStringWithGlow(r, "f", iconColX + ICON_COL_W / 2f, rowTop + LINE_HEIGHT / 2f - 6, 15,
                    ColorUtil.replAlpha(theme, (int) (255 * alpha)));
            r.text(FontRegistry.SF_MEDIUM, nameColX + 10f, baseY, NAME_SIZE, row.name(), titleColor, -0.1f);
            r.textCenter(FontRegistry.SF_SEMI, valueColX + valueColW / 2f + 0.5f, baseY, VALUE_SIZE, row.value(), accentColor);
            rowTop += LINE_HEIGHT;
        }

        boundsW = panelW;
        boundsH = panelH;
    }

    private void drawStatusIcon(Renderer2D r, float centerX, float centerY, int color, float alpha) {
        int fill = ColorUtil.replAlpha(color, (int) (225 * alpha));
        float dot = 3.6f;
        float gap = 1.7f;
        float left = centerX - dot - gap / 2f;
        float top = centerY - dot - gap / 2f;

        r.rect(left, top, dot, dot, 1.2f, fill);
        r.rect(left + dot + gap, top, dot, dot, 1.2f, fill);
        r.rect(left, top + dot + gap, dot, dot, 1.2f, fill);
        r.rect(left + dot + gap, top + dot + gap, dot, dot, 1.2f, fill);
    }

    @Override
    public float getEffectiveWidth(Renderer2D r, FontObject font) {
        return (float) widthAnim.get();
    }

    @Override
    public float getWidth(Renderer2D r, FontObject font) {
        return boundsW;
    }

    @Override
    public float getHeight(Renderer2D r, FontObject font) {
        return boundsH;
    }

    private record EventRow(String name, String value) {
    }
}
