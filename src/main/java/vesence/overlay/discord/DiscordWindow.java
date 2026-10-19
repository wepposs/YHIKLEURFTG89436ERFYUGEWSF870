package vesence.overlay.discord;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import vesence.event.render.WorldRenderEvent;
import vesence.overlay.core.FloatingWindow;

/** JDA adapter boundary. Use bot tokens only; user-token automation is not supported. */
public final class DiscordWindow extends FloatingWindow {
    public DiscordWindow(Vec3d position) { super(position); }
    @Override public void tick() { }
    @Override public void render(WorldRenderEvent event, MatrixStack matrices, float left, float right, float bottom, float top) { }
}
