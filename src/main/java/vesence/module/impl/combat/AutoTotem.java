package vesence.module.impl.combat;

import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import vesence.event.EventInit;
import vesence.event.impl.EventUpdate;
import vesence.event.player.EventInput;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.api.setting.impl.MultiBooleanSetting;
import vesence.module.api.setting.impl.SliderSetting;

/**
 * AutoTotem — полный порт логики из RelevantPremiumpp4 под vesence (1.21.11).
 * Триггеры (кристалл рядом/в руке, обсидиан в руке, падения, летящий тризубец,
 * ТНТ), взятие по хп/хп-на-элитрах, сохранение зачара / только обычные тотемы,
 * цикл шар↔тотем, возврат оффхенда с задержкой, обход Grim (обнуление инпута +
 * подавление спринта), версии свапа 1.21.4 / 1.16.5, детект попа тотема.
 */
@IModule(name = "AutoTotem", description = "Автоматически берёт тотем в опасности", category = Category.COMBAT, bind = -1)
@Environment(EnvType.CLIENT)
public class AutoTotem extends Module {

   public static AutoTotem INSTANCE;

   private final MultiBooleanSetting triggers = new MultiBooleanSetting(
      "Брать от",
      new BooleanSetting("Кристалл рядом", true),
      new BooleanSetting("Кристалл в руке", true),
      new BooleanSetting("Обсидиан в руке", true),
      new BooleanSetting("Падения", true),
      new BooleanSetting("Тризубец летит", true),
      new BooleanSetting("ТНТ рядом", true)
   );
   private final SliderSetting hp = new SliderSetting("Брать от хп", 6.0, 1.0, 20.0, 0.5, false);
   private final SliderSetting hpOnElytra = new SliderSetting("Хп на элитрах", 10.0, 1.0, 20.0, 0.5, false);
   private final SliderSetting crystalRadius = new SliderSetting("Радиус кристалла", 6.0, 1.0, 12.0, 0.5, false).hidden(() -> !this.isCrystalRadiusVisible());
   private final SliderSetting fallHeight = new SliderSetting("Высота падения", 10.0, 3.0, 50.0, 1.0, false).hidden(() -> !this.triggers.get("Падения"));
   private final SliderSetting tridentRadius = new SliderSetting("Радиус тризубца", 8.0, 1.0, 30.0, 0.5, false).hidden(() -> !this.triggers.get("Тризубец летит"));
   private final SliderSetting tntRadius = new SliderSetting("Радиус ТНТ", 8.0, 1.0, 20.0, 0.5, false).hidden(() -> !this.triggers.get("ТНТ рядом"));
   private final BooleanSetting saveEnchanted = new BooleanSetting("Сохранять зачар", true);
   /**
    * «Только обычные тотемы»: при включении модуль НЕ трогает зачарованные
    * тотемы вообще — берёт исключительно обычные. Если обычных нет — модуль
    * не свапается (зачарованный не вырывается).
    */
   private final BooleanSetting onlyNormalTotem = new BooleanSetting("Только обычные тотемы", false);
   private final BooleanSetting skipIfHead = new BooleanSetting("Не брать с шаром в руке", true);
   /**
    * «Брать шар» — режим цикла шар↔тотем по хп. Шар в офф-хенде не блокирует
    * взятие тотема при низком хп; когда хп восстановилось — шар возвращается.
    */
   private final BooleanSetting takeSphere = new BooleanSetting("Брать шар", false);
   private final BooleanSetting returnTotem = new BooleanSetting("Возвращать тотем", true);
   private final SliderSetting returnDelay = new SliderSetting("Задержка возврата", 20.0, 5.0, 100.0, 5.0, false).hidden(() -> !this.returnTotem.get());
   private final BooleanSetting bypassgrim = new BooleanSetting("Обходить Grim", true);
   private final ModeSetting swapVersion = new ModeSetting("Версия свапа", "1.21.4", "1.21.4", "1.16.5");

   private int bypassTicks;
   private boolean sprintPaused;
   private int swapCooldown;
   private int savedTotemSlot = -1;
   private ItemStack originalOffhandItem = ItemStack.EMPTY;
   /** Кэш имени шара (PLAYER_HEAD), лежавшего в оффхенде до свапа. */
   private String originalOffhandHeadName = "";
   private boolean totemTakenByUs = false;
   /** Снимок «в прошлом тике в оффхенде лежал НАШ тотем» — для детекта попа. */
   private boolean prevTickOwnedTotemInOffhand = false;
   private boolean returnMode = false;
   private boolean needFastSwap = false;
   private int safeTicks = 0;

   public AutoTotem() {
      INSTANCE = this;
      this.addSettings(
         new Setting[]{
            this.hp,
            this.hpOnElytra,
            this.saveEnchanted,
            this.onlyNormalTotem,
            this.skipIfHead,
            this.takeSphere,
            this.bypassgrim,
            this.returnTotem,
            this.swapVersion,
            this.returnDelay,
            this.triggers,
            this.crystalRadius,
            this.fallHeight,
            this.tridentRadius,
            this.tntRadius
         }
      );
   }

   @EventInit
   public void onInput(EventInput e) {
      if (this.bypassgrim.get() && this.bypassTicks > 0) {
         if (mc.player == null) {
            return;
         }

         mc.player.setSprinting(false);
         e.setForward(0.0F);
         e.setStrafe(0.0F);
         e.setJump(false);
         e.setSneak(false);
         e.setSprint(false);
      }
   }

   @EventInit
   public void onUpdate(EventUpdate e) {
      if (mc.player != null && mc.level != null) {
         // === ДЕТЕКТ ПОПА ТОТЕМА ===
         boolean ownsTotemInOffhandNow = this.totemTakenByUs && this.hasTotemInOffhand();
         if (this.prevTickOwnedTotemInOffhand && !ownsTotemInOffhandNow) {
            this.totemTakenByUs = false;
            this.returnMode = false;
            this.clearOriginalOffhand();
            this.safeTicks = 0;
         }

         boolean instantDanger = this.isInstantDanger();
         if (instantDanger) {
            this.needFastSwap = true;
            this.safeTicks = 0;
         }

         if (this.swapCooldown > 0) {
            this.swapCooldown--;
         }

         if (this.bypassgrim.get() && this.bypassTicks > 0) {
            mc.player.setSprinting(false);
            this.bypassTicks--;
            if (this.bypassTicks <= 0) {
               if (this.returnMode) {
                  this.performReturn();
               } else {
                  this.performSwap();
               }

               this.restoreSprint();
            }
         } else {
            boolean needTotem = this.shouldTakeTotem(instantDanger);
            if (needTotem && !this.hasTotemInOffhand()) {
               int totemSlot = this.findTotemSlot();
               if (totemSlot == -1) {
                  return;
               }

               if (!this.needFastSwap && this.swapCooldown > 0) {
                  return;
               }

               if (this.originalOffhandItem.isEmpty() && !this.totemTakenByUs) {
                  this.originalOffhandItem = mc.player.getOffhandItem().copy();
                  this.originalOffhandHeadName = computeStableHeadName(this.originalOffhandItem);
               }

               this.savedTotemSlot = totemSlot;
               this.returnMode = false;
               this.safeTicks = 0;
               if (this.bypassgrim.get()) {
                  this.disableSprint();
                  this.bypassTicks = this.needFastSwap ? 1 : 2;
                  this.swapCooldown = this.needFastSwap ? 0 : 2;
               } else {
                  this.performSwap();
                  this.swapCooldown = this.needFastSwap ? 0 : 2;
               }
            }

            boolean isSafe = !needTotem;
            if (isSafe) {
               this.safeTicks++;
            } else {
               this.safeTicks = 0;
            }

            boolean originalWasHeadForCycle = this.takeSphere.get()
               && !this.originalOffhandItem.isEmpty()
               && this.originalOffhandItem.is(Items.PLAYER_HEAD);
            boolean wantReturn = this.returnTotem.get() || originalWasHeadForCycle;

            if (wantReturn
               && !needTotem
               && this.hasTotemInOffhand()
               && this.totemTakenByUs
               && this.safeTicks >= this.returnDelay.get().intValue()) {
               if (!this.needFastSwap && this.swapCooldown > 0) {
                  return;
               }

               this.returnMode = true;
               if (this.bypassgrim.get()) {
                  this.disableSprint();
                  this.bypassTicks = this.needFastSwap ? 1 : 2;
                  this.swapCooldown = this.needFastSwap ? 0 : 2;
               } else {
                  this.performReturn();
                  this.swapCooldown = this.needFastSwap ? 0 : 2;
               }
            }

            if (!instantDanger) {
               this.needFastSwap = false;
            }
         }

         this.prevTickOwnedTotemInOffhand = this.totemTakenByUs && this.hasTotemInOffhand();
      }
   }

   /** Совокупная «мгновенная опасность» — instant-триггеры (fast-swap). */
   private boolean isInstantDanger() {
      return this.isCrystalDanger() || this.isTridentDanger() || this.isTntDanger();
   }

   private boolean isCrystalDanger() {
      if (mc.player == null || mc.level == null) return false;
      float radius = this.crystalRadius.get().floatValue();
      double radiusSq = radius * radius;
      if (this.triggers.get("Кристалл рядом")) {
         for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof EndCrystal && mc.player.distanceToSqr(entity) <= radiusSq) {
               return true;
            }
         }
      }

      if (this.triggers.get("Кристалл в руке")) {
         for (Player player : mc.level.players()) {
            if (player != mc.player
               && mc.player.distanceToSqr(player) <= radiusSq
               && (player.getMainHandItem().is(Items.END_CRYSTAL) || player.getOffhandItem().is(Items.END_CRYSTAL))) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Опасность от тризубца — тотем в момент, когда тризубец РЕАЛЬНО ЛЕТИТ
    * (скорость ненулевая), не свой, в радиусе и ПРИБЛИЖАЕТСЯ.
    */
   private boolean isTridentDanger() {
      if (mc.player == null || mc.level == null) return false;
      if (!this.triggers.get("Тризубец летит")) return false;
      float radius = this.tridentRadius.get().floatValue();
      double radiusSq = radius * radius;
      Vec3 playerPos = new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ());

      for (Entity entity : mc.level.entitiesForRendering()) {
         if (!(entity instanceof ThrownTrident trident)) continue;
         if (trident.getOwner() == mc.player) continue;
         if (mc.player.distanceToSqr(trident) > radiusSq) continue;

         Vec3 vel = trident.getDeltaMovement();
         if (vel.lengthSqr() < 0.05) continue;

         Vec3 tridentPos = new Vec3(trident.getX(), trident.getY(), trident.getZ());
         double distNow = tridentPos.distanceToSqr(playerPos);
         double distNext = tridentPos.add(vel).distanceToSqr(playerPos);
         if (distNext <= distNow) {
            return true;
         }
      }
      return false;
   }

   /** Опасность от праймнутого ТНТ в радиусе. */
   private boolean isTntDanger() {
      if (mc.player == null || mc.level == null) return false;
      if (!this.triggers.get("ТНТ рядом")) return false;
      float radius = this.tntRadius.get().floatValue();
      double radiusSq = radius * radius;

      for (Entity entity : mc.level.entitiesForRendering()) {
         if (entity instanceof PrimedTnt && mc.player.distanceToSqr(entity) <= radiusSq) {
            return true;
         }
      }
      return false;
   }

   private boolean shouldTakeTotem(boolean instantDanger) {
      if (mc.player == null || mc.level == null) return false;
      if (this.skipIfHead.get()) {
         boolean blockingHead = this.takeSphere.get()
            ? mc.player.getMainHandItem().is(Items.PLAYER_HEAD)
            : this.isHoldingHead();
         if (blockingHead) return false;
      }
      float currentHp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
      boolean isGliding = mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA) && mc.player.isFallFlying();
      float hpThreshold = isGliding ? this.hpOnElytra.get().floatValue() : this.hp.get().floatValue();
      if (currentHp <= hpThreshold) {
         return true;
      } else if (instantDanger) {
         return true;
      } else {
         float radius = this.crystalRadius.get().floatValue();
         double radiusSq = radius * radius;
         if (this.triggers.get("Обсидиан в руке")) {
            for (Player player : mc.level.players()) {
               if (player != mc.player
                  && mc.player.distanceToSqr(player) <= radiusSq
                  && (player.getMainHandItem().is(Items.OBSIDIAN) || player.getOffhandItem().is(Items.OBSIDIAN))) {
                  return true;
               }
            }
         }

         return this.triggers.get("Падения") && mc.player.fallDistance >= this.fallHeight.get().floatValue() && !isGliding;
      }
   }

   /** В руке (главной или офф) — голова/шар. */
   private boolean isHoldingHead() {
      if (mc.player == null) return false;
      ItemStack main = mc.player.getMainHandItem();
      ItemStack off = mc.player.getOffhandItem();
      return main.is(Items.PLAYER_HEAD) || off.is(Items.PLAYER_HEAD);
   }

   private boolean hasTotemInOffhand() {
      return mc.player != null && mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
   }

   private int findTotemSlot() {
      if (mc.player == null) return -1;
      int normalTotem = -1;
      int enchantedTotem = -1;

      for (int i = 9; i < 45; i++) {
         ItemStack stack = mc.player.inventoryMenu.getSlot(i).getItem();
         if (stack.is(Items.TOTEM_OF_UNDYING)) {
            boolean isEnchanted = stack.hasFoil();
            if (isEnchanted) {
               if (enchantedTotem == -1) {
                  enchantedTotem = i;
               }
            } else if (normalTotem == -1) {
               normalTotem = i;
            }
         }
      }

      if (this.onlyNormalTotem.get()) {
         return normalTotem;
      }

      if (this.saveEnchanted.get()) {
         return normalTotem != -1 ? normalTotem : enchantedTotem;
      } else {
         return enchantedTotem != -1 ? enchantedTotem : normalTotem;
      }
   }

   private void performSwap() {
      if (mc.player == null) return;
      int totemSlot = this.findTotemSlot();
      if (totemSlot != -1) {
         this.savedTotemSlot = totemSlot;
         this.doSwap(totemSlot);
         this.totemTakenByUs = true;
         mc.player.connection.send(new ServerboundContainerClosePacket(0));
      }
   }

   private void performReturn() {
      if (mc.player == null) {
         this.clearOriginalOffhand();
         return;
      }
      if (!this.hasTotemInOffhand()) {
         this.clearOriginalOffhand();
         return;
      }

      boolean originalWasTotem = !this.originalOffhandItem.isEmpty()
         && this.originalOffhandItem.is(Items.TOTEM_OF_UNDYING);
      boolean originalWasHead = !this.originalOffhandItem.isEmpty()
         && this.originalOffhandItem.is(Items.PLAYER_HEAD);
      boolean originalWasEmpty = this.originalOffhandItem.isEmpty();

      if (originalWasTotem || originalWasHead) {
         int slotToReturn = this.findSlotForItem(this.originalOffhandItem);
         if (slotToReturn == -1 && originalWasHead) {
            slotToReturn = this.findHeadSlotByName(this.originalOffhandItem);
         }
         if (slotToReturn != -1) {
            this.doSwap(slotToReturn);
         } else if (originalWasHead) {
            if (this.savedTotemSlot == -1) {
               this.savedTotemSlot = 9;
            }
            this.doSwap(this.savedTotemSlot);
         } else {
            if (this.savedTotemSlot == -1) {
               this.savedTotemSlot = 9;
            }
            this.doSwap(this.savedTotemSlot);
         }
      } else if (originalWasEmpty) {
         if (this.savedTotemSlot == -1) {
            this.savedTotemSlot = 9;
         }
         this.doSwap(this.savedTotemSlot);
      }

      this.totemTakenByUs = false;
      this.clearOriginalOffhand();
      mc.player.connection.send(new ServerboundContainerClosePacket(0));
   }

   /** Единая точка сброса «памяти» оригинального офф-хенда. */
   private void clearOriginalOffhand() {
      this.originalOffhandItem = ItemStack.EMPTY;
      this.originalOffhandHeadName = "";
      this.savedTotemSlot = -1;
   }

   private int findSlotForItem(ItemStack item) {
      if (item.isEmpty() || mc.player == null) {
         return -1;
      } else {
         for (int i = 9; i < 45; i++) {
            ItemStack stack = mc.player.inventoryMenu.getSlot(i).getItem();
            if (ItemStack.isSameItemSameComponents(stack, item)) {
               return i;
            }
         }

         return -1;
      }
   }

   /** Поиск слота со сферой/шаром (PLAYER_HEAD) по отображаемому имени. */
   private int findHeadSlotByName(ItemStack reference) {
      if (mc.player == null || reference.isEmpty()) return -1;

      String wantRaw = !this.originalOffhandHeadName.isEmpty()
         ? this.originalOffhandHeadName
         : computeStableHeadName(reference);
      if (wantRaw == null) wantRaw = "";
      String wantNorm = normalizeHeadName(wantRaw);

      int byExactName = -1;
      int byNormName = -1;

      for (int i = 9; i < 45; i++) {
         ItemStack stack = mc.player.inventoryMenu.getSlot(i).getItem();
         if (!stack.is(Items.PLAYER_HEAD)) continue;

         if (ItemStack.isSameItemSameComponents(stack, reference)) {
            return i;
         }

         if (wantRaw.isEmpty()) continue;

         String haveRaw = computeStableHeadName(stack);
         if (haveRaw == null || haveRaw.isEmpty()) continue;

         if (byExactName == -1 && wantRaw.equals(haveRaw)) {
            byExactName = i;
            continue;
         }
         if (byNormName == -1) {
            String haveNorm = normalizeHeadName(haveRaw);
            if (!wantNorm.isEmpty() && wantNorm.equals(haveNorm)) {
               byNormName = i;
            }
         }
      }

      if (byExactName != -1) return byExactName;
      if (byNormName != -1) return byNormName;
      return -1;
   }

   /** Безопасно возвращает отображаемое имя PLAYER_HEAD. */
   private static String computeStableHeadName(ItemStack stack) {
      if (stack == null || stack.isEmpty()) return "";
      try {
         Component name = stack.getHoverName();
         if (name == null) return "";
         String s = name.getString();
         return s == null ? "" : s;
      } catch (Throwable t) {
         return "";
      }
   }

   /** Нормализация имени шара: убирает §-форматирование, схлопывает пробелы, lower-case. */
   private static String normalizeHeadName(String s) {
      if (s == null || s.isEmpty()) return "";
      String stripped = s.replaceAll("(?i)§.", "");
      stripped = stripped.replaceAll("\\s+", " ").trim();
      return stripped.toLowerCase(Locale.ROOT);
   }

   private void doSwap(int slot) {
      if (this.swapVersion.is("1.16.5")) {
         this.doSwap1165(slot);
      } else {
         this.doSwap1214(slot);
      }
   }

   private void doSwap1214(int slot) {
      if (slot >= 36 && slot <= 44) {
         int hotbarSlot = slot - 36;
         mc.gameMode.handleContainerInput(0, 45, hotbarSlot, ContainerInput.SWAP, mc.player);
      } else {
         mc.gameMode.handleContainerInput(0, slot, 0, ContainerInput.SWAP, mc.player);
         mc.gameMode.handleContainerInput(0, 45, 0, ContainerInput.SWAP, mc.player);
         mc.gameMode.handleContainerInput(0, slot, 0, ContainerInput.SWAP, mc.player);
      }
   }

   private void doSwap1165(int slot) {
      mc.gameMode.handleContainerInput(0, slot, 40, ContainerInput.SWAP, mc.player);
   }

   private void disableSprint() {
      if (!this.sprintPaused) {
         this.sprintPaused = true;
      }
      if (mc.options != null && mc.options.keySprint != null) {
         mc.options.keySprint.setDown(false);
      }
      if (mc.player != null) {
         mc.player.setSprinting(false);
      }
   }

   private void restoreSprint() {
      this.sprintPaused = false;
   }

   private boolean isCrystalRadiusVisible() {
      return this.triggers.get("Кристалл рядом") || this.triggers.get("Кристалл в руке") || this.triggers.get("Обсидиан в руке");
   }

   @Override
   public void onDisable() {
      this.bypassTicks = 0;
      this.swapCooldown = 0;
      this.clearOriginalOffhand();
      this.totemTakenByUs = false;
      this.returnMode = false;
      this.needFastSwap = false;
      this.safeTicks = 0;
      this.prevTickOwnedTotemInOffhand = false;
      this.restoreSprint();
      super.onDisable();
   }

   /**
    * Сбрасывает «claim» AutoTotem на офф-хенд. Вызывается когда офф-хенд
    * изменил другой модуль (например AutoSwap при ручном свапе).
    */
   public void releaseOffhandClaim() {
      this.totemTakenByUs = false;
      this.returnMode = false;
      this.clearOriginalOffhand();
      this.safeTicks = 0;
      this.prevTickOwnedTotemInOffhand = false;
   }
}
