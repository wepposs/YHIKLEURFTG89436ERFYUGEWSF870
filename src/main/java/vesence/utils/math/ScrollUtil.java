package vesence.utils.math;
import vesence.renderengine.render.Renderer2D;
import vesence.renderengine.utils.MathHelper;
public class ScrollUtil {
    private float target;
    private float scroll;
    private float max;
    private float speed = 10F;
    private boolean enabled;
    private float barHeight;
    private float fadeAlpha;
    private static final float SCROLL_MULTIPLIER = 10.0F;
    private static final float SMOOTHING = 0.08F;

    public void update() {

        if (target < max) target = max;
        if (target > 0) target = 0;
        scroll = scroll + (target - scroll) * SMOOTHING;
        float targetFade = enabled ? 1.0F : 0.0F;
        fadeAlpha = fadeAlpha + (targetFade - fadeAlpha) * 0.15F;
    }

    public void handleScroll(double scrollY) {
        if (this.enabled) {
            float wheel = (float) scrollY * speed;
            target = Math.min(Math.max(target + wheel, max), 0);
        }
    }

    public void setMax(float max, float height) {
        this.max = -max + height;
    }

    public float getScroll() {
        return scroll;
    }

    public void setScroll(float scroll) {
        this.scroll = scroll;
        this.target = scroll;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void reset() {
        this.scroll = 0;
        this.target = 0;
    }

    public void render(Renderer2D renderer2D, float x, float y, float width, float height, float alpha) {
        if (!(this.max >= 0.0F) && fadeAlpha > 0.01F) {
           float percentage = this.max != 0.0F ? this.scroll / this.max : 0.0F;
           float targetBarHeight = height - this.max / (this.max - height) * height;
           this.barHeight = MathHelper.interpolate(targetBarHeight, this.barHeight, 0.9F);
           boolean allowed = this.barHeight < height && this.barHeight > 0.0F;
           if (allowed) {
              float scrollY = y + height * percentage - this.barHeight * percentage;
              float combinedAlpha = alpha * fadeAlpha;
              int mainColor = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1),
                    (int) MathHelper.clamp(255.0F * combinedAlpha, 0.0F, 255.0F));
              int mainColor20 = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1),
                    (int) MathHelper.clamp(20.0F * combinedAlpha, 0.0F, 20.0F));
              renderer2D.rect(x, y, width, height, mainColor20);
              renderer2D.rect(x, scrollY, width, this.barHeight, 1.0F, mainColor);
           }
        }
    }
}
