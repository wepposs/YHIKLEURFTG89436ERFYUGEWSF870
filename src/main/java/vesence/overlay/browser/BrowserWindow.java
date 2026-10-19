package vesence.overlay.browser;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import vesence.event.render.WorldRenderEvent;
import vesence.overlay.core.FloatingWindow;

/** MCEF adapter boundary. The Chromium implementation is optional and loaded separately. */
public final class BrowserWindow extends FloatingWindow {
    private String url = "https://www.youtube.com";
    public BrowserWindow(Vec3d position) { super(position); }
    @Override public void tick() { }
    @Override public void render(WorldRenderEvent event, MatrixStack matrices, float left, float right, float bottom, float top) { }
    @Override public void onClick(Vec2f localPos, int button) { }
    public String url() { return url; }
    public void navigate(String url) { if (url != null && !url.isBlank()) this.url = url; }
}
