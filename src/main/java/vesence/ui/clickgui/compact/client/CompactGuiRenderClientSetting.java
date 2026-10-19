package vesence.ui.clickgui.compact.client;

import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Click;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.api.setting.impl.MultiBooleanSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.module.impl.misc.ClickGui;
import vesence.renderengine.render.Renderer2D;
import vesence.ui.clickgui.GuiScreen;
import vesence.ui.clickgui.compact.CompactGuiScreen;
import vesence.ui.clickgui.compact.setting.CompactGuiMouseClickedSetting;
import vesence.ui.clickgui.compact.setting.CompactGuiRenderSetting;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;
import vesence.utils.render.text.FontRegistry;

@Environment(EnvType.CLIENT)
public final class CompactGuiRenderClientSetting {

   private CompactGuiRenderClientSetting() {
   }

   private static final float ROW_H = 13;
   private static final float PAD = 3;
   private static final float BOX_VPAD = 0;
   private static final float BOX_HPAD = 5;
   private static final float OPT_H = 13f;
   private static final float OPT_GAP = 1.5f;
   private static final float OPT_TOP_GAP = 2f;
   private static final float ARROW_W = 10f;
   private static final float NAME_FONT = 13f;
   private static final float VALUE_FONT = 12.5f;
   private static final float OPT_FONT = 12.5f;
   private static final float LF = 13f;

   private static final float SLIDER_W = 90;
   private static final float SLIDER_H = 4.5f;
   private static final float SLIDER_VALUE_GAP = 6f;

   private static final Map<Object, Animation2> EXPAND_ANIMS = new HashMap<>();
   private static final Map<String, Animation2> OPT_HOVER_ANIMS = new HashMap<>();

   private static Animation2 expandAnim(Object key) {
      return EXPAND_ANIMS.computeIfAbsent(key, k -> new Animation2());
   }

   private static void drawPanelBg(Renderer2D r, float x, float y, float w, float h, float corner, float alpha) {
      if (vesence.module.impl.misc.ClickGui.blurGui.get()) {
         r.blur(x, y, w, h, vesence.module.impl.misc.ClickGui.blurStrengthGui.get().floatValue(), corner, alpha);
      }
   }

   private static Animation2 optHoverAnim(String key) {
      return OPT_HOVER_ANIMS.computeIfAbsent(key, k -> new Animation2());
   }

   private static boolean isHovered(float mx, float my, float x, float y, float w, float h) {
      return mx >= x && mx <= x + w && my >= y && my <= y + h;
   }

   private static java.util.List<String> modeOptions(ModeSetting ms) {
      java.util.List<String> out = new java.util.ArrayList<>();
      for (String m : ms.modes) {
         if (!m.equalsIgnoreCase(ms.currentMode)) out.add(m);
      }
      return out;
   }

   public static float getSettingHeight(Renderer2D renderer, Setting<?> setting, float width) {
      if ((boolean) setting.hidden.get()) return 0;

      if (setting instanceof ModeSetting ms) {
         Animation2 a = expandAnim(ms);
         int optCount = modeOptions(ms).size();
         float extra = OPT_TOP_GAP + optCount * (OPT_H + OPT_GAP);
         return ROW_H + extra * a.get();
      }
      if (setting instanceof MultiBooleanSetting mbs) {
         Animation2 a = expandAnim(mbs);
         float extra = OPT_TOP_GAP + mbs.settings.size() * (OPT_H + OPT_GAP);
         return ROW_H + extra * a.get();
      }
      if (setting instanceof SliderSetting) {
         return ROW_H;
      }
      return CompactGuiRenderSetting.getSettingHeight(renderer, setting, width);
   }

   public static float renderSetting(
         Renderer2D renderer,
         Setting<?> setting,
         float x, float y, float width,
         int mouseX, int mouseY,
         float alpha
   ) {
      if ((boolean) setting.hidden.get()) return 0;

      if (setting instanceof ModeSetting ms) {
         return renderMode(renderer, ms, x, y, width, mouseX, mouseY, alpha);
      }
      if (setting instanceof MultiBooleanSetting mbs) {
         return renderMulti(renderer, mbs, x, y, width, mouseX, mouseY, alpha);
      }
      if (setting instanceof SliderSetting ss) {
         return renderSlider(renderer, ss, x, y, width, mouseX, mouseY, alpha);
      }

      int outlineColor = ColorUtil.replAlpha(Renderer2D.ColorUtil.getOutLineColor(1, 1), (int) (15 * alpha));
      int mainColor    = ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), (int) (255 * alpha));
      int mainColor6   = ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), (int) (12 * alpha));
      int mainColor40  = ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), (int) (80 * alpha));
      int textColor    = ColorUtil.replAlpha(Renderer2D.ColorUtil.getTextColor(1, 1), (int) (255 * alpha));

      return CompactGuiRenderSetting.renderSetting(
            renderer, setting,
            x, y, width,
            mouseX, mouseY,
            outlineColor, mainColor, mainColor6, mainColor40,
            textColor, alpha
      );
   }

   private static float collapsedBoxW(Renderer2D r, String value) {
      float vw = r.measureText(FontRegistry.MONTSERRAT, value, VALUE_FONT).width;
      return vw + BOX_HPAD * 2 + ARROW_W;
   }

   private static float expandedBoxW(Renderer2D r, String value, java.util.List<String> options) {
      float max = collapsedBoxW(r, value);
      for (String o : options) {
         float ow = r.measureText(FontRegistry.MONTSERRAT, o, OPT_FONT).width + BOX_HPAD * 2;
         if (ow > max) max = ow;
      }
      return max;
   }

   public static float renderMode(Renderer2D r, ModeSetting ms, float x, float y, float w,
                                   int mouseX, int mouseY, float alpha) {
      Animation2 expand = expandAnim(ms);
      expand.update();
      expand.run(ms.opened ? 1.0 : 0.0, 0.25, Easings.CUBIC_OUT);
      float ev = expand.get();

      int textCol = ColorUtil.replAlpha(-1, (int) (255 * alpha));
      int subCol = ColorUtil.replAlpha(-1, (int) (155 * alpha));
      int accent = Renderer2D.ColorUtil.getClientColor();

      String value = ms.get();
      java.util.List<String> options = modeOptions(ms);

      float wCollapsed = collapsedBoxW(r, value);
      float wExpanded = expandedBoxW(r, value, options);
      float boxW = wCollapsed + (wExpanded - wCollapsed) * ev;
      float headH = ROW_H - BOX_VPAD * 2;
      float optionsH = options.size() * (OPT_H + OPT_GAP);
      float boxH = headH + (OPT_TOP_GAP + optionsH) * ev;
      float boxX = x + w - boxW - PAD;
      float boxY = y + BOX_VPAD;

      float nameAreaX = x + PAD;
      float nameAreaW = Math.max(10f, boxX - nameAreaX - 4f);
      CompactGuiRenderSetting.renderScrollingText(r, FontRegistry.MONTSERRAT,
            "client_mode_" + System.identityHashCode(ms), ms.name,
            nameAreaX, y + ROW_H / 2f + 3.25f, NAME_FONT, textCol,
            nameAreaX, nameAreaW, NAME_FONT + 4);

      int boxBg = ColorUtil.overCol(
            ColorUtil.replAlpha(-1, (int) (7 * alpha)),
            ColorUtil.replAlpha(-1, (int) (12 * alpha)), ev);
      r.rect(boxX, boxY, boxW, boxH, 3.5f, boxBg);
      r.rectOutline(boxX, boxY, boxW, boxH, 3.5f, ColorUtil.replAlpha(-1, (int) (12 * alpha)), 0.6f);

      int valCol = ColorUtil.overCol(subCol, ColorUtil.replAlpha(accent, (int) (255 * alpha)), ev);
      r.text(FontRegistry.MONTSERRAT, boxX + BOX_HPAD, boxY + headH / 2f + 3f, VALUE_FONT, value, valCol);

      String arrowIcon = "l";
      float arrowSize = 15f;
      float arrowRight = boxX + boxW - 4f;
      float arrowBaselineY = boxY + headH / 2f + 3.25f;
      float arrowW = r.measureText(FontRegistry.MON, arrowIcon, arrowSize).width;
      float arrowCenterX = arrowRight - arrowW / 2f;
      float arrowCenterY = boxY + headH / 2f;
      r.pushRotationAround(90f + 90f * ev, arrowCenterX, arrowCenterY + 0.5f);
      r.textRight(FontRegistry.MON, arrowRight, arrowBaselineY, arrowSize, arrowIcon, valCol);
      r.popRotation();

      if (ev > 0.001f) {
         float listAlpha = alpha * ev;
         float oy = boxY + headH + OPT_TOP_GAP;
         r.rect(boxX, oy - OPT_TOP_GAP / 2f, boxW, 0.5f, ColorUtil.replAlpha(-1, (int) (15 * listAlpha)));
         for (int i = 0; i < options.size(); i++) {
            String mode = options.get(i);
            float ox = boxX + 2;
            float ow = boxW - 4;
            float oh = OPT_H;

            String hk = System.identityHashCode(ms) + "_m_" + i;
            Animation2 hov = optHoverAnim(hk);
            hov.update();
            boolean isHov = ms.opened && isHovered(mouseX, mouseY, ox, oy, ow, oh);
            hov.run(isHov ? 1.0 : 0.0, 0.18, Easings.CUBIC_OUT);
            float hv = hov.get();

            int optCol = ColorUtil.overCol(
                  ColorUtil.replAlpha(-1, (int) (125 * listAlpha)),
                  ColorUtil.replAlpha(-1, (int) (255 * listAlpha)), hv);
            r.pushClipRect(boxX, boxY, boxW, boxH);
            r.text(FontRegistry.MONTSERRAT, ox + BOX_HPAD - 1, oy + oh / 2f + 3f, OPT_FONT, mode, optCol);
            r.popClipRect();
            oy += OPT_H + OPT_GAP;
         }
      }

      float extra = OPT_TOP_GAP + optionsH;
      return ROW_H + extra * ev;
   }

   private static float multiExpandedBoxW(Renderer2D r, MultiBooleanSetting mbs, String value) {
      float max = collapsedBoxW(r, value);
      for (BooleanSetting bs : mbs.settings) {
         float ow = r.measureText(FontRegistry.MONTSERRAT, bs.name, OPT_FONT).width + BOX_HPAD * 2;
         if (ow > max) max = ow;
      }
      return max;
   }

   public static float renderMulti(Renderer2D r, MultiBooleanSetting mbs, float x, float y, float w,
                                    int mouseX, int mouseY, float alpha) {
      Animation2 expand = expandAnim(mbs);
      expand.update();
      expand.run(mbs.opened ? 1.0 : 0.0, 0.25, Easings.CUBIC_OUT);
      float ev = expand.get();

      int textCol = ColorUtil.replAlpha(-1, (int) (255 * alpha));
      int subCol = ColorUtil.replAlpha(-1, (int) (155 * alpha));
      int accent = Renderer2D.ColorUtil.getClientColor();

      int enabledCount = 0;
      for (BooleanSetting bs : mbs.settings) if (bs.get()) enabledCount++;
      String value = enabledCount + "/" + mbs.settings.size();

      float wCollapsed = collapsedBoxW(r, value) + 45f;
      float wExpanded = Math.max(multiExpandedBoxW(r, mbs, value), wCollapsed);
      float boxW = wCollapsed + (wExpanded - wCollapsed) * ev;
      float headH = ROW_H - BOX_VPAD * 2;
      float optionsH = mbs.settings.size() * (OPT_H + OPT_GAP);
      float boxH = headH + (OPT_TOP_GAP + optionsH) * ev;
      float boxX = x + w - boxW - PAD;
      float boxY = y + BOX_VPAD;

      float nameAreaX = x + PAD;
      float nameAreaW = Math.max(10f, boxX - nameAreaX - 4f);
      CompactGuiRenderSetting.renderScrollingText(r, FontRegistry.MONTSERRAT,
            "client_multi_" + System.identityHashCode(mbs), mbs.name,
            nameAreaX, y + ROW_H / 2f + 3.25f, NAME_FONT, textCol,
            nameAreaX, nameAreaW, NAME_FONT + 4);

      int boxBg = ColorUtil.overCol(
            ColorUtil.replAlpha(-1, (int) (7 * alpha)),
            ColorUtil.replAlpha(-1, (int) (12 * alpha)), ev);
      r.rect(boxX, boxY, boxW, boxH, 3.5f, boxBg);
      r.rectOutline(boxX, boxY, boxW, boxH, 3.5f, ColorUtil.replAlpha(-1, (int) (12 * alpha)), 0.6f);

      int valCol = ColorUtil.overCol(subCol, ColorUtil.replAlpha(accent, (int) (255 * alpha)), ev);
      String arrowIcon = "l";
      float arrowSize = 15f;
      float arrowRight = boxX + boxW - 4f;
      float arrowBaselineY = boxY + headH / 2f + 3.25f;
      float arrowW = r.measureText(FontRegistry.MON, arrowIcon, arrowSize).width;
      float arrowCenterX = arrowRight - arrowW / 2f;
      float arrowCenterY = boxY + headH / 2f;

      StringBuilder enabledSb = new StringBuilder();
      for (BooleanSetting bs : mbs.settings) {
         if (bs.get()) {
            if (enabledSb.length() > 0) enabledSb.append(", ");
            enabledSb.append(bs.name);
         }
      }
      String displayValue = enabledSb.length() == 0 ? value : enabledSb.toString();
      float valAreaX = boxX + BOX_HPAD;
      float valAreaW = Math.max(6f, (arrowRight - arrowW - 3f) - valAreaX);
      CompactGuiRenderSetting.renderScrollingText(r, FontRegistry.MONTSERRAT,
            "client_multi_val_" + System.identityHashCode(mbs), displayValue,
            valAreaX, boxY + headH / 2f + 3f, VALUE_FONT, valCol,
            valAreaX, valAreaW, VALUE_FONT + 4);

      r.pushRotationAround(90f + 90f * ev, arrowCenterX, arrowCenterY + 0.5f);
      r.textRight(FontRegistry.MON, arrowRight, arrowBaselineY, arrowSize, arrowIcon, valCol);
      r.popRotation();

      if (ev > 0.001f) {
         float listAlpha = alpha * ev;
         float oy = boxY + headH + OPT_TOP_GAP;
         r.rect(boxX, oy - OPT_TOP_GAP / 2f, boxW, 0.5f, ColorUtil.replAlpha(-1, (int) (15 * listAlpha)));
         for (int i = 0; i < mbs.settings.size(); i++) {
            BooleanSetting bs = mbs.settings.get(i);
            boolean on = bs.get();
            float ox = boxX + 2;
            float ow = boxW - 4;
            float oh = OPT_H;

            String hk = System.identityHashCode(mbs) + "_b_" + i;
            Animation2 hov = optHoverAnim(hk);
            hov.update();
            boolean isHov = mbs.opened && isHovered(mouseX, mouseY, ox, oy, ow, oh);
            hov.run(isHov ? 1.0 : 0.0, 0.18, Easings.CUBIC_OUT);
            float hv = hov.get();

            Animation2 enAnim = optHoverAnim(hk + "_en");
            enAnim.update();
            enAnim.run(on ? 1.0 : 0.0, 0.2, Easings.CUBIC_OUT);
            float env = enAnim.get();

            int offCol = ColorUtil.overCol(
                  ColorUtil.replAlpha(-1, (int) (125 * listAlpha)),
                  ColorUtil.replAlpha(-1, (int) (255 * listAlpha)), hv);
            int optCol = ColorUtil.overCol(offCol, ColorUtil.replAlpha(accent, (int) (255 * listAlpha)), env);

            r.pushClipRect(boxX, boxY, boxW, boxH);
            r.text(FontRegistry.MONTSERRAT, ox + BOX_HPAD - 1, oy + oh / 2f + 3f, OPT_FONT, bs.name, optCol);
            r.popClipRect();

            oy += OPT_H + OPT_GAP;
         }
      }

      float extra = OPT_TOP_GAP + optionsH;
      return ROW_H + extra * ev;
   }

   private static String formatSlider(SliderSetting ss) {
      if (ss.percent) return String.format("%.0f%%", ss.current);
      if (ss.increment >= 1.0) return String.valueOf((int) Math.round(ss.current));
      int d = Math.max(1, (int) Math.ceil(-Math.log10(ss.increment)));
      return String.format("%." + d + "f", ss.current);
   }

   private static float renderSlider(Renderer2D r, SliderSetting ss, float x, float y, float w,
                                     int mouseX, int mouseY, float alpha) {
      int mainColor = ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), (int) (255 * alpha));

      if ((ss == vesence.module.impl.misc.ClickGui.cornerHud && vesence.module.impl.misc.ClickGui.hudCornerOverflow)
       || (ss == vesence.module.impl.misc.ClickGui.cornerGui && vesence.module.impl.misc.ClickGui.guiCornerOverflow)) {
         mainColor = ColorUtil.getColor(255, 60, 60, (int) (255 * alpha));
      }

      r.text(FontRegistry.MONTSERRAT, x + PAD, y + ROW_H / 2f + 3.25f, LF, ss.name,
            ColorUtil.replAlpha(-1, alpha));

      float sw = SLIDER_W;
      float sx = x + w - sw - PAD;
      float sy = y + ROW_H / 2f - SLIDER_H / 2f;
      float sh = SLIDER_H;

      Animation2 sa = GuiScreen.getSliderAnimation(ss);
      float tp = (float) ((ss.current - ss.minimum) / (ss.maximum - ss.minimum));
      sa.update();
      sa.run(tp, 0.2, Easings.CUBIC_OUT);
      float prog = (float) sa.getValue();
      float pw = sw * prog;

      r.rect(sx, sy, sw, sh, 1.25f, ColorUtil.replAlpha(-1, (int) (15 * alpha)));
      r.gradient(sx + 0.5f, sy + 0.3f, Math.max(0, pw - 1), sh - 0.6f, 1.25f,
            ColorUtil.replAlpha(mainColor, alpha),
            ColorUtil.replAlpha(mainColor, (int) (85 * alpha)),
            ColorUtil.replAlpha(mainColor, (int) (85 * alpha)),
            ColorUtil.replAlpha(mainColor, alpha));
      r.rect(sx + Math.max(0, pw - 4), sy - 0.75f, 6, 6, 4,
            ColorUtil.replAlpha(-1, (int) (255 * alpha)));

      String vt;
      if (ss.editing && GuiScreen.editingSliderSetting == ss) {
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
      float valueY = y + ROW_H / 2f + 3.25f;
      int[] valueGrad = new int[] {ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), alpha),
            ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), (int) (200 * alpha))};
      if (ss.editing && GuiScreen.editingSliderSetting == ss) {

         r.text(FontRegistry.MONTSERRAT, sx - SLIDER_VALUE_GAP - vtw, valueY, LF, vt, valueGrad);
      } else {
         vesence.utils.render.text.AnimatedText.draw(r, FontRegistry.MONTSERRAT,
               "cgds_" + System.identityHashCode(ss), vt,
               sx - SLIDER_VALUE_GAP, valueY, LF, valueGrad,
               vesence.utils.render.text.AnimatedText.ALIGN_RIGHT);
      }

      return ROW_H;
   }

   public static boolean handleClick(Renderer2D r, Setting<?> setting, float x, float y, float w,
                                     int mouseX, int mouseY, int button) {
      if ((boolean) setting.hidden.get()) return false;

      if (setting instanceof ModeSetting ms) {
         String value = ms.get();
         java.util.List<String> options = modeOptions(ms);
         float wCollapsed = collapsedBoxW(r, value);
         float wExpanded = expandedBoxW(r, value, options);
         boolean opened = ms.opened;
         float boxW = opened ? wExpanded : wCollapsed;
         float headH = ROW_H - BOX_VPAD * 2;
         float boxX = x + w - boxW - PAD;
         float boxY = y + BOX_VPAD;

         if (isHovered(mouseX, mouseY, boxX, boxY, boxW, headH)) {
            ms.opened = !ms.opened;
            return true;
         }
         if (opened) {
            float oy = boxY + headH + OPT_TOP_GAP;
            for (int i = 0; i < options.size(); i++) {
               if (isHovered(mouseX, mouseY, boxX + 2, oy, boxW - 4, OPT_H)) {
                  ms.currentMode = options.get(i);
                  ms.index = ms.modes.indexOf(ms.currentMode);
                  ms.opened = false;
                  saveConfig();
                  return true;
               }
               oy += OPT_H + OPT_GAP;
            }

            ms.opened = false;
         }
         return true;
      }

      if (setting instanceof MultiBooleanSetting mbs) {
         int enabledCount = 0;
         for (BooleanSetting bs : mbs.settings) if (bs.get()) enabledCount++;
         String value = enabledCount + "/" + mbs.settings.size();
         float wCollapsed = collapsedBoxW(r, value) + 45f;
         float wExpanded = Math.max(multiExpandedBoxW(r, mbs, value), wCollapsed);
         boolean opened = mbs.opened;
         float boxW = opened ? wExpanded : wCollapsed;
         float headH = ROW_H - BOX_VPAD * 2;
         float boxX = x + w - boxW - PAD;
         float boxY = y + BOX_VPAD;

         if (isHovered(mouseX, mouseY, boxX, boxY, boxW, headH)) {
            mbs.opened = !mbs.opened;
            return true;
         }
         if (opened) {
            float oy = boxY + headH + OPT_TOP_GAP;
            for (int i = 0; i < mbs.settings.size(); i++) {
               if (isHovered(mouseX, mouseY, boxX + 2, oy, boxW - 4, OPT_H)) {
                  BooleanSetting bs = mbs.settings.get(i);
                  bs.set(!bs.get());
                  saveConfig();
                  return true;
               }
               oy += OPT_H + OPT_GAP;
            }
            mbs.opened = false;
         }
         return true;
      }

      if (setting instanceof SliderSetting ss) {
         String value = formatSlider(ss);
         float vw = r.measureText(FontRegistry.MONTSERRAT, value, LF).width;
         float trackX = x + w - SLIDER_W - PAD;
         float trackW = SLIDER_W;
         float trackY = y + ROW_H / 2f - SLIDER_H / 2f;
         float valX = trackX - SLIDER_VALUE_GAP - vw;

         if (isHovered(mouseX, mouseY, valX - 3, y, vw + 8, ROW_H)) {
            if (button == 1) {
               ss.current = ss.getDefault();
               saveConfig();
               return true;
            }
            CompactGuiMouseClickedSetting.beginSliderEdit(ss);
            return true;
         }

         if (isHovered(mouseX, mouseY, trackX, trackY - 3, trackW, SLIDER_H + 6)) {
            if (ss.editing) CompactGuiMouseClickedSetting.commitSliderEdit(ss);
            CompactGuiScreen.freezeScale();
            GuiScreen.activeSliderSetting = ss;
            GuiScreen.sliderX = trackX;
            GuiScreen.sliderY = trackY;
            GuiScreen.sliderWidth = trackW;
            float progress = (mouseX - trackX) / trackW;
            progress = Math.max(0f, Math.min(1f, progress));
            double raw = ss.minimum + (ss.maximum - ss.minimum) * progress;
            ss.current = Math.round((raw - ss.minimum) / ss.increment) * ss.increment + ss.minimum;
            ss.current = Math.max(ss.minimum, Math.min(ss.maximum, ss.current));
            saveConfig();
            return true;
         }
         return true;
      }

      return vesence.ui.clickgui.compact.setting.CompactGuiMouseClickedSetting
            .handleSettingClick(r, setting, x, y, w, mouseX, mouseY, button);
   }

   public static float dropdownHeight(Renderer2D renderer, Setting<?> setting) {
      if (setting instanceof ModeSetting ms) {
         Animation2 a = expandAnim(ms);
         int optCount = modeOptions(ms).size();
         float extra = OPT_TOP_GAP + optCount * (OPT_H + OPT_GAP);
         return ROW_H + extra * a.get() + 3;
      }
      if (setting instanceof MultiBooleanSetting mbs) {
         Animation2 a = expandAnim(mbs);
         float extra = OPT_TOP_GAP + mbs.settings.size() * (OPT_H + OPT_GAP);
         return ROW_H + extra * a.get() + 3;
      }
      return 0;
   }

   public static float renderDropdown(Renderer2D renderer, Setting<?> setting,
                                      float x, float y, float width,
                                      int mouseX, int mouseY, float alpha) {
      if (setting instanceof ModeSetting ms) {
         return renderMode(renderer, ms, x, y, width, mouseX, mouseY, alpha);
      }
      if (setting instanceof MultiBooleanSetting mbs) {
         return renderMulti(renderer, mbs, x, y, width, mouseX, mouseY, alpha);
      }
      return 0;
   }

   public static boolean handleDropdownClick(Renderer2D renderer, Setting<?> setting,
                                             float x, float y, float width,
                                             int mouseX, int mouseY, int button) {
      if (setting instanceof ModeSetting || setting instanceof MultiBooleanSetting) {
         return handleClick(renderer, setting, x, y, width, mouseX, mouseY, button);
      }
      return false;
   }

   private static void saveConfig() {
      if (vesence.Vesence.get != null && vesence.Vesence.get.configManager != null) {
         vesence.Vesence.get.configManager.autoSave();
      }
   }

   public static void scrollSlider(SliderSetting ss, double dir) {
      double step = ss.increment * (dir > 0 ? 1 : -1);
      double v = ss.current + step;
      v = Math.round((v - ss.minimum) / ss.increment) * ss.increment + ss.minimum;
      v = Math.max(ss.minimum, Math.min(ss.maximum, v));
      if (v != ss.current) {
         ss.current = v;
         vesence.utils.render.utils.SoundUtil.playUi("slider", 0.12F, 45L);
         saveConfig();
      }
   }

   public static boolean handleScroll(Renderer2D r, Setting<?> setting, float x, float y, float w,
                                      int mouseX, int mouseY, double amount) {
      if ((boolean) setting.hidden.get()) return false;
      if (!(setting instanceof SliderSetting ss)) return false;
      if (isHovered(mouseX, mouseY, x, y, w, ROW_H)) {
         scrollSlider(ss, amount);
         return true;
      }
      return false;
   }
}
