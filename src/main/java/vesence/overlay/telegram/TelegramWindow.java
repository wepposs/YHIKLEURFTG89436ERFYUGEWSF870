package vesence.overlay.telegram;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec3;
import vesence.event.render.WorldRenderEvent;
import vesence.overlay.core.FloatingWindow;

/** TDLight adapter boundary; authentication is intentionally not performed automatically. */
public final class TelegramWindow extends FloatingWindow {
    public TelegramWindow(Vec3 position) { super(position); }
    @Override public void tick() { }
    @Override public void render(WorldRenderEvent event, PoseStack matrices, float left, float right, float bottom, float top) { }
}
