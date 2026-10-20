package vesence.module.impl.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ContainerInput;
import vesence.event.EventInit;
import vesence.event.impl.ClickSlotEvent;
import vesence.event.impl.EventPacket;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.MultiBooleanSetting;

@IModule(name = "LockSlot", description = "Блокирует выброс предметов из выбранных слотов", category = Category.PLAYER, bind = -1)
@Environment(EnvType.CLIENT)
public class LockSlot extends Module {
   private final BooleanSetting slot1 = new BooleanSetting("Слот 1", false);
   private final BooleanSetting slot2 = new BooleanSetting("Слот 2", false);
   private final BooleanSetting slot3 = new BooleanSetting("Слот 3", false);
   private final BooleanSetting slot4 = new BooleanSetting("Слот 4", false);
   private final BooleanSetting slot5 = new BooleanSetting("Слот 5", false);
   private final BooleanSetting slot6 = new BooleanSetting("Слот 6", false);
   private final BooleanSetting slot7 = new BooleanSetting("Слот 7", false);
   private final BooleanSetting slot8 = new BooleanSetting("Слот 8", false);
   private final BooleanSetting slot9 = new BooleanSetting("Слот 9", false);
   private final MultiBooleanSetting lockedSlots = new MultiBooleanSetting(
         "Не выбрасывать",
         slot1, slot2, slot3, slot4, slot5, slot6, slot7, slot8, slot9
   );
   private final BooleanSetting protectSword = new BooleanSetting("Не выбрасывать меч", true);

   private static LockSlot instance;

   public LockSlot() {
      this.addSettings(lockedSlots, protectSword);
      instance = this;
   }

   public static LockSlot getInstance() {
      return instance;
   }

   public static boolean shouldCancelSelectedDrop() {
      LockSlot module = getInstance();
      if (module == null || !module.enable || mc.player == null) {
         return false;
      }

      return module.isLockedHotbarSlot(mc.player.getInventory().getSelectedSlot());
   }

   @EventInit
   public void onPacket(EventPacket event) {
      if (!this.enable || !event.isSend() || mc.player == null) {
         return;
      }

      if (event.getPacket() instanceof ServerboundPlayerActionPacket packet) {
         ServerboundPlayerActionPacket.Action action = packet.getAction();
         if ((action == ServerboundPlayerActionPacket.Action.DROP_ITEM || action == ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS)
               && isLockedHotbarSlot(mc.player.getInventory().getSelectedSlot())) {
            event.cancel();
         }
      }
   }

   @EventInit
   public void onClickSlot(ClickSlotEvent event) {
      if (!this.enable || mc.player == null || event.getActionType() != ContainerInput.THROW) {
         return;
      }

      if (event.getSlotId() < 0 || event.getSlotId() >= mc.player.containerMenu.slots.size()) {
         return;
      }

      Slot slot = mc.player.containerMenu.getSlot(event.getSlotId());
      if (!(slot.container instanceof net.minecraft.world.entity.player.Inventory)) {
         return;
      }

      int inventorySlot = slot.getContainerSlot();
      if (inventorySlot >= 0 && inventorySlot < 9 && isLockedHotbarSlot(inventorySlot)) {
         event.cancel();
      }
   }

   private boolean isLockedHotbarSlot(int hotbarSlot) {
      if (hotbarSlot < 0 || hotbarSlot > 8) {
         return false;
      }

      return isManuallyLocked(hotbarSlot) || hotbarSlot == findProtectedSwordSlot();
   }

   private boolean isManuallyLocked(int hotbarSlot) {
      return switch (hotbarSlot) {
         case 0 -> slot1.get();
         case 1 -> slot2.get();
         case 2 -> slot3.get();
         case 3 -> slot4.get();
         case 4 -> slot5.get();
         case 5 -> slot6.get();
         case 6 -> slot7.get();
         case 7 -> slot8.get();
         case 8 -> slot9.get();
         default -> false;
      };
   }

   private int findProtectedSwordSlot() {
      if (!protectSword.get() || mc.player == null) {
         return -1;
      }

      int bestSlot = -1;
      float bestDamage = -1.0F;

      for (int slot = 0; slot < 9; slot++) {
         ItemStack stack = mc.player.getInventory().getItem(slot);
         if (!stack.is(ItemTags.SWORDS)) {
            continue;
         }

         float damage = stack.getOrDefault(
               DataComponents.ATTRIBUTE_MODIFIERS,
               net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY
         ).modifiers().stream()
               .filter(modifier -> modifier.modifier().id().toString().contains("attack_damage"))
               .map(modifier -> (float) modifier.modifier().amount())
               .reduce(0.0F, Float::max);

         if (damage > bestDamage) {
            bestDamage = damage;
            bestSlot = slot;
         }
      }

      return bestSlot;
   }
}
