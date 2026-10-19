package vesence.module.api.setting.impl;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.module.Theme;
import vesence.module.api.setting.Setting;
import vesence.utils.render.math.animation.anim.util.Animation2;

import java.util.HashMap;
import java.util.Map;

@Environment(EnvType.CLIENT)
public class ThemeSetting extends Setting<Theme> {

    private Theme currentTheme;
    private Theme previousTheme;
    public final Theme[] themes;
    public final Map<Theme, Animation2> themeAnimations = new HashMap<>();
    public final Animation2 transitionAnim = new Animation2();

    public ThemeSetting(String name, Theme defaultTheme) {
        super(name);
        this.themes = Theme.values();
        this.currentTheme = defaultTheme;
        this.previousTheme = defaultTheme;

        for (Theme theme : themes) {
            Animation2 anim = new Animation2();
            anim.set(theme == currentTheme ? 1.0 : 0.0);
            themeAnimations.put(theme, anim);
        }

        transitionAnim.set(1.0);
    }

    public Theme get() {
        return currentTheme;
    }

    public Theme getPrevious() {
        return previousTheme;
    }

    public void set(Theme theme) {
        if (theme != null && theme != currentTheme) {
            this.previousTheme = this.currentTheme;
            this.currentTheme = theme;
            this.transitionAnim.set(0.0);
        }
    }

    public int getColor() {
        return currentTheme.getMain().getRGB();
    }

    public float getTransition() {
        return (float) transitionAnim.get();
    }
}
