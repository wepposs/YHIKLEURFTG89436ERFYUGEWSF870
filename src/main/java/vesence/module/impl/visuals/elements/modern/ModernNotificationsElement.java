package vesence.module.impl.visuals.elements.modern;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import vesence.module.impl.visuals.HudElement;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.notifications.Notifications;
import vesence.utils.render.text.FontObject;

@Environment(EnvType.CLIENT)
public class ModernNotificationsElement extends HudElement {

    private static final float DEFAULT_X = 850f;
    private static final float DEFAULT_Y = 570f;

    private int lastScreenW = 1920;
    private int lastScreenH = 1080;
    private float dragOffsetY = 0f;
    private boolean draggingY = false;

    public ModernNotificationsElement() {
        super("Notifications", DEFAULT_X, DEFAULT_Y);

        addSetting(Notifications.mentionAlert);
        addSetting(Notifications.potionAlert);
    }

    @Override
    public void render(Renderer2D renderer, FontObject font, int screenWidth, int screenHeight, GuiGraphicsExtractor ctx) {
        lastScreenW = screenWidth;
        lastScreenH = screenHeight;
        float scale = getScale();
        x = centerLeft(renderer, font, scale);
        targetX = x;
        Notifications.render(renderer, ctx, unscaledCenterX(screenWidth / 2f, scale), y, 1f);
    }

    @Override
    public float getWidth(Renderer2D renderer, FontObject font) {
        float w = Notifications.boundsWidth();
        return w > 1 ? w : 220f;
    }

    @Override
    public float getHeight(Renderer2D renderer, FontObject font) {
        float h = Notifications.boundsHeight();
        return h > 1 ? h : 40f;
    }

    @Override
    public float getEffectiveWidth(Renderer2D renderer, FontObject font) {
        return getWidth(renderer, font);
    }

    private float centerLeft(Renderer2D renderer, FontObject font, float scale) {
        return lastScreenW / 2f - getWidth(renderer, font) * scale / 2f;
    }

    /** Converts the desired screen centre into the outer HUD scale's local space. */
    private float unscaledCenterX(float screenCenterX, float scale) {
        return x + (screenCenterX - x) / Math.max(0.001f, scale);
    }

    @Override
    public boolean isHovered(double mouseX, double mouseY, Renderer2D renderer, FontObject font) {
        float s = getScale();
        float w = getWidth(renderer, font) * s;
        float h = getHeight(renderer, font) * s;
        float left = x;
        return mouseX >= left && mouseX <= left + w && mouseY >= y && mouseY <= y + h;
    }

    @Override
    public void onMousePress(double mouseX, double mouseY, Renderer2D renderer, FontObject font) {
        if (isHovered(mouseX, mouseY, renderer, font)) {
            draggingY = true;
            dragOffsetY = (float) (mouseY - y);
            setDragging(true);
            targetY = y;
            targetInitialized = true;
            beginDragPreview(x, y);
        }
    }

    @Override
    public void onMouseRelease() {
        boolean was = draggingY;
        draggingY = false;
        setDragging(false);
        endDragPreview();
        if (was && vesence.Vesence.get != null && vesence.Vesence.get.configManager != null) {
            vesence.Vesence.get.configManager.autoSave();
        }
    }

    @Override
    public void onMouseMove(double mouseX, double mouseY, Renderer2D renderer, FontObject font,
                            int screenWidth, int screenHeight) {
        lastScreenW = screenWidth;
        lastScreenH = screenHeight;

        x = centerLeft(renderer, font, getScale());
        targetX = x;

        if (!targetInitialized) {
            targetY = y;
            targetInitialized = true;
        }

        float h = getHeight(renderer, font) * getScale();
        if (draggingY) {
            float newY = (float) (mouseY - dragOffsetY);
            float gridSnap = 10f;
            newY = Math.round(newY / gridSnap) * gridSnap;
            newY = Math.max(0, Math.min(newY, screenHeight - h));
            targetY = newY;
            glideDragPreview(x, targetY);
        } else {
            y += (targetY - y) * 0.35f;
            if (Math.abs(y - targetY) < 0.4f) y = targetY;
        }
    }
}
