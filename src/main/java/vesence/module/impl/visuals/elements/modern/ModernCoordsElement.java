package vesence.module.impl.visuals.elements.modern;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import vesence.module.impl.visuals.HudElement;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;
import vesence.utils.render.text.ColorFormat;
import vesence.utils.render.text.FontObject;
import vesence.utils.render.text.FontRegistry;
import vesence.utils.render.ttf.TtfFonts;

import java.util.Locale;

@Environment(EnvType.CLIENT)
public class ModernCoordsElement extends HudElement {

    private static final float PANEL_PAD = 6f;
    private static final float ICON_COL_W = 35f;
    private static final float COL_GAP = 8f;
    private static final float HEADER_H = 38f;
    private static final float TITLE_SIZE = 33f;
    private static final int PILL_ALPHA = 12;

    private static final float PILL_ICON_X = 11f;
    private static final float PILL_TEXT_X = 34f;
    private static final float PILL_RIGHT_PAD = 15f;
    private static final float PILL_GAP = 9f;
    private static final float PILL_START_OFFSET = 7f;

    private static final String INFO_GLYPH = "n";
    private static final String BPS_GLYPH = "j";
    private static final String COORDS_GLYPH = "y";

    private final Animation2 widthAnim = new Animation2();
    private float boundsW = 255f;

    public ModernCoordsElement() {
        super("Informations", 10f, 40f);
        widthAnim.set(255f);
    }

    @Override
    public void render(Renderer2D r, FontObject font, int screenWidth, int screenHeight, GuiGraphicsExtractor ctx) {
        Minecraft mc = Minecraft.getInstance();
        int bx = mc.player != null ? (int) Math.floor(mc.player.getX()) : 0;
        int by = mc.player != null ? (int) Math.floor(mc.player.getY()) : 0;
        int bz = mc.player != null ? (int) Math.floor(mc.player.getZ()) : 0;
        String bpsValue = String.format(Locale.US, "%.1f", getBps(mc));

        int theme = Renderer2D.ColorUtil.getClientColor();
        int iconColor = ColorUtil.replAlpha(theme, 230);
        int textColor = ColorUtil.replAlpha(-1, 255);
        int pillBg = ColorUtil.replAlpha(-1, PILL_ALPHA);
        int panelBg = ColorUtil.replAlpha(ColorUtil.BLACK, 155);

        String bpsRich = ColorFormat.color(theme) + bpsValue + ColorFormat.reset() + " Bps";
        String coordsRich = ColorFormat.color(theme) + bx + ColorFormat.reset() + "x "
                + ColorFormat.color(theme) + by + ColorFormat.reset() + "y "
                + ColorFormat.color(theme) + bz + ColorFormat.reset() + "z";

        float bpsTextW = r.measureText(FontRegistry.SF_SEMI, bpsValue, TITLE_SIZE, -0.15f).width
                + r.measureText(FontRegistry.SF_SEMI, " Bps", TITLE_SIZE, -0.15f).width;
        float coordsTextW = r.measureText(FontRegistry.SF_SEMI, String.valueOf(bx), TITLE_SIZE, -0.15f).width
                + r.measureText(FontRegistry.SF_SEMI, "x ", TITLE_SIZE, -0.15f).width
                + r.measureText(FontRegistry.SF_SEMI, String.valueOf(by), TITLE_SIZE, -0.15f).width
                + r.measureText(FontRegistry.SF_SEMI, "y ", TITLE_SIZE, -0.15f).width
                + r.measureText(FontRegistry.SF_SEMI, String.valueOf(bz), TITLE_SIZE, -0.15f).width
                + r.measureText(FontRegistry.SF_SEMI, "z", TITLE_SIZE, -0.15f).width;

        float bpsPillW = PILL_TEXT_X + bpsTextW + PILL_RIGHT_PAD;
        float coordsPillW = PILL_TEXT_X + coordsTextW + PILL_RIGHT_PAD;

        float startX = PANEL_PAD + ICON_COL_W + COL_GAP + PILL_START_OFFSET;
        float targetInner = bpsPillW + coordsPillW + PILL_GAP;
        float panelTarget = startX + targetInner + PANEL_PAD;

        widthAnim.update();
        widthAnim.run(panelTarget, 0.2, Easings.SINE_OUT, true);
        float panelW = (float) widthAnim.get();
        float panelH = PANEL_PAD * 2f + HEADER_H;
        boundsW = panelW;

        float animInner = panelW - startX - PANEL_PAD;
        float scale = targetInner > 0.5f ? Math.max(0f, animInner / targetInner) : 1f;
        float drawBpsW = bpsPillW * scale;
        float drawCoordsW = coordsPillW * scale;
        float drawGap = PILL_GAP * scale;

        r.blur(x, y, panelW, panelH, 15, 12, 1f);
        r.rect(x, y, panelW, panelH, 12, panelBg);

        float headerTop = y + PANEL_PAD;
        float pillTextY = headerTop + HEADER_H / 2f + 7.5f;
        float pillIconY = headerTop + HEADER_H / 2f;

        TtfFonts.ICONS.drawStringWithGlow(r, "G",
                x + PANEL_PAD + ICON_COL_W / 2f - 7.5f,
                headerTop + HEADER_H / 2f - 8.5f,
                22f, iconColor);

        float px = x + startX;
        r.rect(px, headerTop, drawBpsW, HEADER_H, 3, 3, 3, 3, pillBg);
        TtfFonts.ICONS.drawStringWithGlow(r, "A", px + PILL_ICON_X, pillIconY - 8f, 18f, iconColor);
        r.text(FontRegistry.SF_SEMI, px + PILL_TEXT_X, pillTextY, TITLE_SIZE, bpsRich, textColor, -0.15f);
        px += drawBpsW + drawGap;

        r.rect(px, headerTop, drawCoordsW, HEADER_H, 3, 6, 6, 3, pillBg);
        TtfFonts.ICONNEW.drawStringWithGlow(r, COORDS_GLYPH, px + PILL_ICON_X, pillIconY - 8f, 18f, iconColor);
        r.text(FontRegistry.SF_SEMI, px + PILL_TEXT_X, pillTextY, TITLE_SIZE, coordsRich, textColor, -0.15f);
    }

    private float getBps(Minecraft mc) {
        if (mc.player == null) return 0f;
        double dx = mc.player.getDeltaMovement().x;
        double dz = mc.player.getDeltaMovement().z;
        return Math.round((float) (Math.sqrt(dx * dx + dz * dz) * 20f) * 10f) / 10f;
    }

    @Override
    public float getWidth(Renderer2D r, FontObject font) {
        return boundsW;
    }

    @Override
    public float getHeight(Renderer2D r, FontObject font) {
        return PANEL_PAD * 2f + HEADER_H;
    }

    @Override
    public float getEffectiveWidth(Renderer2D r, FontObject font) {
        return (float) widthAnim.get();
    }
}
