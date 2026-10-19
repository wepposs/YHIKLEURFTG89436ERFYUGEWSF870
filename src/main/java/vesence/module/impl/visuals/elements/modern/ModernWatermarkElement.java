package vesence.module.impl.visuals.elements.modern;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import vesence.module.impl.visuals.HudElement;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;
import vesence.utils.render.text.ColorFormat;
import vesence.utils.render.text.FontObject;
import vesence.utils.render.text.FontRegistry;
import vesence.utils.render.ttf.TtfFonts;

@Environment(EnvType.CLIENT)
public class ModernWatermarkElement extends HudElement {

    private static final float PANEL_PAD  = 6f;
    private static final float ICON_COL_W = 35f;
    private static final float COL_GAP    = 6f;
    private static final float HEADER_H   = 38;
    private static final float TITLE_SIZE = 33f;
    private static final int   PILL_ALPHA = 12;

    private static final float PILL_ICON_X = 11f;
    private static final float PILL_TEXT_X = 38f;
    private static final float PILL_RIGHT_PAD = 16f;
    private static final float PILL_GAP = 6f;
    private static final float PILL_START_OFFSET = 7f;

    private static final String PILL_ICON = "j";

    /* ================================================================
       ЛОГОТИП
       ================================================================ */

    private static final String LOGO_GLYPH = "\uE900";

    // Размер глифа логотипа.
    private static final float LOGO_SIZE   = 22f;

    // Свечение под логотипом.
    private static final float LOGO_GLOW   = 1.85f;

    // Подстройка положения логотипа внутри иконной колонки.
    private static final float LOGO_OFFSET_X = -2f;
    private static final float LOGO_OFFSET_Y = 0f;

    private final Animation2 widthAnim = new Animation2();
    private float boundsW = 200f;

    public ModernWatermarkElement() {
        super("Watermark", 10f, 10f);
        this.enabled = false;
        widthAnim.set(200);
    }

    @Override
    public void render(Renderer2D r, FontObject font, int screenWidth, int screenHeight, GuiGraphics ctx) {
        int theme = Renderer2D.ColorUtil.getClientColor();
        Minecraft mc = Minecraft.getInstance();

        String ping = (mc.getConnection() != null && mc.player != null
                && mc.getConnection().getPlayerListEntry(mc.player.getUuid()) != null)
                ? String.valueOf(mc.getConnection().getPlayerListEntry(mc.player.getUuid()).getLatency())
                : "0 ms";
        String user = System.getProperty("user.name");
        String fps = "FPS: " + ColorFormat.color(theme) + mc.getCurrentFps();

        float w1 = PILL_TEXT_X + r.measureText(FontRegistry.SF_SEMI, user, TITLE_SIZE, -0.15f).width + PILL_RIGHT_PAD;
        float w2 = PILL_TEXT_X + r.measureText(FontRegistry.SF_SEMI, fps, TITLE_SIZE, -0.15f).width + PILL_RIGHT_PAD;
        float w3 = PILL_TEXT_X + r.measureText(FontRegistry.SF_SEMI, "Ping: " + ping, TITLE_SIZE, -0.15f).width + PILL_RIGHT_PAD;

        float startX = PANEL_PAD + ICON_COL_W + COL_GAP + PILL_START_OFFSET;
        float targetInner = w1 + w2 + w3 + PILL_GAP * 2f;
        float panelTarget = startX + targetInner + PANEL_PAD;

        widthAnim.update();
        widthAnim.run(panelTarget, 0.2, Easings.SINE_OUT, true);
        float panelW = (float) widthAnim.get();
        float panelH = PANEL_PAD * 2f + HEADER_H;

        // Плашки масштабируются вместе с анимированной шириной панели, чтобы расширяться плавно.
        float animInner = panelW - startX - PANEL_PAD;
        float sc = targetInner > 0.5f ? Math.max(0f, animInner / targetInner) : 1f;
        float dw1 = w1 * sc, dw2 = w2 * sc, dw3 = w3 * sc, gap = PILL_GAP * sc;

        int pillBg = ColorUtil.replAlpha(-1, PILL_ALPHA);
        int iconColor = ColorUtil.replAlpha(theme, 230);
        int textColor = ColorUtil.replAlpha(-1, 255);
        int panelBg = ColorUtil.replAlpha(ColorUtil.BLACK, 155);

        // ── Верхний рект: user / fps / ping ──
        r.blur(x, y, panelW, panelH, 15, 12, 1f);
        r.rect(x, y, panelW, panelH, 12, panelBg);

        float headerTop = y + PANEL_PAD;

        // ---- ЛОГОТИП ----
        // Центр иконной колонки: x + PANEL_PAD .. x + PANEL_PAD + ICON_COL_W
        float iconColCenterX = x + PANEL_PAD + ICON_COL_W / 2f;
        float iconColCenterY = headerTop + HEADER_H / 2f;

        float logoX = iconColCenterX - LOGO_SIZE / 2f + LOGO_OFFSET_X;
        float logoY = iconColCenterY - LOGO_SIZE / 2f + LOGO_OFFSET_Y;

        TtfFonts.LOGO.drawStringWithGlow(
                r, LOGO_GLYPH,
                logoX, logoY,
                LOGO_SIZE, iconColor, LOGO_GLOW
        );

        float iconY = headerTop + HEADER_H / 2f - 8;
        float textY = headerTop + HEADER_H / 2f + 7.5f;
        float px = x + startX;

        r.rect(px, headerTop, dw1, HEADER_H, 3, 3, 3, 3, pillBg);
        TtfFonts.LOX.drawString(r, PILL_ICON, px + PILL_ICON_X, iconY, 18, iconColor);
        r.text(FontRegistry.SF_SEMI, px + PILL_TEXT_X, textY, TITLE_SIZE, user, textColor, -0.15f);
        px += dw1 + gap;

        r.rect(px, headerTop, dw2, HEADER_H, 3, 3, 3, 3, pillBg);
        TtfFonts.LOX.drawString(r, "b", px + PILL_ICON_X, iconY, 18, iconColor);
        r.text(FontRegistry.SF_SEMI, px + PILL_TEXT_X, textY, TITLE_SIZE, fps, textColor, -0.15f);
        px += dw2 + gap;

        r.rect(px, headerTop, dw3, HEADER_H, 3, 6, 6, 3, pillBg);
        TtfFonts.ICONNEW.drawString(r, "y", px + PILL_ICON_X, iconY, 18, iconColor);
        r.text(FontRegistry.SF_SEMI, px + PILL_TEXT_X, textY, TITLE_SIZE,
                "Ping: " + ColorFormat.color(theme) + ping, textColor, -0.15f);

        // ── Нижний рект: ip сервера / время ПК ──
        String ip = (mc.getCurrentServerEntry() != null && mc.getCurrentServerEntry().address != null)
                ? mc.getCurrentServerEntry().address
                : (mc.getServer() != null ? "Singleplayer" : "-");
        String time = new java.text.SimpleDateFormat("HH:mm").format(new java.util.Date());

        float wip   = PILL_TEXT_X + r.measureText(FontRegistry.SF_SEMI, ip, TITLE_SIZE, -0.15f).width + PILL_RIGHT_PAD;
        float wtime = PILL_TEXT_X + r.measureText(FontRegistry.SF_SEMI, time, TITLE_SIZE, -0.15f).width + PILL_RIGHT_PAD;
        float panel2W = PANEL_PAD + wip + PILL_GAP + wtime + PANEL_PAD;
        float panel2Y = y + panelH + 6f;

        boundsW = Math.max(panelW, panel2W);

        r.blur(x, panel2Y, panel2W, panelH, 15, 12, 1f);
        r.rect(x, panel2Y, panel2W, panelH, 12, panelBg);

        float headerTop2 = panel2Y + PANEL_PAD;
        float iconY2 = headerTop2 + HEADER_H / 2f - 8;
        float textY2 = headerTop2 + HEADER_H / 2f + 7.5f;
        float px2 = x + PANEL_PAD;

        r.rect(px2, headerTop2, wip, HEADER_H, 6, 3, 3, 6, pillBg);
        TtfFonts.ESSENCE.drawString(r, "n", px2 + PILL_ICON_X, iconY2, 18, iconColor);
        r.text(FontRegistry.SF_SEMI, px2 + PILL_TEXT_X, textY2, TITLE_SIZE, ip, textColor, -0.15f);
        px2 += wip + PILL_GAP;

        r.rect(px2, headerTop2, wtime, HEADER_H, 3, 6, 6, 3, pillBg);
        TtfFonts.ESSENCE.drawString(r, "x", px2 + PILL_ICON_X, iconY2, 18, iconColor);
        r.text(FontRegistry.SF_SEMI, px2 + PILL_TEXT_X, textY2, TITLE_SIZE, time, textColor, -0.15f);
    }

    @Override
    public float getWidth(Renderer2D r, FontObject font) {
        return boundsW;
    }

    @Override
    public float getHeight(Renderer2D r, FontObject font) {
        return (PANEL_PAD * 2f + HEADER_H) * 2f + 6f;
    }

    @Override
    public float getEffectiveWidth(Renderer2D r, FontObject font) {
        return boundsW;
    }
}