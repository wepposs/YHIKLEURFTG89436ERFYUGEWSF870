package vesence.ui.clickgui.component.mouse;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.Vesence;
import vesence.module.impl.misc.ClickGui;
import vesence.ui.clickgui.GuiScreen;
import vesence.ui.clickgui.component.mouse.colorpicker.GuiMouseClickedColorPicker;
import vesence.ui.clickgui.component.mouse.module.GuiMouseClickedModule;
import vesence.ui.clickgui.component.render.ClickRippleEffect;
import vesence.ui.clickgui.component.render.GuiRenderModePopup;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.math.MathHelper;
import vesence.utils.render.math.ScaledResolution;

@Environment(EnvType.CLIENT)
public class GuiMouseClicked extends GuiScreen {
    public static boolean mouseClicked(Renderer2D renderer2D, double pMouseX, double pMouseY, int pButton) {
        int mouseX = (int)pMouseX;
        int mouseY = (int)pMouseY;
        ScaledResolution sr = new ScaledResolution(GuiScreen.mc);
        GuiScreen.x = (int)MathHelper.clamp(GuiScreen.x, 0.0F, sr.getWidth() - GuiScreen.width);
        GuiScreen.y = (int)MathHelper.clamp(GuiScreen.y, 0.0F, sr.getHeight() - GuiScreen.height);

        ClickGui clickGuiModule = (ClickGui) Vesence.get.manager.module.stream()
                .filter(m -> m instanceof ClickGui).findFirst().orElse(null);

        if (!GuiScreen.exit) {
            if (GuiMouseClickedColorPicker.mouseClickedColorPicker(mouseX, mouseY, pButton)) {
                if (GuiScreen.activeModeSetting != null) {
                    GuiScreen.activeModeSetting.opened = false;
                    GuiScreen.activeModeSetting = null;
                }
                return true;
            }

            if (GuiScreen.activeModeSetting != null && GuiScreen.activeModeSetting.opened && pButton == 0) {
                if (GuiRenderModePopup.isHovered(mouseX, mouseY)) {
                    int hoveredIndex = GuiRenderModePopup.getHoveredIndex(mouseX, mouseY);
                    if (hoveredIndex >= 0) {
                        GuiScreen.activeModeSetting.currentMode = GuiScreen.activeModeSetting.modes.get(hoveredIndex);
                        GuiScreen.activeModeSetting.index = hoveredIndex;
                        GuiScreen.activeModeSetting.opened = false;
                        if (Vesence.get.configManager != null) Vesence.get.configManager.autoSave();
                        GuiScreen.activeModeSetting = null;
                        return true;
                    }
                }
                GuiScreen.activeModeSetting.opened = false;
                GuiScreen.activeModeSetting = null;
                return true;
            }

            if (GuiMouseClickedModule.mouseClickedModule(renderer2D, mouseX, mouseY, pButton)) {
                return true;
            }
        }

        if (GuiScreen.activeModeSetting != null) {
            GuiScreen.activeModeSetting.opened = false;
            GuiScreen.activeModeSetting = null;
        }

        if (GuiScreen.activeBindSetting != null && pButton >= 0) {
            int mouseKey = -100 - pButton;
            GuiScreen.activeBindSetting.key = mouseKey;
            GuiScreen.activeBindSetting.active = false;
            GuiScreen.activeBindSetting = null;
            if (Vesence.get.configManager != null) Vesence.get.configManager.autoSave();
            return true;
        } else {
            return false;
        }
    }
}
