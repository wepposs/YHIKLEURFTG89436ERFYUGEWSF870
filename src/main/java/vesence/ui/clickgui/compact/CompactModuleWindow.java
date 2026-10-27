package vesence.ui.clickgui.compact;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.Vesence;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.impl.misc.ClickGui;
import vesence.module.impl.visuals.Hud;
import vesence.renderengine.render.Renderer2D;
import vesence.ui.clickgui.compact.setting.CompactGuiMouseClickedSetting;
import vesence.ui.clickgui.compact.setting.CompactGuiRenderSetting;
import vesence.utils.math.ScrollUtil;
import vesence.utils.render.BorderRadius;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;
import vesence.utils.render.text.FontRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * Floating settings window for compact ClickGui.
 * Watermark has its own six display switches injected below the normal settings.
 */
@Environment(EnvType.CLIENT)
public class CompactModuleWindow {

    public static final float WIDTH = 160f;
    public static final float MAX_HEIGHT = 210f;
    public static final float GAP = 6f;

    private static final float HEADER_H = 20f;
    private static final float PAD = 6f;
    private static final float SETTING_GAP = 3f;
    private static final float CLOSE_SIZE = 12f;

    private static final List<CompactModuleWindow> WINDOWS = new ArrayList<>();

    public final Module module;
    private final ScrollUtil scroll = new ScrollUtil();
    private final Animation2 morph = new Animation2();
    private boolean closing = false;

    private final float originX, originY, originW, originH;
    private float dispX, dispY;
    private boolean dispInit = false;
    private float dispH;
    private boolean dispHInit = false;
    private boolean moved = false;
    private float manualX, manualY;

    private float rx, ry, rw, rh;

    private boolean dragging = false;
    private float dragOffX, dragOffY;

    private CompactModuleWindow(Module module, float ox, float oy, float ow, float oh) {
        this.module = module;
        this.originX = ox;
        this.originY = oy;
        this.originW = ow;
        this.originH = oh;
        morph.set(0.0);
        morph.run(1.0, 0.5, Easings.QUINT_OUT);
    }

    public static boolean hasVisibleSettings(Module mod) {
        for (Setting<?> s : getWindowSettings(mod)) {
            if (!(boolean) s.hidden.get()) return true;
        }
        return false;
    }

    public static boolean isExtracted(Module mod) {
        for (CompactModuleWindow w : WINDOWS) {
            if (w.module == mod) return true;
        }
        return false;
    }

    public static boolean anyOpen() {
        return !WINDOWS.isEmpty();
    }

    public static void open(Module mod, float cardX, float cardY, float cardW, float cardH) {
        if (mod == null) return;
        if (!hasVisibleSettings(mod)) return;
        if (isExtracted(mod)) return;
        WINDOWS.add(new CompactModuleWindow(mod, cardX, cardY, cardW, cardH));
    }

    public static boolean closeTop() {
        for (int i = WINDOWS.size() - 1; i >= 0; i--) {
            CompactModuleWindow w = WINDOWS.get(i);
            if (!w.closing) {
                w.beginClose();
                return true;
            }
        }
        return false;
    }

    public static void closeAll() {
        WINDOWS.clear();
    }

    public static void onRelease() {
        for (CompactModuleWindow w : WINDOWS) w.dragging = false;
    }

    private void beginClose() {
        if (closing) return;
        closing = true;
        morph.run(0.0, 0.42, Easings.QUART_IN);
    }

    /**
     * Returns the settings shown in the floating window.
     * WatermarkElement itself is a HudElement, so its six switches live on Hud
     * and are injected here alongside the existing element scale setting.
     */
    private static List<Setting<?>> getWindowSettings(Module mod) {
        List<Setting<?>> settings = new ArrayList<>(mod.getSettingsForGUI());
        if ("Watermark".equalsIgnoreCase(mod.name)) {
            Hud hud = null;
            try {
                hud = Vesence.get.manager.get(Hud.class);
            } catch (Throwable ignored) {
            }
            if (hud != null) {
                addIfMissing(settings, hud.watermarkFps);
                addIfMissing(settings, hud.watermarkBps);
                addIfMissing(settings, hud.watermarkPing);
                addIfMissing(settings, hud.watermarkNickname);
                addIfMissing(settings, hud.watermarkCoordinates);
                addIfMissing(settings, hud.watermarkTime);
            }
        }
        return settings;
    }

    private static void addIfMissing(List<Setting<?>> settings, Setting<?> candidate) {
        if (candidate == null) return;
        for (Setting<?> s : settings) {
            if (s == candidate || s.name.equals(candidate.name)) return;
        }
        settings.add(candidate);
    }

    public static void render(Renderer2D r, int mouseX, int mouseY, float alpha) {
        if (r == null) return;

        float baseX = CompactGuiScreen.x + CompactGuiScreen.GUI_W + GAP;
        float baseY = CompactGuiScreen.y;

        WINDOWS.removeIf(w -> w.closing && w.morph.getValue() < 0.01);

        float stackY = baseY;
        for (CompactModuleWindow w : WINDOWS) {
            float th = w.computeTargetH(r);
            w.dispH = w.dispHInit ? w.dispH + (th - w.dispH) * 0.22f : th;
            w.dispHInit = true;
            float tx, ty;
            if (w.moved) {
                tx = w.manualX;
                ty = w.manualY;
            } else {
                tx = baseX;
                ty = stackY;
                stackY += w.dispH + GAP;
            }
            w.renderWindow(r, mouseX, mouseY, alpha, tx, ty, w.dispH);
        }
    }

    private float computeTargetH(Renderer2D r) {
        float contentW = WIDTH - PAD * 2f;
        float total = measureTotalHeight(r, contentW);
        float th = HEADER_H + total + PAD;
        return Math.min(th, MAX_HEIGHT);
    }

    private void renderWindow(Renderer2D r, int mouseX, int mouseY, float alpha,
                              float targetX, float targetY, float winH) {
        morph.update();
        float e = (float) morph.getValue();

        if (dragging) {
            moved = true;
            manualX = mouseX - dragOffX;
            manualY = mouseY - dragOffY;
            targetX = manualX;
            targetY = manualY;
        }

        if (!dispInit) {
            dispX = targetX;
            dispY = targetY;
            dispInit = true;
        }
        if (dragging) {
            dispX = targetX;
            dispY = targetY;
        } else {
            dispX += (targetX - dispX) * 0.2f;
            dispY += (targetY - dispY) * 0.2f;
        }

        rx = lerp(originX, dispX, e);
        ry = lerp(originY, dispY, e);
        rw = lerp(originW, WIDTH, e);
        rh = lerp(originH, winH, e);

        float a = alpha * (float) morph.getValue();
        float contentA = alpha * (float) Math.max(0f, (morph.getValue() - 0.35) / 0.65);

        drawWindowBg(r, rx, ry, rw, rh, a);
        if (contentA <= 0.01f) return;

        int cCol = Renderer2D.ColorUtil.getClientColor();
        int titleCol = ColorUtil.replAlpha(cCol, (int) (255 * contentA));
        r.pushClipRect((int) (rx + PAD) - 1, (int) ry,
                (int) (rw - PAD * 2 - CLOSE_SIZE) + 1, (int) HEADER_H);
        r.text(FontRegistry.MONTSERRAT, rx + PAD + 4, ry + 18, 19, module.name, titleCol);
        r.popClipRect();

        float cbx = rx + rw - CLOSE_SIZE - PAD + 2f;
        float cby = ry + (HEADER_H - CLOSE_SIZE) / 2f;
        boolean closeHov = inside(mouseX, mouseY, cbx - 2, cby - 2, CLOSE_SIZE + 4, CLOSE_SIZE + 4);
        r.textRight(FontRegistry.VESENCE, rx + rw - PAD, ry + 13f, 12f, "X",
                ColorUtil.replAlpha(-1, (int) ((closeHov ? 235 : 130) * contentA)));

        float contentW = rw - PAD * 2f;
        float bodyX = rx + PAD;
        float bodyY = ry + HEADER_H;
        float bodyH = rh - HEADER_H - PAD;

        List<Setting<?>> settings = getWindowSettings(module);
        float totalH = measureTotalHeight(r, contentW, settings);
        scroll.setSpeed(26);
        scroll.setMax(totalH, bodyH);
        boolean hov = inside(mouseX, mouseY, rx, ry, rw, rh);
        scroll.setEnabled(hov && totalH > bodyH && !dragging);
        scroll.update();
        float off = scroll.getScroll();

        r.pushClipRect((int) bodyX - 1, (int) bodyY - 1, (int) contentW + 2, (int) bodyH + 2);
        float cy = bodyY + off + 5;
        for (Setting<?> setting : settings) {
            if ((boolean) setting.hidden.get()) continue;
            float sH = CompactGuiRenderSetting.getSettingHeight(r, setting, contentW);
            if (cy + sH >= bodyY - 5 && cy <= bodyY + bodyH + 5) {
                CompactGuiRenderSetting.renderSetting(
                        r, setting, bodyX, cy, contentW,
                        mouseX, mouseY,
                        ColorUtil.replAlpha(Renderer2D.ColorUtil.getOutLineColor(1, 1), (int) (15 * contentA)),
                        ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), (int) (255 * contentA)),
                        ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), (int) (12 * contentA)),
                        ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), (int) (80 * contentA)),
                        ColorUtil.replAlpha(Renderer2D.ColorUtil.getTextColor(1, 1), (int) (255 * contentA)),
                        contentA
                );
            }
            cy += sH + SETTING_GAP;
        }
        r.popClipRect();
    }

    private float measureTotalHeight(Renderer2D r, float contentW) {
        return measureTotalHeight(r, contentW, getWindowSettings(module));
    }

    private float measureTotalHeight(Renderer2D r, float contentW, List<Setting<?>> settings) {
        float total = 0f;
        for (Setting<?> s : settings) {
            if ((boolean) s.hidden.get()) continue;
            total += CompactGuiRenderSetting.getSettingHeight(r, s, contentW) + SETTING_GAP;
        }
        return total;
    }

    private static void drawWindowBg(Renderer2D r, float x, float y, float w, float h, float a) {
        int cCol = Renderer2D.ColorUtil.getClientColor();
        float ga = ClickGui.guiAlpha.get().floatValue() * a;
        float corner = ClickGui.getGuiCorner();

        r.shadow(x, y, w, h, corner, 9f, 0f, ColorUtil.replAlpha(ColorUtil.BLACK, (int) (90 * a)));
        if (!ClickGui.squircleGui.get()) {
            if (ClickGui.blurGui.get()) {
                r.blur(x, y, w, h, ClickGui.blurStrengthGui.get().floatValue(), corner, a);
            }
            int bg = ColorUtil.multAlpha(ColorUtil.multDark(cCol, 0.10f), ga);
            int bg2 = ColorUtil.multAlpha(ColorUtil.multDark(cCol, 0.05f), ga);
            r.gradient(x, y, w, h, corner, bg, bg, bg2, bg2, true);
            int outline = ColorUtil.multAlpha(ColorUtil.multDark(cCol, 1f), ga);
            r.gradientOutline(x, y, w, h, corner, outline, outline,
                    ColorUtil.multAlpha(ColorUtil.BLACK, 0), ColorUtil.multAlpha(ColorUtil.BLACK, 0), 1f, true);
        } else {
            r.drawSquircleShadow(x - 5, y - 5, w + 10, h + 10, ClickGui.getGuiSquirt(),
                    BorderRadius.all(corner), 15, 1, ColorUtil.replAlpha(cCol, (int) (15 * a)));
            if (ClickGui.blurGui.get()) {
                r.blurSquircle(x, y, w, h, ClickGui.blurStrengthGui.get().floatValue(),
                        ClickGui.getGuiSquirt(), BorderRadius.all(corner), a);
            }
            int bg = ColorUtil.multAlpha(ColorUtil.multDark(cCol, 0.08f), ga);
            int bg2 = ColorUtil.multAlpha(ColorUtil.multDark(cCol, 0.10f), ga);
            r.drawSquircleGradient(x, y, w, h, ClickGui.getGuiSquirt(), BorderRadius.all(corner),
                    bg, bg, bg2, bg2);
        }
    }

    public static boolean handleClick(int mouseX, int mouseY, int button) {
        for (int i = WINDOWS.size() - 1; i >= 0; i--) {
            CompactModuleWindow w = WINDOWS.get(i);
            if (w.closing) continue;
            if (w.clickWindow(mouseX, mouseY, button)) {
                if (w.moved) {
                    WINDOWS.remove(i);
                    WINDOWS.add(w);
                }
                return true;
            }
        }
        return false;
    }

    private boolean clickWindow(int mouseX, int mouseY, int button) {
        if (!inside(mouseX, mouseY, rx, ry, rw, rh)) return false;

        if (mouseY <= ry + HEADER_H) {
            float cbx = rx + rw - CLOSE_SIZE - PAD + 2f;
            float cby = ry + (HEADER_H - CLOSE_SIZE) / 2f;
            if (inside(mouseX, mouseY, cbx - 2, cby - 2, CLOSE_SIZE + 4, CLOSE_SIZE + 4)) {
                beginClose();
                return true;
            }
            dragging = true;
            moved = true;
            dragOffX = mouseX - rx;
            dragOffY = mouseY - ry;
            return true;
        }

        if (morph.getValue() < 0.85) return true;

        Renderer2D r = Vesence.getRenderer();
        if (r == null) return true;

        float contentW = rw - PAD * 2f;
        float bodyX = rx + PAD;
        float bodyY = ry + HEADER_H;
        float bodyH = rh - HEADER_H - PAD;
        if (!inside(mouseX, mouseY, bodyX, bodyY, contentW, bodyH)) return true;

        List<Setting<?>> settings = getWindowSettings(module);
        float off = scroll.getScroll();
        float cy = bodyY + off + 5;
        for (Setting<?> setting : settings) {
            if ((boolean) setting.hidden.get()) continue;
            float sH = CompactGuiRenderSetting.getSettingHeight(r, setting, contentW);
            boolean rowVisible = cy + sH >= bodyY && cy <= bodyY + bodyH;
            if (rowVisible
                    && mouseX >= bodyX && mouseX < bodyX + contentW
                    && mouseY >= cy && mouseY < cy + sH) {
                CompactGuiMouseClickedSetting.handleSettingClick(
                        r, setting, bodyX, cy, contentW, mouseX, mouseY, button);
                return true;
            }
            cy += sH + SETTING_GAP;
        }
        return true;
    }

    public static boolean handleScroll(float mouseX, float mouseY, double amount) {
        for (int i = WINDOWS.size() - 1; i >= 0; i--) {
            CompactModuleWindow w = WINDOWS.get(i);
            if (w.closing) continue;
            if (inside(mouseX, mouseY, w.rx, w.ry, w.rw, w.rh)) {
                w.scroll.handleScroll(amount);
                return true;
            }
        }
        return false;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static boolean inside(float px, float py, float x, float y, float w, float h) {
        return px >= x && py >= y && px <= x + w && py <= y + h;
    }
}
