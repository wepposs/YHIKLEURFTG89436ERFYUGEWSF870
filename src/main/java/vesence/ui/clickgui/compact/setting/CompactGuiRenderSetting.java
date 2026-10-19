package vesence.ui.clickgui.compact.setting;

import java.awt.*;
import java.util.HashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BindSettings;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.HueSetting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.api.setting.impl.MultiBooleanSetting;
import vesence.module.api.setting.impl.NoneSetting;
import vesence.module.api.setting.impl.RangeSliderSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.module.api.setting.impl.StringSetting;
import vesence.module.api.setting.impl.TitleSetting;
import vesence.module.api.setting.impl.ThemeSetting;
import vesence.module.Theme;
import vesence.ui.clickgui.GuiScreen;
import vesence.ui.clickgui.compact.CompactGuiRender;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.animation.util.Animation;
import vesence.utils.render.math.animation.anim.util.Easings;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.text.FontObject;
import vesence.utils.render.text.FontRegistry;
import vesence.utils.render.utils.KeyUtil;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class CompactGuiRenderSetting {

    public static HashMap<String, Animation2> modeAnimations = new HashMap<>();
    public static HashMap<String, Animation2> multiBoolAnimations = new HashMap<>();

    private static final Identifier HUE_TEXTURE = Identifier.of("vesence", "textures/gui/hue.png");

    public static final float C_HUE_PREVIEW_H = 10f;
    public static final float C_HUE_CONTENT_TOP = 14f;
    public static final float C_HUE_BAR_H = 5;
    public static final float C_HUE_BAR_GAP = 6f;
    public static final float C_HUE_BOTTOM = 4f;
    public static final int C_HUE_BAR_COUNT = 4;

    public static float hueExpandedExtra() {
        float total = C_HUE_CONTENT_TOP + C_HUE_BAR_COUNT * C_HUE_BAR_H
                + (C_HUE_BAR_COUNT - 1) * C_HUE_BAR_GAP + C_HUE_BOTTOM;
        return total - C_HUE_PREVIEW_H;
    }

    public static float hueBarY(float ry, int i) {
        return ry + C_HUE_CONTENT_TOP + i * (C_HUE_BAR_H + C_HUE_BAR_GAP);
    }

    private static final HashMap<String, Float> scrollOffsets = new HashMap<>();
    private static final HashMap<String, Long> scrollUpdateTimes = new HashMap<>();
    private static final HashMap<String, Long> scrollVisibleTimes = new HashMap<>();
    private static final float SCROLL_SPEED = 22.0f;
    private static final float SCROLL_GAP = 12.0f;
    private static final long SCROLL_DELAY = 1200L;

    private static final float LF = 13;
    private static final float CF = 12;
    private static final float VF = 12;
    private static final float PAD = 3;
    private static final float CHIP_H = 12;
    private static final float CHIP_PAD = 3;
    private static final float CHIP_GAP = 4;

    private static String formatOne(double value, double increment, boolean percent) {
        if (percent) {
            return String.format("%.0f%%", value);
        } else if (increment >= 1.0) {
            return String.valueOf((int) Math.round(value));
        } else {
            int d = Math.max(1, (int) Math.ceil(-Math.log10(increment)));
            return String.format("%." + d + "f", value);
        }
    }

    private static String formatRange(RangeSliderSetting s) {
        return formatOne(s.valueFrom, s.increment, s.percent) + " - " + formatOne(s.valueTo, s.increment, s.percent);
    }

    private static float getScrollOffset(String key, float textWidth, float areaWidth, long now) {
        float maxScroll = Math.max(0f, textWidth - areaWidth);
        if (maxScroll <= 0f) return 0f;
        if (!scrollVisibleTimes.containsKey(key)) scrollVisibleTimes.put(key, now);
        if (!scrollUpdateTimes.containsKey(key)) scrollUpdateTimes.put(key, now);
        if (!scrollOffsets.containsKey(key)) scrollOffsets.put(key, 0f);
        long visibleTime = scrollVisibleTimes.get(key);
        long lastUpdate = scrollUpdateTimes.get(key);
        float offset = scrollOffsets.get(key);
        float delta = (now - lastUpdate) / 750.0f;
        scrollUpdateTimes.put(key, now);
        float cycleWidth = textWidth + SCROLL_GAP;
        if (now - visibleTime >= SCROLL_DELAY) {
            offset += delta * SCROLL_SPEED;
            if (offset >= cycleWidth) offset -= cycleWidth;
        }
        scrollOffsets.put(key, offset);
        return offset;
    }

    public static void renderScrollingText(Renderer2D r, String key, String text, float textX, float textY, float fontSize, int color, float areaX, float areaWidth, float areaHeight) {
        float textWidth = r.measureText(FontRegistry.SF_MEDIUM, text, fontSize).width;
        float maxScroll = Math.max(0f, textWidth - areaWidth);
        long now = System.currentTimeMillis();
        r.pushClipRect((int) areaX, (int)(textY - fontSize + 2), (int) areaWidth, (int)(areaHeight));
        if (maxScroll > 0f) {
            float offset = getScrollOffset(key, textWidth, areaWidth, now);
            float cycleWidth = textWidth + SCROLL_GAP;
            r.text(FontRegistry.SF_MEDIUM, textX - offset, textY, fontSize, text, color);
            r.text(FontRegistry.SF_MEDIUM, textX - offset + cycleWidth, textY, fontSize, text, color);
        } else {
            scrollOffsets.remove(key);
            scrollUpdateTimes.remove(key);
            scrollVisibleTimes.remove(key);
            r.text(FontRegistry.SF_MEDIUM, textX, textY, fontSize, text, color);
        }
        r.popClipRect();
    }
    public static void renderScrollingText(Renderer2D r, FontObject font, String key, String text, float textX, float textY, float fontSize, int color, float areaX, float areaWidth, float areaHeight) {
        float textWidth = r.measureText(font, text, fontSize).width;
        float maxScroll = Math.max(0f, textWidth - areaWidth);
        long now = System.currentTimeMillis();
        r.pushClipRect((int) areaX, (int)(textY - fontSize + 2), (int) areaWidth, (int)(areaHeight));
        if (maxScroll > 0f) {
            float offset = getScrollOffset(key, textWidth, areaWidth, now);
            float cycleWidth = textWidth + SCROLL_GAP;
            r.text(font, textX - offset, textY, fontSize, text, color);
            r.text(font, textX - offset + cycleWidth, textY, fontSize, text, color);
        } else {
            scrollOffsets.remove(key);
            scrollUpdateTimes.remove(key);
            scrollVisibleTimes.remove(key);
            r.text(font, textX, textY, fontSize, text, color);
        }
        r.popClipRect();
    }

    public static float getSettingHeight(Renderer2D r, Setting<?> setting) {
        return getSettingHeight(r, setting, 135);
    }

    public static float getSettingHeight(Renderer2D r, Setting<?> setting, float w) {
        if ((boolean) setting.hidden.get()) return 0;
        if (setting instanceof NoneSetting) return ((NoneSetting) setting).get();
        if (setting instanceof TitleSetting) return 12;
        if (setting instanceof BooleanSetting) return 15;
        if (setting instanceof SliderSetting) return 23;
        if (setting instanceof RangeSliderSetting) return 14;
        if (setting instanceof BindSettings) return 13;
        if (setting instanceof StringSetting) return 31;
        if (setting instanceof HueSetting hs) {
            float base = C_HUE_PREVIEW_H;
            float ot = (float) hs.openAnimation.get();
            return base + hueExpandedExtra() * ot + 5;
        }
        if (setting instanceof ModeSetting ms) {
            return vesence.ui.clickgui.compact.client.CompactGuiRenderClientSetting.dropdownHeight(r, ms);
        }
        if (setting instanceof MultiBooleanSetting mbs) {
            return vesence.ui.clickgui.compact.client.CompactGuiRenderClientSetting.dropdownHeight(r, mbs);
        }
        if (setting instanceof ThemeSetting ts) {
            float cx = PAD;
            float cy = 11;
            float bs = 8;
            float sp = 3;
            for (Theme t : ts.themes) {
                if (cx + bs > w) { cx = PAD; cy += bs + sp; }
                cx += bs + sp;
            }
            return cy + bs + 3;
        }
        return 0;
    }

    public static float renderSetting(
            Renderer2D r, Setting<?> setting,
            float x, float y, float w,
            int mouseX, int mouseY,
            int outlineColor, int mainColor, int mainColor6, int mainColor40,
            int textColor, float alpha
    ) {
        if ((boolean) setting.hidden.get()) return 0;
        r.pushAlpha(alpha);
        float h = 0;
        float ry = y;

        if (setting instanceof NoneSetting) {
            h = ((NoneSetting) setting).get();

        } else if (setting instanceof TitleSetting ts) {

            float tw = r.measureText(FontRegistry.MONTSERRAT, ts.name, 17, -0.3f).width + 3;
            float tx = x + w / 2f;
            float ty = ry + 7.5f;
            float ly = ty - 0.5f;
            float gap = 4f;
            float lx1 = x + PAD;
            float lw1 = (w / 2f) - (tw / 2f) - gap - PAD;
            if (lw1 > 0) {
                r.rect(lx1, ly, lw1, 1.25f, 2,
                        ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), (int)(alpha * 195)));
            }
            float rx2 = tx + (tw / 2f) + gap;
            float rw2 = (x + w - PAD) - rx2;
            if (rw2 > 0) {
                r.rect(rx2, ly, rw2, 1.25f, 2,
                        ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), (int)(alpha * 195)));
            }
            r.textCenter(FontRegistry.MONTSERRAT, tx, ty + 2, 17, ts.name,
                    ColorUtil.replAlpha(-1, (int)(alpha * 225)), -0.3f);
            h = 10;

        } else if (setting instanceof BooleanSetting bs) {
            boolean val = bs.get();
            float cs = 9.5f;
            float cbx = x + PAD;
            float cby = ry + 3;
            bs.anim.update();
            bs.anim.run(val ? 1.0 : 0.0, 0.2F, Easings.SINE_OUT, true);
            float anim = (float) bs.anim.get();
            int boxBg = ColorUtil.overCol(
                    ColorUtil.replAlpha(-1, (int)(20 * alpha)),
                    ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), (int)(80 * alpha)), anim);
            int boxBg2 = ColorUtil.overCol(
                    ColorUtil.replAlpha(-1, (int)(15 * alpha)),
                    ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), (int)(65 * alpha)), anim);
            r.rect(cbx, cby, cs, cs, 3, boxBg);
            r.rectOutline(cbx, cby, cs, cs, 4, boxBg2, 0.25f);
            if (anim > 0.01F) {
                float ca = Math.min(1.0F, anim * 1.5F);
                int cc = ColorUtil.replAlpha(-1, (int)(255 * ca * alpha));
                float cx = cbx + 2;
                float cy = cby + cs / 2f;
                float s = cs * 0.25F * anim;
                r.text(FontRegistry.VESENCE, cx, cy + 1.5f, 11, "Y", cc);
            }
            float tsx = cbx + cs + 3;
            float taw = x + w - tsx - PAD;
            renderScrollingText(r, FontRegistry.MONTSERRAT, "compact_bool_" + setting.name, setting.name, tsx, ry + 10, 13, ColorUtil.overCol(ColorUtil.replAlpha(-1, (int) (100 * alpha)), ColorUtil.replAlpha(-1, alpha), anim), tsx, taw, LF + 4);
            h = 11;

        } else if (setting instanceof SliderSetting ss) {
            float sw = w - PAD * 2;
            float sy = ry + 13.5f;
            float sh = 4.5f;
            Animation2 sa = GuiScreen.getSliderAnimation(ss);
            float tp = (float)((ss.current - ss.minimum) / (ss.maximum - ss.minimum));
            sa.update();
            sa.run(tp, 0.2, Easings.CUBIC_OUT);
            float prog = (float) sa.getValue();
            float pw = sw * prog;
            r.rect(x + PAD, sy, sw, sh, 1.25f, ColorUtil.replAlpha(-1, (int)(15 * alpha)));
            r.gradient(x + PAD + 0.5f, sy + 0.3f, Math.max(0, pw - 1), sh - 0.6f, 1.25f,
                    ColorUtil.replAlpha(mainColor, alpha),
                    ColorUtil.replAlpha(mainColor, (int)(85 * alpha)),
                    ColorUtil.replAlpha(mainColor, (int)(85 * alpha)),
                    ColorUtil.replAlpha(mainColor, alpha));
            r.rect(x + PAD + Math.max(0, pw - 4), sy - 0.75f, 6, 6, 4,
                    ColorUtil.replAlpha(-1, (int)(255 * alpha)));
            String vt;
            if (ss.editing) {
                boolean blink = System.currentTimeMillis() / 500L % 2L == 0L;
                vt = ss.editBuffer + (blink ? "_" : "");
            } else if (ss.percent) {
                vt = String.format("%.0f%%", ss.current);
            } else if (ss.increment >= 1.0) {
                vt = String.valueOf((int) Math.round(ss.current));
            } else {
                int d = Math.max(1, (int) Math.ceil(-Math.log10(ss.increment)));
                vt = String.format("%." + d + "f", ss.current);
            }
            float vtw = r.measureText(FontRegistry.MONTSERRAT, vt, LF).width;
            float staw = w - vtw - 10;
            renderScrollingText(r, FontRegistry.MONTSERRAT, "compact_slider_" + setting.name, setting.name, x + PAD, ry + 7.5f, LF, ColorUtil.replAlpha(-1, alpha), x + PAD, staw, LF + 4);
            int[] sliderValueGrad = new int[] {ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), alpha),
                    ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), (int) (200 * alpha))};
            if (ss.editing) {
                r.text(FontRegistry.MONTSERRAT, x + w - vtw - PAD, ry + 7.5f, LF, vt, sliderValueGrad);
            } else {
                vesence.utils.render.text.AnimatedText.draw(r, FontRegistry.MONTSERRAT,
                        "ds_" + System.identityHashCode(ss), vt, x + w - PAD, ry + 7.5f, LF,
                        sliderValueGrad, vesence.utils.render.text.AnimatedText.ALIGN_RIGHT);
            }
            h = 12;

        } else if (setting instanceof RangeSliderSetting rs) {
            float sw = w - PAD * 2;
            float sy = ry + 11;
            float sh = 2.5f;
            double span = rs.maximum - rs.minimum;
            float fromProg = (float)((rs.valueFrom - rs.minimum) / span);
            float toProg = (float)((rs.valueTo - rs.minimum) / span);
            float fromX = x + PAD + sw * fromProg;
            float toX = x + PAD + sw * toProg;
            r.rect(x + PAD, sy, sw, sh, 1.5f, ColorUtil.replAlpha(-1, (int)(15 * alpha)));
            r.gradient(fromX, sy + 0.3f, Math.max(0, toX - fromX), sh - 0.6f, 1.5f,
                    ColorUtil.replAlpha(mainColor, alpha),
                    ColorUtil.replAlpha(mainColor, (int)(85 * alpha)),
                    ColorUtil.replAlpha(mainColor, (int)(85 * alpha)),
                    ColorUtil.replAlpha(mainColor, alpha));
            r.rect(fromX - 1.75f, sy - 0.5f, 3.5f, 3.5f, 1.5f, ColorUtil.replAlpha(-1, (int)(255 * alpha)));
            r.rect(toX - 1.75f, sy - 0.5f, 3.5f, 3.5f, 1.5f, ColorUtil.replAlpha(-1, (int)(255 * alpha)));
            String vt = formatRange(rs);
            float vtw = r.measureText(FontRegistry.SF_MEDIUM, vt, VF).width;
            float staw = w - vtw - 10;
            renderScrollingText(r, "compact_range_" + setting.name, setting.name, x + PAD, ry + 7, LF, ColorUtil.replAlpha(-1, alpha), x + PAD, staw, LF + 4);
            r.text(FontRegistry.SF_MEDIUM, x + w - vtw - PAD, ry + 7, VF, vt,
                    ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), alpha));
            h = 12;

        } else if (setting instanceof ModeSetting ms) {
            h = vesence.ui.clickgui.compact.client.CompactGuiRenderClientSetting
                    .renderDropdown(r, ms, x, ry, w, mouseX, mouseY, 1f);

        } else if (setting instanceof BindSettings bs) {
            String dn = setting.name != null && !setting.name.isEmpty() ? setting.name : "KEY";
            String kt = bs.active ? "..." : bs.label();
            float ktw = r.measureText(FontRegistry.MONTSERRAT, kt, 12).width;
            // Ограничиваем ширину кнопки, чтобы длинное комбо не выходило за
            // рамки панели: максимум 65% ширины строки.
            float maxBw = (w - PAD * 2) * 0.65f;
            float bw = Math.min(ktw + 7, maxBw);
            float bx = x + w - bw - 3;
            r.rect(bx, ry + 0.75f, bw, 11, 2.5f, ColorUtil.replAlpha(-1, (int)(15 * alpha)));
            // Текст бинда с клипом внутри кнопки (скролл если не влезает).
            r.pushClipRect((int)(bx + 2), (int)(ry - 1), (int)(bw - 4), 14);
            r.textRight(FontRegistry.MONTSERRAT, bx + bw - 3.5f, ry + 8.75f, 12, kt,
                    kt.contains("NONE") ? ColorUtil.replAlpha(-1, (int)(100 * alpha)) : ColorUtil.replAlpha(-1, (int)(255 * alpha)));
            r.popClipRect();
            renderScrollingText(r, FontRegistry.MONTSERRAT, "compact_bind_" + setting.name, dn, x + PAD, ry + 9, 13,
                    ColorUtil.replAlpha(-1, (int)(255 * alpha)), x + PAD, bx - x - PAD - 2, 13 + 4);
            h = 13;

        } else if (setting instanceof StringSetting sts) {
            float fw = w - PAD * 2;
            float fy = ry + 16.5f;
            float fh = 13.5f;
            boolean active = GuiScreen.activeStringSetting == sts && sts.active;

            sts.activeAnim.update();
            sts.activeAnim.run(active ? 1.0 : 0.0, 0.25, Easings.CUBIC_OUT, true);
            float activeT = sts.activeAnim.get();
            int greyText = ColorUtil.getColor(135, 135, 135);
            int textCol = ColorUtil.replAlpha(ColorUtil.overCol(greyText, -1, activeT), alpha);

            String input = sts.input;
            int len = input.length();
            if (len > sts.lastRenderedLength) {
                sts.charAnim.run(0.0, 1.0E-9);
                sts.charAnim.run(1.0, 0.22, Easings.CUBIC_OUT);
            }
            sts.lastRenderedLength = len;
            sts.charAnim.update();
            float charT = sts.charAnim.get();

            r.text(FontRegistry.MONTSERRAT, x + PAD, ry + 9.5f, LF, setting.name,
                    ColorUtil.replAlpha(-1, alpha));
            r.rect(x + PAD, fy, fw, fh, 3, ColorUtil.overCol(ColorUtil.replAlpha(-1, (int)(8 * alpha)), ColorUtil.replAlpha(-1, (int)(14 * alpha)), sts.activeAnim.get()));
            r.rectOutline(x + PAD, fy, fw, fh, 4, ColorUtil.overCol(ColorUtil.replAlpha(-1, (int)(10 * alpha)), ColorUtil.replAlpha(-1, (int)(16 * alpha)), sts.activeAnim.get()), 0.5f);
            float tx = x + PAD + 3;
            float ty = fy + 9;
            float cx = tx + 1;
            float maxX = x + PAD + fw - 3;
            for (int i = 0; i < input.length(); i++) {
                char c = input.charAt(i);
                String cs = String.valueOf(c);
                float cw = r.measureText(FontRegistry.MONTSERRAT, cs, 11.5f).width;
                if (cx + cw > maxX) break;
                boolean lastChar = i == input.length() - 1;
                int charColor = lastChar
                        ? ColorUtil.replAlpha(textCol, (int) (ColorUtil.getAlpha(textCol) * charT))
                        : textCol;
                r.text(FontRegistry.MONTSERRAT, cx, ty, 11.5f, cs, charColor);
                cx += lastChar ? cw * charT : cw;
            }
            if (active) {
                boolean show = System.currentTimeMillis() / 500L % 2L == 0L;
                if (show) r.text(FontRegistry.MONTSERRAT, cx + 1, ty, 11.5f, "_", ColorUtil.replAlpha(-1, alpha));
            }
            h = 18;

        } else if (setting instanceof HueSetting hs) {
            hs.openAnimation.update();
            hs.openAnimation.run(hs.opened ? 1.0 : 0.0, 0.3, Easings.CUBIC_OUT);
            float ot = (float) hs.openAnimation.get();
            r.text(FontRegistry.MONTSERRAT, x + PAD, ry + 10.5f, LF, setting.name,
                    ColorUtil.replAlpha(-1, alpha));
            float bs2 = 9;
            float bx = x + w - bs2 - PAD;
            r.rect(bx, ry + 4, bs2, bs2, 2.5f,
                    Renderer2D.ColorUtil.replAlpha(hs.getColor().getRGB(), (int)(255 * alpha)));
            r.pushScale(hs.openAnimation.get(), x + w / 2f, y + h / 2f);
            h = C_HUE_PREVIEW_H;
            if (ot > 0.01f) {
                r.pushAlpha(ot);
                float barX = x + PAD;
                float barW = w - PAD * 2;
                float rounding = C_HUE_BAR_H / 2f;
                float ch = hs.getHue();
                int whiteBg = ColorUtil.getColor(255, 255, 255, (int)(12 * alpha));
                int fullColor = Color.getHSBColor(ch, hs.saturation, hs.brightness).getRGB();

                float b0 = hueBarY(ry, 0) + 5;
                r.rect(barX, b0, barW, C_HUE_BAR_H, rounding, whiteBg);
                r.drawImage(HUE_TEXTURE, barX, b0, barW, C_HUE_BAR_H,
                        ColorUtil.replAlpha(-1, (int)(255 * alpha)), false, rounding);
                drawBarHandle(r, barX, b0, barW, C_HUE_BAR_H, ch, alpha);

                float b1 = hueBarY(ry, 1) + 5;
                r.rect(barX, b1, barW, C_HUE_BAR_H, rounding, whiteBg);
                int satL = Color.getHSBColor(ch, 0f, hs.brightness).getRGB();
                int satR = Color.getHSBColor(ch, 1f, hs.brightness).getRGB();
                r.gradient(barX, b1, barW, C_HUE_BAR_H, rounding,
                        ColorUtil.replAlpha(satL, (int)(255 * alpha)),
                        ColorUtil.replAlpha(satR, (int)(255 * alpha)),
                        ColorUtil.replAlpha(satR, (int)(255 * alpha)),
                        ColorUtil.replAlpha(satL, (int)(255 * alpha)));
                drawBarHandle(r, barX, b1, barW, C_HUE_BAR_H, hs.saturation, alpha);

                float b2 = hueBarY(ry, 2) + 5;
                r.rect(barX, b2, barW, C_HUE_BAR_H, rounding, whiteBg);
                int brL = Color.getHSBColor(ch, hs.saturation, 0f).getRGB();
                int brR = Color.getHSBColor(ch, hs.saturation, 1f).getRGB();
                r.gradient(barX, b2, barW, C_HUE_BAR_H, rounding,
                        ColorUtil.replAlpha(brL, (int)(255 * alpha)),
                        ColorUtil.replAlpha(brR, (int)(255 * alpha)),
                        ColorUtil.replAlpha(brR, (int)(255 * alpha)),
                        ColorUtil.replAlpha(brL, (int)(255 * alpha)));
                drawBarHandle(r, barX, b2, barW, C_HUE_BAR_H, hs.brightness, alpha);

                float b3 = hueBarY(ry, 3) + 5;
                r.rect(barX, b3, barW, C_HUE_BAR_H, rounding, whiteBg);
                r.gradient(barX, b3, barW, C_HUE_BAR_H, rounding,
                        ColorUtil.replAlpha(fullColor, 0),
                        ColorUtil.replAlpha(fullColor, (int)(255 * alpha)),
                        ColorUtil.replAlpha(fullColor, (int)(255 * alpha)),
                        ColorUtil.replAlpha(fullColor, 0));
                drawBarHandle(r, barX, b3, barW, C_HUE_BAR_H, hs.alpha, alpha);
                r.popAlpha();
                h += hueExpandedExtra() * ot;
            }
            r.popScale();

        } else if (setting instanceof MultiBooleanSetting mbs) {
            h = vesence.ui.clickgui.compact.client.CompactGuiRenderClientSetting
                    .renderDropdown(r, mbs, x, ry, w, mouseX, mouseY, 1f);

        } else if (setting instanceof ThemeSetting ts) {
            r.text(FontRegistry.SF_MEDIUM, x + PAD, ry + 7, LF, setting.name,
                    ColorUtil.replAlpha(-1, alpha));
            float cx = x + PAD;
            float cy = ry + 13;
            float bs = 8;
            float sp = 3;
            Theme sel = ts.get();
            for (Theme theme : ts.themes) {
                if (cx + bs > x + w) { cx = x + PAD; cy += bs + sp; }
                boolean isSel = theme == sel;
                Animation2 ta = ts.themeAnimations.get(theme);
                if (ta != null) {
                    ta.update();
                    ta.run(isSel ? 1.0 : 0.0, 0.5, Easings.BACK_OUT, true);
                }
                float anim = ta != null ? (float) ta.get() : (isSel ? 1.0f : 0.0f);
                int tc = theme.getMain().getRGB();
                r.gradient(cx, cy, bs, bs, 2.5f,
                        ColorUtil.replAlpha(tc, (int)(255 * alpha)),
                        ColorUtil.replAlpha(tc, (int)(255 * alpha)),
                        ColorUtil.replAlpha(tc, (int)(205 * alpha)),
                        ColorUtil.replAlpha(tc, (int)(205 * alpha)));

                cx += bs + sp;
            }
            h = cy - ry + bs;
        }

        r.popAlpha();
        return h;
    }

    private static void drawBarHandle(Renderer2D r, float barX, float barY, float barW, float barH, float t, float alpha) {
        t = Math.max(0f, Math.min(1f, t));
        float handleSize = barH + 2f;
        float hx = barX + t * barW - handleSize / 2f;
        hx = Math.max(barX - 1f, Math.min(barX + barW - handleSize + 1f, hx));
        float hy = barY + (barH - handleSize) / 2f;
        r.rect(hx, hy, handleSize, handleSize, handleSize / 2f, ColorUtil.replAlpha(-1, (int)(255 * alpha)));
        r.rectOutline(hx, hy, handleSize, handleSize, handleSize / 2f, ColorUtil.getColor(0, (int)(60 * alpha)), 1);
    }
}
