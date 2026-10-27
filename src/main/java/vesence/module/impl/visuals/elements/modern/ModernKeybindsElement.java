package vesence.module.impl.visuals.elements.modern;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import vesence.Vesence;
import vesence.module.api.Module;
import vesence.module.impl.misc.ClickGui;
import vesence.module.impl.visuals.HudElement;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;
import vesence.utils.render.text.FontObject;
import vesence.utils.render.text.FontRegistry;
import vesence.utils.render.ttf.TtfFonts;

import java.util.HashMap;
import java.util.Map;

/**
 * Keybinds: компоновка Default-версии — карточка-заголовок «HotKeys» и
 * отдельные выезжающие строки под каждый модуль с биндом (иконка категории,
 * имя, разделитель, клавиша). Дизайн modern: чёрные карточки с тенью,
 * светящаяся TTF-иконка заголовка, SF_SEMI, бинд цветом темы.
 */
@Environment(EnvType.CLIENT)
public class ModernKeybindsElement extends HudElement {

    private static final float PADDING_H = 13;
    private static final float PADDING_V = 12;
    private static final float FONT_SIZE = 28;
    private static final float HEADER_FONT_SIZE = 27;
    private static final float LINE_HEIGHT = 29;
    private static final float HEADER_HEIGHT = 17;
    private static final float HEADER_BOX_HEIGHT = 33;
    private static final float ROW_BOX_HEIGHT = 25;
    private static final float ROW_RADIUS = 8f;
    private static final float ICON_GAP = 6;
    private static final float ROW_RIGHT_EXTRA_GAP = 5f;
    private static final int WHITE_COLOR = 0xFFFFFFFF;
    private static final float EXTRA_HEIGHT = 14;

    private final Map<String, Animation2> moduleAnims = new HashMap<>();
    private final Map<String, Animation2> slotAnims = new HashMap<>();
    private final Animation2 visibilityAnim = new Animation2();
    private final Animation2 widthAnim = new Animation2();

    public ModernKeybindsElement() {
        super("Keybinds", 10f, 80f);
        visibilityAnim.set(0.0);
        widthAnim.set(125.0 + PADDING_H * 2.0);
    }

    @Override
    public void render(Renderer2D renderer, FontObject font, int screenWidth, int screenHeight, GuiGraphicsExtractor ctx) {
        if (FontRegistry.SF_SEMI == null) return;
        FontObject f = FontRegistry.SF_SEMI;

        Minecraft mc = Minecraft.getInstance();
        boolean isChatOpen = mc.gui.screen() instanceof ChatScreen;

        float totalSlotHeight = 0;
        for (Module mod : Vesence.get.manager.getModules()) {
            if (mod.bind == -1) continue;

            Animation2 anim = moduleAnims.computeIfAbsent(mod.name, k -> {
                Animation2 a = new Animation2();
                a.set(0.0);
                return a;
            });
            anim.update();
            anim.run(mod.enable ? 1.0 : 0.0, 0.15, Easings.CUBIC_OUT);

            Animation2 slot = slotAnims.computeIfAbsent(mod.name, k -> {
                Animation2 a = new Animation2();
                a.set(0.0);
                return a;
            });
            slot.update();
            slot.run(mod.enable ? LINE_HEIGHT : 0.0, 0.15, Easings.CUBIC_OUT);
            totalSlotHeight += slot.get();
        }

        int enabledCount = 0;
        for (Module mod : Vesence.get.manager.getModules()) {
            if (mod.bind != -1 && mod.enable) enabledCount++;
        }
        boolean hasAnyBind = enabledCount > 0;

        boolean shouldShow = isChatOpen || hasAnyBind;
        float extraH = hasAnyBind ? EXTRA_HEIGHT : 0f;

        visibilityAnim.update();
        widthAnim.update();
        visibilityAnim.run(shouldShow ? 1.0 : 0.0, 0.15, Easings.CUBIC_OUT);

        float globalAlpha = (float) visibilityAnim.get();
        if (globalAlpha < 0.005) return;

        int themeColor = Renderer2D.ColorUtil.getClientColor();
        int iconColor = ColorUtil.replAlpha(themeColor, (int) (230 * globalAlpha));

        float maxLineW = 0;
        float maxBindW = 0;
        boolean hasLongBind = false;
        for (Module mod : Vesence.get.manager.getModules()) {
            if (mod.bind == -1 || !mod.enable) continue;
            String bindLabel = mod.bindLabel();
            float nameW = renderer.measureText(f, mod.name, FONT_SIZE, -0.1f).width;
            float iconW = renderer.measureText(FontRegistry.MON, mod.category.getIcon(), FONT_SIZE).width;
            float bindW = renderer.measureText(f, bindLabel, FONT_SIZE, -0.1f).width;
            float lineW = nameW + bindW + iconW;
            if (lineW > maxLineW) maxLineW = lineW;
            if (bindW > maxBindW) maxBindW = bindW;
            if (bindLabel.length() > 1) hasLongBind = true;
        }

        float headerIconW = 20f;
        float headerTextW = renderer.measureText(f, "HotKeys", HEADER_FONT_SIZE + 4, -0.15f).width;
        float headerW = headerIconW + ICON_GAP + headerTextW;

        float contentW = Math.max(90, Math.max(maxLineW, headerW));
        float autoRectW = contentW + PADDING_H * 2f + 15;

        widthAnim.run(autoRectW, 0.2, Easings.CUBIC_OUT);
        float rectW = (float) widthAnim.get();

        // ---- Карточка-заголовок ----
        drawHudPanel(renderer, x, y, rectW, HEADER_BOX_HEIGHT, globalAlpha);

        float curY = y + PADDING_V + 9;
        TtfFonts.LOX.drawStringWithGlow(renderer, "d", x + 7.5f, y + HEADER_BOX_HEIGHT / 2f - 12f, 24, iconColor);
        renderer.rect(x + 38, y + 10.5f, 1, 14, ColorUtil.replAlpha(-1, (int) (20 * globalAlpha)));
        renderer.text(f, x + 48, curY + 2.5f, 31, "HotKeys", ColorUtil.replAlpha(-1, globalAlpha), -0.15f);
        curY += HEADER_HEIGHT;

        // ---- Строки модулей ----
        for (Module mod : Vesence.get.manager.getModules()) {
            if (mod.bind == -1) continue;
            Animation2 anim = moduleAnims.get(mod.name);
            Animation2 slot = slotAnims.get(mod.name);
            if (anim == null || slot == null || (anim.get() < 0.01 && slot.get() < 0.5)) continue;

            float modAlpha = (float) anim.get() * globalAlpha;
            int modTextAlpha = (int) (255 * modAlpha);

            String modName = mod.name;
            String bindStr = mod.bindLabel();

            int nameColor = (modTextAlpha << 24) | (WHITE_COLOR & 0x00FFFFFF);
            int bindColor = ColorUtil.replAlpha(themeColor, modTextAlpha);
            float rowX = x + modAlpha * 15 - 15;

            renderer.blur(rowX, curY, rectW, ROW_BOX_HEIGHT, ClickGui.blurStrengthHud.get().floatValue(), ROW_RADIUS, modAlpha);
            renderer.rect(rowX, curY, rectW, ROW_BOX_HEIGHT, ROW_RADIUS,
                    ClickGui.getBackgroundColor((int) (185f * ClickGui.getHudAlpha() * modAlpha)));

            renderer.text(FontRegistry.MON, rowX + 7, curY + 19, 28, mod.category.getIcon(), bindColor);
            renderer.text(f, rowX + 27, curY + 18.5f, 28, modName, nameColor, -0.1f);

            float bindAreaRight = rowX + rectW - 7;
            float bindAreaLeft = bindAreaRight - Math.max(maxBindW + 2, 26f);
            renderer.rect(bindAreaRight - maxBindW - 10, curY + 6, 1, 13, ColorUtil.replAlpha(-1, (int) (20 * modAlpha)));
            if (hasLongBind) {
                float bindCenterX = (bindAreaLeft + bindAreaRight) / 2f;
                renderer.textCenter(f, bindCenterX, curY + 18.5f, 28, bindStr, nameColor, -0.1f);
            } else {
                renderer.textRight(f, bindAreaRight - 1, curY + 18.5f, 28, bindStr, nameColor, -0.1f);
            }

            curY += (float) slot.get();
        }
    }

    @Override
    public float getEffectiveWidth(Renderer2D renderer, FontObject font) {
        return (float) widthAnim.get();
    }

    @Override
    public float getWidth(Renderer2D renderer, FontObject font) {
        FontObject f = FontRegistry.SF_SEMI != null ? FontRegistry.SF_SEMI : font;
        float maxLineW = 0;
        for (Module mod : Vesence.get.manager.getModules()) {
            if (mod.bind == -1 || !mod.enable) continue;
            float nameW = renderer.measureText(f, mod.name, FONT_SIZE, -0.1f).width;
            float bindW = renderer.measureText(f, mod.bindLabel(), FONT_SIZE, -0.1f).width;
            float lineW = (PADDING_H + 17) + nameW + ROW_RIGHT_EXTRA_GAP + bindW + PADDING_H;
            if (lineW > maxLineW) maxLineW = lineW;
        }
        float headerTextW = renderer.measureText(f, "HotKeys", HEADER_FONT_SIZE + 4, -0.15f).width;
        float headerW = headerTextW + 55f;
        float contentW = Math.max(135, Math.max(maxLineW, headerW));
        return contentW + PADDING_H * 2f + 15;
    }

    @Override
    public float getHeight(Renderer2D renderer, FontObject font) {
        float totalSlotHeight = 0;
        boolean hasAnyBind = false;
        for (Module mod : Vesence.get.manager.getModules()) {
            if (mod.bind == -1) continue;
            Animation2 slot = slotAnims.get(mod.name);
            if (slot != null) {
                float sh = (float) slot.get();
                totalSlotHeight += sh;
                if (sh > 0.5f) hasAnyBind = true;
            } else if (mod.enable) {
                totalSlotHeight += LINE_HEIGHT;
                hasAnyBind = true;
            }
        }
        if (!hasAnyBind && totalSlotHeight < 0.5f) return HEADER_BOX_HEIGHT;
        float extraH = hasAnyBind ? EXTRA_HEIGHT : 0f;
        return HEADER_HEIGHT + totalSlotHeight + PADDING_V * 2f + extraH;
    }
}
