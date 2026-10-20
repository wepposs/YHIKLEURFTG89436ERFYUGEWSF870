package vesence.module.impl.visuals.elements;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import vesence.module.impl.misc.ClickGui;
import vesence.module.impl.visuals.HudElement;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.text.FontObject;
import vesence.utils.render.text.FontRegistry;

/**
 * "Ивенты" - обычная (Default Hud) версия. По дизайну повторяет {@code KeybindsElement}:
 * панель-заголовок с иконкой (той же, что в Keybinds) + разделителем + названием, а ниже - строки,
 * у каждой своя скруглённая подложка (blur + rect), слева название, справа значение.
 *
 * <p>ВАЖНО: данные ({@link #ROWS}) - заглушка и ничего не значат, нужны только для вёрстки. Позже
 * заменишь их на реальные. Шрифт - SF_MEDIUM.
 */
@Environment(EnvType.CLIENT)
public class EventsElement extends HudElement {

    // ---- Заголовок ----
    private static final String TITLE       = "Funtime events";  // текст заголовка панели
    private static final String ICON_GLYPH  = "A";        // тот же глиф-иконка, что и в Keybinds (шрифт VESENCE)
    private static final float  HEADER_BOX_H = 33f;       // высота панели-заголовка
    private static final float  ICON_SIZE    = 38f;       // размер иконки (как в Keybinds)
    private static final float  TITLE_FONT   = 31f;       // размер шрифта заголовка (как в Keybinds)

    // ---- Строки ----
    private static final float  ROW_BOX_H     = 25f;      // высота подложки строки
    private static final float  ROW_ADVANCE   = 29f;      // шаг между строками (как LINE_HEIGHT в Keybinds)
    private static final float  ROW_FONT      = 28f;      // размер шрифта строк
    private static final float  ROW_TOP_GAP   = 5f;       // зазор между заголовком и первой строкой
    private static final float  TEXT_LEFT     = 7;      // отступ текста слева
    private static final float  TEXT_RIGHT    = 7f;      // отступ значения справа
    private static final float  COL_GAP       = 18f;      // минимальный зазор между названием и значением

    // ---- Данные-заглушки (как на скрине). Слева - название, справа - значение. ----
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

    // Габариты, посчитанные в последнем render() (как в PotionsElement/KeybindsElement).
    private float boundsW = 160f;
    private float boundsH = HEADER_BOX_H;

    public EventsElement() {
        // Имя должно совпадать с записью в Hud.elements и с ModernEventsElement (для синхронизации).
        super("Ивенты", 10f, 320f);
    }

    @Override
    public void render(Renderer2D renderer, FontObject font, int screenWidth, int screenHeight, GuiGraphicsExtractor ctx) {
        // Шрифт берём прямо тут (шрифты грузятся уже после старта клиента).
        FontObject f = FontRegistry.SF_MEDIUM;
        if (f == null) return;

        int themeColor = Renderer2D.ColorUtil.getClientColor();   // основной цвет темы
        int iconColor  = Renderer2D.ColorUtil.getClientColor1();  // цвет иконки (как в Keybinds)

        // Фон панели/строк - тёмный с оттенком темы (как в Keybinds).
        int boxColor  = ColorUtil.replAlpha(ColorUtil.multDark(themeColor, 0.12f), ClickGui.getHudAlpha());
        int nameColor = ColorUtil.replAlpha(-1, 255);   // название - белое
        int valColor  = ColorUtil.replAlpha(-1, 230);   // значение - чуть тусклее белого
        int sepColor  = ColorUtil.replAlpha(-1, 15);    // разделитель у иконки

        // ---- 1. Считаем ширину панели по самому широкому содержимому ----
        float headerTextW = renderer.measureText(f, TITLE, TITLE_FONT).width;
        // Заголовок: иконка занимает область до ~48px, дальше текст + правый отступ.
        float headerW = 48f + headerTextW + TEXT_RIGHT;

        float maxRowW = 0f;
        for (String[] row : ROWS) {
            float nameW = renderer.measureText(f, row[0], ROW_FONT).width;
            float valW  = renderer.measureText(f, row[1], ROW_FONT).width;
            float rowW  = TEXT_LEFT + nameW + COL_GAP + valW + TEXT_RIGHT;
            if (rowW > maxRowW) maxRowW = rowW;
        }

        float rectW = Math.max(headerW, maxRowW);
        rectW = Math.max(rectW, 185); // минимальная ширина
        boundsW = rectW;

        // ---- 2. Панель-заголовок ----
        renderer.blur(x, y, rectW, HEADER_BOX_H, ClickGui.blurStrengthHud.get().floatValue(), 9, 1f);
        renderer.rect(x, y, rectW, HEADER_BOX_H, 9, boxColor);

        // Иконка (тот же глиф, что в Keybinds) + вертикальный разделитель + заголовок.
        float headerBaseY = y + 21f;
        renderer.text(FontRegistry.VESENCE, x + 9.5f, headerBaseY + 2f, ICON_SIZE, ICON_GLYPH, iconColor, 0.2f);
        renderer.rect(x + 38f, y + 10.5f, 1f, 14f, sepColor);
        renderer.text(f, x + 48f, headerBaseY + 2.5f, TITLE_FONT, TITLE, nameColor, 0.2f);

        // ---- 3. Строки (у каждой своя скруглённая подложка) ----
        float curY = y + HEADER_BOX_H + ROW_TOP_GAP;
        for (String[] row : ROWS) {
            renderer.blur(x, curY, rectW, ROW_BOX_H, 15, 7, 1f);
            renderer.rect(x, curY, rectW, ROW_BOX_H, 7, boxColor);

            // Название слева, значение - справа (right-aligned), как имя/бинд в Keybinds.
            renderer.text(f, x + TEXT_LEFT, curY + 17.5f, ROW_FONT, row[0], nameColor);
            renderer.textRight(f, x + rectW - TEXT_RIGHT, curY + 17.5f, ROW_FONT, row[1], valColor);

            curY += ROW_ADVANCE;
        }

        // Итоговая высота: от верха заголовка до низа последней строки.
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
