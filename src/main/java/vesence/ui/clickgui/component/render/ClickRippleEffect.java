package vesence.ui.clickgui.component.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.ColorUtil;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Environment(EnvType.CLIENT)
public class ClickRippleEffect {

    private static final List<Ripple> ripples = new ArrayList<>();

    private static final float MAX_RADIUS = 22f;

    private static final long DURATION_MS = 550L;

    public static void addRipple(float x, float y) {
        ripples.add(new Ripple(x, y, System.currentTimeMillis()));
    }

    public static void render(Renderer2D renderer2D, int clientColor) {
        long now = System.currentTimeMillis();
        Iterator<Ripple> it = ripples.iterator();
        while (it.hasNext()) {
            Ripple r = it.next();
            float t = (now - r.startTime) / (float) DURATION_MS;
            if (t >= 1f) {
                it.remove();
                continue;
            }

            float eased = 1f - (float) Math.pow(1f - t, 3);

            float radius = eased * MAX_RADIUS;

            float alpha;
            if (t < 0.15f) {
                alpha = t / 0.15f;
            } else {
                alpha = 1f - ((t - 0.15f) / 0.85f);
            }
            alpha = Math.max(0f, Math.min(1f, alpha)) * 0.35f;

            int ringColor = ColorUtil.replAlpha(clientColor, (int)(alpha * 255));
            renderer2D.circle(r.x, r.y, radius, 0f, 1f, ringColor);

            float innerRadius = radius * 0.55f;
            float innerAlpha = alpha * 0.5f;
            int fillColor = ColorUtil.replAlpha(clientColor, (int)(innerAlpha * 255));
            renderer2D.circle(r.x, r.y, innerRadius, 0f, 1f, fillColor);
        }
    }

    public static void clear() {
        ripples.clear();
    }

    private static class Ripple {
        final float x, y;
        final long startTime;

        Ripple(float x, float y, long startTime) {
            this.x = x;
            this.y = y;
            this.startTime = startTime;
        }
    }
}
