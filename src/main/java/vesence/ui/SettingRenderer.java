package vesence.ui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundCategory;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;
import vesence.utils.render.text.FontRegistry;
@Environment(EnvType.CLIENT)
public class SettingRenderer {

    private static final float SETTING_ROW_H = 40f;
    private static final float SETTING_OPTION_H = 32f;
    private static final float SLIDER_ROW_H = 40f;
    private static final float IOS_PADDING = 16f;

    public static class SelectorState {
        public boolean expanded = false;
        public final Animation2 expandAnim = new Animation2();
        public final Animation2 rowHover = new Animation2();
        public final Animation2[] optHover;
        public int selectedIndex = 0;

        public SelectorState(int optionCount) {
            optHover = new Animation2[optionCount];
            for (int i = 0; i < optionCount; i++) optHover[i] = new Animation2();
        }
    }

    public static class SliderState {
        public int dragIndex = -1;
        public float dragStartX = 0f;
        public float dragStartVal = 0f;
        public final float[] volumeCache;
        public final Animation2[] hoverAnims;

        public SliderState(int count) {
            volumeCache = new float[count];
            for (int i = 0; i < count; i++) volumeCache[i] = 1f;
            hoverAnims = new Animation2[count];
            for (int i = 0; i < count; i++) hoverAnims[i] = new Animation2();
        }
    }

    public static class ToggleState {
        public boolean value = false;
        public final Animation2 anim = new Animation2();
        public boolean dragging = false;
        public float dragStartX = 0f;
        public float dragStartVal = 0f;
        public float dragVal = 0f;
        public final Animation2 dragScaleAnim = new Animation2();

        public ToggleState() {
            dragScaleAnim.set(1.0);
        }
    }

    public static float renderSelector(Renderer2D renderer, float cX, float cY, float cW,
                                        String label, String[] options,
                                        SelectorState state, int accentColor,
                                        float lmx, float lmy) {
        int labelColor = ColorUtil.getColor(220, 220, 220);

        state.expandAnim.update();
        float expandT = (float) state.expandAnim.get();
        state.rowHover.update();
        boolean rowHovered = lmx >= cX + IOS_PADDING && lmx <= cX + cW - IOS_PADDING
                && lmy >= cY && lmy <= cY + SETTING_ROW_H + (state.expanded ? options.length * SETTING_OPTION_H * expandT : 0);
        state.rowHover.run(rowHovered ? 1.0 : 0.0, 0.25, Easings.CUBIC_OUT);
        float rowHT = (float) state.rowHover.get();

        renderer.text(FontRegistry.SF_MEDIUM, cX + IOS_PADDING, cY + SETTING_ROW_H / 2f + 5, 27, label, labelColor);

        float selW = 80f;
        float selBaseH = SETTING_ROW_H - 8f;
        float selExpandH = options.length * SETTING_OPTION_H * expandT;
        float selTotalH = selBaseH + selExpandH;
        float selX = cX + cW - IOS_PADDING - selW;
        float selY = cY + 4f;
        int selBg = ColorUtil.getColor(35);
        renderer.rect(selX, selY, selW, selTotalH, 4, selBg);
        renderer.text(FontRegistry.SF_MEDIUM, selX + 10, selY + selBaseH / 2f + 4, 22, options[state.selectedIndex], labelColor);
        renderer.textRight(FontRegistry.ICON, selX + selW - 6, selY + selBaseH / 2f + 6, 22, "s",
                ColorUtil.overCol(ColorUtil.replAlpha(-1, 80), ColorUtil.replAlpha(-1, 220), rowHT));

        if (expandT > 0.01f) {
            float optStartY = selY + selBaseH;
            for (int i = 0; i < options.length; i++) {
                float optY = optStartY + i * SETTING_OPTION_H * expandT;
                float optH = SETTING_OPTION_H * expandT;

                boolean optHovered = lmx >= selX && lmx <= selX + selW && lmy >= optY && lmy <= optY + optH;
                state.optHover[i].update();
                state.optHover[i].run(optHovered ? 1.0 : 0.0, 0.2, Easings.CUBIC_OUT);
                float optHT = (float) state.optHover[i].get();

                boolean isSelected = i == state.selectedIndex;
                int optLabelColor = isSelected ? accentColor : labelColor;
                if (optHT > 0.01f || isSelected) {
                    int optBg = ColorUtil.replAlpha(accentColor, (int)(255 * optHT));
                    if (isSelected) optBg = ColorUtil.replAlpha(accentColor, 125);
                    int optBg2 = ColorUtil.replAlpha(accentColor, (int)(25 * optHT));
                    if (isSelected) optBg2 = ColorUtil.replAlpha(accentColor, 35);
                    renderer.rect(selX + 2, optY + 1, selW - 4, optH - 2, 3, ColorUtil.overCol(optBg, optBg2, 1.0f));
                }
                renderer.text(FontRegistry.SF_MEDIUM, selX + 10, optY + optH / 2f + 4, 22, options[i], optLabelColor);
                if (isSelected) renderer.textRight(FontRegistry.ICON, selX + selW - 8, optY + optH / 2f + 6, 18, "q", accentColor);
            }
        }

        return SETTING_ROW_H + options.length * SETTING_OPTION_H * expandT;
    }

    public static float renderSlider(Renderer2D renderer, float cX, float cY, float cW,
                                      String label, float value, int index,
                                      SliderState state, int accentColor,
                                      float lmx, float lmy) {
        int labelColor = ColorUtil.getColor(220, 220, 220);
        int subLabelColor = ColorUtil.getColor(160, 160, 165);
        MinecraftClient mc = MinecraftClient.getInstance();

        float vol = state.volumeCache[index];
        if (mc != null && state.dragIndex != index) {
            try {
                float mcVol = mc.options.getCategorySoundVolume(SoundCategory.values()[index]);
                if (mcVol != state.volumeCache[index]) {
                    state.volumeCache[index] = mcVol;
                }
            } catch (Exception ignored) {}
            vol = state.volumeCache[index];
        }
        if (state.dragIndex == index) {
            float sliderX = cX + IOS_PADDING + 160;
            float sliderW = cW - IOS_PADDING * 2 - 200;
            vol = Math.max(0f, Math.min(1f, (lmx - sliderX) / Math.max(1f, sliderW)));
            state.volumeCache[index] = vol;
            if (mc != null) {
                try { mc.options.getSoundVolumeOption(SoundCategory.values()[index]).setValue((double) vol); } catch (Exception ignored) {}
            }
        }

        state.hoverAnims[index].update();
        boolean sliderHovered = lmx >= cX + IOS_PADDING && lmx <= cX + cW - IOS_PADDING
                && lmy >= cY && lmy <= cY + SLIDER_ROW_H;
        state.hoverAnims[index].run(sliderHovered ? 1.0 : 0.0, 0.2, Easings.CUBIC_OUT);
        float slHT = (float) state.hoverAnims[index].get();

        renderer.text(FontRegistry.SF_MEDIUM, cX + IOS_PADDING, cY + SLIDER_ROW_H / 2f + 5, 22, label,
                ColorUtil.overCol(subLabelColor, labelColor, slHT));

        float sliderX = cX + IOS_PADDING + 160;
        float sliderW = cW - IOS_PADDING * 2 - 200;
        float sliderY = cY + SLIDER_ROW_H / 2f - 3f;
        float sliderTrackH = 6f;
        float sliderR = sliderTrackH / 2f;

        int trackOff = ColorUtil.replAlpha(ColorUtil.getColor(40, 40, 45), 100);
        renderer.rect(sliderX, sliderY, sliderW, sliderTrackH, sliderR, trackOff);

        if (vol > 0.001f) {
            int fillColor = ColorUtil.overCol(trackOff, accentColor, 0.6f);
            float fillW = sliderW * vol;
            renderer.rect(sliderX, sliderY, Math.max(sliderTrackH, fillW), sliderTrackH, sliderR, fillColor);
        }

        float handleX = sliderX + sliderW * vol;
        float handleY = cY + SLIDER_ROW_H / 2f;
        float handleR = 5f * (1f + 0.2f * slHT);
        int handleColor = ColorUtil.overCol(-1, accentColor, 0.2f + 0.8f * slHT);
        renderer.circle(handleX, handleY, handleR + 1.5f, 0f, 1f, ColorUtil.replAlpha(ColorUtil.getColor(0, 0, 0), 25));
        renderer.circle(handleX, handleY, handleR, 0f, 1f, handleColor);

        String pctStr = Math.round(vol * 100) + "%";
        renderer.textRight(FontRegistry.SF_MEDIUM, cX + cW - IOS_PADDING, cY + SLIDER_ROW_H / 2f + 5, 22, pctStr, subLabelColor);

        return SLIDER_ROW_H;
    }

    public static float renderToggle(Renderer2D renderer, float cX, float cY, float cW,
                                      String label, ToggleState state, int accentColor,
                                      float lmx, float lmy) {
        int labelColor = ColorUtil.getColor(220, 220, 220);
        renderer.text(FontRegistry.SF_MEDIUM, cX + IOS_PADDING, cY + SETTING_ROW_H / 2f + 5, 28, label, labelColor);

        float toggleW = 42;
        float toggleH = 20;
        float toggleX = cX + cW - IOS_PADDING - toggleW;
        float toggleY = cY + SETTING_ROW_H / 2f - toggleH / 2f;
        float toggleR = toggleH / 2f;

        if (state.dragging) {
            float dragDX = (lmx - state.dragStartX) / (toggleW * 0.5f);
            state.dragVal = Math.max(-0.03f, Math.min(1.03f, state.dragStartVal + dragDX));
            state.anim.set(state.dragVal);
        }
        state.dragScaleAnim.update();
        float dragScale = (float) state.dragScaleAnim.get();

        float toggleProgress = state.dragging ? state.dragVal : (float) state.anim.get();
        int trackOff = ColorUtil.replAlpha(ColorUtil.getColor(40, 40, 45), 120);
        int trackColor = ColorUtil.overCol(trackOff, accentColor, toggleProgress);
        renderer.rect(toggleX, toggleY, toggleW, toggleH, toggleR, trackColor);

        float handleBaseH = 15;
        float handleBaseW = handleBaseH * 1.3f;
        float handleH = handleBaseH * dragScale;
        float handleW = handleBaseW * dragScale;
        float baseTravel = toggleW - handleBaseW - 5;
        float handleCenterX = toggleX + 2f + handleBaseW / 2f + toggleProgress * baseTravel;
        float handleX = handleCenterX - handleW / 2f;
        float handleY = toggleY + (toggleH - handleH) / 2f;
        renderer.rect(handleX, handleY, handleW, handleH, handleH / 2f, -1);

        return SETTING_ROW_H;
    }

    public static boolean clickSelector(float lx, float sly, float selX, float selY,
                                         float selW, float selBaseH, float expandH,
                                         SelectorState state, String[] options,
                                         Runnable onSelect) {
        if (lx >= selX && lx <= selX + selW && sly >= selY && sly <= selY + selBaseH + expandH) {
            if (sly < selY + selBaseH) {
                state.expanded = !state.expanded;
                if (state.expanded) state.expandAnim.run(1.0, 0.35, Easings.CUBIC_OUT);
                else state.expandAnim.run(0.0, 0.25, Easings.CUBIC_IN);
            } else if ((float) state.expandAnim.get() > 0.5f) {
                float optStartY = selY + selBaseH;
                for (int i = 0; i < options.length; i++) {
                    float optY = optStartY + i * SETTING_OPTION_H;
                    if (sly >= optY && sly <= optY + SETTING_OPTION_H) {
                        if (i != state.selectedIndex) {
                            state.selectedIndex = i;
                            if (onSelect != null) onSelect.run();
                        }
                        state.expanded = false;
                        state.expandAnim.run(0.0, 0.25, Easings.CUBIC_IN);
                        return true;
                    }
                }
            }
            return true;
        }
        return false;
    }

    public static boolean clickSlider(float lx, float sly, float cX, float cW,
                                        float rowY, int index, SliderState state) {
        float sliderX = cX + IOS_PADDING + 160;
        float sliderW = cW - IOS_PADDING * 2 - 200;
        if (lx >= sliderX - 5 && lx <= sliderX + sliderW + 5 && sly >= rowY && sly <= rowY + SLIDER_ROW_H) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null) {
                float vol = mc.options.getCategorySoundVolume(SoundCategory.values()[index]);
                state.volumeCache[index] = vol;
                state.dragIndex = index;
                state.dragStartX = lx;
                state.dragStartVal = vol;
            }
            return true;
        }
        return false;
    }

    public static boolean clickToggle(float lx, float sly, float cX, float cW,
                                       float rowY, ToggleState state, Runnable onToggle) {
        float toggleW = 42;
        float toggleH = 20;
        float toggleX = cX + cW - IOS_PADDING - toggleW;
        float toggleY = rowY + SETTING_ROW_H / 2f - toggleH / 2f;
        if (lx >= toggleX && lx <= toggleX + toggleW && sly >= toggleY && sly <= toggleY + toggleH) {
            float handleBaseW = 15 * 1.3f;
            float baseTravel = toggleW - handleBaseW - 5;
            float toggleProgress = (float) state.anim.get();
            float handleCenterX = toggleX + 2f + handleBaseW / 2f + toggleProgress * baseTravel;
            float handleX = handleCenterX - handleBaseW / 2f;
            float handleY = toggleY + (toggleH - 15) / 2f;
            if (lx >= handleX && lx <= handleX + handleBaseW && sly >= handleY && sly <= handleY + 15) {
                state.dragging = true;
                state.dragStartX = lx;
                state.dragStartVal = state.value ? 1.0f : 0.0f;
                state.dragVal = state.dragStartVal;
                state.dragScaleAnim.run(1.15, 0.35, Easings.CUBIC_OUT);
            } else {
                state.value = !state.value;
                state.anim.run(state.value ? 1.0 : 0.0, 0.35, Easings.CUBIC_OUT);
                state.dragScaleAnim.set(0.5);
                state.dragScaleAnim.run(1.0, 0.35, Easings.CUBIC_OUT);
                if (onToggle != null) onToggle.run();
            }
            return true;
        }
        return false;
    }

    public static void releaseToggle(ToggleState state, Runnable onToggle) {
        if (state.dragging) {
            state.dragging = false;
            int nearest = Math.round(state.dragVal);
            boolean newVal = nearest >= 1;
            boolean changed = newVal != state.value;
            state.value = newVal;
            state.anim.set(state.dragVal);
            state.anim.run(state.value ? 1.0 : 0.0, 0.35, Easings.CUBIC_OUT);
            if (changed && onToggle != null) onToggle.run();
            state.dragScaleAnim.run(1.0, 0.35, Easings.CUBIC_OUT);
        }
    }
}
