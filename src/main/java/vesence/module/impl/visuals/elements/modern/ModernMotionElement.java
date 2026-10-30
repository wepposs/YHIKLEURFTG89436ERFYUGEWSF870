package vesence.module.impl.visuals.elements.modern;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import vesence.module.impl.misc.ClickGui;
import vesence.module.impl.visuals.HudElement;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;
import vesence.utils.render.text.AnimatedText;
import vesence.utils.render.text.FontObject;
import vesence.utils.render.text.FontRegistry;
import vesence.utils.render.ttf.TtfFonts;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Motion Graph: порт {@code MotionGraphRenderer} из privatedlc (Excellent
 * 1.16.5). Линейный график скорости игрока (BPS, 100 замеров) с подписями
 * Actual/Max. В modern-дизайне vesence: чёрная карточка-заголовок, линия
 * цветом темы с градиентом, сетка-подложка, SF_SEMI.
 */
@Environment(EnvType.CLIENT)
public class ModernMotionElement extends HudElement {

    private static final float GRAPH_W    = 150f;
    private static final float GRAPH_H    = 55f;
    private static final float PADDING    = 10f;
    private static final float CARD_RADIUS = 10f;
    private static final float HEADER_BOX_HEIGHT = 33f;
    private static final float HEADER_H   = 17f;
    private static final float LABEL_FONT = 24f;
    private static final float LINE_W     = 2.2f;

    private static final int WHITE_COLOR = 0xFFFFFFFF;

    private static final int SAMPLES = 100;
    private static final float SAMPLE_INTERVAL_MS = 50f;

    private final Deque<Float> bpsHistory = new ArrayDeque<>(SAMPLES);
    private long lastSampleMs = 0L;

    private double windowStartX   = Double.NaN;
    private double windowStartZ   = Double.NaN;
    private long   windowStartNanos = 0L;
    private double displayedBps   = 0.0;
    private float  maxBps         = 0f;

    private final Animation2 visibilityAnim = new Animation2();
    private final Animation2 widthAnim = new Animation2();

    public ModernMotionElement() {
        super("Motion Graph", 10f, 560f);
        visibilityAnim.set(0.0);
        widthAnim.set(GRAPH_W + PADDING * 2f);
        this.enabled = false;
    }

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
        if (elapsed >= 0.25) {
            double dx = curX - windowStartX, dz = curZ - windowStartZ;
            displayedBps = Math.sqrt(dx * dx + dz * dz) / elapsed;
            windowStartX = curX; windowStartZ = curZ; windowStartNanos = now;
        }
        return displayedBps;
    }

    private void sampleHistory(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (now - lastSampleMs < (long) SAMPLE_INTERVAL_MS) return;
        lastSampleMs = now;

        float bps = (float) updateAndGetBps(mc);
        if (bps > maxBps) maxBps = bps;

        if (bpsHistory.size() >= SAMPLES) bpsHistory.pollFirst();
        bpsHistory.addLast(bps);
    }

    @Override
    public void render(Renderer2D renderer, FontObject font, int screenWidth, int screenHeight, GuiGraphicsExtractor ctx) {
        if (FontRegistry.SF_SEMI == null) return;
        FontObject f = FontRegistry.SF_SEMI;

        Minecraft mc = Minecraft.getInstance();
        boolean isChatOpen = mc.gui.screen() instanceof ChatScreen;

        sampleHistory(mc);

        // Превью в редакторе: синусоида.
        if (isChatOpen && bpsHistory.size() < SAMPLES) {
            float t = System.currentTimeMillis() / 1000f;
            float wave = 4f + 3.5f * (float) Math.sin(t * 1.2f);
            if (bpsHistory.size() >= SAMPLES) bpsHistory.pollFirst();
            bpsHistory.addLast(wave);
            if (wave > maxBps) maxBps = wave;
        }

        boolean hasMotion = maxBps > 0.05f;
        boolean shouldShow = isChatOpen || hasMotion;

        visibilityAnim.update();
        widthAnim.update();
        visibilityAnim.run(shouldShow ? 1.0 : 0.0, 0.15, Easings.CUBIC_OUT);

        float globalAlpha = (float) visibilityAnim.get();
        if (globalAlpha < 0.005f) return;

        int themeColor = Renderer2D.ColorUtil.getClientColor();
        int themeDark = ColorUtil.multDark(themeColor, 0.45f);

        float rectW = GRAPH_W + PADDING * 2f;
        float totalH = HEADER_BOX_HEIGHT + 6f + GRAPH_H + PADDING + LABEL_FONT + 4f;

        widthAnim.run(rectW, 0.2, Easings.CUBIC_OUT);

        // ---- Карточка-заголовок «Motion» ----
        drawHudPanel(renderer, x, y, rectW, HEADER_BOX_HEIGHT, globalAlpha);
        TtfFonts.ESSENCE.drawStringWithGlow(renderer, "d", x + 8f, y + HEADER_BOX_HEIGHT / 2f - 12f, 24,
                ColorUtil.replAlpha(themeColor, (int) (230 * globalAlpha)));
        renderer.rect(x + 38, y + 10.5f, 1, 14, ColorUtil.replAlpha(-1, (int) (20 * globalAlpha)));
        renderer.text(f, x + 48, y + PADDING_V_LABEL(), 31, "Motion", ColorUtil.replAlpha(-1, globalAlpha), -0.15f);

        // ---- Карточка графика ----
        float graphY = y + HEADER_BOX_HEIGHT + 6f;
        if (isBlurEnabled()) {
            renderer.blur(x, graphY, rectW, GRAPH_H + PADDING + LABEL_FONT + 4f, CARD_RADIUS, globalAlpha);
        }
        renderer.shadow(x, graphY, rectW, GRAPH_H + PADDING + LABEL_FONT + 4f, CARD_RADIUS, 5f, 1f,
                ColorUtil.replAlpha(ColorUtil.BLACK, (int) (45 * globalAlpha)));
        renderer.rect(x, graphY, rectW, GRAPH_H + PADDING + LABEL_FONT + 4f, CARD_RADIUS,
                ClickGui.getBackgroundColor(ClickGui.getHudAlpha() * globalAlpha));

        float plotX = x + PADDING;
        float plotY = graphY + PADDING;
        float plotW = GRAPH_W;
        float plotH = GRAPH_H;

        // Сетка-подложка.
        int gridColor = ColorUtil.replAlpha(-1, (int) (16 * globalAlpha));
        for (int gy = 0; gy <= 3; gy++) {
            renderer.rect(plotX, plotY + plotH * gy / 3f, plotW, 1f, gridColor);
        }
        for (int gx = 0; gx <= 5; gx++) {
            renderer.rect(plotX + plotW * gx / 5f, plotY, 1f, plotH, gridColor);
        }

        // Линия графика.
        int n = bpsHistory.size();
        if (n >= 2) {
            Float[] hist = bpsHistory.toArray(new Float[0]);
            float peak = Math.max(1f, maxBps);
            for (int i = 1; i < n; i++) {
                float v0 = hist[i - 1];
                float v1 = hist[i];
                float x0 = plotX + plotW * (i - 1) / (float) (n - 1);
                float x1 = plotX + plotW * i / (float) (n - 1);
                float y0 = plotY + plotH - Math.min(1f, v0 / peak) * plotH;
                float y1 = plotY + plotH - Math.min(1f, v1 / peak) * plotH;

                float dx = x1 - x0;
                float dy = y1 - y0;
                float len = (float) Math.hypot(dx, dy);
                if (len < 0.05f) continue;

                // Сегмент линии: прямоугольник, повёрнутый на угол сегмента.
                float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
                float cx = (x0 + x1) / 2f;
                float cy = (y0 + y1) / 2f;

                // Градиент от тёмного к яркому по «свежести».
                float t = i / (float) (n - 1);
                int c0 = ColorUtil.interpolate(themeDark, themeColor, Math.max(0.0, t - 0.08));
                int c1 = ColorUtil.interpolate(themeDark, themeColor, t);

                renderer.pushRotationAround(angle, cx, cy);
                renderer.gradient(cx - len / 2f, cy - LINE_W / 2f, len, LINE_W, 0f, c0, c1, c1, c0);
                renderer.popRotation();
            }
        }

        // Подписи Actual / Max.
        float labelY = graphY + PADDING + GRAPH_H + 8f;
        float actual = bpsHistory.isEmpty() ? 0f : bpsHistory.peekLast();
        String actualStr = String.format(java.util.Locale.ROOT, "%.1f", actual);
        String maxStr = String.format(java.util.Locale.ROOT, "%.1f", maxBps);

        renderer.text(f, plotX, labelY, LABEL_FONT, "Actual",
                ColorUtil.replAlpha(-1, (int) (160 * globalAlpha)), -0.1f);
        AnimatedText.draw(renderer, f, "motion_actual", actualStr,
                plotX + 70f, labelY, LABEL_FONT,
                ColorUtil.replAlpha(themeColor, (int) (255 * globalAlpha)), AnimatedText.ALIGN_LEFT);

        String maxLbl = "Max";
        float maxLblW = renderer.measureText(f, maxLbl, LABEL_FONT, -0.1f).width;
        renderer.text(f, plotX + plotW - maxLblW, labelY, LABEL_FONT, maxLbl,
                ColorUtil.replAlpha(-1, (int) (160 * globalAlpha)), -0.1f);
        float maxValW = renderer.measureText(f, maxStr, LABEL_FONT, -0.1f).width;
        renderer.textRight(f, plotX + plotW - maxLblW - 12f, labelY, LABEL_FONT, maxStr,
                ColorUtil.replAlpha(-1, (int) (255 * globalAlpha)), -0.1f);
    }

    private static float PADDING_V_LABEL() {
        return 12f + 9f + 2.5f;
    }

    @Override
    public float getWidth(Renderer2D renderer, FontObject font) {
        return GRAPH_W + PADDING * 2f;
    }

    @Override
    public float getHeight(Renderer2D renderer, FontObject font) {
        return HEADER_BOX_HEIGHT + 6f + GRAPH_H + PADDING + LABEL_FONT + 4f;
    }
}
