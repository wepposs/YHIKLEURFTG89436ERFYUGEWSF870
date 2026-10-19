package vesence.module.api.setting.impl;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.module.api.setting.Setting;

import java.util.function.Supplier;

@Environment(EnvType.CLIENT)
public class TitleSetting extends Setting {
    public TitleSetting(String name) {
        super(name);
    }
    public TitleSetting hidden(Supplier<Boolean> hidden) {
        this.hidden = hidden;
        return this;
    }
}
