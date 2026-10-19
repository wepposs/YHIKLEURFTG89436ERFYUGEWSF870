package vesence.overlay.browser;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.util.math.Vector2f;
import net.minecraft.world.phys.Vec3;
import vesence.event.render.WorldRenderEvent;
import vesence.overlay.core.FloatingWindow;

/** MCEF adapter boundary. The Chromium implementation is optional and loaded separately. */
public final class BrowserWindow extends FloatingWindow {
    private String url = "https://www.youtube.com";
    public BrowserWindow(Vec3 position) { super(position); }
    @Override public void tick() { }
    @Override public void render(WorldRenderEvent event, PoseStack matrices, float left, float right, float bottom, float top) { }
    @Override public void onClick(Vector2f localPos, int button) { }
    public String url() { return url; }
    public void navigate(String url) { if (url != null && !url.isBlank()) this.url = url; }
}
