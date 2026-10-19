package vesence.module.impl.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.TridentItem;
import vesence.event.EventInit;
import vesence.event.impl.EventUpdate;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.MultiBooleanSetting;
import vesence.module.api.setting.impl.SliderSetting;

@IModule(name = "ItemRelease", description = "Автоматически отпускает предметы при натягивании", category = Category.PLAYER, bind = -1)
@Environment(EnvType.CLIENT)
public class ItemRelease extends Module {

    private final BooleanSetting bow = new BooleanSetting("Лук", true);
    private final BooleanSetting трезубец = new BooleanSetting("Трезубец", false);
    private final BooleanSetting арбалет = new BooleanSetting("Арбалет", true);
    private final MultiBooleanSetting items = new MultiBooleanSetting("Предметы", bow, трезубец, арбалет);

    private final SliderSetting delay = new SliderSetting("Время натягивания", 4.0, 2.0, 20.0, 0.5);

    public ItemRelease() {
        this.addSettings(new Setting[]{items, delay});
    }

    @EventInit
    public void onUpdate(EventUpdate e) {
        if (mc.player == null) return;

        if (bow.get()) {
            if (mc.player.getMainHandStack().getItem() instanceof BowItem && mc.player.isUsingItem()
                    && mc.player.getItemUseTime() >= delay.get().floatValue()) {
                mc.interactionManager.stopUsingItem(mc.player);
            }
        }

        if (трезубец.get()) {
            if (mc.player.getMainHandStack().getItem() instanceof TridentItem && mc.player.isUsingItem()
                    && mc.player.getItemUseTime() >= 10) {
                mc.interactionManager.stopUsingItem(mc.player);
            }
        }

        if (арбалет.get()) {
            if (mc.player.getMainHandStack().getItem() instanceof CrossbowItem && mc.player.isUsingItem()
                    && mc.player.getItemUseTime() >= 25) {
                mc.interactionManager.stopUsingItem(mc.player);
            }
        }
    }

    private static ItemRelease instance;

    public static ItemRelease getInstance() {
        return instance;
    }
}
