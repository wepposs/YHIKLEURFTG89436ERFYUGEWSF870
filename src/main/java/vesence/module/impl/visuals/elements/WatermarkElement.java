package vesence.module.impl.visuals.elements;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import vesence.Vesence;
import vesence.module.impl.misc.ClickGui;
import vesence.module.impl.visuals.Hud;
import vesence.module.impl.visuals.HudElement;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.text.AnimatedText;
import vesence.utils.render.text.FontObject;
import vesence.utils.render.text.FontRegistry;
import vesence.utils.render.ttf.TtfFonts;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Environment(EnvType.CLIENT)
public class WatermarkElement extends HudElement {

    private static final float CARD_HEIGHT   = 33f;
    private static final float CARD_GAP      = 4f;
    private static final float ROW_GAP       = 4f;
    private static final float CARD_PADDING  = 10f;

    private static final float TOP_ICON_X        = 10f;
    private static final float TOP_TEXT_X        = 32f;
    private static final float BOTTOM_ICON_X     = 7f;
    private static final float BOTTOM_TEXT_X     = 43f;

    private static final float ICON_Y            = 7f;
    private static final float TEXT_Y            = 23.5f;
    private static final float TEXT_SIZE         = 31f;
    private static final float ICON_SIZE         = 16.5f;

    private static final float BOTTOM_DIVIDER_X  = 34f;
    private static final float TOP_FIELD_RIGHT_PAD = 15f;
    private static final float TOP_FIELD_GAP     = 10f;

    private static final float DIVIDER_Y         = 6f;
    private static final float DIVIDER_HEIGHT    = 21f;

    private static final String PERSON_ICON = "j";

    private static final float LOGO_CARD_WIDTH   = 38f;
    private static final float LOGO_CARD_HEIGHT  = CARD_HEIGHT;
    private static final float LOGO_CARD_RADIUS  = 9f;
    private static final String LOGO_GLYPH       = "\uE900";
    private static final float LOGO_SIZE         = 18f;

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm");

    private float boundsW;
    private float boundsH = CARD_HEIGHT * 2f + ROW_GAP;

    private double windowStartX   = Double.NaN;
    private double windowStartZ   = Double.NaN;
    private long   windowStartNanos = 0L;
    private double displayedBps   = 0.0;
    private static final double BPS_WINDOW_SECONDS = 0.25;

    public WatermarkElement() {
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
                       int screenWidth, int screenHeight, DrawContext ctx) {

        MinecraftClient mc = MinecraftClient.getInstance();

        int themeColor = Renderer2D.ColorUtil.getClientColor1();
        int textColor  = ColorUtil.replAlpha(-1, 255);

        String playerName = mc.player != null
                ? mc.player.getGameProfile().name()
                : System.getProperty("user.name");

        String fps      = Integer.toString(mc.getCurrentFps());
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

        // Верхняя панель — справа от логотипа
        float topX       = x + LOGO_CARD_WIDTH + CARD_GAP;
        float secondRowY = y + CARD_HEIGHT + ROW_GAP;

        // Нижняя панель — начинается с x (под логотипом, с самого края)
        float bottomX = x;
        float bpsX    = bottomX + (showCoordinates() ? positionW + CARD_GAP : 0f);

        // ---- ЛОГОТИП ----
        drawLogoCard(renderer, x, y);
        drawLogo(renderer, x, y, themeColor);

        // ---- ВЕРХНЯЯ ПАНЕЛЬ ----
        if (visibleTop > 0) {
            drawCard(renderer, topX, y, topW);
            float statX = topX;
            if (showNickname()) {
                statX = drawTopInfo(renderer, statX, y, playerName, playerW,
                        themeColor, textColor, visibleTop > 1);
            }
            if (showFps()) {
                statX = drawTopSplitInfo(renderer, statX, y, fps, " FPS", fpsW,
                        themeColor, textColor, showPing() || showTime(),
                        IconFont.ESSENCE, "x", "watermark_fps");
            }
            if (showPing()) {
                statX = drawTopSplitInfo(renderer, statX, y, ping, " Ping", pingW,
                        themeColor, textColor, showTime(),
                        IconFont.ESSENCE, "p", "watermark_ping");
            }
            if (showTime()) {
                drawTopSplitInfo(renderer, statX, y, time, " Time", timeW,
                        themeColor, textColor, false,
                        IconFont.ESSENCE, "t", "watermark_time");
            }
        }

        // ---- НИЖНЯЯ ПАНЕЛЬ (начинается с самого левого края, под логотипом) ----
        boolean hasBottom = showCoordinates() || showBps();
        if (hasBottom) {
            if (showCoordinates()) {
                drawCard(renderer, bottomX, secondRowY, positionW);
                drawBottomInfo(renderer, bottomX, secondRowY, position,
                        themeColor, textColor, IconFont.ICON, "n");
            }
            if (showBps()) {
                drawCard(renderer, bpsX, secondRowY, bpsW);
                drawBottomSplitInfo(renderer, bpsX, secondRowY, bps, " BPS",
                        themeColor, textColor, IconFont.ESSENCE, "d");
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

    /* ================================================================ ЛОГОТИП */

    private void drawLogoCard(Renderer2D renderer, float cardX, float cardY) {
        int background = ColorUtil.multAlpha(
                ColorUtil.multDark(Renderer2D.ColorUtil.getClientColor(), 0.12f),
                ClickGui.getHudAlpha()
        );
        renderer.blur(cardX, cardY, LOGO_CARD_WIDTH, LOGO_CARD_HEIGHT,
                ClickGui.blurStrengthHud.get().floatValue(), LOGO_CARD_RADIUS, 1);
        renderer.rect(cardX, cardY, LOGO_CARD_WIDTH, LOGO_CARD_HEIGHT, LOGO_CARD_RADIUS, background);
    }

    private void drawLogo(Renderer2D renderer, float cardX, float cardY, int color) {
        float glyphX = cardX + (LOGO_CARD_WIDTH - LOGO_SIZE) / 2f - 4.5f;
        float glyphY = cardY + (LOGO_CARD_HEIGHT - LOGO_SIZE) / 2f;
        TtfFonts.LOGO.drawString(renderer, LOGO_GLYPH, glyphX, glyphY, LOGO_SIZE, color);
    }

    /* ================================================================ BPS */

    private double updateAndGetBps(MinecraftClient mc) {
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

    /* ================================================================ КАРТОЧКА */

    private void drawCard(Renderer2D renderer, float cardX, float cardY, float width) {
        int background = ColorUtil.multAlpha(
                ColorUtil.multDark(Renderer2D.ColorUtil.getClientColor(), 0.12f),
                ClickGui.getHudAlpha()
        );
        renderer.blur(cardX, cardY, width, CARD_HEIGHT,
                ClickGui.blurStrengthHud.get().floatValue(), 9, 1);
        renderer.rect(cardX, cardY, width, CARD_HEIGHT, 9, background);
    }

    /* ================================================================ TOP */

    private float drawTopInfo(Renderer2D renderer, float fieldX, float cardY,
                              String value, float width, int themeColor,
                              int textColor, boolean divider) {
        drawPersonIcon(renderer, fieldX + TOP_ICON_X - 1.5f, cardY + 2, themeColor);
        renderer.text(FontRegistry.SF_MEDIUM, fieldX + TOP_TEXT_X, cardY + TEXT_Y,
                TEXT_SIZE, value, textColor, -0.1f);
        if (divider) drawDivider(renderer, fieldX + width, cardY);
        return fieldX + width + TOP_FIELD_GAP;
    }

    private float drawTopSplitInfo(Renderer2D renderer, float fieldX, float cardY,
                                   String accent, String suffix, float width,
                                   int themeColor, int textColor, boolean divider,
                                   IconFont iconFont, String iconGlyph, String animationKey) {
        drawIcon(renderer, iconFont, iconGlyph, fieldX + TOP_ICON_X, cardY + 2, themeColor);
        float textX = fieldX + TOP_TEXT_X;
        float accentW;
        if (animatedEnabled()) {
            accentW = AnimatedText.draw(renderer, FontRegistry.SF_MEDIUM, animationKey,
                    accent, textX, cardY + TEXT_Y, TEXT_SIZE, themeColor, AnimatedText.ALIGN_LEFT);
        } else {
            renderer.text(FontRegistry.SF_MEDIUM, textX, cardY + TEXT_Y, TEXT_SIZE, accent, themeColor, -0.1f);
            accentW = renderer.measureText(FontRegistry.SF_MEDIUM, accent, TEXT_SIZE, -0.1f).width;
        }
        renderer.text(FontRegistry.SF_MEDIUM, textX + accentW, cardY + TEXT_Y,
                TEXT_SIZE, suffix, textColor, -0.1f);
        if (divider) drawDivider(renderer, fieldX + width, cardY);
        return fieldX + width + TOP_FIELD_GAP;
    }

    /* ================================================================ BOTTOM */

    private void drawBottomInfo(Renderer2D renderer, float cardX, float cardY,
                                String value, int themeColor, int textColor,
                                IconFont iconFont, String iconGlyph) {
        drawIcon(renderer, iconFont, iconGlyph, cardX + BOTTOM_ICON_X + 2, cardY + 1, themeColor);
        drawDivider(renderer, cardX + BOTTOM_DIVIDER_X, cardY);
        if (animatedEnabled()) {
            AnimatedText.draw(renderer, FontRegistry.SF_MEDIUM, "watermark_position",
                    value, cardX + BOTTOM_TEXT_X, cardY + TEXT_Y, TEXT_SIZE,
                    textColor, AnimatedText.ALIGN_LEFT);
        } else {
            renderer.text(FontRegistry.SF_MEDIUM, cardX + BOTTOM_TEXT_X, cardY + TEXT_Y,
                    TEXT_SIZE, value, textColor, -0.1f);
        }
    }

    private void drawBottomSplitInfo(Renderer2D renderer, float cardX, float cardY,
                                     String accent, String suffix, int themeColor,
                                     int textColor, IconFont iconFont, String iconGlyph) {
        drawIcon(renderer, iconFont, iconGlyph, cardX + BOTTOM_ICON_X + 2, cardY + 1, themeColor);
        drawDivider(renderer, cardX + BOTTOM_DIVIDER_X, cardY);
        float textX = cardX + BOTTOM_TEXT_X;
        float accentW;
        if (animatedEnabled()) {
            accentW = AnimatedText.draw(renderer, FontRegistry.SF_MEDIUM, "watermark_bps",
                    accent, textX, cardY + TEXT_Y, TEXT_SIZE, themeColor, AnimatedText.ALIGN_LEFT);
        } else {
            renderer.text(FontRegistry.SF_MEDIUM, textX, cardY + TEXT_Y, TEXT_SIZE, accent, themeColor, -0.1f);
            accentW = renderer.measureText(FontRegistry.SF_MEDIUM, accent, TEXT_SIZE, -0.1f).width;
        }
        renderer.text(FontRegistry.SF_MEDIUM, textX + accentW, cardY + TEXT_Y,
                TEXT_SIZE, suffix, textColor, -0.1f);
    }

    /* ================================================================ ICONS */

    private void drawPersonIcon(Renderer2D renderer, float iconX, float cardY, int color) {
        drawIcon(renderer, IconFont.LOX, PERSON_ICON, iconX, cardY, color);
    }

    private void drawIcon(Renderer2D renderer, IconFont font, String glyph,
                          float iconX, float cardY, int color) {
        switch (font) {
            case LOX     -> TtfFonts.LOX.drawString(renderer, glyph, iconX, cardY + ICON_Y, ICON_SIZE, color);
            case ESSENCE -> TtfFonts.ESSENCE.drawString(renderer, glyph, iconX, cardY + ICON_Y, ICON_SIZE, color);
            case ICON    -> TtfFonts.ICONNEW.drawString(renderer, glyph, iconX, cardY + ICON_Y, ICON_SIZE, color);
        }
    }

    private enum IconFont { LOX, ESSENCE, ICON }

    /* ================================================================ DIVIDER */

    private void drawDivider(Renderer2D renderer, float dividerX, float cardY) {
        renderer.rect(dividerX, cardY + DIVIDER_Y, 1f, DIVIDER_HEIGHT,
                ColorUtil.replAlpha(-1, 15));
    }

    private float topFieldWidth(Renderer2D renderer, String value) {
        return TOP_TEXT_X + textWidth(renderer, value) + TOP_FIELD_RIGHT_PAD;
    }

    private float bottomFieldWidth(Renderer2D renderer, String value) {
        return BOTTOM_TEXT_X + textWidth(renderer, value) + CARD_PADDING;
    }

    private float textWidth(Renderer2D renderer, String text) {
        return renderer.measureText(FontRegistry.SF_MEDIUM, text, TEXT_SIZE, -0.1f).width;
    }

    /* ================================================================ PLAYER */

    private static String playerPosition(MinecraftClient mc) {
        if (mc.player == null) return "0x 0y 0z";
        return (int) Math.floor(mc.player.getX()) + "x "
                + (int) Math.floor(mc.player.getY()) + "y "
                + (int) Math.floor(mc.player.getZ()) + "z";
    }

    private static String playerPing(MinecraftClient mc) {
        if (mc.player == null || mc.getNetworkHandler() == null) return "0";
        var entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        return entry == null ? "0" : Integer.toString(entry.getLatency());
    }

    /* ================================================================ HUD SIZE */

    @Override public float getWidth(Renderer2D renderer, FontObject font)          { return boundsW; }
    @Override public float getHeight(Renderer2D renderer, FontObject font)         { return boundsH; }
    @Override public float getEffectiveWidth(Renderer2D renderer, FontObject font) { return boundsW; }
}