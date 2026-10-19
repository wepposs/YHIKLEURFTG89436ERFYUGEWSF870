package vesence.module.impl.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Hand;
import vesence.event.EventInit;
import vesence.event.impl.EventUpdate;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.MultiBooleanSetting;
import vesence.utils.other.StopWatch;

@IModule(
   name = "AutoPotion",
   description = "Автоматически бросает зелья",
   category = Category.PLAYER,
   bind = -1
)
@Environment(EnvType.CLIENT)
public class AutoPotion extends Module {

   public final BooleanSetting autoOff = new BooleanSetting("Авто отключение", false);
   public final MultiBooleanSetting potions = new MultiBooleanSetting("Бросать",
      new BooleanSetting("Силу", true),
      new BooleanSetting("Скорость", true),
      new BooleanSetting("Огнестойкость", false)
   );

   private final StopWatch timer = new StopWatch();
   private boolean isActive = false;
   private int selectedSlot = -1;
   private int throwTicks = 0;

   public AutoPotion() {
      this.addSettings(new Setting[]{potions, autoOff});
   }

   @Override
   public void onDisable() {
      isActive = false;
      throwTicks = 0;
      selectedSlot = -1;
      super.onDisable();
   }

   private enum PotionType {
      STRENGTH(StatusEffects.STRENGTH, "Силу"),
      SPEED(StatusEffects.SPEED, "Скорость"),
      FIRE_RESISTANCE(StatusEffects.FIRE_RESISTANCE, "Огнестойкость");

      final RegistryEntry<StatusEffect> effect;
      final String settingName;

      PotionType(RegistryEntry<StatusEffect> effect, String settingName) {
         this.effect = effect;
         this.settingName = settingName;
      }

      public boolean isEnabled(AutoPotion module) {
         return module.potions.get(this.settingName);
      }
   }

   private int findPotionSlot(PotionType type) {
      if (mc.player == null) return -1;

      for (int i = 0; i < 9; i++) {
         ItemStack stack = mc.player.getInventory().getStack(i);

         if (stack.isOf(Items.SPLASH_POTION)) {
            PotionContentsComponent potionComponent = stack.get(DataComponentTypes.POTION_CONTENTS);
            if (potionComponent != null) {
               for (StatusEffectInstance effect : potionComponent.getEffects()) {
                  if (effect.getEffectType() == type.effect) {
                     return i;
                  }
               }
            }
         }
      }
      return -1;
   }

   private boolean hasEffect(RegistryEntry<StatusEffect> effect) {
      return mc.player != null && mc.player.hasStatusEffect(effect);
   }

   private boolean canBuff(PotionType type) {
      if (hasEffect(type.effect)) return false;
      return type.isEnabled(this) && findPotionSlot(type) != -1;
   }

   private boolean shouldThrow() {
      if (mc.player == null || mc.world == null) return false;

      return (canBuff(PotionType.STRENGTH) || canBuff(PotionType.SPEED) || canBuff(PotionType.FIRE_RESISTANCE))
            && mc.player.isOnGround()
            && timer.finished(500);
   }

   @EventInit
   public void onTick(EventUpdate event) {
      if (mc.player == null || mc.world == null) return;

      if (isActive) {
         throwTicks++;

         if (throwTicks >= 2) {
            boolean threwAny = false;

            if (canBuff(PotionType.STRENGTH)) {
               throwPotion(PotionType.STRENGTH);
               threwAny = true;
            }
            if (canBuff(PotionType.SPEED)) {
               throwPotion(PotionType.SPEED);
               threwAny = true;
            }
            if (canBuff(PotionType.FIRE_RESISTANCE)) {
               throwPotion(PotionType.FIRE_RESISTANCE);
               threwAny = true;
            }

            if (selectedSlot != -1) {
               mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(selectedSlot));
            }

            timer.reset();
            isActive = false;
            throwTicks = 0;
            selectedSlot = -1;

            if (autoOff.get() || !threwAny) {
               setState(false);
            }
            return;
         }

         if (throwTicks > 10) {
            if (selectedSlot != -1) {
               mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(selectedSlot));
            }
            isActive = false;
            throwTicks = 0;
            selectedSlot = -1;
         }
         return;
      }

      if (shouldThrow()) {
         isActive = true;
         throwTicks = 0;
         selectedSlot = mc.player.getInventory().getSelectedSlot();
      }
   }

   private void throwPotion(PotionType type) {
      if (!type.isEnabled(this) || hasEffect(type.effect)) return;
      if (mc.player == null || mc.getNetworkHandler() == null) return;

      int slot = findPotionSlot(type);
      if (slot == -1) return;

      mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));

      mc.getNetworkHandler().sendPacket(
         new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, 0, mc.player.getYaw(), 90f));
   }
}
