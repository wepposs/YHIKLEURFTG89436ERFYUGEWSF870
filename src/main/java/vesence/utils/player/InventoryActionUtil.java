package vesence.utils.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.screen.slot.ClickType;
import net.minecraft.world.InteractionHand;
import vesence.utils.other.IMinecraft;

@Environment(EnvType.CLIENT)
public final class InventoryActionUtil implements IMinecraft {
   private InventoryActionUtil() {
   }

   public static boolean useHotbarSlot(int slot, boolean restorePreviousSlot) {
      if (mc.player == null || mc.gameMode == null || mc.getConnection() == null || slot < 0 || slot > 8) {
         return false;
      }

      int previousSlot = mc.player.getInventory().getSelectedSlot();
      selectSlot(slot);
      mc.gameMode.interactItem(mc.player, InteractionHand.MAIN_HAND);
      mc.player.swingHand(InteractionHand.MAIN_HAND);
      if (restorePreviousSlot && previousSlot >= 0 && previousSlot <= 8 && previousSlot != slot) {
         selectSlot(previousSlot);
      }
      return true;
   }

   public static boolean useHand(InteractionHand hand) {
      if (mc.player == null || mc.gameMode == null || hand == null) {
         return false;
      }

      mc.gameMode.interactItem(mc.player, hand);
      mc.player.swingHand(hand);
      return true;
   }

   public static void selectSlot(int slot) {
      if (mc.player == null || mc.getConnection() == null || slot < 0 || slot > 8) {
         return;
      }

      mc.player.getInventory().setSelectedSlot(slot);
      mc.getConnection().sendPacket(new ServerboundSetCarriedItemPacket(slot));
   }

    public static boolean swapWithHotbar(int inventorySlot, int hotbarSlot) {
        if (mc.player == null || mc.gameMode == null || inventorySlot < 0 || hotbarSlot < 0 || hotbarSlot > 8) {
            return false;
        }

        if (inventorySlot >= 0 && inventorySlot < 9) {
            selectSlot(inventorySlot);
            return true;
        }

        mc.gameMode.clickSlot(
                mc.player.containerMenu.syncId,
                toScreenSlot(inventorySlot),
                hotbarSlot,
                ClickType.SWAP,
                mc.player
        );
        closeHandledScreenIfNeeded();
        return true;
    }

   public static boolean swapHotbarSlots(int hotbarSlot1, int hotbarSlot2) {
      if (mc.player == null || mc.gameMode == null || hotbarSlot1 < 0 || hotbarSlot1 > 8 || hotbarSlot2 < 0 || hotbarSlot2 > 8) {
         return false;
      }

      if (hotbarSlot1 == hotbarSlot2) {
         return true;
      }

      mc.gameMode.clickSlot(
              mc.player.containerMenu.syncId,
              toScreenSlot(hotbarSlot1),
              hotbarSlot2,
              ClickType.SWAP,
              mc.player
      );
      closeHandledScreenIfNeeded();
      return true;
   }

   public static boolean useInventorySlotViaHotbar(int inventorySlot, int hotbarSlot, boolean restorePreviousSlot, boolean restoreInventorySlot) {
      if (mc.player == null || mc.gameMode == null || inventorySlot < 9 || inventorySlot >= 36 || hotbarSlot < 0 || hotbarSlot > 8) {
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
         if (i != selected && mc.player.getInventory().getItem(i).isEmpty()) {
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
      if (mc.player == null || mc.gameMode == null || inventorySlot < 0) {
         return false;
      }

      mc.gameMode.clickSlot(
              mc.player.containerMenu.syncId,
              toScreenSlot(inventorySlot),
              40,
              ClickType.SWAP,
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
         ItemStack stack = mc.player.getInventory().getItem(i);
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
      if (mc.player != null && mc.screen == null) {
         mc.player.connection.sendPacket(new ServerboundContainerClosePacket(mc.player.containerMenu.syncId));
      }
   }

   public static void swapHotbarWithOffhandPacket(int hotbarSlot) {
      if (mc.player != null && mc.player.connection != null) {
         int previous = mc.player.getInventory().getSelectedSlot();
         selectSlot(hotbarSlot);
         mc.player.connection.sendPacket(new net.minecraft.network.packet.c2s.play.ServerboundPlayerActionPacket(
                 net.minecraft.network.packet.c2s.play.ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                 net.minecraft.util.math.BlockPos.ORIGIN, net.minecraft.util.math.Direction.DOWN));
         if (hotbarSlot >= 0 && hotbarSlot <= 8) {
             selectSlot(hotbarSlot);
         } else {
             selectSlot(previous);
         }
      }
   }
}
