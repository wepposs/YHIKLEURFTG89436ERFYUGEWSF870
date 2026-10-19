package vesence.ui.clickgui.component.mouse.setting;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.Vesence;
import vesence.module.Theme;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.*;
import vesence.renderengine.render.Renderer2D;
import vesence.ui.clickgui.GuiScreen;
import vesence.ui.clickgui.component.render.GuiRenderMain;
import vesence.ui.clickgui.component.setting.GuiRenderSetting;
import vesence.utils.render.text.FontRegistry;
import vesence.utils.render.utils.KeyUtil;

@Environment(EnvType.CLIENT)
public class GuiMouseClickedSetting extends GuiScreen {
   public static boolean handleSettingClick(Renderer2D renderer2D, Setting setting, float x, float y, float width, int mouseX, int mouseY, int button) {
      if (GuiScreen.activeModeSetting != null && setting != GuiScreen.activeModeSetting) {
         GuiScreen.activeModeSetting.opened = false;
         GuiScreen.activeModeSetting = null;
      }

      float vis = setting.getVisibility();
      if (vis <= 0.001f) return false;
      float visYOffset = (1.0f - vis) * 8.0f;
      float renderY = y + visYOffset;

      if (setting instanceof BooleanSetting) {
         BooleanSetting boolSetting = (BooleanSetting) setting;
         float toggleWidth = 9.0F;
         float toggleHeight = 9.0F;
         float toggleX = x + width - toggleWidth - 2.0F;
         float toggleY = renderY - 1.0F;
         if (button == 0 && GuiRenderMain.isHovered(mouseX, mouseY, toggleX, toggleY, toggleWidth, toggleHeight)) {
            boolSetting.set(!boolSetting.get());
            if (Vesence.get.configManager != null) Vesence.get.configManager.autoSave();
            return true;
         }
         if (button == 0 && GuiRenderMain.isHovered(mouseX, mouseY, x, renderY, width, 12.0F)) {
            boolSetting.set(!boolSetting.get());
            if (Vesence.get.configManager != null) Vesence.get.configManager.autoSave();
            return true;
         }
      }

      if (setting instanceof BindSettings) {
         BindSettings bindSetting = (BindSettings) setting;
         float bindHeight = 10.0F;
         String keyText = bindSetting.active ? "..." : bindSetting.label();
         float keyTextWidth = renderer2D.measureText(FontRegistry.SF_MEDIUM, keyText, 11.5f).width;
         float maxButtonWidth = width * 0.6F;
         float buttonWidth = Math.min(keyTextWidth + 6.0F, maxButtonWidth);
         float bindButtonX = x + width - buttonWidth - 1.0F;
         if (bindButtonX < x + renderer2D.measureText(FontRegistry.SF_MEDIUM, bindSetting.name != null && !bindSetting.name.isEmpty() ? bindSetting.name : "KEY", 12.0F).width + 3.0F) {
            bindButtonX = x + width - buttonWidth;
         }

         boolean hitButton = GuiRenderMain.isHovered(mouseX, mouseY, bindButtonX, renderY - 2.0F, buttonWidth, bindHeight);
         boolean hitRow = GuiRenderMain.isHovered(mouseX, mouseY, x, renderY - 2.0F, width, 12.0F);
         if (hitButton || hitRow) {
            if (button == 0) {
               if (GuiScreen.activeBindSetting != bindSetting) {
                  if (GuiScreen.activeBindSetting != null) {
                     GuiScreen.activeBindSetting.active = false;
                  }
                  GuiScreen.activeBindSetting = bindSetting;
                  bindSetting.active = true;
               }
               return true;
            }

            // ПКМ/СКМ/боковые как бинд (мышиные кнопки, кроме ЛКМ которая = выбор поля)
            if (GuiScreen.activeBindSetting == bindSetting && button >= 1) {
                int mouseKey = -100 - button;
                bindSetting.key = mouseKey;
                bindSetting.active = false;

               GuiScreen.activeBindSetting = null;
               if (Vesence.get.configManager != null) Vesence.get.configManager.autoSave();
               return true;
            }
         }
      }

      if (setting instanceof HueSetting hueSetting) {
         float colorBoxSize = 9.0F;
         float colorBoxX = x + width - colorBoxSize - 3.0F;
         float colorBoxY = renderY - 1.0F;

         if (button == 0 && GuiRenderMain.isHovered(mouseX, mouseY, colorBoxX, colorBoxY, colorBoxSize, colorBoxSize)) {
            hueSetting.opened = !hueSetting.opened;
            return true;
         }

         if (hueSetting.opened && hueSetting.openAnimation.get() > 0.01) {
            float barX = x + 3.0F;
            float barW = width - 6.0F;
            float hitPad = 3.0F;

            for (int bar = 0; bar < GuiRenderSetting.HUE_BAR_COUNT; bar++) {
               float barY = GuiRenderSetting.hueBarY(renderY, bar);
               if (button == 0 && GuiRenderMain.isHovered(mouseX, mouseY, barX - 2.0F, barY - hitPad,
                     barW + 4.0F, GuiRenderSetting.HUE_BAR_H + hitPad * 2.0F)) {
                  float t = Math.max(0.0F, Math.min(1.0F, (mouseX - barX) / barW));
                  applyHueBar(hueSetting, bar, t);
                  hueSetting.draggingBar = bar;
                  GuiScreen.activeHueSetting = hueSetting;
                  GuiScreen.huePickerX = barX;
                  GuiScreen.huePickerY = barY;
                  GuiScreen.huePickerWidth = barW;
                  return true;
               }
            }
         }
      }

      if (setting instanceof SliderSetting) {
         SliderSetting sliderSetting = (SliderSetting) setting;
         float sliderHeight = 3.0F;
         float sliderY = renderY + 8.0F;
         float sliderWidth = width - 4.0F;
         float sliderActualY = sliderY + 1.5F;
         if (button == 0 && GuiRenderMain.isHovered(mouseX, mouseY, x + 3.0F, sliderActualY, sliderWidth, sliderHeight)) {
            GuiScreen.activeSliderSetting = sliderSetting;
            GuiScreen.sliderX = x + 3.0F;
            GuiScreen.sliderY = sliderActualY;
            GuiScreen.sliderWidth = sliderWidth;
            float progress = (mouseX - (x + 3.0F)) / sliderWidth;
            progress = Math.max(0.0F, Math.min(1.0F, progress));
            double raw = sliderSetting.minimum + (sliderSetting.maximum - sliderSetting.minimum) * progress;
            sliderSetting.current = Math.round((raw - sliderSetting.minimum) / sliderSetting.increment) * sliderSetting.increment + sliderSetting.minimum;
            sliderSetting.current = Math.max(sliderSetting.minimum, Math.min(sliderSetting.maximum, sliderSetting.current));
            return true;
         }
      }

      if (setting instanceof RangeSliderSetting) {
         RangeSliderSetting rangeSetting = (RangeSliderSetting) setting;
         float sliderHeight = 3.0F;
         float sliderY = renderY + 8.0F;
         float sliderWidth = width - 4.0F;
         float sliderActualY = sliderY + 1.5F;
         if (button == 0 && GuiRenderMain.isHovered(mouseX, mouseY, x + 3.0F, sliderActualY, sliderWidth, sliderHeight)) {
            GuiScreen.activeRangeSetting = rangeSetting;
            GuiScreen.sliderX = x + 3.0F;
            GuiScreen.sliderY = sliderActualY;
            GuiScreen.sliderWidth = sliderWidth;
            double span = rangeSetting.maximum - rangeSetting.minimum;
            float fromX = x + 3.0F + (float)((rangeSetting.valueFrom - rangeSetting.minimum) / span) * sliderWidth;
            float toX = x + 3.0F + (float)((rangeSetting.valueTo - rangeSetting.minimum) / span) * sliderWidth;
            rangeSetting.draggingThumb = (Math.abs(mouseX - fromX) <= Math.abs(mouseX - toX)) ? 1 : 2;
            float progress = (mouseX - (x + 3.0F)) / sliderWidth;
            progress = Math.max(0.0F, Math.min(1.0F, progress));
            double raw = rangeSetting.minimum + span * progress;
            if (rangeSetting.draggingThumb == 1) {
               rangeSetting.setFrom(raw);
            } else {
               rangeSetting.setTo(raw);
            }
            return true;
         }
      }

      if (setting instanceof ModeSetting) {
         ModeSetting modeSetting = (ModeSetting) setting;
         float startY = renderY + 10.0F;
         float currentX = 0;
         float currentY = 0;
         float spacing = 3.0F;
         float chipHeight = 11.0F;
         float padding = 3.0F;
         float maxWidth = width - 6.0F;

         for (String mode : modeSetting.modes) {
            float nameWidth = renderer2D.measureText(FontRegistry.SF_MEDIUM, mode, 12.0F).width;
            float chipWidth = nameWidth + padding * 2.0F;
            if (currentX > 0 && currentX + chipWidth > maxWidth) {
               currentX = 0;
               currentY += chipHeight + spacing;
            }
            float chipX = x + 3.0F + currentX;
            float chipY = startY + currentY;

            if (button == 0 && GuiRenderMain.isHovered(mouseX, mouseY, chipX, chipY, chipWidth, chipHeight)) {
               modeSetting.currentMode = mode;
               if (Vesence.get.configManager != null) Vesence.get.configManager.autoSave();
               return true;
            }
            currentX += chipWidth + spacing;
         }
      }

      if (setting instanceof StringSetting) {
         StringSetting stringSetting = (StringSetting)setting;
         float textFieldHeight = 13.0F;
         float textFieldWidth = width - 6.0F;
         float textFieldX = x + 3.0F;
         float textFieldY = renderY + 11.5f;
         if (button == 0 && GuiRenderMain.isHovered(mouseX, mouseY, textFieldX, textFieldY, textFieldWidth, textFieldHeight)) {
            if (GuiScreen.activeStringSetting != stringSetting) {
               if (GuiScreen.activeStringSetting != null) {
                  GuiScreen.activeStringSetting.active = false;
               }
               GuiScreen.activeStringSetting = stringSetting;
               stringSetting.active = true;
               vesence.utils.player.MovementManager.getInstance().lockMovement("StringSetting");
            }
            return true;
         }

         if (button == 0 && GuiScreen.activeStringSetting == stringSetting) {
            GuiScreen.activeStringSetting.active = false;
            GuiScreen.activeStringSetting = null;
            vesence.utils.player.MovementManager.getInstance().unlockMovement("StringSetting");
         }
      }

      if (setting instanceof MultiBooleanSetting) {
         MultiBooleanSetting multiBooleanSetting = (MultiBooleanSetting)setting;
         float startY = renderY + 10.0F;
         float currentX = 0;
         float currentY = 0;
         float spacing = 3.0F;
         float boolHeight = 11.0F;
         float padding = 3.0F;
         float maxWidth = width - 6.0F;

         for (BooleanSetting boolSettingx : multiBooleanSetting.settings) {
            float nameWidth = renderer2D.measureText(FontRegistry.SF_MEDIUM, boolSettingx.name, 12.0F).width;
            float boolWidth = nameWidth + padding * 2.0F;
            if (currentX > 0 && currentX + boolWidth > maxWidth) {
               currentX = 0;
               currentY += boolHeight + spacing;
            }
            float chipX = x + 3.0F + currentX;
            float chipY = startY + currentY;

            if (button == 0 && GuiRenderMain.isHovered(mouseX, mouseY, chipX, chipY, boolWidth, boolHeight)) {
               boolSettingx.set(!boolSettingx.get());
               if (Vesence.get.configManager != null) Vesence.get.configManager.autoSave();
               return true;
            }

            currentX += boolWidth + spacing;
         }
      }

      if (setting instanceof ThemeSetting) {
         ThemeSetting themeSetting = (ThemeSetting)setting;
         float boxSize = 10.0F;
         float spacing = 4.0F;
         float currentX = 0;
         float currentY = 0;
         float maxWidth = width - 6.0F;

         for (Theme theme : themeSetting.themes) {
            if (currentX > 0 && currentX + boxSize > maxWidth) {
               currentX = 0;
               currentY += boxSize + spacing;
            }
            float boxX = x + 3.0F + currentX;
            float boxY = renderY + 10.0F + currentY;

            if (button == 0 && GuiRenderMain.isHovered(mouseX, mouseY, boxX, boxY, boxSize, boxSize)) {
               themeSetting.set(theme);
               if (Vesence.get.configManager != null) Vesence.get.configManager.autoSave();
               return true;
            }

            currentX += boxSize + spacing;
         }
      }

      return false;
   }

   public static void applyHueBar(HueSetting hueSetting, int bar, float t) {
      t = Math.max(0.0F, Math.min(1.0F, t));
      switch (bar) {
         case 0 -> hueSetting.current = t * hueSetting.maximum;
         case 1 -> hueSetting.saturation = t;
         case 2 -> hueSetting.brightness = t;
         case 3 -> hueSetting.alpha = t;
      }
      if (Vesence.get.configManager != null) Vesence.get.configManager.autoSave();
   }
}
