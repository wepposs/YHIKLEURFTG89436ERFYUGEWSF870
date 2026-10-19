package vesence.overlay.discord;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec3;
import vesence.event.render.WorldRenderEvent;
import vesence.overlay.core.FloatingWindow;

/** JDA adapter boundary. Use bot tokens only; user-token automation is not supported. */
public final class DiscordWindow extends FloatingWindow {
    public DiscordWindow(Vec3 position) { super(position); }
    @Override public void tick() { }
    @Override public void render(WorldRenderEvent event, PoseStack matrices, float left, float right, float bottom, float top) { }
}
