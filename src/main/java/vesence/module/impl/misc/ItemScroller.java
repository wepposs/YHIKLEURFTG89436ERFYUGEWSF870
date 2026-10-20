package vesence.module.impl.misc;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.item.Item;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ContainerInput;
import com.mojang.blaze3d.platform.InputConstants;
import vesence.event.EventInit;
import vesence.event.impl.ClickSlotEvent;
import vesence.event.impl.HandledScreenEvent;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.utils.other.StopWatch;

@IModule(name = "ItemScroller", description = "Прокручивает подходящие предметы в инвентаре с клавишами-модификаторами", category = Category.MISC, bind = -1)
@Environment(EnvType.CLIENT)
public final class ItemScroller extends Module {

    private final StopWatch stopWatch = new StopWatch();
    private final SliderSetting scrollDelay = new SliderSetting("Задержка прокрутки", 50.0, 0.0, 200.0, 1.0);

    public ItemScroller() {
        this.addSettings(new Setting[]{scrollDelay});
    }

    @EventInit
    public void onHandledScreen(HandledScreenEvent event) {
        if (mc.player == null || mc.gameMode == null) {
            return;
        }

        Slot hoverSlot = event.getSlotHover();
        ContainerInput actionType = isKeyDown(mc.options.keyDrop)
                ? ContainerInput.THROW
                : isKeyDown(mc.options.keyAttack) ? ContainerInput.QUICK_MOVE : null;

        if (isKeyDown(mc.options.keyShift)
                && !isKeyDown(mc.options.keySprint)
                && hoverSlot != null
                && hoverSlot.hasItem()
                && actionType != null
                && stopWatch.every(scrollDelay.get())) {
            int slotId = menuSlotId(hoverSlot);
            if (slotId != -1) {
                mc.gameMode.handleContainerInput(
                        mc.player.containerMenu.containerId,
                        slotId,
                        actionType == ContainerInput.THROW ? 1 : 0,
                        actionType,
                        mc.player
                );
            }
        }
    }

    @EventInit
    public void onClickSlot(ClickSlotEvent event) {
        if (mc.player == null || mc.gameMode == null) {
            return;
        }

        int slotId = event.getSlotId();
        if (slotId < 0 || slotId >= mc.player.containerMenu.slots.size()) {
            return;
        }

        Slot slot = mc.player.containerMenu.getSlot(slotId);
        if (!slot.hasItem()) {
            return;
        }

        Item item = slot.getItem().getItem();
        if (isKeyDown(mc.options.keyShift)
                && isKeyDown(mc.options.keySprint)
                && stopWatch.every(50.0)) {
            mc.player.containerMenu.slots.stream()
                    .filter(scrolledSlot -> scrolledSlot.hasItem()
                            && scrolledSlot.getItem().getItem().equals(item)
                            && scrolledSlot.container.equals(slot.container))
                    .forEach(scrolledSlot -> {
                        int scrolledSlotId = menuSlotId(scrolledSlot);
                        if (scrolledSlotId != -1) {
                            mc.gameMode.handleContainerInput(
                                    mc.player.containerMenu.containerId,
                                    scrolledSlotId,
                                    1,
                                    event.getActionType(),
                                    mc.player
                            );
                        }
                    });
        }
    }

    private boolean isKeyDown(KeyMapping key) {
        if (mc.getWindow() == null) {
            return false;
        }
        InputConstants.Key bound = key.getDefaultKey();
        int code = bound.getValue();
        if (code == InputConstants.UNKNOWN.getValue()) {
            return false;
        }
        // 26.3: GLFW.glfwGetKey/glfwGetMouseButton удалены. Клавиши — SDL-сканкоды через
        // InputConstants.isKeyDown, кнопки мыши — SDL_BUTTON_*MASK через MouseUtil
        // (SDL-нумерация 1/2/3 = ЛКМ/СКМ/ПКМ, поэтому переводим в индекс vesence).
        return switch (bound.getType()) {
            case KEYBOARD -> InputConstants.isKeyDown(code);
            case MOUSE -> vesence.utils.other.MouseUtil.isDown(vesence.utils.other.MouseUtil.button(code));
            default -> false;
        };
    }

    private int menuSlotId(Slot slot) {
        if (slot == null || mc.player == null || mc.player.containerMenu == null) {
            return -1;
        }
        var slots = mc.player.containerMenu.slots;
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i) == slot) {
                return i;
            }
        }
        return slot.index;
    }
}
