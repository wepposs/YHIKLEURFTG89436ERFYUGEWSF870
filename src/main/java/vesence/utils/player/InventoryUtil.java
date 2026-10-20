package vesence.utils.player;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import vesence.event.impl.EventPacket;
import vesence.utils.other.IMinecraft;

public class InventoryUtil implements IMinecraft {

    public int getSlotInInventoryOrHotbar(Item item, boolean inHotBar) {
        int firstSlot = inHotBar ? 0 : 9;
        int lastSlot = inHotBar ? 9 : 36;
        int finalSlot = -1;
        for (int i = firstSlot; i < lastSlot; i++) {
            if (mc.player.getInventory().getItem(i).getItem() == item) {
                finalSlot = i;
            }
        }
        return finalSlot;
    }

    public static int findEmptySlot(boolean inHotBar) {
        int start = inHotBar ? 0 : 9;
        int end = inHotBar ? 9 : 45;
        for (int i = start; i < end; ++i) {
            if (!mc.player.getInventory().getItem(i).isEmpty()) {
                continue;
            }
            return i;
        }
        return -1;
    }

    public static class InteractionHand {
        public static boolean isEnabled;
        private boolean isChangingItem;
        private int originalSlot = -1;

        public void onEventPacket(EventPacket eventPacket) {
            if (!eventPacket.isSend()) {
                return;
            }
            if (eventPacket.getPacket() instanceof ClientboundSetHeldSlotPacket) {
                this.isChangingItem = true;
            }
        }

        public void handleItemChange(boolean resetItem) {
            if (this.isChangingItem && this.originalSlot != -1) {
                isEnabled = true;
                mc.player.getInventory().setSelectedSlot(this.originalSlot);
                if (resetItem) {
                    this.isChangingItem = false;
                    this.originalSlot = -1;
                    isEnabled = false;
                }
            }
        }

        public void setOriginalSlot(int slot) {
            this.originalSlot = slot;
        }
    }
}
