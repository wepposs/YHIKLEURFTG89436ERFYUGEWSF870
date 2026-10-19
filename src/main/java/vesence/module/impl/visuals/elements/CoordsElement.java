package vesence.module.impl.visuals.elements;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import vesence.module.impl.visuals.HudElement;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.BorderRadius;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;
import vesence.utils.render.text.FontObject;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Environment(EnvType.CLIENT)
public class    CoordsElement extends HudElement {

    private static final float PADDING_H = 11;
    private static final float PADDING_V = 11;
    private static final float FONT_SIZE  = 27;

    private static final int WHITE_COLOR = 0xFFFFFFFF;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Animation2 widthAnim = new Animation2();
    private final Animation2 heightAnim = new Animation2();

    public CoordsElement() {
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
    public void render(Renderer2D renderer, FontObject font, int screenWidth, int screenHeight, GuiGraphics ctx) {
        Minecraft mc = Minecraft.getInstance();
        int bx = mc.player != null ? (int) Math.floor(mc.player.getX()) : 0;
        int by = mc.player != null ? (int) Math.floor(mc.player.getY()) : 0;
        int bz = mc.player != null ? (int) Math.floor(mc.player.getZ()) : 0;
        float bps = getBps(mc);
        DateTimeFormatter TIME_FMT2 = DateTimeFormatter.ofPattern("HH:mm:ss");
        String timeStr = LocalTime.now().format(TIME_FMT2);

        int themeColor = Renderer2D.ColorUtil.getClientColor();
        int semiThemeColor = Renderer2D.ColorUtil.replAlpha(themeColor, 100);
        int sepColor = Renderer2D.ColorUtil.getColor(255, 255, 255, 35);

        String coordsLabel = "Coords ";
        String xVal = String.valueOf(bx);
        String xLetter = "x ";
        String yVal = String.valueOf(by);
        String yLetter = "y ";
        String zVal = String.valueOf(bz);
        String zLetter = "z";
        String sep1 = " | ";
        String timeLabel = "Time: ";
        String timeValue = timeStr;
        String sep2 = " | ";
        String bpsLabel = "BPS: ";
        String bpsValue = String.valueOf(bps);

        float coordsLabelW = renderer.measureText(font, coordsLabel, FONT_SIZE).width;
        float xValW = renderer.measureText(font, xVal, FONT_SIZE).width;
        float xLetterW = renderer.measureText(font, xLetter, FONT_SIZE).width;
        float yValW = renderer.measureText(font, yVal, FONT_SIZE).width;
        float yLetterW = renderer.measureText(font, yLetter, FONT_SIZE).width;
        float zValW = renderer.measureText(font, zVal, FONT_SIZE).width;
        float zLetterW = renderer.measureText(font, zLetter, FONT_SIZE).width;
        float sep1W = renderer.measureText(font, sep1, FONT_SIZE).width;
        float timeLabelW = renderer.measureText(font, timeLabel, FONT_SIZE).width;
        float timeValueW = renderer.measureText(font, timeValue, FONT_SIZE).width;
        float sep2W = renderer.measureText(font, sep2, FONT_SIZE).width;
        float bpsLabelW = renderer.measureText(font, bpsLabel, FONT_SIZE).width;
        float bpsValueW = renderer.measureText(font, bpsValue, FONT_SIZE).width;

        float totalW = coordsLabelW + xValW + xLetterW + yValW + yLetterW + zValW + zLetterW
                + sep1W + timeLabelW + timeValueW + sep2W + bpsLabelW + bpsValueW;
        float textH = renderer.measureText(font, coordsLabel, FONT_SIZE).height;
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

        renderer.text(font, curX, curY, FONT_SIZE, coordsLabel, WHITE_COLOR);
        curX += coordsLabelW;

        curX += vesence.utils.render.text.AnimatedText.draw(renderer, font, "info_x", xVal,
              curX, curY, FONT_SIZE, themeColor, vesence.utils.render.text.AnimatedText.ALIGN_LEFT);
        renderer.text(font, curX, curY, FONT_SIZE, xLetter, semiThemeColor);
        curX += xLetterW;

        curX += vesence.utils.render.text.AnimatedText.draw(renderer, font, "info_y", yVal,
              curX, curY, FONT_SIZE, themeColor, vesence.utils.render.text.AnimatedText.ALIGN_LEFT);
        renderer.text(font, curX, curY, FONT_SIZE, yLetter, semiThemeColor);
        curX += yLetterW;

        curX += vesence.utils.render.text.AnimatedText.draw(renderer, font, "info_z", zVal,
              curX, curY, FONT_SIZE, themeColor, vesence.utils.render.text.AnimatedText.ALIGN_LEFT);
        renderer.text(font, curX, curY, FONT_SIZE, zLetter, semiThemeColor);
        curX += zLetterW;

        renderer.text(font, curX, curY, FONT_SIZE, sep1, sepColor);
        curX += sep1W;

        renderer.text(font, curX, curY, FONT_SIZE, timeLabel, WHITE_COLOR);
        curX += timeLabelW;
        curX += vesence.utils.render.text.AnimatedText.draw(renderer, font, "info_time", timeValue,
              curX, curY, FONT_SIZE, themeColor, vesence.utils.render.text.AnimatedText.ALIGN_LEFT);

        renderer.text(font, curX, curY, FONT_SIZE, sep2, sepColor);
        curX += sep2W;

        renderer.text(font, curX, curY, FONT_SIZE, bpsLabel, WHITE_COLOR);
        curX += bpsLabelW;
        vesence.utils.render.text.AnimatedText.draw(renderer, font, "info_bps", bpsValue,
              curX, curY, FONT_SIZE, themeColor, vesence.utils.render.text.AnimatedText.ALIGN_LEFT);
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
        return renderer.measureText(font, full, FONT_SIZE).width + PADDING_H * 2f;
    }

    @Override
    public float getHeight(Renderer2D renderer, FontObject font) {
        return renderer.measureText(font, "Coords", FONT_SIZE).height + PADDING_V * 2f;
    }

    @Override
    public float getEffectiveWidth(Renderer2D renderer, FontObject font) {
        return (float) widthAnim.get();
    }
}
