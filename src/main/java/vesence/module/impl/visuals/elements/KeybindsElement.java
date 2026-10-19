package vesence.module.impl.visuals.elements;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.util.Identifier;
import vesence.Vesence;
import vesence.hmi.script_wrappers.C;
import vesence.module.api.Module;
import vesence.module.impl.misc.ClickGui;
import vesence.module.impl.visuals.HudElement;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.renderengine.render.Renderer2D;

import vesence.utils.other.KeyUtil;
import vesence.utils.render.BorderRadius;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.ResourceManager;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;
import vesence.utils.render.text.ColorFormat;
import vesence.utils.render.text.FontObject;
import vesence.utils.render.text.FontRegistry;
import vesence.utils.render.ttf.TtfFonts;

import java.util.HashMap;
import java.util.Map;

@Environment(EnvType.CLIENT)
public class KeybindsElement extends HudElement {

    private static final float PADDING_H = 13;
    private static final float PADDING_V = 12;
    private static final float FONT_SIZE = 28;
    private static final float HEADER_FONT_SIZE = 27;
    private static final float LINE_HEIGHT = 29;
    private static final float HEADER_HEIGHT = 17;
    private static final float ICON_GAP = 6;
    private static final float ROW_RIGHT_EXTRA_GAP = 5f;
    private static final int WHITE_COLOR = 0xFFFFFFFF;
    private static final float EXTRA_HEIGHT = 14;

    private final Map<String, Animation2> moduleAnims = new HashMap<>();
    private final Map<String, Animation2> slotAnims = new HashMap<>();
    private final Animation2 visibilityAnim = new Animation2();
    private final Animation2 heightAnim = new Animation2();
    private final Animation2 widthAnim = new Animation2();

    public KeybindsElement() {
        super("Keybinds", 10f, 80f);
        visibilityAnim.set(0.0);
        widthAnim.set(125.0 + PADDING_H * 2.0);
    }

    @Override
    public void render(Renderer2D renderer, FontObject font, int screenWidth, int screenHeight, DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean isChatOpen = mc.currentScreen instanceof ChatScreen;

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
        float rectH = HEADER_HEIGHT + totalSlotHeight + PADDING_V * 2f + extraH;

        visibilityAnim.update();
        heightAnim.update();
        widthAnim.update();
        visibilityAnim.run(shouldShow ? 1.0 : 0.0, 0.15, Easings.CUBIC_OUT);
        heightAnim.run(HEADER_HEIGHT + totalSlotHeight + PADDING_V * 2f > 45 ? 1 : 0, 0.15, Easings.CUBIC_OUT);

        float globalAlpha = visibilityAnim.get();
        if (globalAlpha < 0.005) return;

        int themeColor = Renderer2D.ColorUtil.getClientColor();

        String headerText = "Keybinds";

        float maxLineW = 0;
        float maxBindW = 0;
        boolean hasLongBind = false;
        for (Module mod : Vesence.get.manager.getModules()) {
            if (mod.bind == -1 || !mod.enable) continue;
            String bindLabel = bindLabel(mod);
            float nameW = renderer.measureText(font, mod.name, FONT_SIZE).width;
            float iconW = renderer.measureText(FontRegistry.MON, mod.category.getIcon(), FONT_SIZE).width;
            float bindW = renderer.measureText(font, bindLabel, FONT_SIZE).width;
            float lineW = nameW + bindW + iconW;
            if (lineW > maxLineW) maxLineW = lineW;
            if (bindW > maxBindW) maxBindW = bindW;
            if (bindLabel.length() > 1) hasLongBind = true;
        }

        float headerIconW = 20f;
        float headerTextW = renderer.measureText(FontRegistry.SF_MEDIUM, headerText, HEADER_FONT_SIZE).width;
        float headerW = headerIconW + ICON_GAP + headerTextW;

        float contentW = Math.max(90, Math.max(maxLineW, headerW));
        float autoRectW = contentW + PADDING_H * 2f + 15;

        widthAnim.run(autoRectW, 0.2, Easings.CUBIC_OUT);
        float rectW = (float) widthAnim.get();
        renderer.blur(x, y, rectW, 33, ClickGui.blurStrengthHud.get().floatValue(), 9, globalAlpha);
        renderer.rect(x, y, rectW, 33, 9, ColorUtil.multAlpha(ColorUtil.multDark(themeColor, 0.12f), ClickGui.getHudAlpha() * globalAlpha));


        int theme = Renderer2D.ColorUtil.getClientColor1();
        float curY = y + PADDING_V + 9;
        renderer.text(FontRegistry.VESENCE, x + 9.5f, curY + 2, 38, "A", ColorUtil.replAlpha(theme, globalAlpha), 0.2f);
        renderer.rect(x + 38, y + 10.5f, 1, 14, ColorUtil.replAlpha(-1, (int) (15 * globalAlpha)));
        renderer.text(FontRegistry.SF_MEDIUM, x + 48, curY + 2.5f, 31, "HotKeys", ColorUtil.replAlpha(-1, globalAlpha), 0.2f);
        curY += HEADER_HEIGHT;
        for (Module mod : Vesence.get.manager.getModules()) {
            if (mod.bind == -1) continue;
            Animation2 anim = moduleAnims.get(mod.name);
            Animation2 slot = slotAnims.get(mod.name);
            if (anim == null || slot == null || (anim.get() < 0.01 && slot.get() < 0.5)) continue;

            float modAlpha = anim.get() * globalAlpha;
            int modTextAlpha = (int)(255 * modAlpha);

            String modName = mod.name;
            String bindStr = bindLabel(mod);

            int nameColor = (modTextAlpha << 24) | (WHITE_COLOR & 0x00FFFFFF);
            int bindColor = (modTextAlpha << 24) | (themeColor & 0x00FFFFFF);
            renderer.blur(x + modAlpha * 15 - 15, curY, rectW, 25, ClickGui.blurStrengthHud.get().floatValue(), 7, modAlpha);
            renderer.rect(x + modAlpha * 15 - 15, curY, rectW, 25, 7, ColorUtil.multAlpha(ColorUtil.multDark(themeColor, 0.12f), ClickGui.getHudAlpha() * modAlpha));

            renderer.text(FontRegistry.MON, x + 7 + modAlpha * 15 - 15, curY + 19, 28, mod.category.getIcon(), bindColor);
            renderer.text(FontRegistry.SF_MEDIUM, x + 27 + modAlpha * 15 - 15, curY + 18.5f, 28, modName, nameColor);

            float bindAreaRight = x + rectW - 7 + modAlpha * 15 - 15;
            float bindAreaLeft = bindAreaRight - Math.max(maxBindW + 2, 26f);
            renderer.rect(bindAreaRight - maxBindW - 10, curY + 6, 1, 13, ColorUtil.replAlpha(-1, (int) (15 * modAlpha)));
            if (hasLongBind) {
                float bindCenterX = (bindAreaLeft + bindAreaRight) / 2f;
                renderer.textCenter(FontRegistry.SF_MEDIUM, bindCenterX, curY + 18.5f, 28, bindStr, nameColor);
            } else {
                renderer.textRight(FontRegistry.SF_MEDIUM, bindAreaRight - 1, curY + 18.5f, 28, bindStr, nameColor);
            }

            curY += slot.get();
        }
    }

    @Override
    public float getEffectiveWidth(Renderer2D renderer, FontObject font) {
        return (float) widthAnim.get();
    }

    @Override
    public float getWidth(Renderer2D renderer, FontObject font) {
        float maxLineW = 0;
        for (Module mod : Vesence.get.manager.getModules()) {
            if (mod.bind == -1 || !mod.enable) continue;
            float nameW = renderer.measureText(font, mod.name, FONT_SIZE).width;
            float bindW = renderer.measureText(font, bindLabel(mod), FONT_SIZE).width;
            float lineW = (PADDING_H + 17) + nameW + ROW_RIGHT_EXTRA_GAP + bindW + PADDING_H;
            if (lineW > maxLineW) maxLineW = lineW;
        }
        float headerTextW = renderer.measureText(FontRegistry.SF_MEDIUM, "Keybinds", HEADER_FONT_SIZE).width;
        float headerW = headerTextW + 55f;
        float contentW = Math.max(135, Math.max(maxLineW, headerW));
        return contentW + PADDING_H * 2f + 15;
    }

    private static String bindLabel(Module mod) {
        return mod.bindLabel();
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
        if (!hasAnyBind && totalSlotHeight < 0.5f) return HEADER_HEIGHT + PADDING_V * 2f;
        float extraH = hasAnyBind ? EXTRA_HEIGHT : 0f;
        return HEADER_HEIGHT + totalSlotHeight + PADDING_V * 2f + extraH;
    }
}
