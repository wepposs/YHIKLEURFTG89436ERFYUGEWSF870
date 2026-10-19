package vesence.utils.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import vesence.utils.other.IMinecraft;

@Environment(EnvType.CLIENT)
public final class InventoryActionUtil implements IMinecraft {
   private InventoryActionUtil() {
   }

   public static boolean useHotbarSlot(int slot, boolean restorePreviousSlot) {
      if (mc.player == null || mc.interactionManager == null || mc.getNetworkHandler() == null || slot < 0 || slot > 8) {
         return false;
      }

      int previousSlot = mc.player.getInventory().getSelectedSlot();
      selectSlot(slot);
      mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
      mc.player.swingHand(Hand.MAIN_HAND);
      if (restorePreviousSlot && previousSlot >= 0 && previousSlot <= 8 && previousSlot != slot) {
         selectSlot(previousSlot);
      }
      return true;
   }

   public static boolean useHand(Hand hand) {
      if (mc.player == null || mc.interactionManager == null || hand == null) {
         return false;
      }

      mc.interactionManager.interactItem(mc.player, hand);
      mc.player.swingHand(hand);
      return true;
   }

   public static void selectSlot(int slot) {
      if (mc.player == null || mc.getNetworkHandler() == null || slot < 0 || slot > 8) {
         return;
      }

      mc.player.getInventory().setSelectedSlot(slot);
      mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
   }

    public static boolean swapWithHotbar(int inventorySlot, int hotbarSlot) {
        if (mc.player == null || mc.interactionManager == null || inventorySlot < 0 || hotbarSlot < 0 || hotbarSlot > 8) {
            return false;
        }

        if (inventorySlot >= 0 && inventorySlot < 9) {
            selectSlot(inventorySlot);
            return true;
        }

        mc.interactionManager.clickSlot(
                mc.player.currentScreenHandler.syncId,
                toScreenSlot(inventorySlot),
                hotbarSlot,
                SlotActionType.SWAP,
                mc.player
        );
        closeHandledScreenIfNeeded();
        return true;
    }

   public static boolean swapHotbarSlots(int hotbarSlot1, int hotbarSlot2) {
      if (mc.player == null || mc.interactionManager == null || hotbarSlot1 < 0 || hotbarSlot1 > 8 || hotbarSlot2 < 0 || hotbarSlot2 > 8) {
         return false;
      }

      if (hotbarSlot1 == hotbarSlot2) {
         return true;
      }

      mc.interactionManager.clickSlot(
              mc.player.currentScreenHandler.syncId,
              toScreenSlot(hotbarSlot1),
              hotbarSlot2,
              SlotActionType.SWAP,
              mc.player
      );
      closeHandledScreenIfNeeded();
      return true;
   }

   public static boolean useInventorySlotViaHotbar(int inventorySlot, int hotbarSlot, boolean restorePreviousSlot, boolean restoreInventorySlot) {
      if (mc.player == null || mc.interactionManager == null || inventorySlot < 9 || inventorySlot >= 36 || hotbarSlot < 0 || hotbarSlot > 8) {
         return false;
      }

      int previousSlot = mc.player.getInventory().getSelectedSlot();
      if (!swapWithHotbar(inventorySlot, hotbarSlot)) {
         return false;
      }
      useHotbarSlot(hotbarSlot, false);
      if (restorePreviousSlot && previousSlot >= 0 && previousSlot <= 8 && previousSlot != hotbarSlot) {
         selectSlot(previousSlot);
      }
      if (restoreInventorySlot) {
         swapWithHotbar(inventorySlot, hotbarSlot);
      }
      return true;
   }

   public static int findBestTemporaryHotbarSlot() {
      if (mc.player == null) {
         return -1;
      }

      int selected = mc.player.getInventory().getSelectedSlot();
      for (int i = 0; i < 9; i++) {
         if (i != selected && mc.player.getInventory().getStack(i).isEmpty()) {
            return i;
         }
      }
      for (int i = 0; i < 9; i++) {
         if (i != selected) {
            return i;
         }
      }
      return selected;
   }

   public static boolean swapWithOffhand(int inventorySlot) {
      if (mc.player == null || mc.interactionManager == null || inventorySlot < 0) {
         return false;
      }

      mc.interactionManager.clickSlot(
              mc.player.currentScreenHandler.syncId,
              toScreenSlot(inventorySlot),
              40,
              SlotActionType.SWAP,
              mc.player
      );
      closeHandledScreenIfNeeded();
      return true;
   }

   public static int findItem(Item item, boolean hotbarOnly) {
      if (mc.player == null || item == null) {
         return -1;
      }

      int start = hotbarOnly ? 0 : 9;
      int end = hotbarOnly ? 9 : 36;
      for (int i = start; i < end; i++) {
         ItemStack stack = mc.player.getInventory().getStack(i);
         if (stack.isOf(item)) {
            return i;
         }
      }
      return -1;
   }

    public static boolean selectHotbarSlotIfPresent(int slot) {
        if (slot >= 0 && slot < 9) {
            selectSlot(slot);
            return true;
        }
        return false;
    }

    public static boolean selectSlotOfItemInHotbar(Item item) {
        int slot = findItem(item, true);
        if (slot >= 0) {
            selectSlot(slot);
            return true;
        }
        return false;
    }

   public static int toScreenSlot(int inventorySlot) {
      return inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
   }

   public static void closeHandledScreenIfNeeded() {
      if (mc.player != null && mc.currentScreen == null) {
         mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
      }
   }

   public static void swapHotbarWithOffhandPacket(int hotbarSlot) {
      if (mc.player != null && mc.player.networkHandler != null) {
         int previous = mc.player.getInventory().getSelectedSlot();
         selectSlot(hotbarSlot);
         mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket(
                 net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                 net.minecraft.util.math.BlockPos.ORIGIN, net.minecraft.util.math.Direction.DOWN));
         if (hotbarSlot >= 0 && hotbarSlot <= 8) {
             selectSlot(hotbarSlot);
         } else {
             selectSlot(previous);
         }
      }
   }
}
