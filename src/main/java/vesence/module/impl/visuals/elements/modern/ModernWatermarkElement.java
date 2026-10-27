package vesence.module.impl.visuals.elements.modern;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import vesence.Vesence;
import vesence.module.impl.misc.ClickGui;
import vesence.module.impl.visuals.Hud;
import vesence.module.impl.visuals.HudElement;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.text.AnimatedText;
import vesence.utils.render.text.FontObject;
import vesence.utils.render.text.FontRegistry;
import vesence.utils.render.ttf.TtfFontFamily;
import vesence.utils.render.ttf.TtfFonts;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Watermark: компоновка Default-версии (лого-карточка, верхняя панель
 * Nick | FPS | Ping | Time с разделителями, нижняя панель Coords | BPS),
 * исполненная в modern-дизайне — чёрные карточки с тенью, светящиеся
 * TTF-иконки, шрифт SF_SEMI.
 *
 * <p>Уважает все тумблеры модуля Hud (FPS, BPS, Ping, Nick, Coordinates,
 * Time, Animated), BPS считается по скользящему окну.
 */
@Environment(EnvType.CLIENT)
public class ModernWatermarkElement extends HudElement {

    private static final float CARD_HEIGHT   = 33f;
    private static final float CARD_GAP      = 4f;
    private static final float ROW_GAP       = 4f;
    private static final float CARD_PADDING  = 10f;
    private static final float CARD_RADIUS   = 10f;

    private static final float TOP_ICON_X        = 10f;
    private static final float TOP_TEXT_X        = 32f;
    private static final float BOTTOM_ICON_X     = 9f;
    private static final float BOTTOM_TEXT_X     = 43f;

    private static final float ICON_Y            = 9f;
    private static final float TEXT_Y            = 23.5f;
    private static final float TEXT_SIZE         = 31f;
    private static final float ICON_SIZE         = 16.5f;

    private static final float BOTTOM_DIVIDER_X  = 34f;
    private static final float TOP_FIELD_RIGHT_PAD = 15f;
    private static final float TOP_FIELD_GAP     = 10f;

    private static final float DIVIDER_Y         = 6f;
    private static final float DIVIDER_HEIGHT    = 21f;

    private static final String PERSON_GLYPH = "j";

    private static final float LOGO_CARD_WIDTH   = 38f;
    private static final String LOGO_GLYPH       = "\uE900";
    private static final float LOGO_SIZE         = 18f;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final double BPS_WINDOW_SECONDS = 0.25;

    private float boundsW;
    private float boundsH = CARD_HEIGHT * 2f + ROW_GAP;

    private double windowStartX   = Double.NaN;
    private double windowStartZ   = Double.NaN;
    private long   windowStartNanos = 0L;
    private double displayedBps   = 0.0;

    public ModernWatermarkElement() {
        super("Watermark", 10f, 10f);
        this.enabled = false;
    }

    private Hud getHud() {
        try { return Vesence.get.manager.get(Hud.class); }
        catch (Throwable ignored) { return null; }
    }

    private boolean showFps()         { Hud h = getHud(); return h == null || h.isWatermarkFpsEnabled(); }
    private boolean showBps()         { Hud h = getHud(); return h == null || h.isWatermarkBpsEnabled(); }
    private boolean showPing()        { Hud h = getHud(); return h != null && h.isWatermarkPingEnabled(); }
    private boolean showNickname()    { Hud h = getHud(); return h == null || h.isWatermarkNicknameEnabled(); }
    private boolean showCoordinates() { Hud h = getHud(); return h == null || h.isWatermarkCoordinatesEnabled(); }
    private boolean showTime()        { Hud h = getHud(); return h == null || h.isWatermarkTimeEnabled(); }
    private boolean animatedEnabled() { Hud h = getHud(); return h == null || h.isWatermarkAnimatedEnabled(); }

    @Override
    public void render(Renderer2D renderer, FontObject font,
                       int screenWidth, int screenHeight, GuiGraphicsExtractor ctx) {

        Minecraft mc = Minecraft.getInstance();
        if (FontRegistry.SF_SEMI == null) return;

        int themeColor = Renderer2D.ColorUtil.getClientColor();
        int iconColor  = ColorUtil.replAlpha(themeColor, 230);
        int textColor  = ColorUtil.replAlpha(-1, 255);

        String playerName = mc.player != null
                ? mc.player.getGameProfile().name()
                : System.getProperty("user.name");

        String fps      = Integer.toString(mc.getFps());
        String time     = LocalTime.now().format(TIME_FORMAT);
        String position = playerPosition(mc);
        String bps      = String.format(Locale.ROOT, "%.1f", updateAndGetBps(mc));
        String ping     = playerPing(mc);

        float playerW = showNickname() ? topFieldWidth(renderer, playerName) : 0f;
        float fpsW    = showFps()      ? topFieldWidth(renderer, fps + " FPS") : 0f;
        float pingW   = showPing()     ? topFieldWidth(renderer, ping + " Ping") : 0f;
        float timeW   = showTime()     ? topFieldWidth(renderer, time + " Time") : 0f;

        int visibleTop = (showNickname() ? 1 : 0) + (showFps() ? 1 : 0)
                + (showPing() ? 1 : 0) + (showTime() ? 1 : 0);

        float topW = 0f;
        if (visibleTop > 0) {
            topW = playerW + fpsW + pingW + timeW
                    + (visibleTop - 1) * TOP_FIELD_GAP + 8f;
        }

        float positionW = showCoordinates() ? bottomFieldWidth(renderer, position) : 0f;
        float bpsW      = showBps()         ? bottomFieldWidth(renderer, bps + " BPS") : 0f;

        float topX       = x + LOGO_CARD_WIDTH + CARD_GAP;
        float secondRowY = y + CARD_HEIGHT + ROW_GAP;

        float bottomX = x;
        float bpsX    = bottomX + (showCoordinates() ? positionW + CARD_GAP : 0f);

        // ---- ЛОГОТИП ----
        drawCard(renderer, x, y, LOGO_CARD_WIDTH, CARD_HEIGHT);
        TtfFonts.LOGO.drawString(renderer, LOGO_GLYPH,
                x + (LOGO_CARD_WIDTH - LOGO_SIZE) / 2f - 4.5f,
                y + (CARD_HEIGHT - LOGO_SIZE) / 2f,
                LOGO_SIZE, iconColor);

        // ---- ВЕРХНЯЯ ПАНЕЛЬ ----
        if (visibleTop > 0) {
            drawCard(renderer, topX, y, topW, CARD_HEIGHT);
            float statX = topX;
            if (showNickname()) {
                drawIcon(renderer, TtfFonts.LOX, PERSON_GLYPH, statX + TOP_ICON_X, y, iconColor);
                renderer.text(FontRegistry.SF_SEMI, statX + TOP_TEXT_X, y + TEXT_Y,
                        TEXT_SIZE, playerName, textColor, -0.15f);
                if (visibleTop > 1) drawDivider(renderer, statX + playerW, y);
                statX += playerW + TOP_FIELD_GAP;
            }
            if (showFps()) {
                statX = drawTopSplitInfo(renderer, statX, y, fps, " FPS", fpsW,
                        themeColor, iconColor, textColor, showPing() || showTime(),
                        TtfFonts.ESSENCE, "x", "watermark_fps");
            }
            if (showPing()) {
                statX = drawTopSplitInfo(renderer, statX, y, ping, " Ping", pingW,
                        themeColor, iconColor, textColor, showTime(),
                        TtfFonts.ESSENCE, "p", "watermark_ping");
            }
            if (showTime()) {
                drawTopSplitInfo(renderer, statX, y, time, " Time", timeW,
                        themeColor, iconColor, textColor, false,
                        TtfFonts.ESSENCE, "t", "watermark_time");
            }
        }

        // ---- НИЖНЯЯ ПАНЕЛЬ ----
        boolean hasBottom = showCoordinates() || showBps();
        if (hasBottom) {
            if (showCoordinates()) {
                drawCard(renderer, bottomX, secondRowY, positionW, CARD_HEIGHT);
                drawIcon(renderer, TtfFonts.ICONNEW, "n", bottomX + BOTTOM_ICON_X, secondRowY, iconColor);
                drawDivider(renderer, bottomX + BOTTOM_DIVIDER_X, secondRowY);
                drawAnimatedOrPlain(renderer, "watermark_position", position,
                        bottomX + BOTTOM_TEXT_X, secondRowY + TEXT_Y, textColor);
            }
            if (showBps()) {
                drawCard(renderer, bpsX, secondRowY, bpsW, CARD_HEIGHT);
                drawIcon(renderer, TtfFonts.ESSENCE, "d", bpsX + BOTTOM_ICON_X, secondRowY, iconColor);
                drawDivider(renderer, bpsX + BOTTOM_DIVIDER_X, secondRowY);
                float accentW;
                if (animatedEnabled()) {
                    accentW = AnimatedText.draw(renderer, FontRegistry.SF_SEMI, "watermark_bps",
                            bps, bpsX + BOTTOM_TEXT_X, secondRowY + TEXT_Y, TEXT_SIZE,
                            themeColor, AnimatedText.ALIGN_LEFT);
                } else {
                    renderer.text(FontRegistry.SF_SEMI, bpsX + BOTTOM_TEXT_X, secondRowY + TEXT_Y,
                            TEXT_SIZE, bps, themeColor, -0.15f);
                    accentW = measure(renderer, bps);
                }
                renderer.text(FontRegistry.SF_SEMI, bpsX + BOTTOM_TEXT_X + accentW, secondRowY + TEXT_Y,
                        TEXT_SIZE, " BPS", textColor, -0.15f);
            }
        }

        // ---- Bounds ----
        boundsW = LOGO_CARD_WIDTH;
        if (visibleTop > 0) boundsW = Math.max(boundsW, LOGO_CARD_WIDTH + CARD_GAP + topW);
        if (hasBottom) boundsW = Math.max(boundsW,
                (showCoordinates() ? positionW : 0f)
                        + (showBps() ? CARD_GAP + bpsW : 0f));

        boundsH = hasBottom ? CARD_HEIGHT * 2f + ROW_GAP : CARD_HEIGHT;
    }

    /* ================================================================ КАРТОЧКА */

    /** Чёрная modern-карточка: blur из настроек, тень, фон с глобальной прозрачностью HUD. */
    private void drawCard(Renderer2D renderer, float cardX, float cardY, float w, float h) {
        if (isBlurEnabled()) {
            renderer.blur(cardX, cardY, w, h,
                    ClickGui.blurStrengthHud.get().floatValue(), CARD_RADIUS, 1f);
        }
        renderer.shadow(cardX, cardY, w, h, CARD_RADIUS, 5f, 1f,
                ColorUtil.replAlpha(ColorUtil.BLACK, 45));
        renderer.rect(cardX, cardY, w, h, CARD_RADIUS,
                ClickGui.getBackgroundColor(ClickGui.getHudAlpha()));
    }

    private float drawTopSplitInfo(Renderer2D renderer, float fieldX, float cardY,
                                   String accent, String suffix, float width,
                                   int themeColor, int iconColor, int textColor, boolean divider,
                                   TtfFontFamily iconFont, String iconGlyph, String animationKey) {
        drawIcon(renderer, iconFont, iconGlyph, fieldX + TOP_ICON_X, cardY, iconColor);
        float textX = fieldX + TOP_TEXT_X;
        float accentW;
        if (animatedEnabled()) {
            accentW = AnimatedText.draw(renderer, FontRegistry.SF_SEMI, animationKey,
                    accent, textX, cardY + TEXT_Y, TEXT_SIZE, themeColor, AnimatedText.ALIGN_LEFT);
        } else {
            renderer.text(FontRegistry.SF_SEMI, textX, cardY + TEXT_Y, TEXT_SIZE, accent, themeColor, -0.15f);
            accentW = measure(renderer, accent);
        }
        renderer.text(FontRegistry.SF_SEMI, textX + accentW, cardY + TEXT_Y,
                TEXT_SIZE, suffix, textColor, -0.15f);
        if (divider) drawDivider(renderer, fieldX + width, cardY);
        return fieldX + width + TOP_FIELD_GAP;
    }

    private void drawAnimatedOrPlain(Renderer2D renderer, String key, String value,
                                     float textX, float textY, int color) {
        if (animatedEnabled()) {
            AnimatedText.draw(renderer, FontRegistry.SF_SEMI, key, value,
                    textX, textY, TEXT_SIZE, color, AnimatedText.ALIGN_LEFT);
        } else {
            renderer.text(FontRegistry.SF_SEMI, textX, textY, TEXT_SIZE, value, color, -0.15f);
        }
    }

    private void drawIcon(Renderer2D renderer, TtfFontFamily font, String glyph,
                          float iconX, float cardY, int color) {
        font.drawStringWithGlow(renderer, glyph, iconX, cardY + ICON_Y - 2f, ICON_SIZE, color);
    }

    private void drawDivider(Renderer2D renderer, float dividerX, float cardY) {
        renderer.rect(dividerX, cardY + DIVIDER_Y, 1f, DIVIDER_HEIGHT,
                ColorUtil.replAlpha(-1, 20));
    }

    private float topFieldWidth(Renderer2D renderer, String value) {
        return TOP_TEXT_X + measure(renderer, value) + TOP_FIELD_RIGHT_PAD;
    }

    private float bottomFieldWidth(Renderer2D renderer, String value) {
        return BOTTOM_TEXT_X + measure(renderer, value) + CARD_PADDING;
    }

    private float measure(Renderer2D renderer, String text) {
        return renderer.measureText(FontRegistry.SF_SEMI, text, TEXT_SIZE, -0.15f).width;
    }

    /* ================================================================ BPS */

    private double updateAndGetBps(Minecraft mc) {
        if (mc.player == null) {
            windowStartX = Double.NaN; windowStartZ = Double.NaN;
            displayedBps = 0.0; return 0.0;
        }
        double curX = mc.player.getX(), curZ = mc.player.getZ();
        long now = System.nanoTime();
        if (Double.isNaN(windowStartX)) {
            windowStartX = curX; windowStartZ = curZ;
            windowStartNanos = now; return displayedBps;
        }
        double elapsed = (now - windowStartNanos) / 1_000_000_000.0;
        if (elapsed >= BPS_WINDOW_SECONDS) {
            double dx = curX - windowStartX, dz = curZ - windowStartZ;
            displayedBps = Math.sqrt(dx * dx + dz * dz) / elapsed;
            windowStartX = curX; windowStartZ = curZ; windowStartNanos = now;
        }
        return displayedBps;
    }

    /* ================================================================ PLAYER */

    private static String playerPosition(Minecraft mc) {
        if (mc.player == null) return "0x 0y 0z";
        return (int) Math.floor(mc.player.getX()) + "x "
                + (int) Math.floor(mc.player.getY()) + "y "
                + (int) Math.floor(mc.player.getZ()) + "z";
    }

    private static String playerPing(Minecraft mc) {
        if (mc.player == null || mc.getConnection() == null) return "0";
        var entry = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return entry == null ? "0" : Integer.toString(entry.getLatency());
    }

    /* ================================================================ HUD SIZE */

    @Override public float getWidth(Renderer2D renderer, FontObject font)          { return boundsW; }
    @Override public float getHeight(Renderer2D renderer, FontObject font)         { return boundsH; }
    @Override public float getEffectiveWidth(Renderer2D renderer, FontObject font) { return boundsW; }
}
