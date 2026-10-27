package vesence.module.impl.visuals.elements.modern;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import vesence.module.impl.misc.ClickGui;
import vesence.module.impl.visuals.HudElement;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.text.FontObject;
import vesence.utils.render.text.FontRegistry;
import vesence.utils.render.ttf.TtfFonts;

/**
 * Ивенты: компоновка Default-версии — карточка-заголовок «Funtime events» и
 * строки с отдельной подложкой (название слева, значение справа). Дизайн
 * modern: чёрные карточки с тенью, светящаяся TTF-иконка, SF_SEMI, значение
 * цветом темы.
 *
 * <p>ВАЖНО: данные ({@link #ROWS}) — заглушка только для вёрстки, позже
 * заменить на реальные ивенты.
 */
@Environment(EnvType.CLIENT)
public class ModernEventsElement extends HudElement {

    private static final String TITLE       = "Funtime events";
    private static final float  HEADER_BOX_H = 33f;
    private static final float  TITLE_FONT   = 31f;

    private static final float  ROW_BOX_H     = 25f;
    private static final float  ROW_ADVANCE   = 29f;
    private static final float  ROW_FONT      = 28f;
    private static final float  ROW_RADIUS    = 8f;
    private static final float  ROW_TOP_GAP   = 5f;
    private static final float  TEXT_LEFT     = 7f;
    private static final float  TEXT_RIGHT    = 7f;
    private static final float  COL_GAP       = 18f;

    private static final String[][] ROWS = {
            {"anarchy310", "7\u043C 36\u0441"},
            {"anarchy213", "7\u043C 36\u0441"},
            {"anarchy506", "7\u043C 36\u0441"},
            {"anarchy605", "7\u043C 37\u0441"},
            {"anarchy219", "7\u043C 37\u0441"},
            {"anarchy311", "7\u043C 38\u0441"},
            {"anarchy313", "7\u043C 38\u0441"},
            {"anarchy604", "7\u043C 38\u0441"},
            {"anarchy215", "7\u043C 38\u0441"},
            {"anarchy507", "7\u043C 39\u0441"},
    };

    private float boundsW = 160f;
    private float boundsH = HEADER_BOX_H;

    public ModernEventsElement() {
        super("Ивенты", 10f, 320f);
    }

    @Override
    public void render(Renderer2D renderer, FontObject font, int screenWidth, int screenHeight, GuiGraphicsExtractor ctx) {
        FontObject f = FontRegistry.SF_SEMI;
        if (f == null) return;

        int themeColor = Renderer2D.ColorUtil.getClientColor();

        int nameColor = ColorUtil.replAlpha(-1, 255);
        int valColor  = ColorUtil.replAlpha(themeColor, 255);

        // ---- 1. Ширина панели по самому широкому содержимому ----
        float headerTextW = renderer.measureText(f, TITLE, TITLE_FONT, -0.15f).width;
        float headerW = 48f + headerTextW + TEXT_RIGHT;

        float maxRowW = 0f;
        for (String[] row : ROWS) {
            float nameW = renderer.measureText(f, row[0], ROW_FONT, -0.1f).width;
            float valW  = renderer.measureText(f, row[1], ROW_FONT, -0.1f).width;
            float rowW  = TEXT_LEFT + nameW + COL_GAP + valW + TEXT_RIGHT;
            if (rowW > maxRowW) maxRowW = rowW;
        }

        float rectW = Math.max(headerW, maxRowW);
        rectW = Math.max(rectW, 185);
        boundsW = rectW;

        // ---- 2. Карточка-заголовок (modern: чёрная, тень, blur из настроек) ----
        drawHudPanel(renderer, x, y, rectW, HEADER_BOX_H, 1f);

        float headerBaseY = y + 21f;
        TtfFonts.ESSENCE.drawStringWithGlow(renderer, "s", x + 8f, y + HEADER_BOX_H / 2f - 12f, 24,
                ColorUtil.replAlpha(themeColor, 230));
        renderer.rect(x + 38f, y + 10.5f, 1f, 14f, ColorUtil.replAlpha(-1, 20));
        renderer.text(f, x + 48f, headerBaseY + 2.5f, TITLE_FONT, TITLE, nameColor, -0.15f);

        // ---- 3. Строки (у каждой своя скруглённая подложка) ----
        int rowBg = ClickGui.getBackgroundColor((int) (185f * ClickGui.getHudAlpha()));
        float curY = y + HEADER_BOX_H + ROW_TOP_GAP;
        for (String[] row : ROWS) {
            renderer.blur(x, curY, rectW, ROW_BOX_H, ClickGui.blurStrengthHud.get().floatValue(), ROW_RADIUS, 1f);
            renderer.rect(x, curY, rectW, ROW_BOX_H, ROW_RADIUS, rowBg);


            renderer.text(f, x + TEXT_LEFT, curY + 17.5f, ROW_FONT, row[0], nameColor, -0.1f);
            renderer.textRight(f, x + rectW - TEXT_RIGHT, curY + 17.5f, ROW_FONT, row[1], valColor, -0.1f);

            curY += ROW_ADVANCE;
        }

        boundsH = (curY - ROW_ADVANCE + ROW_BOX_H) - y;
    }

    @Override
    public float getWidth(Renderer2D renderer, FontObject font) {
        return boundsW;
    }

    @Override
    public float getHeight(Renderer2D renderer, FontObject font) {
        return boundsH;
    }
}
