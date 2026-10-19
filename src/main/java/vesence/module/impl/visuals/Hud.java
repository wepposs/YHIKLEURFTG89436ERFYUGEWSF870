package vesence.module.impl.visuals;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import org.lwjgl.glfw.GLFW;
import vesence.event.EventInit;
import vesence.event.render.EventScreen;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.impl.*;
import vesence.module.impl.misc.ClickGui;
import vesence.module.impl.visuals.elements.*;
import vesence.module.impl.visuals.elements.modern.*;
import vesence.utils.notifications.Notifications;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;
import vesence.utils.render.text.FontObject;
import vesence.utils.render.text.FontRegistry;

import java.util.ArrayList;
import java.util.List;

@IModule(name = "Hud", description = "Выводит разную информацию на экран", category = Category.VISUALS, bind = 0)
public class Hud extends Module {

    public final MultiBooleanSetting elements = new MultiBooleanSetting("Элементы",
            new BooleanSetting("Watermark", true),
            new BooleanSetting("Informations", true),
            new BooleanSetting("Keybinds", true),
            new BooleanSetting("Target Hud", true),
            new BooleanSetting("Potions List", true),
            new BooleanSetting("Cooldowns", true),
            new BooleanSetting("Staff List", true),
            new BooleanSetting("Hud Binds Assist", true),
            new BooleanSetting("Notifications", true),
            new BooleanSetting("Scoreboard", true),
            new BooleanSetting("\u0418\u0432\u0435\u043d\u0442\u044b", true)
    );

    private final BooleanSetting mentionAlert = Notifications.mentionAlert;
    public static final BooleanSetting contour = new BooleanSetting("Контур", false);

    // Watermark options are regular HUD settings and therefore appear in the GUI.
    public final BooleanSetting watermarkFps = new BooleanSetting("FPS", true);
    public final BooleanSetting watermarkBps = new BooleanSetting("BPS", true);
    public final BooleanSetting watermarkPing = new BooleanSetting("Ping", false);
    public final BooleanSetting watermarkNickname = new BooleanSetting("Nick", true);
    public final BooleanSetting watermarkCoordinates = new BooleanSetting("Coordinates", true);
    public final BooleanSetting watermarkTime = new BooleanSetting("Time", true);
    public final BooleanSetting watermarkAnimated = new BooleanSetting("Animated", true);

    private final List<HudElement> defaultElements = new ArrayList<>();
    private final List<HudElement> modernElements = new ArrayList<>();
    private boolean prevLeftDown  = false;
    private boolean prevRightDown = false;

    private final Animation2 gridAnim = new Animation2();

    private final boolean[] prevArrow = new boolean[4];
    private final long[] arrowRepeatAt = new long[4];
    private boolean nudgeDirty = false;
    private HudElement lastInteracted = null;

    private float resetBtnX, resetBtnY, resetBtnW, resetBtnH;
    private boolean resetBtnVisible = false;
    private final Animation2 resetBtnHover = new Animation2();

    private float panelAnimX = Float.NaN;
    private float panelAnimY = Float.NaN;
    private int panelAnchorIndex = 0;
    private long panelLastTime = 0L;

    private boolean switchInit = false;
    private boolean lastModern = false;
    private long switchStart = 0L;
    private static final float SWITCH_DURATION = 0.4f;
    private static final float SWITCH_STAGGER = 0.05f;

    public Hud() {
        this.hiddenFromGui = true;

        defaultElements.add(new WatermarkElement());
        defaultElements.add(new CoordsElement());
        defaultElements.add(new KeybindsElement());
        defaultElements.add(new TargetHudElement());
        defaultElements.add(new PotionsElement());
        defaultElements.add(new CooldownsElement());
        defaultElements.add(new StaffListElement());
        defaultElements.add(new AssistElement());
        defaultElements.add(new NotificationsElement());
        defaultElements.add(new ScoreboardElement());
        defaultElements.add(new EventsElement());

        modernElements.add(new ModernWatermarkElement());
        modernElements.add(new ModernCoordsElement());
        modernElements.add(new ModernKeybindsElement());
        modernElements.add(new ModernTargetHudElement());
        modernElements.add(new ModernPotionsElement());
        modernElements.add(new ModernCooldownsElement());
        modernElements.add(new ModernStaffListElement());
        modernElements.add(new ModernAssistElement());
        modernElements.add(new ModernNotificationsElement());
        modernElements.add(new ModernScoreboardElement());
        modernElements.add(new ModernEventsElement());

        for (HudElement modern : modernElements) {
            for (HudElement def : defaultElements) {
                if (def.name.equals(modern.name)) {
                    modern.scaleSetting = def.scaleSetting;
                    break;
                }
            }
        }

        this.addSettings(
                elements,
                watermarkFps,
                watermarkBps,
                watermarkPing,
                watermarkNickname,
                watermarkCoordinates,
                watermarkTime,
                watermarkAnimated
        );

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!this.enable || !mentionAlert.get() || mc.player == null) return;
            String text = message.getString();
            String myName = mc.player.getName().getString();
            if (text.contains(myName)) {
                Notifications.addMention();
            }
        });
    }

    public List<HudElement> getHudElements() {
        return ClickGui.isModern() ? modernElements : defaultElements;
    }

    public List<HudElement> getAllHudElements() {
        List<HudElement> all = new ArrayList<>(defaultElements);
        all.addAll(modernElements);
        return all;
    }

    public void syncInactivePositions() {
        List<HudElement> active = getHudElements();
        List<HudElement> inactive = ClickGui.isModern() ? defaultElements : modernElements;
        for (HudElement a : active) {
            for (HudElement b : inactive) {
                if (b.name.equals(a.name)) {
                    b.x = a.x;
                    b.y = a.y;
                    b.targetX = a.targetX;
                    b.targetY = a.targetY;
                    b.targetInitialized = a.targetInitialized;
                    break;
                }
            }
        }
    }

    public boolean isWatermarkFpsEnabled() {
        return watermarkFps.get();
    }

    public boolean isWatermarkBpsEnabled() {
        return watermarkBps.get();
    }

    public boolean isWatermarkPingEnabled() {
        return watermarkPing.get();
    }

    public boolean isWatermarkNicknameEnabled() {
        return watermarkNickname.get();
    }

    public boolean isWatermarkCoordinatesEnabled() {
        return watermarkCoordinates.get();
    }

    public boolean isWatermarkTimeEnabled() {
        return watermarkTime.get();
    }

    public boolean isWatermarkAnimatedEnabled() {
        return watermarkAnimated.get();
    }

    public boolean isElementEnabled(String name) {
        return elements.get(name);
    }

    public void setElementEnabled(String name, boolean enabled) {
        for (BooleanSetting bs : elements.settings) {
            if (bs.name.equals(name)) {
                bs.set(enabled);
                return;
            }
        }
    }

    public List<String> getElementNames() {
        List<String> names = new ArrayList<>();
        for (BooleanSetting bs : elements.settings) {
            names.add(bs.name);
        }
        return names;
    }

    @EventInit
    public void onRender(EventScreen event) {
        if (!this.enable) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        boolean isGuiOpen  = mc.currentScreen != null;
        boolean isChatOpen = mc.currentScreen instanceof ChatScreen;

        double mouseX = 0, mouseY = 0;
        boolean leftDown  = false;
        boolean rightDown = false;
        boolean altDown   = false;
        boolean shiftDown = false;

        if (isGuiOpen) {
            long handle = mc.getWindow().getHandle();
            double[] mx = new double[1], my = new double[1];
            GLFW.glfwGetCursorPos(handle, mx, my);
            leftDown  = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT)  == GLFW.GLFW_PRESS;
            rightDown = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
            altDown   = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_ALT)   == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_ALT)  == GLFW.GLFW_PRESS;
            shiftDown = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT)== GLFW.GLFW_PRESS;

            double ratioX = (double) event.viewportWidth()  / mc.getWindow().getWidth();
            double ratioY = (double) event.viewportHeight() / mc.getWindow().getHeight();
            mouseX = mx[0] * ratioX;
            mouseY = my[0] * ratioY;
        }

        Renderer2D renderer = event.renderer();
        FontObject  font    = event.defaultFont();

        boolean modernNow = ClickGui.isModern();
        if (!switchInit) {
            switchInit = true;
            lastModern = modernNow;
        } else if (modernNow != lastModern) {
            lastModern = modernNow;
            switchStart = System.currentTimeMillis();
        }
        float switchElapsed = (System.currentTimeMillis() - switchStart) / 1000f;

        int screenW = event.viewportWidth();
        int screenH = event.viewportHeight();

        gridAnim.update();
        gridAnim.run(isChatOpen ? 1.0 : 0.0, 0.2, Easings.CUBIC_OUT);
        float gridT = (float) gridAnim.get();
        if (gridT < 0.005f) {
            panelAnimX = Float.NaN;
            panelAnimY = Float.NaN;
            panelLastTime = 0L;
        }
        if (gridT > 0.005f && ClickGui.hudGrid.get()) {
            renderGrid(renderer, screenW, screenH, gridT);
        }

        resetBtnVisible = false;
        if (gridT > 0.005f && ClickGui.hudEditHints.get()) {
            renderEditHelper(renderer, font, screenW, screenH, gridT, mouseX, mouseY);
        }

        boolean leftJustPressed  = leftDown  && !prevLeftDown;
        boolean rightJustPressed = rightDown && !prevRightDown;

        boolean anyElementDragging = false;
        for (HudElement element : getHudElements()) {
            if (element.isDragging()) { anyElementDragging = true; break; }
        }

        if (isChatOpen && elements.get("Notifications")) {
            Notifications.showExample();
        } else {
            Notifications.hideExample();
        }

        HudElement hovered = null;
        if (isChatOpen) {
            List<HudElement> list = getHudElements();
            for (int i = list.size() - 1; i >= 0; i--) {
                HudElement el = list.get(i);
                if (!elements.get(el.name)) continue;
                if (el.isHovered(mouseX, mouseY, renderer, font)) { hovered = el; break; }
            }
        }

        boolean consumedClick = false;
        if (isChatOpen && leftJustPressed && resetBtnVisible
                && mouseX >= resetBtnX && mouseX <= resetBtnX + resetBtnW
                && mouseY >= resetBtnY && mouseY <= resetBtnY + resetBtnH) {
            resetAllPositions();
            vesence.utils.render.utils.SoundUtil.playUi("cb_off", 0.45f, 60L);
            consumedClick = true;
        }

        if (isChatOpen && leftDown && leftJustPressed && !anyElementDragging && !consumedClick) {
            List<HudElement> list = getHudElements();
            for (int i = list.size() - 1; i >= 0; i--) {
                HudElement el = list.get(i);
                if (!elements.get(el.name)) continue;
                if (el.scaleSetting.sliding || el.isDragging()) continue;
                if (el.isHovered(mouseX, mouseY, renderer, font)) {
                    el.onMousePress(mouseX, mouseY, renderer, font);
                    if (el.isDragging()) {
                        anyElementDragging = true;
                        lastInteracted = el;
                        list.remove(i);
                        list.add(el);
                    }
                    break;
                }
            }
        }

        if (isChatOpen && rightJustPressed && hovered != null) {
            hovered.resetPosition();
            lastInteracted = hovered;
            vesence.utils.render.utils.SoundUtil.playUi("cb_off", 0.4f, 60L);
            autoSaveConfig();
        }

        if (isChatOpen) {
            handleArrowNudge(hovered, shiftDown, screenW, screenH, renderer, font);
        }

        List<HudElement> renderList = getHudElements();

        List<HudElement> snapTargets = new ArrayList<>();
        for (HudElement element : renderList) {
            if (elements.get(element.name)) snapTargets.add(element);
        }

        if (isChatOpen) {
            for (HudElement element : renderList) {
                if (!leftDown) {
                    element.onMouseRelease();
                }
                element.snapSiblings  = snapTargets;
                element.dragAltHeld   = altDown;
                element.dragShiftHeld = shiftDown;
                element.onMouseMove(mouseX, mouseY, renderer, font, screenW, screenH);
            }
        } else {
            for (HudElement element : renderList) {
                if (element.isDragging()) {
                    element.onMouseRelease();
                }
                element.snapSiblings  = snapTargets;
                element.dragAltHeld   = false;
                element.dragShiftHeld = false;
                element.onMouseMove(mouseX, mouseY, renderer, font, screenW, screenH);
            }
        }

        for (HudElement element : renderList) {
            element.guideAnim.update();
            element.guideAnim.run((isChatOpen && element.isDragging()) ? 1.0 : 0.0, 0.15, Easings.CUBIC_OUT);
            float guideProgress = (float) element.guideAnim.get();
            if (isChatOpen && guideProgress > 0.01f) {
                renderDragGuides(renderer, font, element, event.viewportWidth(), event.viewportHeight(), guideProgress);
            }
        }

        for (int idx = 0; idx < renderList.size(); idx++) {
            HudElement element = renderList.get(idx);
            boolean enabled = elements.get(element.name);

            element.alphaAnim.update();
            element.alphaAnim.run(enabled ? 1.0 : 0.0, 0.2, Easings.CUBIC_OUT);
            float elemAlpha = (float) element.alphaAnim.get();
            if (elemAlpha < 0.005f) continue;

            float switchProg = Math.max(0f, Math.min(1f, (switchElapsed - idx * SWITCH_STAGGER) / SWITCH_DURATION));
            float switchFade = 1f - (float) Math.pow(1f - switchProg, 3f);
            float switchScale = 0.55f + 0.45f * easeOutBack(switchProg);
            float switchSlide = (1f - switchFade) * 26f;
            float switchTilt = (1f - switchFade) * ((idx & 1) == 0 ? 7f : -7f);
            float baseY = element.y;
            element.y = baseY + switchSlide;

            float scale;
            float rawScale = element.scaleSetting.get().floatValue();
            if (element.scaleSetting.sliding) {
                element.scaleAnim.set(rawScale);
                scale = rawScale;
            } else {
                element.scaleAnim.update();
                element.scaleAnim.run(rawScale, 0.25, Easings.SINE_OUT, true);
                scale = (float) element.scaleAnim.get();
            }
            boolean pushedOuterScale = false;
            boolean pushedAlpha = false;
            boolean pushedInnerScale = false;
            boolean pushedRotation = false;
            boolean pushedContextMatrix = false;

            try {
                renderer.pushScale(scale, element.x, element.y);
                pushedOuterScale = true;
                renderer.pushAlpha(elemAlpha * switchFade);
                pushedAlpha = true;

                element.updateInteraction(mouseX, mouseY, renderer, font, element.isDragging());
                float expand = element.getExpand() * switchScale;
                boolean transforming = Math.abs(expand - 1f) > 0.0005f || Math.abs(switchTilt) > 0.02f;

                float cw = element.getEffectiveWidth(renderer, font);
                float ch = element.getHeight(renderer, font);
                float centerXfb = element.x + cw / 2f;
                float centerYfb = element.y + ch / 2f;

                if (transforming) {
                    renderer.pushScale(expand, expand, centerXfb, centerYfb);
                    pushedInnerScale = true;

                    if (Math.abs(switchTilt) > 0.02f) {
                        renderer.pushRotationAround(switchTilt, centerXfb, centerYfb);
                        pushedRotation = true;
                    }

                    if (ClickGui.isModern()) {
                        float guiScale = (float) vesence.utils.other.Mathf.getScaleFactor();
                        org.joml.Matrix3x2fStack ctxMatrices = event.drawContext().getMatrices();
                        ctxMatrices.pushMatrix();
                        pushedContextMatrix = true;
                        float cgx = centerXfb / guiScale;
                        float cgy = centerYfb / guiScale;
                        ctxMatrices.translate(cgx, cgy);
                        if (Math.abs(switchTilt) > 0.02f) {
                            ctxMatrices.rotate((float) Math.toRadians(switchTilt));
                        }
                        ctxMatrices.scale(expand, expand);
                        ctxMatrices.translate(-cgx, -cgy);
                    }
                }

                element.render(renderer, font, event.viewportWidth(), event.viewportHeight(), event.drawContext());
            } catch (Exception e) {
                System.err.println("[weppos] HUD element render error (" + element.name + " / " + element.getClass().getSimpleName() + "): " + e.getMessage());
                e.printStackTrace();
            } finally {
                if (pushedContextMatrix) {
                    try {
                        event.drawContext().getMatrices().popMatrix();
                    } catch (Exception ignored) {
                    }
                }
                if (pushedRotation) {
                    renderer.popRotation();
                }
                if (pushedInnerScale) {
                    renderer.popScale();
                }
                if (pushedAlpha) {
                    renderer.popAlpha();
                }
                if (pushedOuterScale) {
                    renderer.popScale();
                }
                element.y = baseY;
            }
        }

        if (isChatOpen && ClickGui.hudCoords.get()) {
            for (HudElement element : renderList) {
                float guideProgress = (float) element.guideAnim.get();
                if (guideProgress > 0.01f) {
                    renderDragReadout(renderer, font, element, screenW, screenH, guideProgress);
                }
            }
        }

        if (isGuiOpen) {
            prevLeftDown  = leftDown;
            prevRightDown = rightDown;
        }

        syncInactivePositions();
    }

    private static float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        float u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }

    private void renderGrid(Renderer2D renderer, int screenW, int screenH, float animT) {
        float gridSize = (float) ClickGui.hudGridDensity.current;
        if (gridSize < 2f) gridSize = 2f;

        int minorColor = ColorUtil.getColor(255, 255, 255, (int) (13 * animT));
        int majorColor = ColorUtil.getColor(255, 255, 255, (int) (28 * animT));
        float thickness = 0.5f;

        int col = 0;
        for (float gx = 0; gx <= screenW; gx += gridSize, col++) {
            renderer.rect(gx, 0, thickness, screenH, (col % 5 == 0) ? majorColor : minorColor);
        }

        int row = 0;
        for (float gy = 0; gy <= screenH; gy += gridSize, row++) {
            renderer.rect(0, gy, screenW, thickness, (row % 5 == 0) ? majorColor : minorColor);
        }
    }

    private void drawGuideSegment(Renderer2D renderer, boolean vertical, float pos,
                                  float start, float end, float thickness, int color, boolean dashed) {
        if (end < start) { float t = start; start = end; end = t; }
        float length = end - start;
        if (length <= 0f) return;

        if (!dashed) {
            if (vertical) renderer.rect(pos - thickness / 2f, start, thickness, length, color);
            else          renderer.rect(start, pos - thickness / 2f, length, thickness, color);
            return;
        }

        float dash = 6f, gap = 5f;
        for (float p = start; p < end; p += dash + gap) {
            float seg = Math.min(dash, end - p);
            if (vertical) renderer.rect(pos - thickness / 2f, p, thickness, seg, color);
            else          renderer.rect(p, pos - thickness / 2f, seg, thickness, color);
        }
    }

    private void renderDragGuides(Renderer2D renderer, FontObject font, HudElement el,
                                  int screenW, int screenH, float alpha) {
        if (!ClickGui.hudGuides.get() || !el.isDragging()) return;

        float scale = el.getScale();
        float ew = el.getEffectiveWidth(renderer, font) * scale;
        float eh = el.getHeight(renderer, font) * scale;
        float op = (float) ClickGui.hudGuideOpacity.current;
        float previewX = el.getDragPreviewX();
        float previewY = el.getDragPreviewY();
        float guideAlpha = alpha * op;
        float time = System.currentTimeMillis() / 1000f;
        float vibration = (float) Math.sin(time * 18f) * 0.65f;
        float pulse = 0.5f + 0.5f * (float) Math.sin(time * 5.5f);
        float corner = Math.min(12f, Math.min(ew, eh) / 3f);

        float sourcePad = 6f + pulse * 3f;
        renderer.blur(el.x - sourcePad, el.y - sourcePad, ew + sourcePad * 2f, eh + sourcePad * 2f,
                10f + pulse * 5f, corner + sourcePad, 0.18f * guideAlpha);
        renderer.shadow(el.x - 2f + vibration, el.y - 2f - vibration, ew + 4f, eh + 4f, corner + 2f,
                9f + pulse * 4f, 1f,
                ColorUtil.replAlpha(-1, (int) (32 * guideAlpha)));

        drawAnimatedDashedLine(renderer, el.x, el.y, previewX, previewY, time);
        drawAnimatedDashedLine(renderer, el.x + ew, el.y, previewX + ew, previewY, time + 0.25f);
        drawAnimatedDashedLine(renderer, el.x, el.y + eh, previewX, previewY + eh, time + 0.5f);
        drawAnimatedDashedLine(renderer, el.x + ew, el.y + eh, previewX + ew, previewY + eh, time + 0.75f);

        renderer.blur(previewX, previewY, ew, eh, 9f, 0f, 0.12f * guideAlpha);
        renderer.rect(previewX, previewY, ew, eh, 0f, ColorUtil.replAlpha(ColorUtil.BLACK, (int) (58 * guideAlpha)));
        renderer.shadow(previewX, previewY, ew, eh, 0f, 8f, 1.2f,
                ColorUtil.getColor(255, 255, 255, 42));
        renderer.rectOutline(previewX, previewY, ew, eh, 0f, ColorUtil.getColor(255, 255, 255, 210), 1.2f);
        renderer.rectOutline(previewX + 4f, previewY + 4f, ew - 8f, eh - 8f, 0f,
                ColorUtil.getColor(255, 255, 255, 92), 0.8f);
    }

    private void drawAnimatedDashedLine(Renderer2D renderer, float x1, float y1, float x2, float y2,
                                        float time) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.hypot(dx, dy);
        if (length < 1f) return;

        float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
        float dash = 7f;
        float gap = 6f;
        float flow = (time * 20f) % (dash + gap);
        int gradientStart = ColorUtil.getColor(255, 255, 255, 255);
        int gradientEnd = ColorUtil.getColor(255, 255, 255, 0);
        int glowStart = ColorUtil.getColor(255, 255, 255, 76);
        int glowEnd = ColorUtil.getColor(255, 255, 255, 0);

        for (float p = -flow; p < length; p += dash + gap) {
            float start = Math.max(0f, p);
            float end = Math.min(length, p + dash);
            if (end <= start) continue;

            float progress = (start + end) * 0.5f / length;
            float centerX = x1 + dx * ((start + end) * 0.5f / length);
            float centerY = y1 + dy * ((start + end) * 0.5f / length);

            renderer.pushRotationAround(angle, centerX, centerY);
            renderer.animatedGradientBar(centerX - (end - start) / 2f, centerY - 1.9f, end - start, 3.8f,
                    glowStart, glowEnd, 5.6f, progress * 7f);
            renderer.animatedGradientBar(centerX - (end - start) / 2f, centerY - 0.8f, end - start, 1.6f,
                    gradientStart, gradientEnd, 5.6f, progress * 7f);
            renderer.popRotation();
        }
    }

    private void renderDragReadout(Renderer2D renderer, FontObject font, HudElement el,
                                   int screenW, int screenH, float alpha) {
        float scale = el.getScale();
        float ew = el.getEffectiveWidth(renderer, font) * scale;
        float eh = el.getHeight(renderer, font) * scale;
        float readX = el.isDragging() ? el.getDragPreviewX() : el.x;
        float readY = el.isDragging() ? el.getDragPreviewY() : el.y;
        float cx = readX + ew / 2f;

        int theme  = Renderer2D.ColorUtil.getClientColor();
        int semi   = ColorUtil.getColor(255, 255, 255, (int) (255 * alpha));
        int accent = ColorUtil.replAlpha(theme, (int) (255 * alpha));

        float fs = 25;
        String lblX = "x";
        String valX = String.valueOf(Math.round(readX));
        String lblY = "  y";
        String valY = String.valueOf(Math.round(readY));

        float wLX = renderer.measureText(font, lblX, fs).width;
        float wVX = renderer.measureText(font, valX, fs).width;
        float wLY = renderer.measureText(font, lblY, fs).width;
        float wVY = renderer.measureText(font, valY, fs).width;
        float th  = renderer.measureText(font, lblX, fs).height;
        float total = wLX + wVX + wLY + wVY;

        float padX = 9f, padY = 5f;
        float boxW = total + padX * 2f;
        float boxH = th + padY * 2f;
        float boxX = cx - boxW / 2f;
        float boxY = readY - boxH - 8f;
        if (boxY < 3f) boxY = readY + eh + 8f;
        boxX = Math.max(3f, Math.min(boxX, screenW - boxW - 3f));

        float corner = 5;

        if (HudElement.isBlurEnabled()) {
            renderer.blur(boxX, boxY, boxW, boxH, ClickGui.blurStrengthHud.get().floatValue(), corner, alpha);
        }
        renderer.rect(boxX, boxY, boxW, boxH, corner, ColorUtil.getColor(0, 0, 0, (int) (155 * alpha)));

        float tx = boxX + padX;
        float ty = boxY + boxH / 2f + th / 2f - 1f;
        renderer.text(font, tx, ty, fs, lblX, semi);   tx += wLX;
        renderer.text(font, tx, ty, fs, valX, accent); tx += wVX;
        renderer.text(font, tx, ty, fs, lblY, semi);   tx += wLY;
        renderer.text(font, tx, ty, fs, valY, accent);
    }

    private void renderEditHelper(Renderer2D renderer, FontObject font, int screenW, int screenH,
                                  float alpha, double mouseX, double mouseY) {
        String[] keys = { "ЛКМ", "ПКМ", "Стрелки", "Shift+Стрелки", "Alt", "Shift" };
        String[] acts = { "перетащить", "сброс элемента", "сдвиг 1px", "сдвиг 10px", "свободно", "без сетки" };

        FontObject uiFont = FontRegistry.SF_SEMI;
        String title = "Редактор HUD";
        String subtitle = "Управление элементами";
        float titleFs = 27f;
        float subtitleFs = 18f;
        float keyFs = 20f;
        float actionFs = 22f;
        float rowH = 27f;
        float panelPad = 8f;
        float headerH = 42f;
        float headerGap = 7f;
        float keyGap = 10f;

        float maxKeyW = 0f, maxActW = 0f;
        for (int i = 0; i < keys.length; i++) {
            maxKeyW = Math.max(maxKeyW, renderer.measureText(uiFont, keys[i], keyFs, -0.1f).width);
            maxActW = Math.max(maxActW, renderer.measureText(uiFont, acts[i], actionFs, -0.1f).width);
        }

        float keyW = Math.max(82f, maxKeyW + 20f);
        float titleW = renderer.measureText(uiFont, title, titleFs, -0.1f).width + panelPad * 4f;
        float panelW = Math.max(248f, Math.max(titleW, panelPad * 2f + keyW + keyGap + maxActW + 16f));
        float panelH = panelPad * 2f + headerH + headerGap + keys.length * rowH;

        float btnH = 32f, btnGap = 10f;
        float totalH = panelH + btnGap + btnH;
        float blockW = panelW;

        float M = 16f;
        float rightX  = screenW - blockW - M;
        float leftX   = M;
        float centerX = (screenW - blockW) / 2f;
        float highY   = M;
        float midY    = (screenH - totalH) / 2f;
        float topY    = M;
        float bottomY = screenH - totalH - M;
        float[][] cands = {
                {rightX,  highY},
                {rightX,  midY},
                {rightX,  topY},
                {leftX,   highY},
                {leftX,   midY},
                {leftX,   topY},
                {centerX, topY},
                {rightX,  bottomY},
                {leftX,   bottomY},
                {centerX, bottomY},
        };

        HudElement dragged = null;
        for (HudElement el : getHudElements()) {
            if (el.isDragging()) { dragged = el; break; }
        }

        if (dragged == null) {
            panelAnchorIndex = 0;
        } else {
            float dscale = dragged.getScale();
            float dex = dragged.x, dey = dragged.y;
            float dew = dragged.getEffectiveWidth(renderer, font) * dscale;
            float deh = dragged.getHeight(renderer, font) * dscale;
            float cx = cands[panelAnchorIndex][0], cy = cands[panelAnchorIndex][1];
            if (rectsOverlap(cx, cy, blockW, totalH, dex - 14f, dey - 14f, dew + 28f, deh + 28f)) {
                int best = 0;
                float bestPenalty = Float.MAX_VALUE;
                for (int i = 0; i < cands.length; i++) {
                    float p = panelPenalty(cands[i][0], cands[i][1], blockW, totalH, renderer, font);
                    if (p < bestPenalty) { bestPenalty = p; best = i; }
                }
                panelAnchorIndex = best;
            }
        }

        float targetX = cands[panelAnchorIndex][0];
        float targetY = cands[panelAnchorIndex][1];

        long now = System.currentTimeMillis();
        if (panelLastTime == 0L) panelLastTime = now;
        float dt = Math.min((now - panelLastTime) / 1000f, 0.1f);
        panelLastTime = now;

        if (Float.isNaN(panelAnimX) || Float.isNaN(panelAnimY)) {
            panelAnimX = targetX;
            panelAnimY = targetY;
        } else {
            float factor = 1f - (float) Math.exp(-11f * dt);
            panelAnimX += (targetX - panelAnimX) * factor;
            panelAnimY += (targetY - panelAnimY) * factor;
        }

        float panelX = panelAnimX;
        float panelY = panelAnimY;

        int theme = Renderer2D.ColorUtil.getClientColor();
        float corner = 12f;
        int titleColor = ColorUtil.replAlpha(-1, (int) (245 * alpha));
        int subtitleColor = ColorUtil.replAlpha(-1, (int) (115 * alpha));
        int actionColor = ColorUtil.replAlpha(-1, (int) (185 * alpha));
        int keyColor = ColorUtil.replAlpha(theme, (int) (245 * alpha));
        int accent = ColorUtil.replAlpha(theme, (int) (255 * alpha));

        if (HudElement.isBlurEnabled()) {
            renderer.blur(panelX, panelY, panelW, panelH, ClickGui.blurStrengthHud.get().floatValue(), corner, alpha);
        }
        renderer.shadow(panelX, panelY, panelW, panelH, corner, 6f, 1f,
                ColorUtil.replAlpha(ColorUtil.BLACK, (int) (55 * alpha)));
        renderer.rect(panelX, panelY, panelW, panelH, corner, ColorUtil.getColor(0, 0, 0, (int) (150 * alpha)));

        float headerX = panelX + panelPad;
        float headerY = panelY + panelPad;
        float headerW = panelW - panelPad * 2f;
        renderer.rect(headerX, headerY, headerW, headerH, 6f, ColorUtil.replAlpha(-1, (int) (12 * alpha)));
        renderer.text(uiFont, headerX + 12f, headerY + 20f, titleFs, title, titleColor, -0.1f);
        renderer.text(uiFont, headerX + 12f, headerY + 34f, subtitleFs, subtitle, subtitleColor, -0.1f);
        renderer.rect(headerX + 6f, headerY + 8f, 2f, headerH - 16f, 1f, accent);

        float rowTop = headerY + headerH + headerGap;
        for (int i = 0; i < keys.length; i++) {
            float rowY = rowTop + i * rowH;
            float keyX = panelX + panelPad;
            float keyH = rowH - 4f;
            float keyY = rowY + 2f;
            renderer.rect(keyX, keyY, keyW, keyH, 5f, ColorUtil.replAlpha(-1, (int) (10 * alpha)));

            float keyTextH = renderer.measureText(uiFont, keys[i], keyFs, -0.1f).height;
            float actionTextH = renderer.measureText(uiFont, acts[i], actionFs, -0.1f).height;
            renderer.textCenter(uiFont, keyX + keyW / 2f, keyY + keyH / 2f + keyTextH / 2f - 1f,
                    keyFs, keys[i], keyColor, -0.1f);
            renderer.text(uiFont, keyX + keyW + keyGap, rowY + rowH / 2f + actionTextH / 2f - 1f,
                    actionFs, acts[i], actionColor, -0.1f);
        }

        float btnW = panelW;
        float btnX = panelX;
        float btnY = panelY + panelH + btnGap;
        boolean hoverBtn = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        resetBtnHover.update();
        resetBtnHover.run(hoverBtn ? 1.0 : 0.0, 0.15, Easings.CUBIC_OUT);
        float hb = (float) resetBtnHover.get();

        float btnCorner = 8f;
        if (HudElement.isBlurEnabled()) {
            renderer.blur(btnX, btnY, btnW, btnH, ClickGui.blurStrengthHud.get().floatValue(), btnCorner, alpha);
        }
        renderer.rect(btnX, btnY, btnW, btnH, btnCorner,
                ColorUtil.replAlpha(theme, (int) ((75 + 55 * hb) * alpha)));
        renderer.rectOutline(btnX, btnY, btnW, btnH, btnCorner,
                ColorUtil.replAlpha(theme, (int) ((145 + 45 * hb) * alpha)), 1f);

        String label = "Сбросить всё";
        float lw = renderer.measureText(uiFont, label, actionFs, -0.1f).width;
        float lh = renderer.measureText(uiFont, label, actionFs, -0.1f).height;
        renderer.text(uiFont, btnX + (btnW - lw) / 2f, btnY + btnH / 2f + lh / 2f - 1f,
                actionFs, label, titleColor, -0.1f);

        resetBtnX = btnX;
        resetBtnY = btnY;
        resetBtnW = btnW;
        resetBtnH = btnH;
        resetBtnVisible = true;
    }

    private static boolean rectsOverlap(float ax, float ay, float aw, float ah,
                                        float bx, float by, float bw, float bh) {
        return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by;
    }

    private float panelPenalty(float bx, float by, float blockW, float blockH,
                               Renderer2D renderer, FontObject font) {
        float penalty = 0f;
        for (HudElement el : getHudElements()) {
            if (!elements.get(el.name)) continue;
            float s = el.getScale();
            float ew = el.getEffectiveWidth(renderer, font) * s;
            float eh = el.getHeight(renderer, font) * s;
            float ox = Math.max(0f, Math.min(bx + blockW, el.x + ew) - Math.max(bx, el.x));
            float oy = Math.max(0f, Math.min(by + blockH, el.y + eh) - Math.max(by, el.y));
            penalty += ox * oy;
        }
        return penalty;
    }

    private void handleArrowNudge(HudElement hovered, boolean shiftDown, int screenW, int screenH,
                                  Renderer2D renderer, FontObject font) {
        HudElement target = hovered;
        if (target == null && lastInteracted != null && isElementEnabled(lastInteracted.name)) {
            target = lastInteracted;
        }

        long handle = MinecraftClient.getInstance().getWindow().getHandle();
        int[] codes = { GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN };
        float[] dxs = { -1f, 1f, 0f, 0f };
        float[] dys = { 0f, 0f, -1f, 1f };
        float step = shiftDown ? 10f : 1f;
        long now = System.currentTimeMillis();
        boolean anyDown = false;

        for (int k = 0; k < 4; k++) {
            boolean down = GLFW.glfwGetKey(handle, codes[k]) == GLFW.GLFW_PRESS;
            boolean fire = false;
            if (down) {
                anyDown = true;
                if (!prevArrow[k]) { fire = true; arrowRepeatAt[k] = now + 300; }
                else if (now >= arrowRepeatAt[k]) { fire = true; arrowRepeatAt[k] = now + 40; }
            }
            prevArrow[k] = down;
            if (fire && target != null) {
                target.nudge(dxs[k] * step, dys[k] * step, screenW, screenH, renderer, font);
                lastInteracted = target;
                nudgeDirty = true;
            }
        }

        if (!anyDown && nudgeDirty) {
            nudgeDirty = false;
            autoSaveConfig();
        }
    }

    private void resetAllPositions() {
        for (HudElement el : getAllHudElements()) {
            el.resetPosition();
        }
        autoSaveConfig();
    }

    private void autoSaveConfig() {
        if (vesence.Vesence.get != null && vesence.Vesence.get.configManager != null) {
            vesence.Vesence.get.configManager.autoSave();
        }
    }

    public boolean onKeyTyped(char c) {
        return false;
    }
}