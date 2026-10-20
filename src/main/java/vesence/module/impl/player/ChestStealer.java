package vesence.module.impl.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.ContainerInput;
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

        if (mc.player.containerMenu instanceof ChestMenu container) {
            int size = container.getContainer().getContainerSize();

            for (int index = 0; index < size; ++index) {
                ItemStack stack = container.getContainer().getItem(index);
                if (stack.isEmpty()) continue;

                if (delay.get() == 0.0) {
                    mc.gameMode.handleContainerInput(container.containerId, index, 0, ContainerInput.QUICK_MOVE, mc.player);
                } else {
                    if (System.currentTimeMillis() - lastStealTime >= delay.get().longValue()) {
                        mc.gameMode.handleContainerInput(container.containerId, index, 0, ContainerInput.QUICK_MOVE, mc.player);
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
