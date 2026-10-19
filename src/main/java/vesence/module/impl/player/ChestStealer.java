package vesence.module.impl.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import vesence.event.EventInit;
import vesence.event.impl.EventUpdate;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.SliderSetting;

@IModule(name = "ChestStealer", description = "Автоматически забирает предметы из сундука", category = Category.PLAYER, bind = -1)
@Environment(EnvType.CLIENT)
public class ChestStealer extends Module {

    public final SliderSetting delay = new SliderSetting("Задержка", 100.0, 0.0, 1000.0, 1.0);

    private long lastStealTime = 0;

    public ChestStealer() {
        this.addSettings(new Setting[]{delay});
    }

    @EventInit
    public void onUpdate(EventUpdate e) {
        if (mc.player == null) return;

        if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler container) {
            int size = container.getInventory().size();

            for (int index = 0; index < size; ++index) {
                ItemStack stack = container.getInventory().getStack(index);
                if (stack.isEmpty()) continue;

                if (delay.get() == 0.0) {
                    mc.interactionManager.clickSlot(container.syncId, index, 0, SlotActionType.QUICK_MOVE, mc.player);
                } else {
                    if (System.currentTimeMillis() - lastStealTime >= delay.get().longValue()) {
                        mc.interactionManager.clickSlot(container.syncId, index, 0, SlotActionType.QUICK_MOVE, mc.player);
                        lastStealTime = System.currentTimeMillis();
                    }
                }
            }
        }
    }

    private static ChestStealer instance;

    public static ChestStealer getInstance() {
        return instance;
    }
}
