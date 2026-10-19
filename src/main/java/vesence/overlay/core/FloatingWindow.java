package vesence.overlay.core;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import vesence.event.render.WorldRenderEvent;

/** A small world-space window. Implementations own their content and lifecycle. */
public abstract class FloatingWindow {
    protected Vec3d worldPos;
    protected float width = 2.4f;
    protected float height = 1.35f;
    protected boolean visible = true;
    protected boolean focused;

    protected FloatingWindow(Vec3d worldPos) { this.worldPos = worldPos; }

    public abstract void tick();

    public abstract void render(WorldRenderEvent event, MatrixStack matrices, float left, float right,
                                float bottom, float top);

    public void onClick(Vec2f localPos, int button) { }
    public void onKeyPress(int keyCode, int scanCode, int modifiers) { }
    public void onCharTyped(char chr) { }
    public void close() { }

    public Vec3d position() { return worldPos; }
    public void position(Vec3d position) { this.worldPos = position; }
    public float width() { return width; }
    public float height() { return height; }
    public boolean isVisible() { return visible; }
    public void visible(boolean visible) { this.visible = visible; }
    public boolean isFocused() { return focused; }
    public void focused(boolean focused) { this.focused = focused; }
}
