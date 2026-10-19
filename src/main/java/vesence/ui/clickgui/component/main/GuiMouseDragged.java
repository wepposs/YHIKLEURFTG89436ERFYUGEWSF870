package vesence.ui.clickgui.component.main;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.module.api.setting.impl.HueSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.ui.clickgui.GuiScreen;
import vesence.utils.render.math.ScaleHelper;

@Environment(EnvType.CLIENT)
public class GuiMouseDragged extends GuiScreen {
   public static boolean mouseDragged(double pMouseX, double pMouseY, int pButton, double pDragX, double pDragY) {
      float[] coords = ScaleHelper.calc((float)pMouseX, (float)pMouseY);
      int mouseX = (int)coords[0];
      int mouseY = (int)coords[1];
      float guiMouseX = vesence.ui.clickgui.compact.CompactGuiScreen.toGuiX(mouseX);
      float guiMouseY = vesence.ui.clickgui.compact.CompactGuiScreen.toGuiY(mouseY);

      if (GuiScreen.activeHueSetting != null) {
         HueSetting hueSetting = GuiScreen.activeHueSetting;
         float pickerX = GuiScreen.huePickerX;
         float pickerW = GuiScreen.huePickerWidth;

         if (hueSetting.draggingBar >= 0) {
            float t = Math.max(0.0F, Math.min(1.0F, (guiMouseX - pickerX) / pickerW));
            vesence.ui.clickgui.component.mouse.setting.GuiMouseClickedSetting.applyHueBar(hueSetting, hueSetting.draggingBar, t);
            return true;
         }

         if (hueSetting.pickingSaturationBrightness) {
            float pickerH = 35;
            float xPos = Math.max(0.0F, Math.min(guiMouseX - pickerX, pickerW));
            float yPos = Math.max(0.0F, Math.min(guiMouseY - GuiScreen.huePickerY, pickerH));
            hueSetting.saturation = xPos / pickerW;
            hueSetting.brightness = 1.0F - yPos / pickerH;
            return true;
         }

         if (hueSetting.pickingHue) {
            float huePos = Math.max(0.0F, Math.min(guiMouseX - pickerX, pickerW));
            hueSetting.current = huePos / pickerW * hueSetting.maximum;
            return true;
         }
      }

      if (GuiScreen.activeColorPicker != null && GuiScreen.activeColorPicker instanceof HueSetting) {
         HueSetting hueSetting = (HueSetting)GuiScreen.activeColorPicker;
         float pickerX = GuiScreen.colorPickerX;
         float pickerY = GuiScreen.colorPickerY;
         if (pickerX != 0.0F || pickerY != 0.0F) {
            float paletteWidth = 63.92F;
            float paletteHeight = 47.02F;
            float paletteX = pickerX + 5.0F;
            float paletteY = pickerY + 5.0F;
            if (GuiScreen.pickingSaturationBrightness) {
               float x = Math.max(0.0F, Math.min(mouseX - paletteX, paletteWidth));
               float y = Math.max(0.0F, Math.min(mouseY - paletteY, paletteHeight));
               hueSetting.saturation = x / paletteWidth;
               hueSetting.brightness = 1.0F - y / paletteHeight;
               return true;
            }

            if (GuiScreen.pickingHue) {
               float hueSliderWidth = 64.0F;
               float hueSliderHeight = 2.59F;
               float hueSliderX = pickerX + 5.0F;
               float hueSliderY = paletteY + paletteHeight + 5.0F;
               float huePos = Math.max(0.0F, Math.min(mouseX - hueSliderX, hueSliderWidth));
               hueSetting.current = huePos / hueSliderWidth * 106.0F;
               return true;
            }
         }
      }

      if (GuiScreen.activeSliderSetting != null) {
         SliderSetting sliderSetting = GuiScreen.activeSliderSetting;
         double oldValue = sliderSetting.current;
         float progress = (guiMouseX - GuiScreen.sliderX) / GuiScreen.sliderWidth;
         progress = Math.max(0.0F, Math.min(1.0F, progress));
         sliderSetting.current = snapToStep(sliderSetting.minimum + (sliderSetting.maximum - sliderSetting.minimum) * progress, sliderSetting.minimum, sliderSetting.maximum, sliderSetting.increment);
         if (sliderSetting.current != oldValue) {
            vesence.utils.render.utils.SoundUtil.playUi("slider", 0.12F, 45L);
         }
         return true;
      } else if (GuiScreen.activeRangeSetting != null) {
         vesence.module.api.setting.impl.RangeSliderSetting rangeSetting = GuiScreen.activeRangeSetting;
         float progress = (mouseX - GuiScreen.sliderX) / GuiScreen.sliderWidth;
         progress = Math.max(0.0F, Math.min(1.0F, progress));
         double raw = snapToStep(rangeSetting.minimum + (rangeSetting.maximum - rangeSetting.minimum) * progress, rangeSetting.minimum, rangeSetting.maximum, rangeSetting.increment);
         if (rangeSetting.draggingThumb == 2) {
            rangeSetting.setTo(raw);
         } else {
            rangeSetting.setFrom(raw);
         }
         return true;
      } else {
         return false;
      }
   }

   private static double snapToStep(double value, double min, double max, double step) {
      double snapped = Math.round((value - min) / step) * step + min;
      return Math.max(min, Math.min(max, snapped));
   }
}
