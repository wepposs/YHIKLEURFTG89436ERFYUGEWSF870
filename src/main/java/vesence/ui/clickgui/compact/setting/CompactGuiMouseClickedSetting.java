package vesence.ui.clickgui.compact.setting;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.Vesence;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BindSettings;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.HueSetting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.api.setting.impl.MultiBooleanSetting;
import vesence.module.api.setting.impl.RangeSliderSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.module.api.setting.impl.StringSetting;
import vesence.module.api.setting.impl.ThemeSetting;
import vesence.module.Theme;
import vesence.ui.clickgui.GuiScreen;
import vesence.ui.clickgui.compact.CompactGuiRender;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.text.FontRegistry;
import vesence.utils.render.utils.KeyUtil;

@Environment(EnvType.CLIENT)
public class CompactGuiMouseClickedSetting {

    private static final float LF = 12;
    private static final float CF = 12;
    private static final float PAD = 3;
    private static final float CHIP_H = 12  ;
    private static final float CHIP_PAD = 3;
    private static final float CHIP_GAP = 4;

    public static boolean handleSettingClick(Renderer2D r, Setting<?> setting, float x, float y, float w, int mouseX, int mouseY, int button) {
        if (button != 0) return false;

        if (setting instanceof BooleanSetting bs) {
            float rowH = 13;
            if (CompactGuiRender.isHovered(mouseX, mouseY, x, y, w, rowH)) {
                boolean newVal = !bs.get();
                bs.set(newVal);
                vesence.utils.render.utils.SoundUtil.playUi(newVal ? "cb_on" : "cb_off", 0.25F, 40L);
                if (Vesence.get.configManager != null) Vesence.get.configManager.autoSave();
                return true;
            }
        }

        if (setting instanceof SliderSetting ss) {

            String vt;
            if (ss.percent) vt = String.format("%.0f%%", ss.current);
            else if (ss.increment >= 1.0) vt = String.valueOf((int) Math.round(ss.current));
            else { int d = Math.max(1, (int) Math.ceil(-Math.log10(ss.increment))); vt = String.format("%." + d + "f", ss.current); }
            float vtw = r.measureText(FontRegistry.MONTSERRAT, vt, LF).width;
            float valX = x + w - vtw - PAD;
            if (CompactGuiRender.isHovered(mouseX, mouseY, valX - 3, y, vtw + PAD + 4, 13)) {
                beginSliderEdit(ss);
                return true;
            }

            float sy = y + 13.5f;
            float sh = 4.5f;
            if (CompactGuiRender.isHovered(mouseX, mouseY, x, sy - 1, w, sh)) {
                double oldValue = ss.current;
                if (ss.editing) commitSliderEdit(ss);
                GuiScreen.activeSliderSetting = ss;
                GuiScreen.sliderX = x + PAD;
                GuiScreen.sliderY = sy;
                GuiScreen.sliderWidth = w - PAD * 2;
                float progress = (mouseX - x - PAD) / (w - PAD * 2);
                progress = Math.max(0f, Math.min(1f, progress));
                double raw = ss.minimum + (ss.maximum - ss.minimum) * progress;
                ss.current = Math.round((raw - ss.minimum) / ss.increment) * ss.increment + ss.minimum;
                ss.current = Math.max(ss.minimum, Math.min(ss.maximum, ss.current));
                if (ss.current != oldValue) {
                    vesence.utils.render.utils.SoundUtil.playUi("slider", 0.12F, 45L);
                }
                return true;
            }
        }

        if (setting instanceof RangeSliderSetting rs) {
            float sy = y + 11;
            float sh = 5;
            if (CompactGuiRender.isHovered(mouseX, mouseY, x, sy - 1, w, sh + 2)) {
                GuiScreen.activeRangeSetting = rs;
                GuiScreen.sliderX = x + PAD;
                GuiScreen.sliderY = sy;
                GuiScreen.sliderWidth = w - PAD * 2;
                double span = rs.maximum - rs.minimum;
                float fromX = x + PAD + (float)((rs.valueFrom - rs.minimum) / span) * (w - PAD * 2);
                float toX = x + PAD + (float)((rs.valueTo - rs.minimum) / span) * (w - PAD * 2);
                rs.draggingThumb = (Math.abs(mouseX - fromX) <= Math.abs(mouseX - toX)) ? 1 : 2;
                float progress = (mouseX - x - PAD) / (w - PAD * 2);
                progress = Math.max(0f, Math.min(1f, progress));
                double raw = rs.minimum + span * progress;
                if (rs.draggingThumb == 1) rs.setFrom(raw); else rs.setTo(raw);
                return true;
            }
        }

        if (setting instanceof ModeSetting ms) {
            return vesence.ui.clickgui.compact.client.CompactGuiRenderClientSetting
                    .handleDropdownClick(r, ms, x, y, w, mouseX, mouseY, button);
        }

        if (setting instanceof BindSettings bs) {
            String kt = bs.active ? "..." : bs.label();
            float ktw = r.measureText(FontRegistry.SF_MEDIUM, kt, 12).width;
            float maxBw = (w - PAD * 2) * 0.65f;
            float bw = Math.min(ktw + 7, maxBw);
            float bx = x + w - bw - 3;
            // Кликаем как по самой кнопке, так и по всей строке настройки —
            // так по длинному биндовому чипу проще попасть.
            boolean hitButton = CompactGuiRender.isHovered(mouseX, mouseY, bx, y + 0.75f, bw, 11);
            boolean hitRow = CompactGuiRender.isHovered(mouseX, mouseY, x, y, w, 13);
            if (hitButton || hitRow) {
                if (GuiScreen.activeBindSetting != bs) {
                    if (GuiScreen.activeBindSetting != null) GuiScreen.activeBindSetting.active = false;
                    GuiScreen.activeBindSetting = bs;
                    bs.active = true;
                }
                return true;
            }
        }

        if (setting instanceof StringSetting sts) {
            float fw = w - PAD * 2;
            float fy = y + 16.5f;
            float fh = 13.5f;
            if (CompactGuiRender.isHovered(mouseX, mouseY, x + PAD, fy, fw, fh)) {
                if (GuiScreen.activeStringSetting != sts) {
                    if (GuiScreen.activeStringSetting != null) GuiScreen.activeStringSetting.active = false;
                    GuiScreen.activeStringSetting = sts;
                    sts.active = true;
                    vesence.utils.player.MovementManager.getInstance().lockMovement("StringSetting");
                }
                return true;
            }
            if (GuiScreen.activeStringSetting == sts) {
                GuiScreen.activeStringSetting.active = false;
                GuiScreen.activeStringSetting = null;
                vesence.utils.player.MovementManager.getInstance().unlockMovement("StringSetting");
            }
        }

        if (setting instanceof HueSetting hs) {
            float bs = 9;
            float bx = x + w - bs - PAD;
            if (CompactGuiRender.isHovered(mouseX, mouseY, bx, y + 4, bs, bs)) {
                hs.opened = !hs.opened;
                return true;
            }
            if (hs.opened && hs.openAnimation.get() > 0.3) {
                float barX = x + PAD;
                float barW = w - PAD * 2;
                float hitPad = 3f;
                for (int bar = 0; bar < CompactGuiRenderSetting.C_HUE_BAR_COUNT; bar++) {
                    float barY = CompactGuiRenderSetting.hueBarY(y, bar);
                    if (CompactGuiRender.isHovered(mouseX, mouseY, barX - 2f, barY - hitPad,
                            barW + 4f, CompactGuiRenderSetting.C_HUE_BAR_H + hitPad * 2f)) {
                        float t = Math.max(0f, Math.min(1f, (mouseX - barX) / barW));
                        vesence.ui.clickgui.component.mouse.setting.GuiMouseClickedSetting.applyHueBar(hs, bar, t);
                        hs.draggingBar = bar;
                        GuiScreen.activeHueSetting = hs;
                        GuiScreen.huePickerX = barX;
                        GuiScreen.huePickerY = barY;
                        GuiScreen.huePickerWidth = barW;
                        return true;
                    }
                }
            }
        }

        if (setting instanceof MultiBooleanSetting mbs) {
            return vesence.ui.clickgui.compact.client.CompactGuiRenderClientSetting
                    .handleDropdownClick(r, mbs, x, y, w, mouseX, mouseY, button);
        }

        if (setting instanceof ThemeSetting ts) {
            float cx = x + PAD;
            float cy = y + 13;
            float bs = 8;
            float sp = 3;
            for (Theme theme : ts.themes) {
                if (cx + bs > x + w) { cx = x + PAD; cy += bs + sp; }
                if (CompactGuiRender.isHovered(mouseX, mouseY, cx, cy, bs, bs)) {
                    ts.set(theme);
                    if (Vesence.get.configManager != null) Vesence.get.configManager.autoSave();
                    return true;
                }
                cx += bs + sp;
            }
        }

        return false;
    }

    public static void beginSliderEdit(SliderSetting ss) {
        if (GuiScreen.editingSliderSetting != null && GuiScreen.editingSliderSetting != ss) {
            commitSliderEdit(GuiScreen.editingSliderSetting);
        }

        if (GuiScreen.activeStringSetting != null) {
            GuiScreen.activeStringSetting.active = false;
            GuiScreen.activeStringSetting = null;
            vesence.utils.player.MovementManager.getInstance().unlockMovement("StringSetting");
        }
        ss.editing = true;

        ss.editBuffer = formatEditValue(ss);
        GuiScreen.editingSliderSetting = ss;
        GuiScreen.activeSliderSetting = null;
        vesence.utils.player.MovementManager.getInstance().lockMovement("SliderEdit");
    }

    private static String formatEditValue(SliderSetting ss) {
        if (ss.increment >= 1.0) {
            return String.valueOf((int) Math.round(ss.current));
        }
        int d = Math.max(1, (int) Math.ceil(-Math.log10(ss.increment)));
        return String.format(java.util.Locale.US, "%." + d + "f", ss.current);
    }

    public static void commitSliderEdit(SliderSetting ss) {
        if (ss == null) return;
        String buf = ss.editBuffer != null ? ss.editBuffer.trim().replace(',', '.').replace("%", "") : "";
        if (!buf.isEmpty()) {
            try {
                double v = Double.parseDouble(buf);
                v = Math.max(ss.minimum, Math.min(ss.maximum, v));

                v = Math.round((v - ss.minimum) / ss.increment) * ss.increment + ss.minimum;
                ss.current = Math.max(ss.minimum, Math.min(ss.maximum, v));
                if (Vesence.get.configManager != null) Vesence.get.configManager.autoSave();
            } catch (NumberFormatException ignored) {
            }
        }
        ss.editing = false;
        ss.editBuffer = "";
        if (GuiScreen.editingSliderSetting == ss) GuiScreen.editingSliderSetting = null;
        vesence.utils.player.MovementManager.getInstance().unlockMovement("SliderEdit");
    }

    public static void cancelSliderEdit(SliderSetting ss) {
        if (ss == null) return;
        ss.editing = false;
        ss.editBuffer = "";
        if (GuiScreen.editingSliderSetting == ss) GuiScreen.editingSliderSetting = null;
        vesence.utils.player.MovementManager.getInstance().unlockMovement("SliderEdit");
    }
}
