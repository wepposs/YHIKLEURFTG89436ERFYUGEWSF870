package vesence.module.impl.visuals;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.Vesence;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.TitleSetting;
import vesence.module.impl.misc.ClickGui;

import java.util.ArrayList;
import java.util.function.Supplier;

/**
 * Единый модуль интерфейса: заменяет категорию Display (элементы HUD),
 * вкладку Theme (тема клиента) и вкладку Client (настройки GUI/HUD/звуков).
 * Всегда включён — сами настройки применяются глобально через ClickGui/ThemeModule.
 */
@IModule(name = "Interface", description = "Тема, элементы HUD и настройки клиента", category = Category.VISUALS, bind = 0)
@Environment(EnvType.CLIENT)
public class InterfaceModule extends Module {

    public InterfaceModule() {
        this.addSetting(new TitleSetting("Тема"));
        ThemeModule themeModule = Vesence.get.manager.get(ThemeModule.class);
        if (themeModule != null) {
            this.addSetting(themeModule.theme);
        }

        this.addSetting(new TitleSetting("Элементы HUD"));
        Hud hud = Vesence.get.manager.get(Hud.class);
        for (Module m : new ArrayList<>(Vesence.get.manager.getModules())) {
            if (!(m instanceof HudToggleModule)) continue;
            HudToggleModule element = (HudToggleModule) m;
            this.addSetting(elementToggle(element));
            for (Setting<?> s : element.getSettings()) {
                this.addSetting(gateTo(s, element));
            }
            if (hud != null && "Watermark".equalsIgnoreCase(element.name)) {
                this.addSetting(gateTo(hud.watermarkFps, element));
                this.addSetting(gateTo(hud.watermarkBps, element));
                this.addSetting(gateTo(hud.watermarkPing, element));
                this.addSetting(gateTo(hud.watermarkNickname, element));
                this.addSetting(gateTo(hud.watermarkCoordinates, element));
                this.addSetting(gateTo(hud.watermarkTime, element));
            }
        }

        this.addSetting(new TitleSetting("Клиент"));
        this.addSetting(ClickGui.blurHud);
        this.addSetting(ClickGui.blurStrengthHud);
        this.addSetting(ClickGui.cornerHud);
        this.addSetting(ClickGui.hudAlpha);
        this.addSetting(ClickGui.squircleHud);
        this.addSetting(ClickGui.blurGui);
        this.addSetting(ClickGui.blurStrengthGui);
        this.addSetting(ClickGui.cornerGui);
        this.addSetting(ClickGui.squircleGui);
        this.addSetting(ClickGui.sort);
        this.addSetting(ClickGui.guiScale);
        this.addSetting(ClickGui.guiAlpha);
        this.addSetting(ClickGui.colorMode);
        this.addSetting(ClickGui.customColor1);
        this.addSetting(ClickGui.customColor2);
        this.addSetting(ClickGui.themeTintBg);
        this.addSetting(ClickGui.moduleSoundTitle);
        this.addSetting(ClickGui.moduleEnableSound);
        this.addSetting(ClickGui.moduleSoundVolume);

        this.addSetting(new TitleSetting("Редактор HUD"));
        this.addSetting(ClickGui.hudGrid);
        this.addSetting(ClickGui.hudGridDensity);
        this.addSetting(ClickGui.hudGuides);
        this.addSetting(ClickGui.hudGuideStyle);
        this.addSetting(ClickGui.hudGuideOpacity);
        this.addSetting(ClickGui.hudGuideClientColor);
        this.addSetting(ClickGui.hudGuideColor);
        this.addSetting(ClickGui.hudCoords);
        this.addSetting(ClickGui.hudCenterSnap);
        this.addSetting(ClickGui.hudEdgeSnap);
        this.addSetting(ClickGui.hudElementSnap);
        this.addSetting(ClickGui.hudSnapStrength);
        this.addSetting(ClickGui.hudEditHints);

        this.defaultEnabled = true;
        this.setState(true);
    }

    /** Прокси-переключатель элемента HUD: читает и пишет состояние самого HudToggleModule. */
    private static BooleanSetting elementToggle(HudToggleModule element) {
        return new BooleanSetting(element.name, element.enable) {
            @Override
            public Boolean get() {
                return element.enable;
            }

            @Override
            public void set(boolean state) {
                if (element.enable != state) {
                    element.toggle();
                }
            }
        };
    }

    /** Показывать настройку элемента HUD только пока элемент включён. */
    private static <T extends Setting<?>> T gateTo(T setting, Module owner) {
        Supplier<Boolean> original = setting.hidden;
        setting.hidden = () -> original.get() || !owner.enable;
        return setting;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        this.enable = true;
        super.onEnable();
    }
}
