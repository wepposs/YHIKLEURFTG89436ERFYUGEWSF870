package vesence.overlay.telegram;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import vesence.event.render.WorldRenderEvent;
import vesence.overlay.core.FloatingWindow;

/** TDLight adapter boundary; authentication is intentionally not performed automatically. */
public final class TelegramWindow extends FloatingWindow {
    public TelegramWindow(Vec3d position) { super(position); }
    @Override public void tick() { }
    @Override public void render(WorldRenderEvent event, MatrixStack matrices, float left, float right, float bottom, float top) { }
}
