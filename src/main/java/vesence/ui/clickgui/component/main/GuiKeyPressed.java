package vesence.ui.clickgui.component.main;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.Vesence;
import vesence.module.api.setting.impl.StringSetting;
import vesence.ui.clickgui.GuiScreen;
import vesence.utils.render.math.animation.anim.util.Easings;
import vesence.utils.render.utils.ClipboardUtil;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * Обработка клавиш в ClickGui.
 *
 * <p>Порт 26.3: {@code KeyEvent#key()} — это SDL-сканкод, а не GLFW-код. Старые числовые
 * литералы (256 = ESC, 259 = BACKSPACE, 261 = DELETE) в 26.3 означали бы совсем другие
 * клавиши (41 = ESC, 42 = BACKSPACE, 76 = DELETE). Поэтому везде используем
 * {@code InputConstants.KEY_*}.
 */
@Environment(EnvType.CLIENT)
public class GuiKeyPressed extends GuiScreen {

   public static boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (GuiScreen.activeModuleBind != null) {
         if (keyCode == InputConstants.KEY_ESCAPE) {
            GuiScreen.activeModuleBind.binding = false;
            GuiScreen.activeModuleBind = null;
         } else if (keyCode == InputConstants.KEY_DELETE) {
             GuiScreen.activeModuleBind.bind = -1;
             GuiScreen.activeModuleBind.binding = false;

            GuiScreen.getModuleBindAnimation(GuiScreen.activeModuleBind).run(0.0, 0.2, Easings.SINE_OUT);
            GuiScreen.activeModuleBind = null;
            if (Vesence.get.configManager != null) Vesence.get.configManager.autoSave();
         } else {
             GuiScreen.activeModuleBind.bind = keyCode;
             GuiScreen.activeModuleBind.binding = false;
             GuiScreen.getModuleBindAnimation(GuiScreen.activeModuleBind).run(1.0, 0.2, Easings.SINE_OUT);
             GuiScreen.activeModuleBind = null;
             if (Vesence.get.configManager != null) Vesence.get.configManager.autoSave();

         }

         return true;
      } else if (GuiScreen.activeBindSetting != null) {
         if (keyCode == InputConstants.KEY_ESCAPE) {
            GuiScreen.activeBindSetting.active = false;
            GuiScreen.activeBindSetting = null;
         } else if (keyCode == InputConstants.KEY_DELETE) {
             GuiScreen.activeBindSetting.key = -1;
             GuiScreen.activeBindSetting.active = false;

            GuiScreen.activeBindSetting = null;
            if (Vesence.get.configManager != null) Vesence.get.configManager.autoSave();
         } else {
             GuiScreen.activeBindSetting.key = keyCode;
             GuiScreen.activeBindSetting.active = false;
             GuiScreen.activeBindSetting = null;
             if (Vesence.get.configManager != null) Vesence.get.configManager.autoSave();

         }

         return true;
      } else {
         if (GuiScreen.activeStringSetting != null) {
            StringSetting ss = GuiScreen.activeStringSetting;
            boolean ctrl = ClipboardUtil.isCtrl(modifiers);
            if (ctrl && keyCode == InputConstants.KEY_A) {
               GuiScreen.textSelectAll = ss.input != null && !ss.input.isEmpty();
               return true;
            }
            if (ctrl && keyCode == InputConstants.KEY_C) {
               if (ss.input != null && !ss.input.isEmpty()) ClipboardUtil.set(ss.input);
               return true;
            }
            if (ctrl && keyCode == InputConstants.KEY_X) {
               if (ss.input != null && !ss.input.isEmpty()) {
                  ClipboardUtil.set(ss.input);
                  ss.input = "";
                  if (Vesence.get.configManager != null) Vesence.get.configManager.autoSave();
               }
               GuiScreen.textSelectAll = false;
               return true;
            }
            if (ctrl && keyCode == InputConstants.KEY_V) {
               String base = GuiScreen.textSelectAll ? "" : (ss.input == null ? "" : ss.input);
               ss.input = ClipboardUtil.appendFiltered(base, ClipboardUtil.get(), 16, ClipboardUtil.MODE_PRINTABLE);
               GuiScreen.textSelectAll = false;
               if (Vesence.get.configManager != null) Vesence.get.configManager.autoSave();
               return true;
            }

            if (keyCode == InputConstants.KEY_ESCAPE) {
               GuiScreen.textSelectAll = false;
               GuiScreen.activeStringSetting.active = false;
               GuiScreen.activeStringSetting = null;
               vesence.utils.player.MovementManager.getInstance().unlockMovement("StringSetting");
               if (Vesence.get.configManager != null) Vesence.get.configManager.autoSave();

               return true;
            }

            if (keyCode == InputConstants.KEY_BACKSPACE) {
               if (GuiScreen.textSelectAll) {
                  ss.input = "";
                  GuiScreen.textSelectAll = false;
                  if (Vesence.get.configManager != null) Vesence.get.configManager.autoSave();
               } else if (ss.input != null && !ss.input.isEmpty()) {
                  ss.input = ctrl ? "" : ss.input.substring(0, ss.input.length() - 1);
                  if (Vesence.get.configManager != null) Vesence.get.configManager.autoSave();
               }

               return true;
            }
         }

         if (GuiScreen.activeSearch) {
            boolean ctrl = ClipboardUtil.isCtrl(modifiers);
            if (ctrl && keyCode == InputConstants.KEY_A) {
               GuiScreen.textSelectAll = !GuiScreen.searchText.isEmpty();
               return true;
            }
            if (ctrl && keyCode == InputConstants.KEY_C) {
               if (!GuiScreen.searchText.isEmpty()) ClipboardUtil.set(GuiScreen.searchText);
               return true;
            }
            if (ctrl && keyCode == InputConstants.KEY_X) {
               if (!GuiScreen.searchText.isEmpty()) {
                  ClipboardUtil.set(GuiScreen.searchText);
                  GuiScreen.searchText = "";
               }
               GuiScreen.textSelectAll = false;
               return true;
            }
            if (ctrl && keyCode == InputConstants.KEY_V) {
               String base = GuiScreen.textSelectAll ? "" : GuiScreen.searchText;
               GuiScreen.searchText = ClipboardUtil.appendFiltered(base, ClipboardUtil.get(), 50, ClipboardUtil.MODE_ALNUM_SPACE);
               GuiScreen.textSelectAll = false;
               return true;
            }

            if (keyCode == InputConstants.KEY_ESCAPE) {
               GuiScreen.activeSearch = false;
               GuiScreen.searchText = "";
               GuiScreen.textSelectAll = false;
               return true;
            }

            if (keyCode == InputConstants.KEY_BACKSPACE) {
               if (GuiScreen.textSelectAll) {
                  GuiScreen.searchText = "";
                  GuiScreen.textSelectAll = false;
               } else if (!GuiScreen.searchText.isEmpty()) {
                  GuiScreen.searchText = ctrl ? "" : GuiScreen.searchText.substring(0, GuiScreen.searchText.length() - 1);
               }
               return true;
            }
         }

         return false;
      }
   }
}
