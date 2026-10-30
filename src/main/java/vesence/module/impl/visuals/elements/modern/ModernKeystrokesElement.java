package vesence.module.impl.visuals.elements.modern;

import com.mojang.blaze3d.platform.InputConstants;
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
import vesence.utils.render.text.FontObject;
import vesence.utils.render.text.FontRegistry;

/**
 * Keystrokes: порт {@code KeystrokesRenderer} из privatedlc (Excellent 1.16.5).
 * Классическая сетка W / A S D / Space с анимацией нажатия. В modern-дизайне
 * vesence: чёрные карточки с тенью, SF_SEMI, нажатая клавиша подсвечивается
 * цветом темы (заливка + иконка-свечение).
 */
@Environment(EnvType.CLIENT)
public class ModernKeystrokesElement extends HudElement {

    private static final float KEY_SIZE   = 38f;
    private static final float KEY_GAP    = 4f;
    private static final float SPACE_W    = KEY_SIZE * 3 + KEY_GAP * 2;
    private static final float SPACE_H    = 26f;
    private static final float FONT_SIZE  = 26f;
    private static final float KEY_RADIUS = 10f;

    private static final int WHITE_COLOR = 0xFFFFFFFF;

    // W A S D — GLFW-коды, Space — GLFW 32.
    private static final int[] KEY_CODES = { InputConstants.KEY_W, InputConstants.KEY_A, InputConstants.KEY_S, InputConstants.KEY_D };
    private static final String[] KEY_LABELS = { "W", "A", "S", "D" };

    private final Animation2[] keyAnims = new Animation2[5]; // W A S D Space
    private final Animation2 visibilityAnim = new Animation2();

    public ModernKeystrokesElement() {
        super("Keystrokes", 640f, 400f);
        for (int i = 0; i < keyAnims.length; i++) {
            keyAnims[i] = new Animation2();
            keyAnims[i].set(0.0);
        }
        visibilityAnim.set(0.0);
        this.enabled = false;
    }

    private boolean isDown(int code) {
        return InputConstants.isKeyDown(code);
    }

    private void drawKey(Renderer2D renderer, FontObject f, float kx, float ky, float w, float h,
                         String label, float pressT, float globalAlpha, int themeColor) {
        float alpha = globalAlpha;

        // Нажатая клавиша: заливка цветом темы поверх тёмной карточки.
        int bg = ClickGui.getBackgroundColor((int) ((185f + 55f * pressT) * ClickGui.getHudAlpha() * alpha));
        int pressedTint = ColorUtil.replAlpha(themeColor, (int) (70f * pressT * alpha));

        if (isBlurEnabled()) {
            renderer.blur(kx, ky, w, h, ClickGui.blurStrengthHud.get().floatValue(), KEY_RADIUS, alpha);
        }
        renderer.shadow(kx, ky, w, h, KEY_RADIUS, 5f, 1f,
                ColorUtil.replAlpha(ColorUtil.BLACK, (int) (45 * alpha)));
        renderer.rect(kx, ky, w, h, KEY_RADIUS, bg);
        if (pressT > 0.005f) {
            renderer.rect(kx, ky, w, h, KEY_RADIUS, pressedTint);
            renderer.rectOutline(kx, ky, w, h, KEY_RADIUS,
                    ColorUtil.replAlpha(themeColor, (int) (170 * pressT * alpha)), 1.2f);
        }

        // Лёгкое сжатие при нажатии (scale-in как в источнике).
        float shrink = 1f - 0.04f * pressT;
        float cx = kx + w / 2f;
        float cy = ky + h / 2f;

        int labelColor = pressT > 0.4f
                ? ColorUtil.replAlpha(themeColor, (int) (255 * alpha))
                : ColorUtil.replAlpha(-1, (int) (255 * alpha));

        float textW = renderer.measureText(f, label, FONT_SIZE, -0.1f).width;
        float textH = renderer.measureText(f, label, FONT_SIZE, -0.1f).height;

        renderer.pushScale(shrink, cx, cy);
        renderer.text(f, cx - textW / 2f, cy + textH / 2f - 1.5f, FONT_SIZE, label, labelColor, -0.1f);
        renderer.popScale();
    }

    @Override
    public void render(Renderer2D renderer, FontObject font, int screenWidth, int screenHeight, GuiGraphicsExtractor ctx) {
        if (FontRegistry.SF_SEMI == null) return;
        FontObject f = FontRegistry.SF_SEMI;

        Minecraft mc = Minecraft.getInstance();
        boolean isChatOpen = mc.gui.screen() instanceof ChatScreen;

        boolean[] down = new boolean[5];
        down[0] = isDown(KEY_CODES[0]);
        down[1] = isDown(KEY_CODES[1]);
        down[2] = isDown(KEY_CODES[2]);
        down[3] = isDown(KEY_CODES[3]);
        down[4] = InputConstants.isKeyDown(InputConstants.KEY_SPACE);

        for (int i = 0; i < keyAnims.length; i++) {
            keyAnims[i].update();
            keyAnims[i].run(down[i] ? 1.0 : 0.0, 0.12, Easings.CUBIC_OUT);
        }

        visibilityAnim.update();
        visibilityAnim.run(1.0, 0.15, Easings.CUBIC_OUT);
        float globalAlpha = (float) visibilityAnim.get();
        if (globalAlpha < 0.005f) return;

        int themeColor = Renderer2D.ColorUtil.getClientColor();

        float col = KEY_SIZE + KEY_GAP;
        float row = KEY_SIZE + KEY_GAP;

        // W (центр верхнего ряда)
        drawKey(renderer, f, x + col, y, KEY_SIZE, KEY_SIZE, KEY_LABELS[0],
                (float) keyAnims[0].get(), globalAlpha, themeColor);
        // A S D
        drawKey(renderer, f, x, y + row, KEY_SIZE, KEY_SIZE, KEY_LABELS[1],
                (float) keyAnims[1].get(), globalAlpha, themeColor);
        drawKey(renderer, f, x + col, y + row, KEY_SIZE, KEY_SIZE, KEY_LABELS[2],
                (float) keyAnims[2].get(), globalAlpha, themeColor);
        drawKey(renderer, f, x + col * 2, y + row, KEY_SIZE, KEY_SIZE, KEY_LABELS[3],
                (float) keyAnims[3].get(), globalAlpha, themeColor);
        // Space
        drawKey(renderer, f, x, y + row * 2, SPACE_W, SPACE_H, "—",
                (float) keyAnims[4].get(), globalAlpha, themeColor);
    }

    @Override
    public float getWidth(Renderer2D renderer, FontObject font) {
        return KEY_SIZE * 3 + KEY_GAP * 2;
    }

    @Override
    public float getHeight(Renderer2D renderer, FontObject font) {
        return KEY_SIZE * 2 + SPACE_H + KEY_GAP * 2;
    }
}
