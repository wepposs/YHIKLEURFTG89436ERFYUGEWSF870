package vesence.ui.clickgui.component.setting;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.module.Theme;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.*;
import vesence.renderengine.render.Renderer2D;
import vesence.ui.clickgui.GuiScreen;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.animation.util.Animation;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;
import vesence.utils.render.text.FontRegistry;
import vesence.utils.render.utils.KeyUtil;
import net.minecraft.util.Identifier;

import java.awt.*;
import java.util.HashMap;

@Environment(EnvType.CLIENT)
public class GuiRenderSetting {

   private static final Identifier HUE_TEXTURE = Identifier.of("vesence", "textures/gui/hue.png");

   public static final float HUE_PREVIEW_H = 12.0F;
   public static final float HUE_PANEL_TOP = 4.0F;
   public static final float HUE_PANEL_BOTTOM = 4.0F;
   public static final float HUE_BAR_H = 9.0F;
   public static final float HUE_BAR_GAP = 7.0F;
   public static final int HUE_BAR_COUNT = 4;

   public static float hueExpandedHeight() {
      return HUE_PANEL_TOP + HUE_BAR_COUNT * HUE_BAR_H + (HUE_BAR_COUNT - 1) * HUE_BAR_GAP + HUE_PANEL_BOTTOM;
   }

   public static float hueBarY(float renderY, int index) {
      float top = renderY + HUE_PREVIEW_H + HUE_PANEL_TOP;
      return top + index * (HUE_BAR_H + HUE_BAR_GAP);
   }

   public static Animation multiAnim = new Animation();
   public static HashMap<String, Float> animation = new HashMap<>();
   public static HashMap<String, Float> animation2 = new HashMap<>();
   public static HashMap<String, Float> multiBooleanAnimation = new HashMap<>();
   public static HashMap<String, Animation2> modeAnimations = new HashMap<>();
   public static HashMap<String, Animation2> modeHoverAnimations = new HashMap<>();
   public static HashMap<String, Animation2> multiBoolAnimations = new HashMap<>();

   private static final HashMap<String, Float> scrollOffsets = new HashMap<>();
   private static final HashMap<String, Long> scrollUpdateTimes = new HashMap<>();
   private static final HashMap<String, Long> scrollVisibleTimes = new HashMap<>();
   private static final float SCROLL_SPEED = 22.0f;
   private static final float SCROLL_GAP = 12.0f;
   private static final long SCROLL_DELAY = 1200L;

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

   private static void renderScrollingText(Renderer2D renderer2D, String key, String text, float textX, float textY, float fontSize, int color, float areaX, float areaWidth, float areaHeight) {
      float textWidth = renderer2D.measureText(FontRegistry.SF_MEDIUM, text, fontSize).width;
      float maxScroll = Math.max(0f, textWidth - areaWidth);
      long now = System.currentTimeMillis();
      renderer2D.pushClipRect((int) areaX, (int)(textY - fontSize + 2), (int) areaWidth, (int)(areaHeight));
      if (maxScroll > 0f) {
         float offset = getScrollOffset(key, textWidth, areaWidth, now);
         float cycleWidth = textWidth + SCROLL_GAP;
         float firstX = textX - offset;
         float secondX = firstX + cycleWidth;
         renderer2D.text(FontRegistry.SF_MEDIUM, firstX, textY, fontSize, text, color);
         renderer2D.text(FontRegistry.SF_MEDIUM, secondX, textY, fontSize, text, color);
      } else {
         scrollOffsets.remove(key);
         scrollUpdateTimes.remove(key);
         scrollVisibleTimes.remove(key);
         renderer2D.text(FontRegistry.SF_MEDIUM, textX, textY, fontSize, text, color);
      }
      renderer2D.popClipRect();
   }

   private static final float LABEL_FONT = 12;
   private static final float CHIP_FONT = 12;
   private static final float VALUE_FONT = 10.0F;

   private static String formatOneValue(double value, double increment, boolean percent) {
      if (percent) {
         return String.format("%.0f%%", value);
      } else if (increment >= 1.0) {
         return String.valueOf((int) Math.round(value));
      } else {
         int decimals = Math.max(1, (int) Math.ceil(-Math.log10(increment)));
         return String.format("%." + decimals + "f", value);
      }
   }

   private static String formatRangeValue(RangeSliderSetting s) {
      return formatOneValue(s.valueFrom, s.increment, s.percent) + " - " + formatOneValue(s.valueTo, s.increment, s.percent);
   }

   public static float getSettingHeight(Renderer2D renderer2D, Setting setting) {
      return getSettingHeight(renderer2D, setting, 97.0F);
   }

   public static float getSettingHeight(Renderer2D renderer2D, Setting setting, float layoutWidth) {
      float vis = setting.getVisibility();
      if (vis <= 0.001f) return 0;
      float baseHeight;
      if (setting instanceof NoneSetting) {
         baseHeight = ((NoneSetting)setting).get();
      } else if (setting instanceof TitleSetting) {
         baseHeight = 14;
      } else if (setting instanceof BooleanSetting) {
         baseHeight = 12;
      } else if (setting instanceof SliderSetting) {
         baseHeight = 18;
      } else if (setting instanceof RangeSliderSetting) {
         baseHeight = 18;
      } else if (setting instanceof ThemeSetting themeSetting) {
         float boxSize = 10;
         float spacing = 4;
         float currentX = 0;
         float currentY = 0;
         float width = layoutWidth - 6;
         for (Theme theme : themeSetting.themes) {
            if (currentX > 0 && currentX + boxSize > width) {
               currentX = 0;
               currentY += boxSize + spacing;
            }
            currentX += boxSize + spacing;
         }
         baseHeight = 10 + currentY + boxSize + 7;
      } else if (setting instanceof ModeSetting modeSetting) {
         float currentX = 0;
         float currentY = 0;
         float spacing = 3;
         float chipHeight = 11;
         float padding = 3.0F;
         float width = layoutWidth - 6;
         for (String mode : modeSetting.modes) {
            float nameWidth = renderer2D.measureText(FontRegistry.SF_MEDIUM, mode, CHIP_FONT).width;
            float chipWidth = nameWidth + padding * 2.0F;
            if (currentX > 0 && currentX + chipWidth > width) {
               currentX = 0;
               currentY += chipHeight + spacing;
            }
            currentX += chipWidth + spacing;
         }
         baseHeight = 10 + currentY + chipHeight + 7;
      } else if (setting instanceof BindSettings) {
         baseHeight = 12;
      } else if (setting instanceof StringSetting) {
         baseHeight = 28;
      } else if (setting instanceof HueSetting hueSetting) {
         baseHeight = HUE_PREVIEW_H;
         float openT = (float) hueSetting.openAnimation.get();
         baseHeight += hueExpandedHeight() * openT;
      } else if (setting instanceof MultiBooleanSetting) {
         float currentX = 0;
         float currentY = 0;
         float spacing = 3;
         float boolHeight = 11;
         float padding = 3.0F;
         float width = layoutWidth - 6;
         for (BooleanSetting boolSetting : ((MultiBooleanSetting)setting).settings) {
            float nameWidth = renderer2D.measureText(FontRegistry.SF_MEDIUM, boolSetting.name, CHIP_FONT).width;
            float boolWidth = nameWidth + padding * 2.0F;
            if (currentX > 0 && currentX + boolWidth > width) {
               currentX = 0;
               currentY += boolHeight + spacing;
            }
            currentX += boolWidth + spacing;
         }
         baseHeight = 10 + currentY + boolHeight + 7;
      } else {
         baseHeight = 0;
      }
      return baseHeight * vis;
   }

   public static float renderSetting(
      Renderer2D renderer2D,
      Setting setting,
      float x,
      float y,
      float width,
      int mouseX,
      int mouseY,
      int outlineColor,
      int mainColor,
      int mainColor6,
      int mainColor40,
      int textColor,
      float mainAlpha
   ) {
      float vis = setting.getVisibility();
      if (vis <= 0.001f) return 0;
      float visAlpha = vis;
      float visYOffset = (1.0f - vis) * 8.0f;
      renderer2D.pushAlpha(mainAlpha * visAlpha);
      float height = 0.0F;
      float renderY = y + visYOffset;
      if (setting instanceof NoneSetting) {
         height = ((NoneSetting)setting).get();
      } else if (setting instanceof TitleSetting titleSetting) {
         float textX = x + width / 2f;
         float textY = renderY + 8;
         renderer2D.textCenter(FontRegistry.SF_MEDIUM, textX, textY, 17, titleSetting.name,
            ColorUtil.replAlpha(-1, (int)(mainAlpha * 225)), -0.3f);
         height = 14;
      } else if (setting instanceof BooleanSetting boolSetting) {
         boolean value = boolSetting.get();
         float toggleWidth = 9;
         float toggleHeight = 9;
         float toggleX = x + width - toggleWidth - 2.0F;
         float toggleY = renderY - 1;
         float handleSize = 4;
         float handleTravel = toggleWidth - handleSize - 4;
         boolSetting.anim.update();
         boolSetting.anim.run(value ? 1.0 : 0.0, 0.4F, Easings.BACK_OUT, true);
         renderer2D.rect(toggleX, toggleY, toggleWidth, toggleHeight, 1, 5, ColorUtil.replAlpha(-1, (int) (22.95F * mainAlpha)));
         float handleX = toggleX + 2.5F * handleTravel;
         float handleY = toggleY + (toggleHeight - handleSize) / 2.0F;
         renderer2D.rect(handleX, handleY, handleSize, handleSize, 2, 1, ColorUtil.overCol(
                 ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), (int) (0 * mainAlpha)), ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), (int) (255 * mainAlpha)), boolSetting.anim.get()
         ));
         float boolTextAreaWidth = toggleX - x - 5.0F;
         renderScrollingText(renderer2D, "bool_" + setting.name, setting.name, x + 3, renderY + 5, LABEL_FONT, ColorUtil.replAlpha(-1, mainAlpha), x + 3, boolTextAreaWidth, LABEL_FONT + 4);
         height = 12;
      } else if (setting instanceof SliderSetting sliderSetting) {
         float sliderHeight = 3.0F;
         float sliderY = renderY + 8.0F;
         float sliderWidth = width - 4;
         Animation2 sliderAnim = GuiScreen.getSliderAnimation(sliderSetting);
         float targetProgress = (float)((sliderSetting.current - sliderSetting.minimum) / (sliderSetting.maximum - sliderSetting.minimum));
         sliderAnim.update();
         sliderAnim.run(targetProgress, 0.24F, Easings.BACK_OUT, true);
         float progress = (float)sliderAnim.getValue();
         float progressWidth = sliderWidth * progress;
         renderer2D.rect(x + 3, sliderY + 1.5F, sliderWidth, sliderHeight, 0.5F, 3, ColorUtil.replAlpha(-1, (int) (15 * mainAlpha)));
         renderer2D.gradient(x + 3.5F, sliderY + 1.8F, Math.max(0, progressWidth - 1.0F), sliderHeight - 0.6F, 0.5F, ColorUtil.replAlpha(mainColor, mainAlpha),
                 ColorUtil.replAlpha(mainColor, (int) (85 * mainAlpha)),
                 ColorUtil.replAlpha(mainColor, (int) (85 * mainAlpha)),
                 ColorUtil.replAlpha(mainColor, mainAlpha));
         renderer2D.rect(x + 2 + Math.max(0, progressWidth - 1.0F), sliderY, 6, 6, 3, ColorUtil.replAlpha(-1, (int) (255 * mainAlpha)));
         renderer2D.rect(x + 3.5F + Math.max(0, progressWidth - 1.0F), renderY + 9.5F, 3, 3, 1.5F, ColorUtil.replAlpha(0, (int) (147.9 * mainAlpha)));
         String valueText;
         if (sliderSetting.percent) {
            valueText = String.format("%.0f%%", sliderSetting.current);
         } else if (sliderSetting.increment >= 1.0) {
            valueText = String.valueOf((int) Math.round(sliderSetting.current));
         } else {
            int decimals = Math.max(1, (int) Math.ceil(-Math.log10(sliderSetting.increment)));
            valueText = String.format("%." + decimals + "f", sliderSetting.current);
         }
         float valueTextW = renderer2D.measureText(FontRegistry.SF_MEDIUM, valueText, VALUE_FONT).width;
         float sliderTextAreaWidth = width - valueTextW - 10.0F;
         renderScrollingText(renderer2D, "slider_" + setting.name, setting.name, x + 3, renderY + 5, LABEL_FONT, ColorUtil.replAlpha(-1, mainAlpha), x + 3, sliderTextAreaWidth, LABEL_FONT + 4);
         renderer2D.text(
            FontRegistry.SF_MEDIUM,
            x + width - valueTextW - 4.5f,
            renderY + 5,
                 LABEL_FONT,
            valueText,
            ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), mainAlpha)
         );
         height = 18;
      } else if (setting instanceof RangeSliderSetting rangeSetting) {
         float sliderHeight = 3.0F;
         float sliderY = renderY + 8.0F;
         float sliderWidth = width - 4;
         double span = rangeSetting.maximum - rangeSetting.minimum;
         float fromProgress = (float)((rangeSetting.valueFrom - rangeSetting.minimum) / span);
         float toProgress = (float)((rangeSetting.valueTo - rangeSetting.minimum) / span);
         float fromX = x + 3 + sliderWidth * fromProgress;
         float toX = x + 3 + sliderWidth * toProgress;

         renderer2D.rect(x + 3, sliderY + 1.5F, sliderWidth, sliderHeight, 1.5F, ColorUtil.replAlpha(-1, (int) (15 * mainAlpha)));
         renderer2D.gradient(fromX, sliderY + 1.8F, Math.max(0, toX - fromX), sliderHeight - 0.6F, 1.5F,
                 ColorUtil.replAlpha(mainColor, mainAlpha),
                 ColorUtil.replAlpha(mainColor, (int) (85 * mainAlpha)),
                 ColorUtil.replAlpha(mainColor, (int) (85 * mainAlpha)),
                 ColorUtil.replAlpha(mainColor, mainAlpha));
         renderer2D.rect(fromX - 2, sliderY, 6, 6, 3, ColorUtil.replAlpha(-1, (int) (255 * mainAlpha)));
         renderer2D.rect(fromX - 0.5F, sliderY + 1.5F, 3, 3, 1.5F, ColorUtil.replAlpha(0, (int) (147.9 * mainAlpha)));

         renderer2D.rect(toX - 2, sliderY, 6, 6, 3, ColorUtil.replAlpha(-1, (int) (255 * mainAlpha)));
         renderer2D.rect(fromX - 0.5F, sliderY + 1.5F, 3, 3, 1.5F, ColorUtil.replAlpha(0, (int) (147.9 * mainAlpha)));

         String valueText = formatRangeValue(rangeSetting);
         float valueTextW = renderer2D.measureText(FontRegistry.SF_MEDIUM, valueText, VALUE_FONT).width;
         float sliderTextAreaWidth = width - valueTextW - 10.0F;
         renderScrollingText(renderer2D, "range_" + setting.name, setting.name, x + 3, renderY + 5, LABEL_FONT, ColorUtil.replAlpha(-1, mainAlpha), x + 3, sliderTextAreaWidth, LABEL_FONT + 4);
         renderer2D.text(
            FontRegistry.SF_MEDIUM,
            x + width - valueTextW - 4.5f,
            renderY + 5,
                 LABEL_FONT,
            valueText,
            ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), mainAlpha)
         );
         height = 18;
      } else if (setting instanceof ModeSetting modeSetting) {
         renderer2D.text(FontRegistry.SF_MEDIUM, x + 3, renderY + 5, LABEL_FONT, setting.name, ColorUtil.replAlpha(-1, (int)(255 * mainAlpha)));
         float startY = renderY + 10;
         float currentX = 0;
         float currentY = 0;
         float spacing = 3;
         float chipHeight = 11;
         float padding = 3.0F;
         float maxWidth = width - 6;
         for (String mode : modeSetting.modes) {
            float nameWidth = renderer2D.measureText(FontRegistry.SF_MEDIUM, mode, CHIP_FONT).width;
            float chipWidth = nameWidth + padding * 2.0F;
            if (currentX > 0 && currentX + chipWidth > maxWidth) {
               currentX = 0;
               currentY += chipHeight + spacing;
            }
            float chipX = x + 3 + currentX;
            float chipY = startY + currentY;

            String animKey = "gui_mode_chip_" + setting.name + "_" + mode;
            boolean isSelected = modeSetting.currentMode.equals(mode);
            Animation2 chipAnim = modeAnimations.computeIfAbsent(animKey, k -> {
               Animation2 a = new Animation2();
               a.setValue(isSelected ? 1.0 : 0.0);
               return a;
            });
            chipAnim.update();
            chipAnim.run(isSelected ? 1.0 : 0.0, 0.28, Easings.SINE_OUT, true);
            float anim = (float) chipAnim.getValue();
            renderer2D.rect(chipX, chipY, chipWidth, chipHeight, 3,
                    ColorUtil.overCol(ColorUtil.getColor(255, 15), ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), 65), anim));
            float bgAlpha = mainAlpha * (0.04f + (1.0f - anim) * 0.04f);
            int modeColor = ColorUtil.overCol(ColorUtil.getColor(245, 245, 245, 22.95F), ColorUtil.replAlpha(ColorUtil.multBright(Renderer2D.ColorUtil.getClientColor(), 0.21f), 175), anim);
            renderer2D.text(FontRegistry.SF_MEDIUM, chipX + padding, chipY + 1.0F + 7, CHIP_FONT, mode, modeColor);
            currentX += chipWidth + spacing;
         }
         height = 10 + currentY + chipHeight + 7;
      } else if (setting instanceof BindSettings bindSetting) {
         float bindHeight = 10;
         String displayName = setting.name != null && !setting.name.isEmpty() ? setting.name : "KEY";
         String keyText = bindSetting.active ? "..." : bindSetting.label();
         float keyTextWidth = renderer2D.measureText(FontRegistry.SF_MEDIUM, keyText, CHIP_FONT - 0.5f).width;
         float minButtonWidth = 14.0F;
         // Ограничиваем ширину кнопки, чтобы длинное комбо не вылезало за панель.
         float maxButtonWidth = width * 0.6F;
         float buttonWidth = Math.min(keyTextWidth + 6, maxButtonWidth);
         float bindButtonX = x + width - buttonWidth - 1.0F;
         if (bindButtonX < x + renderer2D.measureText(FontRegistry.SF_MEDIUM, displayName, LABEL_FONT).width + 3.0F) {
            bindButtonX = x + width - buttonWidth;
         }
         float bindTextAreaWidth = bindButtonX - x - 5.0F;
         renderScrollingText(renderer2D, "bind_" + setting.name, displayName, x + 3, renderY + 6, LABEL_FONT, ColorUtil.replAlpha(-1, 255), x + 3, bindTextAreaWidth, LABEL_FONT + 4);
         renderer2D.rect(bindButtonX, renderY - 2, buttonWidth, bindHeight, 2.5F, ColorUtil.replAlpha(-1, 25));
         renderer2D.rectOutline(bindButtonX, renderY - 2, buttonWidth, bindHeight, 3, ColorUtil.replAlpha(-1, 15), 1);
         renderer2D.pushClipRect((int)(bindButtonX + 2), (int)(renderY - 3), (int)(buttonWidth - 4), (int)(bindHeight + 2));
         renderer2D.textRight(
            FontRegistry.SF_MEDIUM,
            bindButtonX + buttonWidth - 3,
            renderY + 1.0F + 4.5f,
            CHIP_FONT - 0.5f,
            keyText,
            ColorUtil.replAlpha(-1, 255)
         );
         renderer2D.popClipRect();
         height = 12;
      } else if (setting instanceof StringSetting stringSetting) {
         float textFieldHeight = 13;
         float textFieldWidth = width - 6;
         float textFieldX = x + 3;
         float textX = textFieldX + 3.0F;
         float textY = renderY + 20;
         boolean isActive = GuiScreen.activeStringSetting == stringSetting && stringSetting.active;

         stringSetting.activeAnim.update();
         stringSetting.activeAnim.run(isActive ? 1.0 : 0.0, 0.25, Easings.CUBIC_OUT, true);
         float activeT = stringSetting.activeAnim.get();
         int greyText = ColorUtil.getColor(135, 135, 135);
         int textColor2 = ColorUtil.replAlpha(ColorUtil.overCol(greyText, -1, activeT), mainAlpha);

         String inputText = stringSetting.input;
         int len = inputText.length();
         if (len > stringSetting.lastRenderedLength) {
            stringSetting.charAnim.run(0.0, 1.0E-9);
            stringSetting.charAnim.run(1.0, 0.22, Easings.CUBIC_OUT);
         }
         stringSetting.lastRenderedLength = len;
         stringSetting.charAnim.update();
         float charT = stringSetting.charAnim.get();

         renderer2D.text(FontRegistry.SF_MEDIUM, x + 3, renderY + 5, LABEL_FONT, setting.name, ColorUtil.replAlpha(-1, mainAlpha));
         renderer2D.rect(textFieldX, renderY + 11.5f, textFieldWidth, textFieldHeight, 3, ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), (int) (25 * mainAlpha)));
         boolean isEmpty = inputText.isEmpty();
         float cursorX = textX;
         if (!isEmpty) {
            float currentX = textX;
            float maxX = textFieldX + textFieldWidth - 3.0F;
            for (int i = 0; i < inputText.length(); i++) {
               char c = inputText.charAt(i);
               String charStr = String.valueOf(c);
               float charWidth = renderer2D.measureText(FontRegistry.SF_MEDIUM, charStr, CHIP_FONT).width;
               if (currentX + charWidth > maxX) {
                  cursorX = currentX;
                  break;
               }

               boolean lastChar = i == inputText.length() - 1;
               int charColor = lastChar
                     ? ColorUtil.replAlpha(textColor2, (int) (ColorUtil.getAlpha(textColor2) * charT))
                     : textColor2;
               renderer2D.text(FontRegistry.SF_MEDIUM, currentX + 1, textY, CHIP_FONT, charStr, charColor);

               currentX += lastChar ? charWidth * charT : charWidth;
               cursorX = currentX;
            }
         }
         if (isActive) {
            long currentTime = System.currentTimeMillis();
            boolean showCursor = currentTime / 500L % 2L == 0L;
            if (showCursor) {
               renderer2D.rect(cursorX + 2, textY - 5, 1.0F, 6.5f, 0.5F, ColorUtil.replAlpha(-1, mainAlpha));
            }
         }
         height = 27.0F;
      } else if (setting instanceof HueSetting hueSetting) {
         hueSetting.openAnimation.update();
         hueSetting.openAnimation.run(hueSetting.opened ? 1.0 : 0.0, 0.3, Easings.CUBIC_OUT);
         float openT = (float) hueSetting.openAnimation.get();
         float previewH = HUE_PREVIEW_H;
         float colorBoxSize = 9.0F;
         float colorBoxX = x + width - colorBoxSize - 3.0F;
         renderer2D.text(FontRegistry.SF_MEDIUM, x + 3, renderY + 5, LABEL_FONT, setting.name, ColorUtil.replAlpha(-1, mainAlpha));
         Color hueColor = hueSetting.getColor();
         renderer2D.rect(colorBoxX, renderY - 1, colorBoxSize, colorBoxSize, 3,
            Renderer2D.ColorUtil.replAlpha(hueColor.getRGB(), (int)(255.0F * mainAlpha)));
         height = previewH;
         if (openT > 0.01f) {
            renderer2D.pushAlpha(openT);

            float barX = x + 3.0F;
            float barW = width - 6.0F;
            float rounding = HUE_BAR_H / 2.0F;
            float currentHue = hueSetting.getHue();
            int whiteBg = ColorUtil.getColor(255, 255, 255, (int)(12 * mainAlpha));
            int fullColor = Color.getHSBColor(currentHue, hueSetting.saturation, hueSetting.brightness).getRGB();

            float bar0Y = hueBarY(renderY, 0);
            renderer2D.rect(barX, bar0Y, barW, HUE_BAR_H, rounding, whiteBg);
            renderer2D.drawImage(HUE_TEXTURE, barX, bar0Y, barW, HUE_BAR_H,
               ColorUtil.replAlpha(-1, (int)(255 * mainAlpha)), false, rounding);
            drawBarHandle(renderer2D, barX, bar0Y, barW, HUE_BAR_H, currentHue, mainAlpha);

            float bar1Y = hueBarY(renderY, 1);
            renderer2D.rect(barX, bar1Y, barW, HUE_BAR_H, rounding, whiteBg);
            int satLeft = Color.getHSBColor(currentHue, 0.0f, hueSetting.brightness).getRGB();
            int satRight = Color.getHSBColor(currentHue, 1.0f, hueSetting.brightness).getRGB();
            renderer2D.gradient(barX, bar1Y, barW, HUE_BAR_H, rounding,
               ColorUtil.replAlpha(satLeft, (int)(255 * mainAlpha)),
               ColorUtil.replAlpha(satRight, (int)(255 * mainAlpha)),
               ColorUtil.replAlpha(satRight, (int)(255 * mainAlpha)),
               ColorUtil.replAlpha(satLeft, (int)(255 * mainAlpha)));
            drawBarHandle(renderer2D, barX, bar1Y, barW, HUE_BAR_H, hueSetting.saturation, mainAlpha);

            float bar2Y = hueBarY(renderY, 2);
            renderer2D.rect(barX, bar2Y, barW, HUE_BAR_H, rounding, whiteBg);
            int brightLeft = Color.getHSBColor(currentHue, hueSetting.saturation, 0.0f).getRGB();
            int brightRight = Color.getHSBColor(currentHue, hueSetting.saturation, 1.0f).getRGB();
            renderer2D.gradient(barX, bar2Y, barW, HUE_BAR_H, rounding,
               ColorUtil.replAlpha(brightLeft, (int)(255 * mainAlpha)),
               ColorUtil.replAlpha(brightRight, (int)(255 * mainAlpha)),
               ColorUtil.replAlpha(brightRight, (int)(255 * mainAlpha)),
               ColorUtil.replAlpha(brightLeft, (int)(255 * mainAlpha)));
            drawBarHandle(renderer2D, barX, bar2Y, barW, HUE_BAR_H, hueSetting.brightness, mainAlpha);

            float bar3Y = hueBarY(renderY, 3);
            renderer2D.rect(barX, bar3Y, barW, HUE_BAR_H, rounding, whiteBg);
            renderer2D.gradient(barX, bar3Y, barW, HUE_BAR_H, rounding,
               ColorUtil.replAlpha(fullColor, 0),
               ColorUtil.replAlpha(fullColor, (int)(255 * mainAlpha)),
               ColorUtil.replAlpha(fullColor, (int)(255 * mainAlpha)),
               ColorUtil.replAlpha(fullColor, 0));
            drawBarHandle(renderer2D, barX, bar3Y, barW, HUE_BAR_H, hueSetting.alpha, mainAlpha);

            renderer2D.popAlpha();
            height += hueExpandedHeight() * openT;
         }
      } else if (setting instanceof MultiBooleanSetting multiBooleanSetting) {
         renderer2D.text(FontRegistry.SF_MEDIUM, x + 3, renderY + 5, LABEL_FONT, setting.name, ColorUtil.replAlpha(-1, 185));
         float startY = renderY + 10;
         float currentX = 0;
         float currentY = 0;
         float spacing = 3;
         float boolHeight = 11;
         float padding = 3.0F;
         float maxWidth = width - 6;
         for (BooleanSetting boolSetting : multiBooleanSetting.settings) {
            float nameWidth = renderer2D.measureText(FontRegistry.SF_MEDIUM, boolSetting.name, CHIP_FONT).width;
            float boolWidth = nameWidth + padding * 2.0F;
            if (currentX > 0 && currentX + boolWidth > maxWidth) {
               currentX = 0;
               currentY += boolHeight + spacing;
            }
            float chipX = x + 3 + currentX;
            float chipY = startY + currentY;

            String animKey = setting.name + "_" + boolSetting.name;
            boolean isOn = boolSetting.get();
            Animation2 chipAnim = multiBoolAnimations.computeIfAbsent(animKey, k -> {
               Animation2 a = new Animation2();
               a.setValue(isOn ? 1.0 : 0.0);
               return a;
            });
            chipAnim.update();
            chipAnim.run(isOn ? 1.0 : 0.0, 0.28, Easings.SINE_OUT, true);
            float anim = (float) chipAnim.getValue();
            float bgAlpha = mainAlpha * (0.04f + (1.0f - anim) * 0.04f);
            renderer2D.gradient(chipX, chipY, boolWidth, boolHeight, 3, ColorUtil.overCol(ColorUtil.getColor(0, 15), ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), 35), anim),
                    ColorUtil.overCol(ColorUtil.replAlpha(-1, 0), ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), 0), anim),
                    ColorUtil.overCol(ColorUtil.replAlpha(-1, 0), ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), 0), anim),
                    ColorUtil.overCol(ColorUtil.getColor(0, 15), ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), 35), anim));
            renderer2D.gradientOutline(chipX, chipY, boolWidth, boolHeight, 3, ColorUtil.replAlpha(-1, (int)(bgAlpha * 65)),
                    ColorUtil.replAlpha(-1, (int)(bgAlpha * 0)),ColorUtil.replAlpha(-1, (int)(bgAlpha * 0)),
                    ColorUtil.replAlpha(-1, (int)(bgAlpha * 65)),1);
            int modeColor = ColorUtil.overCol(ColorUtil.replAlpha(-1, 65), ColorUtil.replAlpha(ColorUtil.multBright(Renderer2D.ColorUtil.getClientColor(), 0.6f), 175), anim);
            renderer2D.text(FontRegistry.SF_MEDIUM, chipX + padding, chipY + 1.0F + 7, CHIP_FONT, boolSetting.name, modeColor);
            currentX += boolWidth + spacing;
         }
         height = 10 + currentY + boolHeight + 7;
      } else if (setting instanceof ThemeSetting themeSetting) {
         renderer2D.text(FontRegistry.SF_MEDIUM, x + 3, renderY + 5, LABEL_FONT, setting.name, ColorUtil.replAlpha(-1, mainAlpha));
         float boxSize = 10;
         float spacing = 4;
         float currentX = 0;
         float currentY = 0;
         float maxWidth = width - 6;
         Theme selectedTheme = themeSetting.get();
         for (Theme theme : themeSetting.themes) {
            if (currentX > 0 && currentX + boxSize > maxWidth) {
               currentX = 0;
               currentY += boxSize + spacing;
            }
            float boxX = x + 3 + currentX;
            float boxY = renderY + 10 + currentY;

            boolean isSelected = theme == selectedTheme;
            Animation2 themeAnim = themeSetting.themeAnimations.get(theme);
            if (themeAnim != null) {
               themeAnim.update();
               themeAnim.run(isSelected ? 1.0 : 0.0, 0.5, Easings.BACK_OUT, true);
            }
            float anim = themeAnim != null ? (float) themeAnim.get() : (isSelected ? 1.0f : 0.0f);
            int themeColor = theme.getMain().getRGB();
            renderer2D.gradient(
                    boxX, boxY, boxSize, boxSize, 3,
                    ColorUtil.replAlpha(themeColor, (int)(255 * mainAlpha)),
                    ColorUtil.replAlpha(themeColor, (int)(255 * mainAlpha)),
                    ColorUtil.replAlpha(themeColor, (int)(205 * mainAlpha)),
                    ColorUtil.replAlpha(themeColor, (int)(205 * mainAlpha)));
            currentX += boxSize + spacing;
         }
         height = 10 + currentY + boxSize + 7;
      }
      renderer2D.popAlpha();
      return (height * vis) + 1.0F;
   }

   private static void drawBarHandle(Renderer2D renderer2D, float barX, float barY, float barW, float barH, float t, float mainAlpha) {
      t = Math.max(0.0F, Math.min(1.0F, t));
      float handleSize = barH + 2.0F;
      float hx = barX + t * barW - handleSize / 2.0F;
      hx = Math.max(barX - 1.0F, Math.min(barX + barW - handleSize + 1.0F, hx));
      float hy = barY + (barH - handleSize) / 2.0F;
      renderer2D.rect(hx, hy, handleSize, handleSize, handleSize / 2.0F, ColorUtil.replAlpha(-1, (int)(255 * mainAlpha)));
      renderer2D.rectOutline(hx, hy, handleSize, handleSize, handleSize / 2.0F, ColorUtil.getColor(0, (int)(60 * mainAlpha)), 1);
   }
}
