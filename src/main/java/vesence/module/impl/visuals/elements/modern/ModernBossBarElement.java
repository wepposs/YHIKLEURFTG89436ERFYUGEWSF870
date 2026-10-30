package vesence.module.impl.visuals.elements.modern;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.world.BossEvent;
import vesence.mixin.BossBarHudAccessor;
import vesence.module.impl.misc.ClickGui;
import vesence.module.impl.visuals.HudElement;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;
import vesence.utils.render.text.FontObject;
import vesence.utils.render.text.FontRegistry;

import java.util.Map;
import java.util.UUID;

/**
 * Boss Bar: порт {@code BossBarRenderer} из privatedlc (Excellent 1.16.5).
 * Первый активный боссбар: карточка с названием и тонкой полосой прогресса
 * цвета босса (по умолчанию — цвет темы). В редакторе (чат открыт) —
 * превью-бар. В modern-дизайне vesence: чёрная карточка с тенью, blur,
 * SF_SEMI, свечение полосы.
 */
@Environment(EnvType.CLIENT)
public class ModernBossBarElement extends HudElement {

    private static final float BAR_MAX_W    = 220f;
    private static final float BAR_H        = 8f;
    private static final float PADDING_H    = 14f;
    private static final float PADDING_V    = 10f;
    private static final float CARD_RADIUS  = 10f;
    private static final float TITLE_FONT   = 27f;

    private static final int WHITE_COLOR = 0xFFFFFFFF;

    private final Animation2 visibilityAnim = new Animation2();
    private final Animation2 progressAnim = new Animation2();
    private final Animation2 widthAnim = new Animation2();

    private float boundsW;
    private float boundsH;

    public ModernBossBarElement() {
        super("Boss Bar", 320f, 14f);
        visibilityAnim.set(0.0);
        progressAnim.set(1.0);
        widthAnim.set(BAR_MAX_W + PADDING_H * 2f);
        this.enabled = false;
    }

    private record BossInfo(String name, float progress, int color) {}

    private BossInfo getBossInfo(Minecraft mc, boolean preview) {
        try {
            var bossOverlay = mc.gui.hud.getBossOverlay();
            if (bossOverlay != null) {
                Map<UUID, BossEvent> bars = ((BossBarHudAccessor) (Object) bossOverlay).getBossBars();
                if (bars != null && !bars.isEmpty()) {
                    BossEvent event = bars.values().iterator().next();
                    return new BossInfo(
                            event.getName().getString(),
                            event.getProgress(),
                            bossBarRgb(event)
                    );
                }
            }
        } catch (Exception ignored) {}
        if (preview) {
            return new BossInfo("Вы играете с Vesence", 0.62f, 0);
        }
        return null;
    }

    /** RGB-цвет полосы по цвету боссбара (порт цветовой логики источника). */
    private static int bossBarRgb(BossEvent event) {
        return switch (event.getColor()) {
            case PINK   -> 0xFF00ABAB;
            case BLUE   -> 0xFF5555FF;
            case RED    -> 0xFFAB3232;
            case GREEN  -> 0xFF00AB00;
            case YELLOW -> 0xFFABAB00;
            case PURPLE -> 0xFFAB00AB;
            case WHITE  -> 0xFFABABAB;
        };
    }

    @Override
    public void render(Renderer2D renderer, FontObject font, int screenWidth, int screenHeight, GuiGraphicsExtractor ctx) {
        if (FontRegistry.SF_SEMI == null) return;
        FontObject f = FontRegistry.SF_SEMI;

        Minecraft mc = Minecraft.getInstance();
        boolean isChatOpen = mc.gui.screen() instanceof ChatScreen;

        BossInfo boss = getBossInfo(mc, isChatOpen);
        boolean shouldShow = boss != null;

        visibilityAnim.update();
        progressAnim.update();
        widthAnim.update();

        visibilityAnim.run(shouldShow ? 1.0 : 0.0, 0.15, Easings.CUBIC_OUT);
        progressAnim.run(boss != null ? Math.max(0f, Math.min(1f, boss.progress())) : 0.0, 0.3, Easings.QUAD_OUT);

        float globalAlpha = (float) visibilityAnim.get();
        if (globalAlpha < 0.005f) return;

        if (boss == null) return;

        int themeColor = Renderer2D.ColorUtil.getClientColor();
        // Цвет полосы: цвет босса (если валиден) или цвет темы.
        int barColor = boss.color() != 0 ? ColorUtil.replAlpha(boss.color(), 255) : themeColor;
        int barDark = ColorUtil.multDark(barColor, 0.45f);

        String title = boss.name();
        float titleW = renderer.measureText(f, title, TITLE_FONT, -0.15f).width;

        float rectW = Math.max(120f, Math.min(BAR_MAX_W, Math.max(titleW, 120f)) + PADDING_H * 2f);
        widthAnim.run(rectW, 0.2, Easings.CUBIC_OUT);
        rectW = (float) widthAnim.get();

        float rectH = PADDING_V * 2f + 18f + 6f + BAR_H;

        // ---- Карточка ----
        if (isBlurEnabled()) {
            renderer.blur(x, y, rectW, rectH, ClickGui.blurStrengthHud.get().floatValue(), CARD_RADIUS, globalAlpha);
        }
        renderer.shadow(x, y, rectW, rectH, CARD_RADIUS, 5f, 1f,
                ColorUtil.replAlpha(ColorUtil.BLACK, (int) (45 * globalAlpha)));
        renderer.rect(x, y, rectW, rectH, CARD_RADIUS,
                ClickGui.getBackgroundColor(ClickGui.getHudAlpha() * globalAlpha));

        // Название (с клипом по ширине карточки).
        renderer.pushClipRect((int) (x + PADDING_H), (int) (y + PADDING_V - 4f), (int) (rectW - PADDING_H * 2f), 26);
        renderer.text(f, x + PADDING_H, y + PADDING_V + 13f, TITLE_FONT, title,
                ColorUtil.replAlpha(-1, globalAlpha), -0.15f);
        renderer.popClipRect();

        // Полоса прогресса.
        float barX = x + PADDING_H;
        float barY = y + PADDING_V + 18f + 6f;
        float barW = rectW - PADDING_H * 2f;
        renderer.rect(barX, barY, barW, BAR_H, 4f, ColorUtil.replAlpha(-1, (int) (30 * globalAlpha)));

        float pct = (float) progressAnim.get();
        if (pct > 0.005f) {
            float fillW = barW * pct;
            // Свечение полосы.
            renderer.shadow(barX, barY, fillW, BAR_H, 4f, 3f, 0.7f,
                    ColorUtil.replAlpha(barColor, (int) (26 * globalAlpha)));
            renderer.gradient(barX, barY, fillW, BAR_H, 4f, barDark, barColor, barColor, barDark);
        }

        boundsW = rectW;
        boundsH = rectH;
    }

    @Override
    public float getWidth(Renderer2D renderer, FontObject font) {
        return boundsW > 0f ? boundsW : BAR_MAX_W + PADDING_H * 2f;
    }

    @Override
    public float getHeight(Renderer2D renderer, FontObject font) {
        return boundsH > 0f ? boundsH : PADDING_V * 2f + 18f + 6f + BAR_H;
    }
}
