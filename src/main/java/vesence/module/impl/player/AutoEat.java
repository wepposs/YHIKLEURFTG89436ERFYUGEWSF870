package vesence.module.impl.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import vesence.event.EventInit;
import vesence.event.impl.EventUpdate;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.utils.player.InventoryActionUtil;

@IModule(name = "AutoEat", description = "Автоматически ест еду при определенном уровне голода", category = Category.MISC, bind = -1)
@Environment(EnvType.CLIENT)
public class AutoEat extends Module {

    public static SliderSetting hungerLevel = new SliderSetting("Уровень голода", 14, 1, 20, 1);

    private boolean isEating = false;
    private int prevSlot = -1;
    private int swappedFoodSlot = -1;
    private InteractionHand eatHand = InteractionHand.MAIN_HAND;

    public AutoEat() {
        hungerLevel.name = "Уровень голода";
        addSettings(hungerLevel);
    }

    @EventInit
    public void onUpdate(EventUpdate e) {
        if (!this.enable) return;
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (mc.gui.screen() != null) {
            if (isEating) {
                stopEating();
            }
            return;
        }

        if (isEating) {
            if (mc.player.isUsingItem() && isFood(mc.player.getActiveItem())) {
                mc.options.keyUse.setDown(true);
                return;
            }
            stopEating();
            return;
        }

        int foodLevel = mc.player.getFoodData().getFoodLevel();
        if (foodLevel > getConfiguredHungerLevel()) return;
        if (!mc.player.canEat(false)) return;

        ItemStack offhand = mc.player.getItemInHand(InteractionHand.OFF_HAND);
        if (isFood(offhand)) {
            eatHand = InteractionHand.OFF_HAND;
            swappedFoodSlot = -1;
            startEating(InteractionHand.OFF_HAND);
            return;
        }

        int hotbarSlot = findFoodSlot(0, 9);
        if (hotbarSlot != -1) {
            prevSlot = mc.player.getInventory().getSelectedSlot();
            eatHand = InteractionHand.MAIN_HAND;
            swappedFoodSlot = -1;
            if (prevSlot != hotbarSlot) {
                InventoryActionUtil.selectSlot(hotbarSlot);
            }
            startEating(InteractionHand.MAIN_HAND);
            return;
        }

        int invSlot = findFoodSlot(9, 36);
        if (invSlot != -1) {
            if (InventoryActionUtil.swapWithOffhand(invSlot)) {
                swappedFoodSlot = invSlot;
                eatHand = InteractionHand.OFF_HAND;
                startEating(InteractionHand.OFF_HAND);
            }
            return;
        }
    }

    private void startEating(InteractionHand hand) {
        mc.options.keyUse.setDown(true);
        mc.gameMode.useItem(mc.player, hand);
        isEating = true;
    }

    private void stopEating() {
        mc.options.keyUse.setDown(false);
        isEating = false;

        restoreSwappedFood();

        if (eatHand == InteractionHand.MAIN_HAND && prevSlot != -1) {
            InventoryActionUtil.selectSlot(prevSlot);
        }

        prevSlot = -1;
        eatHand = InteractionHand.MAIN_HAND;
    }

    private void restoreSwappedFood() {
        if (swappedFoodSlot == -1 || mc.player == null) {
            swappedFoodSlot = -1;
            return;
        }

        if (swappedFoodSlot < 9) {
            InventoryActionUtil.swapHotbarWithOffhandPacket(swappedFoodSlot);
        } else {
            InventoryActionUtil.swapWithOffhand(swappedFoodSlot);
        }
        swappedFoodSlot = -1;
    }

    private int findFoodSlot(int start, int end) {
        for (int i = start; i < end; i++) {
            if (isFood(mc.player.getInventory().getItem(i))) {
                return i;
            }
        }
        return -1;
    }

    private int getConfiguredHungerLevel() {
        return hungerLevel.get().intValue();
    }

    private boolean isFood(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.has(DataComponents.FOOD);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (isEating) {
            stopEating();
        } else {
            restoreSwappedFood();
        }
    }
}