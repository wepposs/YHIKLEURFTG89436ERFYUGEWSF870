package vesence.module.impl.misc;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Item;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.lwjgl.glfw.GLFW;
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
        if (mc.player == null || mc.interactionManager == null) {
            return;
        }

        Slot hoverSlot = event.getSlotHover();
        SlotActionType actionType = isKeyDown(mc.options.dropKey)
                ? SlotActionType.THROW
                : isKeyDown(mc.options.attackKey) ? SlotActionType.QUICK_MOVE : null;

        if (isKeyDown(mc.options.sneakKey)
                && !isKeyDown(mc.options.sprintKey)
                && hoverSlot != null
                && hoverSlot.hasStack()
                && actionType != null
                && stopWatch.every(scrollDelay.get())) {
            int slotId = menuSlotId(hoverSlot);
            if (slotId != -1) {
                mc.interactionManager.clickSlot(
                        mc.player.currentScreenHandler.syncId,
                        slotId,
                        actionType == SlotActionType.THROW ? 1 : 0,
                        actionType,
                        mc.player
                );
            }
        }
    }

    @EventInit
    public void onClickSlot(ClickSlotEvent event) {
        if (mc.player == null || mc.interactionManager == null) {
            return;
        }

        int slotId = event.getSlotId();
        if (slotId < 0 || slotId >= mc.player.currentScreenHandler.slots.size()) {
            return;
        }

        Slot slot = mc.player.currentScreenHandler.getSlot(slotId);
        if (!slot.hasStack()) {
            return;
        }

        Item item = slot.getStack().getItem();
        if (isKeyDown(mc.options.sneakKey)
                && isKeyDown(mc.options.sprintKey)
                && stopWatch.every(50.0)) {
            mc.player.currentScreenHandler.slots.stream()
                    .filter(scrolledSlot -> scrolledSlot.hasStack()
                            && scrolledSlot.getStack().getItem().equals(item)
                            && scrolledSlot.inventory.equals(slot.inventory))
                    .forEach(scrolledSlot -> {
                        int scrolledSlotId = menuSlotId(scrolledSlot);
                        if (scrolledSlotId != -1) {
                            mc.interactionManager.clickSlot(
                                    mc.player.currentScreenHandler.syncId,
                                    scrolledSlotId,
                                    1,
                                    event.getActionType(),
                                    mc.player
                            );
                        }
                    });
        }
    }

    private boolean isKeyDown(KeyBinding key) {
        if (mc.getWindow() == null) {
            return false;
        }
        InputUtil.Key bound = key.getDefaultKey();
        int code = bound.getCode();
        if (code == InputUtil.UNKNOWN_KEY.getCode()) {
            return false;
        }
        long handle = mc.getWindow().getHandle();
        return switch (bound.getCategory()) {
            case KEYSYM -> GLFW.glfwGetKey(handle, code) == GLFW.GLFW_PRESS;
            case MOUSE -> GLFW.glfwGetMouseButton(handle, code) == GLFW.GLFW_PRESS;
            default -> false;
        };
    }

    private int menuSlotId(Slot slot) {
        if (slot == null || mc.player == null || mc.player.currentScreenHandler == null) {
            return -1;
        }
        var slots = mc.player.currentScreenHandler.slots;
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i) == slot) {
                return i;
            }
        }
        return slot.id;
    }
}
