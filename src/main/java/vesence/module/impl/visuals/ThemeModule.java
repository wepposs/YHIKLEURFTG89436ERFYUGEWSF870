package vesence.module.impl.visuals;

import vesence.Vesence;
import vesence.event.EventInit;
import vesence.event.EventManager;
import vesence.event.render.EventScreen;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.impl.ThemeSetting;
import vesence.module.Theme;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;

@IModule(name = "Theme", description = "Управление темой оформления", category = Category.VISUALS, bind = 0)
public class ThemeModule extends Module {

    public final ThemeSetting theme = new ThemeSetting("Тема клиента", Theme.Blue);

    public ThemeModule() {
        this.addSettings(theme);
        this.hiddenFromGui = true;

        setState(true);
    }

    @Override
    public void onEnable() {
        super.onEnable();
    }

    @Override
    public void onDisable() {

        super.onDisable();
        this.enable = true;
        super.onEnable();
    }

    public static Theme getCurrentTheme() {
        try {
            ThemeModule tm = (ThemeModule) Vesence.get.manager.get(ThemeModule.class);
            if (tm != null && tm.theme != null) {
                return tm.theme.get();
            }
        } catch (Exception ignored) {}
        return Theme.Blue;
    }

    public static Theme getPreviousTheme() {
        try {
            ThemeModule tm = (ThemeModule) Vesence.get.manager.get(ThemeModule.class);
            if (tm != null && tm.theme != null) {
                return tm.theme.getPrevious();
            }
        } catch (Exception ignored) {}
        return Theme.Blue;
    }

    public static float getThemeTransition() {
        try {
            ThemeModule tm = (ThemeModule) Vesence.get.manager.get(ThemeModule.class);
            if (tm != null && tm.theme != null) {
                return tm.theme.getTransition();
            }
        } catch (Exception ignored) {}
        return 1.0f;
    }

    @EventInit
    public void onRender(EventScreen event) {
        theme.transitionAnim.update();
        theme.transitionAnim.run(1.0, 0.4, Easings.CUBIC_OUT);

        for (Theme t : theme.themes) {
            Animation2 anim = theme.themeAnimations.get(t);
            if (anim != null) {
                anim.update();
                anim.run(t == theme.get() ? 1.0 : 0.0, 0.5, Easings.BACK_OUT, true);
            }
        }
    }
}
