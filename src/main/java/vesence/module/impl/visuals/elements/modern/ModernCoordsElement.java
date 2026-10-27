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
import vesence.utils.render.text.AnimatedText;
import vesence.utils.render.text.FontObject;
import vesence.utils.render.text.FontRegistry;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Informations: компоновка Default-версии — одна карточка со строкой
 * «Coords Xx Yy Zz | Time: HH:mm:ss | BPS: N», значения анимируются
 * через {@link AnimatedText}. Дизайн modern: чёрная карточка с тенью и
 * blur из настроек, шрифт SF_SEMI.
 */
@Environment(EnvType.CLIENT)
public class ModernCoordsElement extends HudElement {

    private static final float PADDING_H = 11;
    private static final float PADDING_V = 11;
    private static final float FONT_SIZE  = 27;

    private static final int WHITE_COLOR = 0xFFFFFFFF;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Animation2 widthAnim = new Animation2();
    private final Animation2 heightAnim = new Animation2();

    public ModernCoordsElement() {
        super("Informations", 10f, 40f);
        widthAnim.set(200);
        heightAnim.set(30);
    }

    private float getBps(Minecraft mc) {
        if (mc.player == null) return 0f;
        double dx = mc.player.getDeltaMovement().x;
        double dz = mc.player.getDeltaMovement().z;
        return Math.round((float)(Math.sqrt(dx * dx + dz * dz) * 20f) * 10f) / 10f;
    }

    @Override
    public void render(Renderer2D renderer, FontObject font, int screenWidth, int screenHeight, GuiGraphicsExtractor ctx) {
        if (FontRegistry.SF_SEMI == null) return;
        FontObject f = FontRegistry.SF_SEMI;

        Minecraft mc = Minecraft.getInstance();
        int bx = mc.player != null ? (int) Math.floor(mc.player.getX()) : 0;
        int by = mc.player != null ? (int) Math.floor(mc.player.getY()) : 0;
        int bz = mc.player != null ? (int) Math.floor(mc.player.getZ()) : 0;
        float bps = getBps(mc);
        String timeStr = LocalTime.now().format(TIME_FMT);

        int themeColor = Renderer2D.ColorUtil.getClientColor();
        int semiThemeColor = ColorUtil.replAlpha(themeColor, 100);
        int sepColor = ColorUtil.getColor(255, 255, 255, 45);

        String coordsLabel = "Coords ";
        String xVal = String.valueOf(bx);
        String xLetter = "x ";
        String yVal = String.valueOf(by);
        String yLetter = "y ";
        String zVal = String.valueOf(bz);
        String zLetter = "z";
        String sep1 = " | ";
        String timeLabel = "Time: ";
        String sep2 = " | ";
        String bpsLabel = "BPS: ";
        String bpsValue = String.valueOf(bps);

        float coordsLabelW = renderer.measureText(f, coordsLabel, FONT_SIZE, -0.15f).width;
        float xValW = renderer.measureText(f, xVal, FONT_SIZE, -0.15f).width;
        float xLetterW = renderer.measureText(f, xLetter, FONT_SIZE, -0.15f).width;
        float yValW = renderer.measureText(f, yVal, FONT_SIZE, -0.15f).width;
        float yLetterW = renderer.measureText(f, yLetter, FONT_SIZE, -0.15f).width;
        float zValW = renderer.measureText(f, zVal, FONT_SIZE, -0.15f).width;
        float zLetterW = renderer.measureText(f, zLetter, FONT_SIZE, -0.15f).width;
        float sep1W = renderer.measureText(f, sep1, FONT_SIZE, -0.15f).width;
        float timeLabelW = renderer.measureText(f, timeLabel, FONT_SIZE, -0.15f).width;
        float timeValueW = renderer.measureText(f, timeStr, FONT_SIZE, -0.15f).width;
        float sep2W = renderer.measureText(f, sep2, FONT_SIZE, -0.15f).width;
        float bpsLabelW = renderer.measureText(f, bpsLabel, FONT_SIZE, -0.15f).width;
        float bpsValueW = renderer.measureText(f, bpsValue, FONT_SIZE, -0.15f).width;

        float totalW = coordsLabelW + xValW + xLetterW + yValW + yLetterW + zValW + zLetterW
                + sep1W + timeLabelW + timeValueW + sep2W + bpsLabelW + bpsValueW;
        float textH = renderer.measureText(f, coordsLabel, FONT_SIZE, -0.15f).height;
        float targetW = totalW + PADDING_H * 2f;
        float targetH = textH + PADDING_V * 2f;

        widthAnim.update();
        heightAnim.update();
        widthAnim.run(targetW, 0.2, Easings.SINE_OUT, true);
        heightAnim.run(targetH, 0.2, Easings.SINE_OUT, true);

        float rectW = (float) widthAnim.get();
        float rectH = (float) heightAnim.get();

        drawHudPanel(renderer, x, y, rectW, rectH, 1f);

        float curX = x + PADDING_H - 1;
        float curY = y + PADDING_V + 14;

        renderer.text(f, curX, curY, FONT_SIZE, coordsLabel, WHITE_COLOR, -0.15f);
        curX += coordsLabelW;

        curX += AnimatedText.draw(renderer, f, "info_x", xVal, curX, curY, FONT_SIZE, themeColor, AnimatedText.ALIGN_LEFT);
        renderer.text(f, curX, curY, FONT_SIZE, xLetter, semiThemeColor, -0.15f);
        curX += xLetterW;

        curX += AnimatedText.draw(renderer, f, "info_y", yVal, curX, curY, FONT_SIZE, themeColor, AnimatedText.ALIGN_LEFT);
        renderer.text(f, curX, curY, FONT_SIZE, yLetter, semiThemeColor, -0.15f);
        curX += yLetterW;

        curX += AnimatedText.draw(renderer, f, "info_z", zVal, curX, curY, FONT_SIZE, themeColor, AnimatedText.ALIGN_LEFT);
        renderer.text(f, curX, curY, FONT_SIZE, zLetter, semiThemeColor, -0.15f);
        curX += zLetterW;

        renderer.text(f, curX, curY, FONT_SIZE, sep1, sepColor, -0.15f);
        curX += sep1W;

        renderer.text(f, curX, curY, FONT_SIZE, timeLabel, WHITE_COLOR, -0.15f);
        curX += timeLabelW;
        curX += AnimatedText.draw(renderer, f, "info_time", timeStr, curX, curY, FONT_SIZE, themeColor, AnimatedText.ALIGN_LEFT);

        renderer.text(f, curX, curY, FONT_SIZE, sep2, sepColor, -0.15f);
        curX += sep2W;

        renderer.text(f, curX, curY, FONT_SIZE, bpsLabel, WHITE_COLOR, -0.15f);
        curX += bpsLabelW;
        AnimatedText.draw(renderer, f, "info_bps", bpsValue, curX, curY, FONT_SIZE, themeColor, AnimatedText.ALIGN_LEFT);
    }

    @Override
    public float getWidth(Renderer2D renderer, FontObject font) {
        Minecraft mc = Minecraft.getInstance();
        int bx = mc.player != null ? (int) Math.floor(mc.player.getX()) : 0;
        int by = mc.player != null ? (int) Math.floor(mc.player.getY()) : 0;
        int bz = mc.player != null ? (int) Math.floor(mc.player.getZ()) : 0;
        float bps = getBps(mc);
        String timeStr = LocalTime.now().format(TIME_FMT);
        String full = "Coords " + bx + "x " + by + "y " + bz + "z | Time: " + timeStr + " | BPS: " + bps;
        FontObject f = FontRegistry.SF_SEMI != null ? FontRegistry.SF_SEMI : font;
        return renderer.measureText(f, full, FONT_SIZE, -0.15f).width + PADDING_H * 2f;
    }

    @Override
    public float getHeight(Renderer2D renderer, FontObject font) {
        FontObject f = FontRegistry.SF_SEMI != null ? FontRegistry.SF_SEMI : font;
        return renderer.measureText(f, "Coords", FONT_SIZE, -0.15f).height + PADDING_V * 2f;
    }

    @Override
    public float getEffectiveWidth(Renderer2D renderer, FontObject font) {
        return (float) widthAnim.get();
    }
}
