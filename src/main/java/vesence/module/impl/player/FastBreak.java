package vesence.module.impl.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.event.EventInit;
import vesence.event.impl.EventUpdate;
import vesence.mixin.ClientPlayerInteractionManagerAccessor;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.SliderSetting;

@IModule(name = "FastBreak", description = "Ускоряет разрушение блоков", category = Category.PLAYER, bind = -1)
@Environment(EnvType.CLIENT)
public class FastBreak extends Module {

    public SliderSetting speed = new SliderSetting("Скорость", 0.7, 0.3, 1.0, 0.1);

    private static FastBreak instance;

    public FastBreak() {
        this.addSettings(new Setting[]{speed});
        instance = this;
    }

    @EventInit
    public void onUpdate(EventUpdate e) {
        if (mc.player == null || mc.interactionManager == null) return;

        ((ClientPlayerInteractionManagerAccessor) mc.interactionManager).setBlockBreakingCooldown(0);
    }

    public static FastBreak getInstance() {
        return instance;
    }
}
