package vesence.module.impl.visuals.elements.modern;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import vesence.Vesence;
import vesence.module.api.Module;
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

@Environment(EnvType.CLIENT)
public class ModernKeybindsElement extends HudElement {

    private static final float PANEL_PAD   = 6f;
    private static final float COL_GAP     = 6f;
    private static final float HEADER_H    = 38f;
    private static final float HEADER_GAP  = 6f;
    private static final float ICON_COL_W  = 35;
    private static final float LINE_HEIGHT = 23;
    private static final float COL_PAD_V   = 5f;
    private static final float TEXT_PAD_H  = 12f;

    private static final float MIN_WIDTH   = 190f;
    private static final float TITLE_SIZE  = 33f;
    private static final float NAME_SIZE   = 30.5f;
    private static final float ICON_SIZE   = 30;

    private static final int   PILL_ALPHA  = 12;

    private final Map<String, Animation2> moduleAnims = new HashMap<>();
    private final Map<String, Animation2> slotAnims   = new HashMap<>();
    private final Animation2 visibilityAnim = new Animation2();
    private final Animation2 widthAnim      = new Animation2();

    private float boundsW = MIN_WIDTH;
    private float boundsH = HEADER_H + PANEL_PAD * 2f;

    public ModernKeybindsElement() {
        super("Keybinds", 10f, 80f);
        visibilityAnim.set(0.0);
        widthAnim.set(MIN_WIDTH);
    }

    @Override
    public void render(Renderer2D r, FontObject font, int screenWidth, int screenHeight, DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean isChatOpen = mc.currentScreen instanceof ChatScreen;

        float totalSlotHeight = 0;
        float maxNameW = 0f, maxValueW = 0f;
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

            if (mod.enable) {
                float nameW  = r.measureText(FontRegistry.SF_MEDIUM, mod.name, NAME_SIZE, -0.1f).width;
                float valueW = r.measureText(FontRegistry.SF_SEMI, mod.bindLabel(), NAME_SIZE).width;
                if (nameW > maxNameW) maxNameW = nameW;
                if (valueW > maxValueW) maxValueW = valueW;
            }
        }

        int enabledCount = 0;
        for (Module mod : Vesence.get.manager.getModules()) {
            if (mod.bind != -1 && mod.enable) enabledCount++;
        }
        boolean hasAnyBind = enabledCount > 0;

        boolean shouldShow = isChatOpen || hasAnyBind;
        visibilityAnim.update();
        widthAnim.update();
        visibilityAnim.run(shouldShow ? 1.0 : 0.0, 0.15, Easings.CUBIC_OUT);
        float alpha = (float) visibilityAnim.get();
        if (alpha < 0.005f) return;

        int theme = Renderer2D.ColorUtil.getClientColor();

        float nameColW  = maxNameW  + TEXT_PAD_H * 2f;
        float valueColW = maxValueW + TEXT_PAD_H * 2f;

        float titleW = r.measureText(FontRegistry.SF_SEMI, "Binds", TITLE_SIZE, -0.15f).width;
        float headerPillMin = 14f + titleW + 40f;
        float rightBlockW = Math.max(headerPillMin, nameColW + COL_GAP + valueColW);
        if (rightBlockW > nameColW + COL_GAP + valueColW) {
            nameColW = rightBlockW - COL_GAP - valueColW;
        }

        float panelTarget = Math.max(MIN_WIDTH,
                PANEL_PAD * 2f + ICON_COL_W + COL_GAP + nameColW + COL_GAP + valueColW);
        widthAnim.run(panelTarget, 0.2, Easings.CUBIC_OUT);
        float panelW = (float) widthAnim.get();

        float x0 = x + PANEL_PAD;
        float iconColX  = x0;
        float valueColX = x + panelW - PANEL_PAD - valueColW;
        float nameColX  = iconColX + ICON_COL_W + COL_GAP;
        float nameColW2 = valueColX - COL_GAP - nameColX;

        float colsH = totalSlotHeight < 0.5f ? 0f : (totalSlotHeight + COL_PAD_V * 2f);
        float panelH = PANEL_PAD + HEADER_H + (colsH > 0.5f ? HEADER_GAP + colsH : 0f) + PANEL_PAD;

        r.blur(x, y, panelW, panelH, 15, 12, alpha);
        r.rect(x, y, panelW, panelH, 12, ColorUtil.replAlpha(ColorUtil.BLACK, (int) (155 * alpha)));

        float headerTop = y + PANEL_PAD;
        TtfFonts.LOX.drawStringWithGlow(r, "d", iconColX + ICON_COL_W / 2f - 12, headerTop + HEADER_H / 2f - 11f, 24,
                ColorUtil.replAlpha(theme, (int) (230 * alpha)));

        float headerPillX = nameColX;
        float headerPillW = valueColX + valueColW - headerPillX + 1;
        r.rect(headerPillX, headerTop, headerPillW, HEADER_H, 3, 6, hasAnyBind ? 3 : 6, 3, ColorUtil.replAlpha(-1, (int) (PILL_ALPHA * alpha)));
        r.text(FontRegistry.SF_SEMI, headerPillX + 12, headerTop + HEADER_H / 2f + 7.5f, TITLE_SIZE, "Binds",
                ColorUtil.replAlpha(-1, (int) (255 * alpha)), -0.15f);
        r.textRight(FontRegistry.SF_BOLD, headerPillX + headerPillW - 12, headerTop + HEADER_H / 2f + 6, 30, ":::",
                ColorUtil.replAlpha(-1, (int) (75 * alpha)), 1);

        if (colsH < 0.5f) {
            boundsW = panelW;
            boundsH = panelH;
            return;
        }

        float colsTop = headerTop + HEADER_H + HEADER_GAP;
        int colColor = ColorUtil.replAlpha(-1, (int) (PILL_ALPHA * alpha));
        r.rect(iconColX,  colsTop, ICON_COL_W, colsH, 3, 3, 3, 6, colColor);
        r.rect(nameColX,  colsTop, nameColW2,  colsH, 3, 3, 3, 3, colColor);
        r.rect(valueColX, colsTop, valueColW,  colsH, 3, 3, 6, 3, colColor);

        float rowTop = colsTop + COL_PAD_V;
        for (Module mod : Vesence.get.manager.getModules()) {
            if (mod.bind == -1) continue;
            Animation2 anim = moduleAnims.get(mod.name);
            Animation2 slot = slotAnims.get(mod.name);
            if (anim == null || slot == null || (anim.get() < 0.01 && slot.get() < 0.5)) continue;

            float rowAlpha = (float) anim.get() * alpha;
            String name  = mod.name;
            String value = mod.bindLabel();
            String glyph = mod.category.getIcon();

            float baseY = rowTop + LINE_HEIGHT / 2f + NAME_SIZE * 0.33f - 4;

            r.textCenter(FontRegistry.MON, iconColX + ICON_COL_W / 2f, rowTop + LINE_HEIGHT / 2f + 7, ICON_SIZE, glyph,
                    ColorUtil.replAlpha(theme, (int) (255 * rowAlpha)), -0.1f);
            r.text(FontRegistry.SF_MEDIUM, nameColX + 10, baseY, NAME_SIZE, name,
                    ColorUtil.replAlpha(-1, (int) (255 * rowAlpha)), -0.1f);
            r.textCenter(FontRegistry.SF_SEMI, valueColX + valueColW / 2f + 0.5f, baseY, NAME_SIZE, value,
                    ColorUtil.replAlpha(theme, (int) (255 * rowAlpha)));

            rowTop += (float) slot.get();
        }

        boundsW = panelW;
        boundsH = panelH;
    }

    @Override
    public float getEffectiveWidth(Renderer2D r, FontObject font) {
        return (float) widthAnim.get();
    }

    @Override
    public float getWidth(Renderer2D r, FontObject font) {
        return boundsW;
    }

    @Override
    public float getHeight(Renderer2D r, FontObject font) {
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
        float colsH = (!hasAnyBind && totalSlotHeight < 0.5f) ? 0f : (totalSlotHeight + COL_PAD_V * 2f);
        return PANEL_PAD + HEADER_H + (colsH > 0.5f ? HEADER_GAP + colsH : 0f) + PANEL_PAD;
    }
}
