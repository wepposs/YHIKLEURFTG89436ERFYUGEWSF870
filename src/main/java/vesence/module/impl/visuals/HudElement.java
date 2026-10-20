package vesence.module.impl.visuals;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.api.setting.impl.MultiBooleanSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.module.impl.misc.ClickGui;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.BorderRadius;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;
import vesence.utils.render.text.FontObject;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public abstract class HudElement {

    public String name;
    public float x;
    public float y;

    public final float defaultX;
    public final float defaultY;
    public boolean enabled;
    public final List<BooleanSetting> settings = new ArrayList<>();
    public final List<ModeSetting> modeSettings = new ArrayList<>();
    public final List<MultiBooleanSetting> multiBooleanSettings = new ArrayList<>();

    public final List<BooleanSetting> bottomSettings = new ArrayList<>();
    public final List<ModeSetting> bottomModeSettings = new ArrayList<>();
    public final List<MultiBooleanSetting> bottomMultiBooleanSettings = new ArrayList<>();
    public boolean settingsOpen = false;

    public final BooleanSetting blurSetting = new BooleanSetting("Размытие", true);
    public static final BooleanSetting outline = new BooleanSetting("Контур", true);
    public SliderSetting scaleSetting = new SliderSetting("Размер", 1.0, 0.5, 2.0, 0.1);

    public static boolean isBlurEnabled() {
        return vesence.module.impl.misc.ClickGui.blurHud.get();
    }

    public static void drawHudPanel(Renderer2D renderer, float x, float y, float w, float h, float alpha) {
        vesence.module.impl.misc.ClickGui.tickLimits();
        float corner = vesence.module.impl.misc.ClickGui.getHudCorner();

        boolean modern = vesence.module.impl.misc.ClickGui.isModern();

        if (isBlurEnabled()) {
            renderer.blur(x, y, w, h, vesence.module.impl.misc.ClickGui.blurStrengthHud.get().intValue(), corner, alpha);
        }

        if (modern) {
            renderer.shadow(x, y, w, h, corner, 5, 1, ColorUtil.replAlpha(ColorUtil.BLACK, (int) (45 * alpha)));
            renderer.rect(x, y, w, h, corner, ColorUtil.replAlpha(ColorUtil.BLACK, ClickGui.hudAlpha.get().floatValue() * alpha));
        } else {
            renderer.rect(x, y, w, h, corner,
                    ColorUtil.multAlpha(ColorUtil.multDark(Renderer2D.ColorUtil.getClientColor(), 0.12f),
                            vesence.module.impl.misc.ClickGui.getHudAlpha() * alpha));
        }
    }

    public static void drawSteppedRect(Renderer2D renderer, float rightX, float topY,
                                       float[] widths, float[] rowHeights, int color,
                                       float corner, float stepRound) {
        if (widths == null || widths.length == 0) return;

        float curY = topY;
        for (int i = 0; i < widths.length; i++) {
            float w = widths[i];
            float h = rowHeights[i];
            if (w < 0.5f || h < 0.5f) { curY += h; continue; }

            float rowX = rightX - w;

            boolean isFirst = (i == 0);
            boolean isLast = (i == widths.length - 1);
            boolean widerThanPrev = !isFirst && widths[i] > widths[i - 1] + 0.5f;
            boolean widerThanNext = !isLast && widths[i] > widths[i + 1] + 0.5f;

            float rTL = isFirst ? corner : (widerThanPrev ? stepRound : 0);
            float rTR = isFirst ? corner : 0;
            float rBL = isLast ? corner : (widerThanNext ? stepRound : 0);
            float rBR = isLast ? corner : 0;

            renderer.rect(rowX, curY, w, h, rTL, rTR, rBR, rBL, color);
            curY += h;
        }
    }

    public final Animation2 alphaAnim = new Animation2();
    public final Animation2 scaleAnim = new Animation2();
    public final Animation2 guideAnim = new Animation2();

    public transient List<HudElement> snapSiblings = java.util.Collections.emptyList();
    public transient boolean dragAltHeld = false;
    public transient boolean dragShiftHeld = false;

    public final transient List<GuideLine> guideLines = new ArrayList<>();
    private boolean prevSnapX = false;
    private boolean prevSnapY = false;

    public static final class GuideLine {
        public final boolean vertical;   // true вЂ” РІРµСЂС‚РёРєР°Р»СЊРЅР°СЏ Р»РёРЅРёСЏ (РїРѕСЃС‚РѕСЏРЅРЅС‹Р№ x)
        public final float pos;          // x РґР»СЏ РІРµСЂС‚РёРєР°Р»СЊРЅРѕР№, y РґР»СЏ РіРѕСЂРёР·РѕРЅС‚Р°Р»СЊРЅРѕР№
        public final float start;        // РЅР°С‡Р°Р»Рѕ РїРѕ РїРµСЂРїРµРЅРґРёРєСѓР»СЏСЂРЅРѕР№ РѕСЃРё
        public final float end;          // РєРѕРЅРµС† РїРѕ РїРµСЂРїРµРЅРґРёРєСѓР»СЏСЂРЅРѕР№ РѕСЃРё
        public final boolean element;    // true вЂ” СЃРµРіРјРµРЅС‚ РјРµР¶РґСѓ СЌР»РµРјРµРЅС‚Р°РјРё, false вЂ” Р»РёРЅРёСЏ СЌРєСЂР°РЅР°

        public GuideLine(boolean vertical, float pos, float start, float end, boolean element) {
            this.vertical = vertical;
            this.pos = pos;
            this.start = start;
            this.end = end;
            this.element = element;
        }
    }

    private static final class AxisSnap {
        float newPos;
        float linePos;
        float spanStart;
        float spanEnd;
        float dist;
        boolean screenLine;
    }

    private boolean dragging = false;
    private float screenDragOffsetX = 0;
    private float screenDragOffsetY = 0;
    private float dragPreviewX = 0;
    private float dragPreviewY = 0;
    private boolean dragPreviewInitialized = false;
    private long dragPreviewLastTime = 0L;

    protected void setDragging(boolean v) {
        this.dragging = v;
    }

    public float targetX;
    public float targetY;
    public boolean targetInitialized = false;
    private long lastMoveTime = 0;

    private final Animation2 hoverAnim = new Animation2();
    private final Animation2 pressAnim = new Animation2();

    private static final float LERP_SPEED = 18f;
    private static final float DRAG_LERP_SPEED = 17f;
    private static final float EDGE_MARGIN = 8f;

    private static final float PRESS_EXPAND = 0.05f;

    public HudElement(String name, float defaultX, float defaultY) {
        this.name     = name;
        this.x        = defaultX;
        this.y        = defaultY;
        this.defaultX = defaultX;
        this.defaultY = defaultY;
        this.targetX  = defaultX;
        this.targetY  = defaultY;
        this.enabled  = true;
        hoverAnim.set(0.0);
        pressAnim.set(0.0);
        alphaAnim.set(1.0);
        scaleAnim.set(1.0);
        guideAnim.set(0.0);
    }

    public float getScale() {
        return (float) scaleAnim.get();
    }

    public void addSetting(BooleanSetting setting) {
        settings.add(setting);
    }

    public void addModeSetting(ModeSetting setting) {
        modeSettings.add(setting);
    }

    public void addMultiBooleanSetting(MultiBooleanSetting setting) {
        multiBooleanSettings.add(setting);
    }

    public BooleanSetting getSetting(String settingName) {
        for (BooleanSetting s : settings) {
            if (s.name.equals(settingName)) return s;
        }
        return null;
    }

    public abstract void render(Renderer2D renderer, FontObject font, int screenWidth, int screenHeight, GuiGraphicsExtractor drawContext);
    public abstract float getWidth(Renderer2D renderer, FontObject font);
    public abstract float getHeight(Renderer2D renderer, FontObject font);

    protected void renderBackground(Renderer2D renderer, float x, float y, float w, float h, float rounding) {
        renderBackground(renderer, x, y, w, h, rounding, 1f);
    }

    protected void renderBackground(Renderer2D renderer, float x, float y, float w, float h, float rounding, float alpha) {
        renderer.rect(x, y, w, h, rounding, ColorUtil.getColor(25,alpha));
        if (isContourEnabled()) {
            renderer.rectOutline(x, y, w, h, rounding, getContourColor(alpha), 1f);
        } else {
            renderer.rectOutline(x, y, w, h, rounding, ColorUtil.getColor(255, (int) (25 * alpha)), 1);
        }
    }

    public static boolean isContourEnabled() {

        return false;
    }

    public static int getContourColor(float alpha) {
        return ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), (int) (90 * alpha));
    }

    public static int getGlobalContourColor(float alpha) {
        return getContourColor(alpha);
    }

    public static void renderContourRectOutline(Renderer2D renderer, float px, float py, float w, float h, float rounding, float alpha) {
        if (!isContourEnabled()) return;
        renderer.rectOutline(px, py, w, h, rounding, getContourColor(alpha), 1f);
    }

    public static void renderContourSquircleOutline(Renderer2D renderer, float px, float py, float w, float h,
                                                    float squirt, BorderRadius radius, float alpha) {
        if (!isContourEnabled()) return;
        renderer.drawSquircleOutline(px, py, w, h, squirt, radius, getContourColor(alpha), 1f);
    }

    public void updateInteraction(double mouseX, double mouseY, Renderer2D renderer, FontObject font, boolean isPressed) {
        hoverAnim.update();
        pressAnim.update();

        boolean hovered = isHovered(mouseX, mouseY, renderer, font);
        hoverAnim.run(hovered ? 1.0 : 0.0, 0.20, Easings.CUBIC_OUT);
        pressAnim.run(isPressed ? 1.0 : 0.0, 0.22, Easings.CUBIC_OUT);
    }

    public float getExpand() {
        return 1f + PRESS_EXPAND * (float) pressAnim.get();
    }

    public void renderHoverOutline(Renderer2D renderer, FontObject font) {
        float t = (float) hoverAnim.get();
        if (t < 0.01f) return;

        float w = getEffectiveWidth(renderer, font);
        float h = getHeight(renderer, font);

        float pad = 3f + 2f * t;
        float ox = x - pad;
        float oy = y - pad;
        float ow = w + pad * 2f;
        float oh = h + pad * 2f;

        float corner = vesence.module.impl.misc.ClickGui.getHudCorner() + pad;
        int base = -1;
        int glow = ColorUtil.replAlpha(base, (int) (55 * t));
        int line = ColorUtil.replAlpha(base, (int) (200 * t));

        renderer.gradientOutline(ox, oy, ow, oh, corner, line, line, glow, glow, 1.5f, true);
    }

    public boolean isHovered(double mouseX, double mouseY, Renderer2D renderer, FontObject font) {
        float s = getScale();
        float w = getEffectiveWidth(renderer, font) * s;
        float h = getHeight(renderer, font) * s;
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    public void onMousePress(double mouseX, double mouseY, Renderer2D renderer, FontObject font) {
        if (isHovered(mouseX, mouseY, renderer, font)) {
            dragging          = true;
            screenDragOffsetX = (float)(mouseX - x);
            screenDragOffsetY = (float)(mouseY - y);
            targetX = x;
            targetY = y;
            targetInitialized = true;
            beginDragPreview(x, y);
        }
    }

    public void onMouseRelease() {
        boolean wasDragging = dragging;
        dragging = false;
        endDragPreview();
        prevSnapX = false;
        prevSnapY = false;

        if (wasDragging && vesence.Vesence.get != null && vesence.Vesence.get.configManager != null) {
            vesence.Vesence.get.configManager.autoSave();
        }
    }

    public void resetPosition() {
        dragging = false;
        endDragPreview();
        targetX = defaultX;
        targetY = defaultY;
        targetInitialized = true;
        prevSnapX = false;
        prevSnapY = false;
        guideLines.clear();
    }

    public void nudge(float dx, float dy, int screenWidth, int screenHeight, Renderer2D renderer, FontObject font) {
        if (!targetInitialized) {
            targetX = x;
            targetY = y;
            targetInitialized = true;
        }
        float s = getScale();
        float w = getEffectiveWidth(renderer, font) * s;
        float h = getHeight(renderer, font) * s;
        targetX = Math.max(0, Math.min(targetX + dx, screenWidth  - w));
        targetY = Math.max(0, Math.min(targetY + dy, screenHeight - h));
    }

    public void onMouseMove(double mouseX, double mouseY, Renderer2D renderer, FontObject font,
                            int screenWidth, int screenHeight) {
        if (!targetInitialized) {
            targetX = x;
            targetY = y;
            targetInitialized = true;
            lastMoveTime = System.currentTimeMillis();
        }

        long now = System.currentTimeMillis();
        float dt = Math.min((now - lastMoveTime) / 1000f, 0.1f);
        lastMoveTime = now;

        if (dragging) {
            float s = getScale();
            float w = getEffectiveWidth(renderer, font) * s;
            float h = getHeight(renderer, font) * s;

            float newX = (float)(mouseX - screenDragOffsetX);
            float newY = (float)(mouseY - screenDragOffsetY);

            boolean freeMove = dragAltHeld;                              // Alt вЂ” РїРѕР»РЅРѕСЃС‚СЊСЋ Р±РµР· РїСЂРёРІСЏР·РѕРє
            boolean gridStep = !dragAltHeld && !dragShiftHeld && ClickGui.hudGrid.get(); // Shift вЂ” Р±РµР· С€Р°РіР° СЃРµС‚РєРё
            float gridSize = (float) ClickGui.hudGridDensity.current;

            guideLines.clear();
            boolean snappedX = false;
            boolean snappedY = false;

            if (gridStep && gridSize > 0f) {
                newX = Math.round(newX / gridSize) * gridSize;
                newY = Math.round(newY / gridSize) * gridSize;
            }

            if (!freeMove) {
                float base = (float) ClickGui.hudSnapStrength.current;
                float threshold = base * (0.6f + 0.4f * s); // РїРѕСЂРѕРі СЃ СѓС‡С‘С‚РѕРј РјР°СЃС€С‚Р°Р±Р° СЌР»РµРјРµРЅС‚Р°

                AxisSnap sx = bestSnapAxis(true, newX, w, newY, h, screenWidth, screenHeight, threshold, renderer, font);
                if (sx != null) {
                    newX = sx.newPos;
                    guideLines.add(new GuideLine(true, sx.linePos, sx.spanStart, sx.spanEnd, !sx.screenLine));
                    snappedX = true;
                }
                AxisSnap sy = bestSnapAxis(false, newY, h, newX, w, screenHeight, screenWidth, threshold, renderer, font);
                if (sy != null) {
                    newY = sy.newPos;
                    guideLines.add(new GuideLine(false, sy.linePos, sy.spanStart, sy.spanEnd, !sy.screenLine));
                    snappedY = true;
                }
            }

            newX = Math.max(0, Math.min(newX, screenWidth  - w));
            newY = Math.max(0, Math.min(newY, screenHeight - h));

            prevSnapX = snappedX;
            prevSnapY = snappedY;

            targetX = newX;
            targetY = newY;

            // The real element stays put while a preview glides under the cursor.
            glideDragPreview(targetX, targetY);
        } else {
            float factor = 1f - (float) Math.exp(-LERP_SPEED * dt);
            x += (targetX - x) * factor;
            y += (targetY - y) * factor;
            if (Math.abs(x - targetX) < 0.4f) x = targetX;
            if (Math.abs(y - targetY) < 0.4f) y = targetY;
        }
    }

    /**
     */
    private AxisSnap bestSnapAxis(boolean vertical, float pos, float size, float otherPos, float otherSize,
                                  int screenPrimary, int screenSecondary, float threshold,
                                  Renderer2D renderer, FontObject font) {
        AxisSnap best = null;

        if (ClickGui.hudCenterSnap.get()) {
            float c = screenPrimary / 2f;
            best = consider(best, pos, c - size / 2f, c, 0, screenSecondary, true, threshold);
        }

        if (ClickGui.hudEdgeSnap.get()) {
            best = consider(best, pos, EDGE_MARGIN, EDGE_MARGIN, 0, screenSecondary, true, threshold);
            best = consider(best, pos, screenPrimary - EDGE_MARGIN - size, screenPrimary - EDGE_MARGIN, 0, screenSecondary, true, threshold);
        }

        if (ClickGui.hudElementSnap.get()) {
            for (HudElement sib : snapSiblings) {
                if (sib == this) continue;
                float ss = sib.getScale();
                float sibStart;
                float sibSize;
                float sibSecStart;
                float sibSecEnd;
                if (vertical) {
                    sibSize = sib.getEffectiveWidth(renderer, font) * ss;
                    sibStart = sib.x;
                    sibSecStart = sib.y;
                    sibSecEnd = sib.y + sib.getHeight(renderer, font) * ss;
                } else {
                    sibSize = sib.getHeight(renderer, font) * ss;
                    sibStart = sib.y;
                    sibSecStart = sib.x;
                    sibSecEnd = sib.x + sib.getEffectiveWidth(renderer, font) * ss;
                }
                float sibL = sibStart;
                float sibC = sibStart + sibSize / 2f;
                float sibR = sibStart + sibSize;

                float spanStart = Math.min(otherPos, sibSecStart);
                float spanEnd = Math.max(otherPos + otherSize, sibSecEnd);

                best = consider(best, pos, sibL, sibL, spanStart, spanEnd, false, threshold);           // РєСЂР°Р№в†’Р»РµРІС‹Р№
                best = consider(best, pos, sibR, sibR, spanStart, spanEnd, false, threshold);           // РєСЂР°Р№в†’РїСЂР°РІС‹Р№
                best = consider(best, pos, sibL - size, sibL, spanStart, spanEnd, false, threshold);    // РґР°Р»СЊРЅРёР№ РєСЂР°Р№в†’Р»РµРІС‹Р№
                best = consider(best, pos, sibR - size, sibR, spanStart, spanEnd, false, threshold);    // РґР°Р»СЊРЅРёР№ РєСЂР°Р№в†’РїСЂР°РІС‹Р№
                best = consider(best, pos, sibC - size / 2f, sibC, spanStart, spanEnd, false, threshold); // С†РµРЅС‚СЂв†’С†РµРЅС‚СЂ
            }
        }
        return best;
    }

    private AxisSnap consider(AxisSnap best, float curPos, float candidatePos, float linePos,
                              float spanStart, float spanEnd, boolean screenLine, float threshold) {
        float dist = Math.abs(candidatePos - curPos);
        if (dist <= threshold && (best == null || dist < best.dist)) {
            AxisSnap a = new AxisSnap();
            a.newPos = candidatePos;
            a.linePos = linePos;
            a.spanStart = spanStart;
            a.spanEnd = spanEnd;
            a.screenLine = screenLine;
            a.dist = dist;
            return a;
        }
        return best;
    }

    public boolean isDragging() {
        return dragging;
    }

    public float getDragPreviewX() {
        return dragPreviewInitialized ? dragPreviewX : x;
    }

    public float getDragPreviewY() {
        return dragPreviewInitialized ? dragPreviewY : y;
    }

    protected void beginDragPreview(float startX, float startY) {
        dragPreviewX = startX;
        dragPreviewY = startY;
        dragPreviewInitialized = true;
        dragPreviewLastTime = System.currentTimeMillis();
    }

    protected void glideDragPreview(float destinationX, float destinationY) {
        if (!dragPreviewInitialized) {
            beginDragPreview(x, y);
        }

        long now = System.currentTimeMillis();
        float dt = Math.min((now - dragPreviewLastTime) / 1000f, 0.1f);
        dragPreviewLastTime = now;
        float factor = 1f - (float) Math.exp(-DRAG_LERP_SPEED * dt);
        dragPreviewX += (destinationX - dragPreviewX) * factor;
        dragPreviewY += (destinationY - dragPreviewY) * factor;
    }

    protected void endDragPreview() {
        dragPreviewInitialized = false;
        dragPreviewLastTime = 0L;
    }

    public float getEffectiveWidth(Renderer2D renderer, FontObject font) {
        return getWidth(renderer, font);
    }
}
