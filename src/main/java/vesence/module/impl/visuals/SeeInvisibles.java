package vesence.module.impl.visuals;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.SliderSetting;

@IModule(name = "See Invisibles", description = "Позволяет видеть игроков в невидимости", category = Category.VISUALS, bind = -1)
@Environment(EnvType.CLIENT)
public class SeeInvisibles extends Module {

    public static SliderSetting alpha = new SliderSetting("Прозрачность", 100.0, 0.0, 255.0, 1.0);

    public SeeInvisibles() {
        this.addSettings(new Setting[]{alpha});
    }
}
