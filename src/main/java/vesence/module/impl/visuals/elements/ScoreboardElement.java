package vesence.module.impl.visuals.elements;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import vesence.Vesence;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.impl.misc.NameProtect;
import vesence.module.impl.visuals.Hud;
import vesence.module.impl.visuals.HudElement;
import vesence.module.impl.visuals.NoRender;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.text.FontObject;
import vesence.utils.render.text.FontRegistry;
import vesence.utils.render.text.FrameFont;
import vesence.utils.render.text.LegacyTextHelper;
import vesence.utils.render.text.LegacyTextHelper.ColoredSegment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ScoreboardElement — кастомный серверный скорборд-сайдбар.
 *
 * <p>Полный порт логики {@code ru.whylol.client.modules.impl.render.base.implement.ScoreboardHud}
 * из RelevantPremiumpp4, адаптированный под рендер vesence (Renderer2D +
 * {@link HudElement#drawHudPanel} = «рект клиента») и шрифты vesence
 * (MSDF {@link FontRegistry#SF_MEDIUM}).
 *
 * <p>Сохранена вся логика источника:
 * <ul>
 *   <li>{@link #resolveSidebarObjective} — team-color-aware выбор sidebar-objective
 *       (как ванильный {@code InGameHud.renderScoreboardSidebar});</li>
 *   <li>{@link #resolveNameText} — {@code Team.decorateName} (реальный текст лежит
 *       в prefix/suffix команды owner-а строки);</li>
 *   <li>{@link #buildLayout} с кэшем 50 мс;</li>
 *   <li>очистка текста: {@link #stripDecorative}/{@link #stripFormatting}/
 *       {@link #splitLeadingDecor}/{@link #isDecorativeSquare}/{@link #isPseudographic}/
 *       {@link #isHex6}/{@link #isLegacyCode};</li>
 *   <li>интеграция NameProtect: замена ника/грифа/сервер-слов ({@code np.patch})
 *       и перекраска красного в белый ({@code np.remapScoreboardColor}).</li>
 * </ul>
 *
 * <p>РЕНДЕР ТЕКСТА (порт {@code drawMixed}/{@code measureMixed}, «как в оригинале»):
 * <ul>
 *   <li>символы с MSDF-глифом → гладкий шрифт клиента ({@link Renderer2D#text},
 *       аналог suisse в relevant);</li>
 *   <li>символы рамки/баров ({@link #isFrameFallback}: Box Drawing U+2500..257F
 *       и ❚ U+275A), которых НЕТ в MSDF-атласе, → рисуются ГЛАДКИМ шрифтом Arial
 *       ({@link FrameFont}) через {@code Renderer2D.drawImage} — тот же TTF Arial,
 *       что и в relevant, но каждый глиф кэшируется в свою текстуру (в 1.21.11 нет
 *       старого рендер-API, которым рисовал relevant). Тинт = цвет рамки, альфа/
 *       масштаб элемента — автоматически;</li>
 *   <li>прочий не-рисуемый мусор без глифа — ПРОПУСКАЕТСЯ (как в relevant).</li>
 * </ul>
 * Так красная рамка скорборда отображается «как в оригинале». Цвета́ берутся
 * per-segment из сервера (§/&#-коды) + тема клиента; центрирование тела строки
 * и вся логика очистки — без изменений.
 */
@Environment(EnvType.CLIENT)
public class ScoreboardElement extends HudElement {

    // ── layout ──────────────────────────────────────────────────────────
    //   Пропорции взяты из relevant ScoreboardHud (PAD_X=5, PAD_Y=3,
    //   EXTRA_W=5, TITLE=15, LINE=14 (ratio 15/14), sepH=0.7, зазоры +2/+3,
    //   line advance = font.getHeight()+0.5) и перенесены в масштаб шрифтов
    //   vesence (SF_MEDIUM) с сохранением соотношений: заголовок чуть крупнее
    //   строк, тонкий разделитель, компактные отступы, шаг строки ≈ 0.71×size
    //   (как у StaffList/Cooldowns — без наложения).
    protected static final float PAD_X = 8.0F;
    protected static final float PAD_Y = 6.0F;
    protected static final float EXTRA_W = 7.0F;
    protected static final int   MAX_LINES = 15;

    protected static final float TITLE_SIZE = 26.0F;
    protected static final float LINE_SIZE = 24.0F;

    /** Высота блока заголовка (от верха заголовка до верха разделителя). */
    protected static final float TITLE_AREA_H = 18.0F;
    /** Смещение базовой линии заголовка от верха его блока. */
    protected static final float TITLE_BASELINE_OFF = 15.0F;
    /** Шаг строк (baseline-to-baseline). ≈0.71×LINE_SIZE — как у StaffList. */
    protected static final float LINE_ADVANCE = 17.0F;
    /** Смещение базовой линии строки от её верха. */
    protected static final float LINE_BASELINE_OFF = 14.0F;
    /** Зазор между заголовком и первой строкой. */
    protected static final float SEP_GAP = 3.5F;

    // ── чекбоксы/галочки (STATIC — общие для default и modern экземпляров) ──
    /** Показывать заголовок objective. */
    public static final BooleanSetting showTitle = new BooleanSetting("Заголовок", true);
    /** Рисовать фон-панель (рект клиента). */
    public static final BooleanSetting showBackground = new BooleanSetting("Фон", true);

    private CachedLayout cache;
    private long lastCacheMs = 0L;

    private float boundsW = 0.0F;
    private float boundsH = 0.0F;

    public ScoreboardElement() {
        // Дефолт-позиция — левый край (как в relevant ScoreboardHud: 5,195),
        // всегда в пределах экрана и перетаскиваемая. Координаты во viewport-
        // пространстве vesence. Пользователь двигает куда хочет (сохраняется).
        super("Scoreboard", 10.0F, 200.0F);
        addSetting(showTitle);
        addSetting(showBackground);
    }

    /**
     * Активен ли кастомный скорборд (для миксинов, подавляющих ванильный):
     * модуль Hud включён И элемент «Scoreboard» включён. Аналог
     * {@code Interface.isScoreboardHudVisible()} из relevant.
     */
    public static boolean shouldSuppressVanilla() {
        try {
            if (Vesence.get == null || Vesence.get.manager == null) return false;
            Hud hud = Vesence.get.manager.get(Hud.class);
            if (hud == null || !hud.enable) return false;
            return hud.isElementEnabled("Scoreboard");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isTableHidden() {
        try {
            return NoRender.isElementActive("Таблица");
        } catch (Throwable ignored) {
            return false;
        }
    }

    // ── рендер ────────────────────────────────────────────────────────────

    @Override
    public void render(Renderer2D renderer, FontObject font, int screenWidth, int screenHeight, DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) {
            boundsW = 0.0F;
            boundsH = 0.0F;
            return;
        }
        // Если Overlay Remover прячет таблицу — не рисуем ничего (как ваниль).
        if (isTableHidden()) {
            boundsW = 0.0F;
            boundsH = 0.0F;
            return;
        }

        CachedLayout layout = buildLayout(renderer);
        if (layout == null || layout.lines.isEmpty()) {
            boundsW = 0.0F;
            boundsH = 0.0F;
            return;
        }

        boundsW = layout.width;
        boundsH = layout.height;

        FontObject textFont = FontRegistry.SF_MEDIUM;
        int themeColor = Renderer2D.ColorUtil.getClientColor();

        NameProtect np = NameProtect.getInstance();
        boolean redToWhite = np != null && np.shouldScoreboardRedToWhite();

        float rectW = layout.width;
        float rectH = layout.height;

        // ── Фон (рект клиента: blur + shadow + rounded rect) ──────────────
        if (showBackground.get()) {
            drawHudPanel(renderer, x, y, rectW, rectH, 1.0F);
        }

        // Клип по границам панели — обрезает вылезающий текст (как maxX-clip в relevant).
        renderer.pushClipRect(x, y, rectW, rectH);
        try {
            float curY = y + PAD_Y;
            float maxTextX = x + rectW - PAD_X;

            // ── Заголовок (по центру) ─────────────────────────────────────
            if (showTitle.get()) {
                float titleW = segmentsWidth(renderer, textFont, layout.titleRaw, TITLE_SIZE);
                float titleX = x + (rectW - titleW) / 2.0F;
                drawColored(renderer, textFont, layout.titleRaw, titleX, curY,
                        curY + TITLE_BASELINE_OFF, TITLE_SIZE, themeColor, maxTextX, redToWhite, np);
                // Линия-разделитель убрана (по просьбе) — оставляем только зазор до строк.
                curY += TITLE_AREA_H + SEP_GAP;
            }

            // ── Строки ───────────────────────────────────────────────────
            //   Делим строку на «ведущую рамку» (псевдографика в начале) и
            //   «тело». Рамку (Box Drawing) рисуем слева ванильным шрифтом,
            //   тело — ПО ЦЕНТРУ панели. Порт splitLeadingDecor из relevant.
            for (String line : layout.lines) {
                String[] split = splitLeadingDecor(line);
                String lead = split[0];
                String body = split[1];
                float lineTop = curY;
                float baseline = curY + LINE_BASELINE_OFF;

                if (!lead.isEmpty()) {
                    drawColored(renderer, textFont, lead, x + PAD_X, lineTop, baseline,
                            LINE_SIZE, -1, maxTextX, redToWhite, np);
                }
                if (!body.isEmpty()) {
                    float bodyW = segmentsWidth(renderer, textFont, body, LINE_SIZE);
                    float bodyX = x + (rectW - bodyW) / 2.0F;
                    float minBodyX = x + PAD_X + segmentsWidth(renderer, textFont, lead, LINE_SIZE) + 4.0F;
                    if (bodyX < minBodyX) bodyX = minBodyX;
                    drawColored(renderer, textFont, body, bodyX, lineTop, baseline,
                            LINE_SIZE, -1, maxTextX, redToWhite, np);
                }
                curY += LINE_ADVANCE;
            }
        } finally {
            renderer.popClipRect();
        }
    }

    // ── измерение / отрисовка цветных сегментов (MSDF + рамка ванилью) ──────

    /** Суммарная ширина текста с §/&-цвет-кодами (MSDF + рамочный fallback). */
    private float segmentsWidth(Renderer2D renderer, FontObject font, String text, float size) {
        if (text == null || text.isEmpty()) return 0.0F;
        List<ColoredSegment> segs = LegacyTextHelper.parseSegments(text, -1);
        float w = 0.0F;
        for (ColoredSegment seg : segs) {
            w += measureMixed(renderer, font, seg.text(), size);
        }
        return w;
    }

    /**
     * Рисует {@code text} с §/&-цветами слева направо от {@code x}.
     * {@code redToWhite} + {@code np} — перекраска красного в белый (порт
     * {@code np.remapScoreboardColor} из relevant, применяется per-segment).
     */
    private void drawColored(Renderer2D renderer, FontObject font, String text,
                             float x, float lineTop, float baseline, float size, int defaultColor,
                             float maxX, boolean redToWhite, NameProtect np) {
        if (text == null || text.isEmpty()) return;
        List<ColoredSegment> segs = LegacyTextHelper.parseSegments(text, defaultColor);
        float cx = x;
        for (ColoredSegment seg : segs) {
            if (cx > maxX) break;
            int col = (redToWhite && np != null) ? np.remapScoreboardColor(seg.color()) : seg.color();
            cx = drawMixed(renderer, font, seg.text(), cx, lineTop, baseline, size, col, maxX);
        }
    }

    /**
     * Порт {@code ScoreboardHud.drawMixed}: разбивает {@code text} на «runs» —
     * символы с MSDF-глифом рисуются {@link Renderer2D#text} (гладкий шрифт
     * клиента), символы рамки ({@link #isFrameFallback}, Box Drawing/бары)
     * дорисовываются ВАНИЛЬНЫМ шрифтом MC через {@link DrawContext} (у него
     * есть эти глифы), а прочий не-рисуемый мусор — ПРОПУСКАЕТСЯ (как в relevant).
     * Возвращает новый {@code x} (курсор).
     */
    private float drawMixed(Renderer2D renderer, FontObject font, String text,
                            float x, float lineTop, float baseline, float size, int color, float maxX) {
        if (text == null || text.isEmpty()) return x;
        float cx = x;
        int i = 0;
        int n = text.length();
        while (i < n) {
            if (cx > maxX) break;
            char c = text.charAt(i);
            if (FontRegistry.hasGlyph(font, c)) {
                int start = i;
                while (i < n && FontRegistry.hasGlyph(font, text.charAt(i))) i++;
                String run = text.substring(start, i);
                renderer.text(font, cx, baseline, size, run, color);
                cx += renderer.measureText(font, run, size).width;
            } else if (isFrameFallback(c)) {
                int start = i;
                while (i < n && !FontRegistry.hasGlyph(font, text.charAt(i)) && isFrameFallback(text.charAt(i))) i++;
                String run = text.substring(start, i);
                cx += drawFrameRun(renderer, run, cx, lineTop, color);
            } else {
                i++; // не-рисуемый мусор без глифа и не рамка — пропускаем (как relevant)
            }
        }
        return cx;
    }

    /**
     * Ширина строки как в {@link #drawMixed}: MSDF-глифы — {@link Renderer2D#measureText},
     * рамочные символы — {@link #measureFrameRun}, прочее — 0.
     */
    private float measureMixed(Renderer2D renderer, FontObject font, String text, float size) {
        if (text == null || text.isEmpty()) return 0.0F;
        float w = 0.0F;
        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (FontRegistry.hasGlyph(font, c)) {
                int start = i;
                while (i < n && FontRegistry.hasGlyph(font, text.charAt(i))) i++;
                w += renderer.measureText(font, text.substring(start, i), size).width;
            } else if (isFrameFallback(c)) {
                int start = i;
                while (i < n && !FontRegistry.hasGlyph(font, text.charAt(i)) && isFrameFallback(text.charAt(i))) i++;
                w += measureFrameRun(text.substring(start, i));
            } else {
                i++;
            }
        }
        return w;
    }

    /**
     * Можно ли символ (отсутствующий в MSDF-атласе) дорисовать ванильным
     * шрифтом. Порт {@code ScoreboardHud.isFrameFallback}: ТОЛЬКО символы
     * рамки/баров — Box Drawing (U+2500..257F) и bar-dingbat ❚ (U+275A).
     * Остальное без глифа — декоративный мусор, его не рисуем.
     */
    private static boolean isFrameFallback(char c) {
        return (c >= 0x2500 && c <= 0x257F) || c == '\u275A';
    }

    /**
     * Высота «ячейки» рамочного глифа в логических px = шагу строки,
     * чтобы вертикальные бары рамки (║) стыковались между строками.
     */
    private float frameCellHeight() {
        return LINE_ADVANCE;
    }

    /** Логическая ширина рамочного run (совпадает с {@link #drawFrameRun}). */
    private float measureFrameRun(String run) {
        if (run == null || run.isEmpty()) return 0.0F;
        int cellH = FrameFont.cellHeight();
        if (cellH <= 0) return 0.0F;
        float scale = frameCellHeight() / (float) cellH;
        float w = 0.0F;
        for (int i = 0; i < run.length(); i++) {
            w += FrameFont.advanceOf(run.charAt(i)) * scale;
        }
        return w;
    }

    /**
     * Рисует рамочный {@code run} ГЛАДКИМ шрифтом Arial ({@link FrameFont}) через
     * {@code Renderer2D.drawImage} — тем же путём, что и остальные текстуры
     * клиента (тинт = цвет рамки; альфа/масштаб/трансформы элемента применяются
     * автоматически, без ручного пересчёта координат). Высота глифа =
     * {@link #frameCellHeight} (= шаг строки) → вертикальные бары ║ стыкуются
     * между строками. Так рамка выглядит гладко, «как в оригинале». Возвращает
     * логическую ширину run.
     */
    private float drawFrameRun(Renderer2D renderer, String run, float lx, float lineTop, int color) {
        if (run == null || run.isEmpty()) return 0.0F;
        int cellH = FrameFont.cellHeight();
        if (cellH <= 0) return 0.0F;
        float scale = frameCellHeight() / (float) cellH;
        int argb = (color >>> 24) == 0 ? (color | 0xFF000000) : color;
        float cx = lx;
        for (int i = 0; i < run.length(); i++) {
            FrameFont.Glyph g = FrameFont.getGlyph(run.charAt(i));
            if (g == null) continue;
            float gw = g.advance * scale;
            // FrameFont теперь грузит текстуру сверху вниз (как TtfFont) →
            // рисуем без переворота (flipVertically=false).
            renderer.drawImage(g.texId, cx, lineTop, gw, frameCellHeight(), argb, false);
            cx += gw;
        }
        return cx - lx;
    }

    // ── построение лейаута (кэш 50 мс) ──────────────────────────────────────

    private CachedLayout buildLayout(Renderer2D renderer) {
        long now = System.currentTimeMillis();
        if (cache != null && now - lastCacheMs < 50L) return cache;
        lastCacheMs = now;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) {
            cache = null;
            return null;
        }

        ScoreboardObjective obj = resolveSidebarObjective(mc.world.getScoreboard(), mc);
        if (obj == null) {
            cache = null;
            return null;
        }

        Scoreboard sb = obj.getScoreboard();

        String titleRaw = stripDecorative(applyNameProtect(LegacyTextHelper.extractRaw(obj.getDisplayName())));

        List<ScoreboardEntry> entries = new ArrayList<>(sb.getScoreboardEntries(obj));
        List<String> lines = entries.stream()
                .filter(e -> e != null && !e.hidden())
                .sorted(Comparator.comparingInt(ScoreboardEntry::value).reversed()
                        .thenComparing(ScoreboardEntry::owner, String::compareToIgnoreCase))
                .limit(MAX_LINES)
                .map(entry -> stripDecorative(applyNameProtect(LegacyTextHelper.extractRaw(resolveNameText(sb, entry)))))
                // Фильтр как в relevant: пустые строки (в т.ч. «только цвет-код») —
                // визуальные отступы между секциями. Рамка (Box Drawing U+2500..257F)
                // СОХРАНЯЕТСЯ и дорисовывается ванильным шрифтом (см. drawMixed).
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());

        if (lines.isEmpty()) {
            cache = null;
            return null;
        }

        FontObject textFont = FontRegistry.SF_MEDIUM;

        float maxW = 0.0F;
        if (showTitle.get()) {
            maxW = segmentsWidth(renderer, textFont, titleRaw, TITLE_SIZE);
        }
        for (String line : lines) {
            float w = segmentsWidth(renderer, textFont, line, LINE_SIZE);
            if (w > maxW) maxW = w;
        }

        float totalWidth = maxW + PAD_X * 2.0F + EXTRA_W;

        float headerH = 0.0F;
        if (showTitle.get()) {
            headerH = TITLE_AREA_H + SEP_GAP;
        }
        float totalHeight = PAD_Y + headerH + lines.size() * LINE_ADVANCE + PAD_Y;

        cache = new CachedLayout(titleRaw, lines, totalWidth, totalHeight);
        return cache;
    }

    // ── NameProtect ─────────────────────────────────────────────────────────

    private static String applyNameProtect(String text) {
        NameProtect np = NameProtect.getInstance();
        if (np != null && np.enable) {
            return np.patch(text);
        }
        return text;
    }

    // ── очистка текста (порт из ScoreboardHud) ──────────────────────────────

    private static String stripFormatting(String s) {
        if (s == null) return "";
        s = s.replaceAll("&#[0-9a-fA-F]{6}", "");
        s = s.replaceAll("(?i)[\u00A7&$][0-9a-fk-orx]", "");
        return s;
    }

    /**
     * Делит строку на {@code [lead, body]}: lead — ведущая последовательность
     * декоративных символов (псевдографика рамки в начале), body — остальной
     * полезный текст. §-цвет-коды ({@code &#rrggbb}) сохраняются в своей части.
     */
    private static String[] splitLeadingDecor(String line) {
        if (line == null || line.isEmpty()) return new String[]{"", ""};
        int i = 0;
        int leadEnd = 0;
        boolean sawDecor = false;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (c == '&' && i + 1 < line.length() && line.charAt(i + 1) == '#'
                    && i + 8 <= line.length() && isHex6(line, i + 2)) {
                i += 8;
                continue;
            }
            if (isPseudographic(c) || (c == ' ' && sawDecor)) {
                if (isPseudographic(c)) sawDecor = true;
                i++;
                leadEnd = i;
            } else {
                break;
            }
        }
        if (!sawDecor) {
            return new String[]{"", line};
        }
        String lead = line.substring(0, leadEnd);
        String body = line.substring(leadEnd);
        return new String[]{lead, body};
    }

    /** Символ из блоков псевдографики (Box Drawing / Block / Geometric / dingbat). */
    private static boolean isPseudographic(char c) {
        return (c >= 0x2500 && c <= 0x25FF) || (c >= 0x2700 && c <= 0x27BF);
    }

    /**
     * Декоративный «квадратик»-tofu (серверный маркер/иконка): оставляем
     * обычный текст (&lt; U+2500) и Box Drawing (U+2500..257F, рамка), всё
     * прочее (Block/Geometric/Dingbats/суррогаты/PUA) — режем.
     */
    private static boolean isDecorativeSquare(char c) {
        if (c < 0x2500) {
            return false;
        }
        if (c >= 0x2500 && c <= 0x257F) {
            return false;
        }
        return true;
    }

    /**
     * Убирает декоративный мусор и литеральные §-коды, сохраняя {@code &#rrggbb}
     * цвет-коды и Box Drawing рамку. Порт {@code ScoreboardHud.stripDecorative}.
     */
    private static String stripDecorative(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '&' && i + 1 < s.length() && s.charAt(i + 1) == '#'
                    && i + 8 <= s.length() && isHex6(s, i + 2)) {
                out.append(s, i, i + 8);
                i += 8;
                continue;
            }
            if (isDecorativeSquare(c)) {
                i++;
                continue;
            }
            if (c == '\u00A7') {
                if (i + 1 < s.length() && isLegacyCode(s.charAt(i + 1))) {
                    i += 2;
                } else {
                    i++;
                }
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString().replaceAll("\\s{2,}", " ").stripTrailing();
    }

    /** Символ легаси-кода после § (0-9, a-f, k-o, r, x). */
    private static boolean isLegacyCode(char c) {
        char code = Character.toLowerCase(c);
        return (code >= '0' && code <= '9')
            || (code >= 'a' && code <= 'f')
            || code == 'k' || code == 'l' || code == 'm'
            || code == 'n' || code == 'o' || code == 'r' || code == 'x';
    }

    private static boolean isHex6(String s, int start) {
        if (start + 6 > s.length()) return false;
        for (int i = 0; i < 6; i++) {
            char c = s.charAt(start + i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hex) return false;
        }
        return true;
    }

    // ── scoreboard API (порт из ScoreboardHud) ──────────────────────────────

    /**
     * Решает, какой sidebar-objective показать. Повторяет логику ванильного
     * {@code InGameHud.renderScoreboardSidebar}: если у команды игрока есть
     * цвет (≠ RESET) — берём TEAM_&lt;color&gt; слот, иначе — общий SIDEBAR.
     */
    private ScoreboardObjective resolveSidebarObjective(Scoreboard scoreboard, MinecraftClient mc) {
        if (scoreboard == null) return null;
        ScoreboardObjective sidebar = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (mc.player == null) return sidebar;

        Team team = scoreboard.getScoreHolderTeam(mc.player.getName().getString());
        if (team != null) {
            Formatting color = team.getColor();
            if (color != null && color != Formatting.RESET) {
                ScoreboardDisplaySlot slot = ScoreboardDisplaySlot.fromFormatting(color);
                if (slot != null) {
                    ScoreboardObjective teamObj = scoreboard.getObjectiveForSlot(slot);
                    if (teamObj != null) return teamObj;
                }
            }
        }
        return sidebar;
    }

    /**
     * Реальный текст строки лежит в prefix/suffix команды owner-а —
     * {@code Team.decorateName}. Без этого owner пустой и контент пропадает.
     */
    private Text resolveNameText(Scoreboard scoreboard, ScoreboardEntry entry) {
        Text base = entry.name();
        if (base == null) base = Text.empty();

        String owner = entry.owner();
        Team ownerTeam = owner != null ? scoreboard.getScoreHolderTeam(owner) : null;
        return Team.decorateName(ownerTeam, base);
    }

    // ── bounds ──────────────────────────────────────────────────────────────

    @Override
    public float getEffectiveWidth(Renderer2D renderer, FontObject font) {
        return getWidth(renderer, font);
    }

    @Override
    public float getWidth(Renderer2D renderer, FontObject font) {
        CachedLayout l = buildLayout(renderer);
        return l == null ? 0.0F : l.width;
    }

    @Override
    public float getHeight(Renderer2D renderer, FontObject font) {
        CachedLayout l = buildLayout(renderer);
        return l == null ? 0.0F : l.height;
    }

    private record CachedLayout(String titleRaw, List<String> lines, float width, float height) {}
}
