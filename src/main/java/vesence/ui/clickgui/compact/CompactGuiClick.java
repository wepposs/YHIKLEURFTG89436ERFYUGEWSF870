package vesence.ui.clickgui.compact;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import vesence.Vesence;
import vesence.module.api.Category;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.HueSetting;
import vesence.module.impl.misc.ClickGui;
import vesence.renderengine.render.Renderer2D;
import vesence.ui.clickgui.GuiScreen;
import vesence.ui.clickgui.compact.setting.CompactGuiMouseClickedSetting;
import vesence.ui.clickgui.compact.setting.CompactGuiRenderSetting;
import vesence.ui.clickgui.component.render.ClickRippleEffect;
import vesence.ui.clickgui.component.render.GuiRenderModePopup;
import vesence.utils.render.math.animation.anim.util.Easings;
import vesence.utils.render.text.FontRegistry;
import vesence.utils.render.utils.ClipboardUtil;
import vesence.utils.render.utils.KeyUtil;

import org.lwjgl.glfw.GLFW;

import java.util.List;

@Environment(EnvType.CLIENT)
public class CompactGuiClick {

    public static float[] findColorPickerPosition(Renderer2D renderer2D, HueSetting hueSetting) {
        if (hueSetting == null) {
            return null;
        } else {
            int index = 1;
            float downY = GuiScreen.getScrollUtil().getScroll();
            float downYSetting1 = 0.0F;
            float downYSetting2 = 0.0F;

            for (Module module : GuiScreen.modules) {
                float settingsHeight = 12.0F;
                if (GuiScreen.openSettingsModules.contains(module)) {
                    for (Setting setting : module.getSettingsForGUI()) {
                        settingsHeight += CompactGuiRenderSetting.getSettingHeight(renderer2D, setting);
                    }

                    settingsHeight = Math.max(settingsHeight, 20.0F);
                }

                if (index % 2 == 0) {
                    float currentDownY = downY + downYSetting2 - 30.0F;
                    if (GuiScreen.openSettingsModules.contains(module)) {
                        float settingY = GuiScreen.y + 64.69F + currentDownY + 4.0F;
                        float settingX = GuiScreen.x + 238.35F + 9.0F;
                        float settingWidth = 111.47F;
                        float totalSettingsHeight = 0.0F;

                        for (Setting setting : module.getSettingsForGUI()) {
                            if (setting == hueSetting) {
                                float pickerX = settingX + settingWidth - 15.0F;
                                float pickerY = settingY + totalSettingsHeight - 5.0F;
                                return new float[]{pickerX, pickerY};
                            }

                            totalSettingsHeight += CompactGuiRenderSetting.getSettingHeight(renderer2D, setting) + 3.0F;
                        }

                        downYSetting2 += settingsHeight;
                    }
                } else {
                    float currentDownY = downY + downYSetting1;
                    if (GuiScreen.openSettingsModules.contains(module)) {
                        float settingY = GuiScreen.y + 64.69F + currentDownY + 4.0F;
                        float settingX = GuiScreen.x + 111.885F + 9.0F;
                        float settingWidth = 111.47F;
                        float totalSettingsHeight = 0.0F;

                        for (Setting setting : module.getSettingsForGUI()) {
                            if (setting == hueSetting) {
                                float pickerX = settingX + settingWidth - 15.0F;
                                float pickerY = settingY + totalSettingsHeight - 5.0F;
                                return new float[]{pickerX, pickerY};
                            }

                            totalSettingsHeight += CompactGuiRenderSetting.getSettingHeight(renderer2D, setting) + 3.0F;
                        }

                        downYSetting1 += settingsHeight;
                    }

                    downY += 30.325F;
                }

                index++;
            }

            return null;
        }
    }
    public static boolean mouseClicked(Renderer2D renderer, int rawMouseX, int rawMouseY, int button) {
        int mouseX = (int) CompactGuiScreen.toGuiX(rawMouseX);
        int mouseY = (int) CompactGuiScreen.toGuiY(rawMouseY);

        // Любой клик снимает выделение "всё" (Ctrl+A) с текстовых полей.
        CompactGuiScreen.searchSelectAll = false;
        GuiScreen.textSelectAll = false;

        if (GuiScreen.activeBindSetting != null && button >= 0) {
            GuiScreen.activeBindSetting.key = -100 - button;
            GuiScreen.activeBindSetting.active = false;
            GuiScreen.activeBindSetting = null;
            if (Vesence.get.configManager != null) Vesence.get.configManager.autoSave();
            return true;
        }

        if (GuiScreen.activeModeSetting != null && GuiScreen.activeModeSetting.opened) {
            if (button == 0 && GuiRenderModePopup.isHovered(mouseX, mouseY)) {
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

        float gx = CompactGuiScreen.x;
        float gy = CompactGuiScreen.y;
        float gw = CompactGuiScreen.GUI_W;
        float gh = CompactGuiScreen.GUI_H;

        if (CompactModuleWindow.handleClick(mouseX, mouseY, button)) return true;

        if (!CompactGuiRender.isHovered(mouseX, mouseY, gx, gy, gw, gh)) return false;

        if (GuiScreen.editingSliderSetting != null) {
            vesence.module.api.setting.impl.SliderSetting prevEdit = GuiScreen.editingSliderSetting;
            vesence.ui.clickgui.compact.setting.CompactGuiMouseClickedSetting.commitSliderEdit(prevEdit);
        }

        float lpx = gx + CompactGuiScreen.LEFT_PANEL_GAP;
        float lpy = gy + CompactGuiScreen.LEFT_PANEL_GAP + 25;
        float lpw = CompactGuiScreen.LEFT_PANEL_W;
        float lph = gh - CompactGuiScreen.LEFT_PANEL_GAP * 2;

        if (CompactGuiRender.isHovered(mouseX, mouseY, lpx, lpy, lpw, lph)) {
            return handleLeftPanelClick(renderer, mouseX, mouseY, button, lpx, lpy, lpw, lph);
        }

        float rpx = gx + CompactGuiScreen.LEFT_PANEL_W + CompactGuiScreen.LEFT_PANEL_GAP * 2;
        float rpy = gy + CompactGuiScreen.LEFT_PANEL_GAP;
        float rpw = CompactGuiScreen.RIGHT_PANEL_W;
        float rph = gh - CompactGuiScreen.LEFT_PANEL_GAP * 2;

        if (CompactGuiRender.isHovered(mouseX, mouseY, rpx, rpy, rpw, rph)) {
            return handleRightPanelClick(renderer, mouseX, mouseY, button, rpx, rpy, rpw, rph);
        }

        return false;
    }

    private static boolean handleLeftPanelClick(Renderer2D renderer, int mouseX, int mouseY, int button,
                                                 float panelX, float panelY, float panelW, float panelH) {
        float sx = panelX + CompactGuiScreen.LEFT_PADDING;
        float sw = CompactGuiScreen.SEARCH_W;

        if (button == 1) return false;

        if (CompactGuiRender.isHovered(mouseX, mouseY, sx, panelY + CompactGuiScreen.LEFT_PADDING - 5, sw, CompactGuiScreen.LEFT_SEARCH_H)) {
            CompactGuiScreen.searchActive = !CompactGuiScreen.searchActive;
            return true;
        }

        float catStartY = panelY + CompactGuiScreen.LEFT_PADDING + CompactGuiScreen.LEFT_SEARCH_H + 2.5f;
        Category[] cats = Category.values();

        for (int i = 0; i < cats.length; i++) {
            float cy = catStartY + CompactGuiScreen.getCategoryYOffset(i);
            if (CompactGuiRender.isHovered(mouseX, mouseY, sx, cy, sw, CompactGuiScreen.LEFT_CATEGORY_H)) {
                boolean changed = !CompactGuiScreen.isCategoryVisuallySelected(cats[i]);
                CompactGuiScreen.selectCategory(cats[i]);
                CompactGuiScreen.searchActive = false;
                CompactGuiScreen.searchText = "";
                if (changed) vesence.utils.render.utils.SoundUtil.playUi("switchcategory", 0.2F);
                return true;
            }
        }

        float themeY = catStartY + CompactGuiScreen.getThemeYOffset();
        if (CompactGuiRender.isHovered(mouseX, mouseY, sx, themeY, sw, CompactGuiScreen.LEFT_CATEGORY_H)) {
            boolean changed = !CompactGuiScreen.isThemeVisuallySelected();
            CompactGuiScreen.selectThemeTab();
            CompactGuiScreen.searchActive = false;
            CompactGuiScreen.searchText = "";
            if (changed) vesence.utils.render.utils.SoundUtil.playUi("switchcategory", 0.2F);
            return true;
        }

        float clientY = catStartY + CompactGuiScreen.getClientYOffset();
        if (CompactGuiRender.isHovered(mouseX, mouseY, sx, clientY, sw, CompactGuiScreen.LEFT_CATEGORY_H)) {
            boolean changed = !CompactGuiScreen.isClientVisuallySelected();
            CompactGuiScreen.selectClientTab();
            CompactGuiScreen.searchActive = false;
            CompactGuiScreen.searchText = "";
            if (changed) vesence.utils.render.utils.SoundUtil.playUi("switchcategory", 0.2F);
            return true;
        }

        CompactGuiScreen.searchActive = false;
        return true;
    }

    private static boolean handleRightPanelClick(Renderer2D renderer, int mouseX, int mouseY, int button,
                                                  float panelX, float panelY, float panelW, float panelH) {
        if (button != 0 && button != 1) return false;

        if (CompactGuiScreen.themeSelected && CompactGuiScreen.searchText.isEmpty()) {
            if (button != 0) return false;
            float cPadT = 4;
            float cXt = panelX + cPadT;
            float cYt = panelY + cPadT;
            float cWt = panelW - cPadT * 2;
            float scrollOffT = CompactGuiScreen.moduleScroll.getScroll();
            vesence.module.Theme[] themes = vesence.module.Theme.values();
            for (int i = 0; i < themes.length; i++) {
                float[] r = CompactGuiRender.themeCellRect(i, cXt, cYt, cWt, scrollOffT);
                if (CompactGuiRender.isHovered(mouseX, mouseY, r[0], r[1], r[2], r[3])) {
                    applyTheme(themes[i]);
                    return true;
                }
            }
            return true;
        }

        if (CompactGuiScreen.clientSelected && CompactGuiScreen.searchText.isEmpty()) {
            float cPadC = 4;
            float cXc = panelX + cPadC;
            float cYc = panelY + cPadC;
            float cWc = panelW - cPadC * 2 - 4;
            float scrollOffC = CompactGuiScreen.clientScroll.getScroll();
            float curY = cYc + scrollOffC;
            for (Setting<?> setting : vesence.ui.clickgui.compact.client.ClientSettings.getSettings()) {
                if ((boolean) setting.hidden.get()) continue;
                float sH = vesence.ui.clickgui.compact.client.CompactGuiRenderClientSetting
                        .getSettingHeight(renderer, setting, cWc);
                if (CompactGuiRender.isHovered(mouseX, mouseY, cXc, curY, cWc, sH)) {
                    return vesence.ui.clickgui.compact.client.CompactGuiRenderClientSetting.handleClick(
                            renderer, setting, cXc, curY, cWc, mouseX, mouseY, button);
                }
                curY += sH + 2.5f;
            }
            return true;
        }

        List<Module> modules = CompactGuiRender.getCurrentModules();

        float cPad = 4;
        float cX = panelX + cPad;
        float cY = panelY + cPad;
        float cW = panelW - cPad * 2;

        int cols = 2;
        float colGap = CompactGuiScreen.MODULE_COL_GAP;
        float colW = (cW - colGap) / cols;
        float scrollOff = CompactGuiScreen.moduleScroll.getScroll();

        float[] colY = new float[cols];

        for (int i = 0; i < modules.size(); i++) {
            Module mod = modules.get(i);
            float cardH = CompactGuiRender.computeCardHeight(renderer, mod, colW);

            int shortestCol = 0;
            for (int c = 1; c < cols; c++) {
                if (colY[c] < colY[shortestCol]) shortestCol = c;
            }

            float cardX = cX + shortestCol * (colW + colGap);
            float cardY = cY + colY[shortestCol] + scrollOff;

            if (CompactGuiRender.isHovered(mouseX, mouseY, cardX, cardY, colW, cardH)) {
                if (button == 0) {
                    float pad = CompactGuiScreen.MODULE_CARD_PADDING;
                    float innerW = colW - pad * 2;

                    float togW = 16;
                    float togH = 8;
                    float togX = cardX + colW - pad - togW;
                    float togY = cardY + pad + 1.5f;
                    float headerH = pad + 18;
                    if (CompactGuiRender.isHovered(mouseX, mouseY, cardX, cardY, colW, headerH)) {
                        mod.toggle();
                        return true;
                    }

                    float curY = cardY + pad + 18;

                    String desc = mod.description != null ? mod.description : "";
                    if (!desc.isEmpty()) {
                        List<String> descLines = CompactGuiRender.wrapText(renderer, desc,
                                CompactGuiScreen.MODULE_DESC_FONT, innerW);
                        curY += descLines.size() * (CompactGuiScreen.MODULE_DESC_FONT - 3f);
                    }

                    curY += CompactGuiScreen.MODULE_DIVIDER_GAP + 3;

                    String bText = mod.binding ? "..." : vesence.utils.render.utils.KeyUtil.getKey(mod.bind);
                    float ktw = renderer.measureText(FontRegistry.SF_MEDIUM, bText, 9.5f).width;
                    float bw = ktw + 5;
                    float bx = cardX + colW - pad - bw;
                    if (CompactGuiRender.isHovered(mouseX, mouseY, bx, curY, bw, 11)) {
                        if (mod.binding) {
                            mod.binding = false;
                            GuiScreen.activeModuleBind = null;
                            GuiScreen.getModuleBindAnimation(mod).run(0.0, 0.2, Easings.SINE_OUT);
                        } else {
                            if (GuiScreen.activeModuleBind != null) {
                                GuiScreen.activeModuleBind.binding = false;
                                GuiScreen.getModuleBindAnimation(GuiScreen.activeModuleBind).run(0.0, 0.2, Easings.SINE_OUT);
                            }
                            GuiScreen.activeModuleBind = mod;
                            mod.binding = true;
                            GuiScreen.getModuleBindAnimation(mod).run(1.0, 0.2, Easings.SINE_OUT);
                        }
                        return true;
                    }

                    curY += 13;

                    return true;
                }
                if (button == 1) {
                    CompactModuleWindow.open(mod, cardX, cardY, colW, cardH);
                    return true;
                }
                if (button == 2) {
                    if (mod.binding) {
                        mod.binding = false;
                        GuiScreen.activeModuleBind = null;
                        GuiScreen.getModuleBindAnimation(mod).run(0.0, 1.0, Easings.SINE_OUT);
                    } else {
                        if (GuiScreen.activeModuleBind != null) {
                            GuiScreen.activeModuleBind.binding = false;
                            GuiScreen.getModuleBindAnimation(GuiScreen.activeModuleBind).run(0.0, 1.0, Easings.SINE_OUT);
                        }
                        GuiScreen.activeModuleBind = mod;
                        mod.binding = true;
                        GuiScreen.getModuleBindAnimation(mod).run(1.0, 1.0, Easings.SINE_OUT);
                    }
                    return true;
                }
            }

            colY[shortestCol] += cardH + CompactGuiScreen.MODULE_ROW_GAP;
        }

        return false;
    }

    private static void applyTheme(vesence.module.Theme theme) {
        try {
            if (Vesence.get != null && Vesence.get.manager != null) {
                vesence.module.impl.visuals.ThemeModule tm =
                        (vesence.module.impl.visuals.ThemeModule) Vesence.get.manager.get(vesence.module.impl.visuals.ThemeModule.class);
                if (tm != null && tm.theme != null) {
                    tm.theme.set(theme);
                    if (Vesence.get.configManager != null) Vesence.get.configManager.autoSave();
                }
            }
        } catch (Exception ignored) {}
    }

    public static boolean mouseDragged(int rawMouseX, int rawMouseY, int button) {
        return false;
    }

    public static boolean mouseScrolled(double rawMouseX, double rawMouseY, double horizontalAmount, double verticalAmount) {
        double mouseX = CompactGuiScreen.toGuiX(rawMouseX);
        double mouseY = CompactGuiScreen.toGuiY(rawMouseY);
        float gx = CompactGuiScreen.x;
        float gy = CompactGuiScreen.y;
        float gw = CompactGuiScreen.GUI_W;
        float gh = CompactGuiScreen.GUI_H;

        boolean sliderMode = isSliderAdjustKeyDown();

        if (sliderMode && CompactGuiScreen.scrollCapturedSlider != null
                && System.currentTimeMillis() - CompactGuiScreen.scrollCaptureTime <= CompactGuiScreen.SCROLL_CAPTURE_WINDOW_MS) {
            scrollSliderValue(CompactGuiScreen.scrollCapturedSlider, verticalAmount);
            CompactGuiScreen.scrollCaptureTime = System.currentTimeMillis();
            return true;
        }

        if (CompactModuleWindow.handleScroll((float) mouseX, (float) mouseY, verticalAmount)) {
            return true;
        }

        if (!CompactGuiRender.isHovered((float) mouseX, (float) mouseY, gx, gy, gw, gh)) {
            CompactGuiScreen.scrollCapturedSlider = null;
            return false;
        }
        float rpx = gx + CompactGuiScreen.LEFT_PANEL_W + CompactGuiScreen.LEFT_PANEL_GAP * 2;
        float rpy = gy + CompactGuiScreen.LEFT_PANEL_GAP;
        float rpw = CompactGuiScreen.RIGHT_PANEL_W;
        float rph = gh - CompactGuiScreen.LEFT_PANEL_GAP * 2;

        if (CompactGuiRender.isHovered((float) mouseX, (float) mouseY, rpx, rpy, rpw, rph)) {
            Renderer2D renderer = Vesence.getRenderer();

            if (sliderMode && renderer != null) {
                if (CompactGuiScreen.clientSelected && CompactGuiScreen.searchText.isEmpty()) {
                    if (scrollClientSlider(renderer, (int) mouseX, (int) mouseY, rpx, rpy, rpw, verticalAmount)) {
                        return true;
                    }
                } else {
                    if (scrollModuleSlider(renderer, (int) mouseX, (int) mouseY, rpx, rpy, rpw, verticalAmount)) {
                        return true;
                    }
                }

                return true;
            }

            CompactGuiScreen.scrollCapturedSlider = null;
            if (CompactGuiScreen.clientSelected && CompactGuiScreen.searchText.isEmpty()) {
                CompactGuiScreen.clientScroll.handleScroll(verticalAmount);
            } else {
                CompactGuiScreen.moduleScroll.handleScroll(verticalAmount);
            }
            return true;
        }

        return false;
    }

    private static boolean isSliderAdjustKeyDown() {
        long h = MinecraftClient.getInstance().getWindow().getHandle();
        return org.lwjgl.glfw.GLFW.glfwGetKey(h, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS
                || org.lwjgl.glfw.GLFW.glfwGetKey(h, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }

    private static boolean scrollClientSlider(Renderer2D renderer, int mouseX, int mouseY,
                                              float panelX, float panelY, float panelW, double amount) {
        float cPadC = 4;
        float cXc = panelX + cPadC;
        float cYc = panelY + cPadC;
        float cWc = panelW - cPadC * 2 - 4;
        float scrollOffC = CompactGuiScreen.clientScroll.getScroll();
        float curY = cYc + scrollOffC;
        for (Setting<?> setting : vesence.ui.clickgui.compact.client.ClientSettings.getSettings()) {
            if ((boolean) setting.hidden.get()) continue;
            float sH = vesence.ui.clickgui.compact.client.CompactGuiRenderClientSetting
                    .getSettingHeight(renderer, setting, cWc);
            if (setting instanceof vesence.module.api.setting.impl.SliderSetting ss
                    && CompactGuiRender.isHovered(mouseX, mouseY, cXc, curY, cWc, sH)) {
                scrollSliderValue(ss, amount);
                CompactGuiScreen.scrollCapturedSlider = ss;
                CompactGuiScreen.scrollCaptureTime = System.currentTimeMillis();
                return true;
            }
            curY += sH + 2.5f;
        }
        return false;
    }

    private static boolean scrollModuleSlider(Renderer2D renderer, int mouseX, int mouseY,
                                             float panelX, float panelY, float panelW, double amount) {
        List<Module> modules = CompactGuiRender.getCurrentModules();

        float cPad = 4;
        float cX = panelX + cPad;
        float cY = panelY + cPad;
        float cW = panelW - cPad * 2;

        int cols = 2;
        float colGap = CompactGuiScreen.MODULE_COL_GAP;
        float colW = (cW - colGap) / cols;
        float scrollOff = CompactGuiScreen.moduleScroll.getScroll();

        float[] colY = new float[cols];

        for (int i = 0; i < modules.size(); i++) {
            Module mod = modules.get(i);
            float cardH = CompactGuiRender.computeCardHeight(renderer, mod, colW);

            int shortestCol = 0;
            for (int c = 1; c < cols; c++) {
                if (colY[c] < colY[shortestCol]) shortestCol = c;
            }

            float cardX = cX + shortestCol * (colW + colGap);
            float cardY = cY + colY[shortestCol] + scrollOff;

            if (CompactGuiRender.isHovered(mouseX, mouseY, cardX, cardY, colW, cardH)) {
                float pad = CompactGuiScreen.MODULE_CARD_PADDING;
                float innerW = colW - pad * 2;

                float curY = cardY + pad + 18;

                String desc = mod.description != null ? mod.description : "";
                if (!desc.isEmpty()) {
                    List<String> descLines = CompactGuiRender.wrapText(renderer, desc,
                            CompactGuiScreen.MODULE_DESC_FONT, innerW);
                    curY += descLines.size() * (CompactGuiScreen.MODULE_DESC_FONT - 3f);
                }

                curY += CompactGuiScreen.MODULE_DIVIDER_GAP + 3;
                curY += 13;

                List<Setting<?>> settings = mod.getSettingsForGUI();
                if (!settings.isEmpty()) {
                    curY += 2 + 5;
                    for (Setting<?> setting : settings) {
                        if ((boolean) setting.hidden.get()) continue;
                        float sH = CompactGuiRender.getSettingHeight(renderer, setting, innerW);
                        if (setting instanceof vesence.module.api.setting.impl.SliderSetting ss
                                && CompactGuiRender.isHovered(mouseX, mouseY, cardX + pad, curY, innerW, sH)) {
                            scrollSliderValue(ss, amount);
                            CompactGuiScreen.scrollCapturedSlider = ss;
                            CompactGuiScreen.scrollCaptureTime = System.currentTimeMillis();
                            return true;
                        }
                        curY += sH + 2.5f;
                    }
                }
                return false;
            }

            colY[shortestCol] += cardH + CompactGuiScreen.MODULE_ROW_GAP;
        }
        return false;
    }

    private static void scrollSliderValue(vesence.module.api.setting.impl.SliderSetting ss, double dir) {
        double step = ss.increment * (dir > 0 ? 1 : -1);
        double v = ss.current + step;
        v = Math.round((v - ss.minimum) / ss.increment) * ss.increment + ss.minimum;
        v = Math.max(ss.minimum, Math.min(ss.maximum, v));
        if (v != ss.current) {
            ss.current = v;
            vesence.utils.render.utils.SoundUtil.playUi("slider", 0.12F, 45L);
            if (Vesence.get != null && Vesence.get.configManager != null) {
                Vesence.get.configManager.autoSave();
            }
        }
    }

    public static boolean charTyped(char ch, int modifiers) {

        if (GuiScreen.editingSliderSetting != null) {
            vesence.module.api.setting.impl.SliderSetting es = GuiScreen.editingSliderSetting;
            if ((ch >= '0' && ch <= '9') || ch == '.' || ch == ',' || ch == '-') {
                if (GuiScreen.textSelectAll) {
                    es.editBuffer = "";
                    GuiScreen.textSelectAll = false;
                }
                if (es.editBuffer.length() < 12) {
                    es.editBuffer += ch;
                    vesence.utils.render.utils.SoundUtil.playUi("searchtyping", 0.1F);
                }
                return true;
            }
            return true;
        }

        if (!CompactGuiScreen.searchActive) return false;

        if (ch >= 32) {
            if (CompactGuiScreen.searchSelectAll) {
                CompactGuiScreen.searchText = "";
                CompactGuiScreen.searchSelectAll = false;
            }
            if (CompactGuiScreen.searchText.length() < 64) {
                CompactGuiScreen.searchText += ch;
                CompactGuiScreen.moduleScroll.reset();
                vesence.utils.render.utils.SoundUtil.playUi("searchtyping", 0.1F);
            }
            return true;
        }
        return false;
    }

    public static boolean keyPressed(int key, int scancode, int modifiers) {
        if (GuiScreen.activeModuleBind != null) {
            return false;
        }

        if (GuiScreen.editingSliderSetting != null) {
            vesence.module.api.setting.impl.SliderSetting es = GuiScreen.editingSliderSetting;
            boolean ctrl = ClipboardUtil.isCtrl(modifiers);
            if (ctrl && key == GLFW.GLFW_KEY_A) {
                GuiScreen.textSelectAll = !es.editBuffer.isEmpty();
                return true;
            }
            if (ctrl && key == GLFW.GLFW_KEY_C) {
                if (!es.editBuffer.isEmpty()) ClipboardUtil.set(es.editBuffer);
                return true;
            }
            if (ctrl && key == GLFW.GLFW_KEY_X) {
                if (!es.editBuffer.isEmpty()) {
                    ClipboardUtil.set(es.editBuffer);
                    es.editBuffer = "";
                }
                GuiScreen.textSelectAll = false;
                return true;
            }
            if (ctrl && key == GLFW.GLFW_KEY_V) {
                String base = GuiScreen.textSelectAll ? "" : es.editBuffer;
                es.editBuffer = ClipboardUtil.appendFiltered(base, ClipboardUtil.get(), 12, ClipboardUtil.MODE_NUMERIC);
                GuiScreen.textSelectAll = false;
                return true;
            }
            if (key == 257 || key == 335) {
                GuiScreen.textSelectAll = false;
                vesence.ui.clickgui.compact.setting.CompactGuiMouseClickedSetting.commitSliderEdit(es);
                return true;
            }
            if (key == 256) {
                GuiScreen.textSelectAll = false;
                vesence.ui.clickgui.compact.setting.CompactGuiMouseClickedSetting.cancelSliderEdit(es);
                return true;
            }
            if (key == 259) {
                if (GuiScreen.textSelectAll) {
                    es.editBuffer = "";
                    GuiScreen.textSelectAll = false;
                } else if (!es.editBuffer.isEmpty()) {
                    es.editBuffer = ctrl ? "" : es.editBuffer.substring(0, es.editBuffer.length() - 1);
                }
                return true;
            }
            return true;
        }

        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && CompactModuleWindow.anyOpen()) {
            CompactModuleWindow.closeTop();
            return true;
        }

        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_F && (modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL) != 0) {
            CompactGuiScreen.searchActive = true;
            return true;
        }

        if (GuiScreen.activeBindSetting != null) {
            if (key == 256) {
                GuiScreen.activeBindSetting.active = false;
                GuiScreen.activeBindSetting = null;
            } else if (key == 261) {
                GuiScreen.activeBindSetting.key = -1;
                GuiScreen.activeBindSetting.active = false;
                GuiScreen.activeBindSetting = null;
                if (Vesence.get.configManager != null) Vesence.get.configManager.autoSave();
            } else {
                GuiScreen.activeBindSetting.key = key;
                GuiScreen.activeBindSetting.active = false;
                GuiScreen.activeBindSetting = null;
                if (Vesence.get.configManager != null) Vesence.get.configManager.autoSave();
            }
            return true;
        }

        if (GuiScreen.activeStringSetting != null) {
            if (key == 256) {
                GuiScreen.activeStringSetting.active = false;
                GuiScreen.activeStringSetting = null;
                vesence.utils.player.MovementManager.getInstance().unlockMovement("StringSetting");
                if (Vesence.get.configManager != null) Vesence.get.configManager.autoSave();
                return true;
            }
            if (key == 259) {
                if (!GuiScreen.activeStringSetting.input.isEmpty()) {
                    GuiScreen.activeStringSetting.input = GuiScreen.activeStringSetting.input.substring(0, GuiScreen.activeStringSetting.input.length() - 1);
                    if (Vesence.get.configManager != null) Vesence.get.configManager.autoSave();
                }
                return true;
            }
        }

        if (!CompactGuiScreen.searchActive) return false;

        boolean ctrl = ClipboardUtil.isCtrl(modifiers);
        if (ctrl && key == GLFW.GLFW_KEY_A) {
            CompactGuiScreen.searchSelectAll = !CompactGuiScreen.searchText.isEmpty();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_C) {
            if (!CompactGuiScreen.searchText.isEmpty()) ClipboardUtil.set(CompactGuiScreen.searchText);
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_X) {
            if (!CompactGuiScreen.searchText.isEmpty()) {
                ClipboardUtil.set(CompactGuiScreen.searchText);
                CompactGuiScreen.searchText = "";
                CompactGuiScreen.moduleScroll.reset();
            }
            CompactGuiScreen.searchSelectAll = false;
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_V) {
            String base = CompactGuiScreen.searchSelectAll ? "" : CompactGuiScreen.searchText;
            CompactGuiScreen.searchText = ClipboardUtil.appendFiltered(base, ClipboardUtil.get(), 64, ClipboardUtil.MODE_PRINTABLE);
            CompactGuiScreen.searchSelectAll = false;
            CompactGuiScreen.moduleScroll.reset();
            return true;
        }

        if (key == 259) {
            if (CompactGuiScreen.searchSelectAll) {
                CompactGuiScreen.searchText = "";
                CompactGuiScreen.searchSelectAll = false;
                CompactGuiScreen.moduleScroll.reset();
            } else if (!CompactGuiScreen.searchText.isEmpty()) {
                CompactGuiScreen.searchText = ctrl
                        ? ""
                        : CompactGuiScreen.searchText.substring(0, CompactGuiScreen.searchText.length() - 1);
                CompactGuiScreen.moduleScroll.reset();
            }
            return true;
        }
        if (key == 256) {
            if (CompactGuiScreen.searchActive) {
                CompactGuiScreen.searchActive = false;
                CompactGuiScreen.searchSelectAll = false;
                return true;
            }
        }

        return false;
    }
}
