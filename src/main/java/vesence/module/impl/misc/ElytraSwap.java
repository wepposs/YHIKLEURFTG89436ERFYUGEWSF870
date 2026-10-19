package vesence.module.impl.misc;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.packet.c2s.play.ServerboundPlayerCommandPacket.Mode;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.Holder;
import net.minecraft.screen.slot.ClickType;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.util.PlayerInput;
import vesence.Vesence;
import vesence.event.EventInit;
import vesence.event.impl.EventUpdate;
import vesence.mixin.BossBarHudAccessor;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.impl.BindSettings;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.MoveComponent;

/**
 * ElytraSwap («elytra helper») — полный порт ru.whylol.client.modules.impl.misc.ElytraSwap
 * из RelevantPremiumpp4 (MC 1.21.4) в vesence (MC 1.21.11, Fabric).
 *
 * Возможности (все чекбоксы сохранены):
 *  - Бинд элитры: авто-свап элитра &lt;-&gt; лучший нагрудник (учёт зачарований/прочности,
 *    приоритет материала), с опциональным обходом Grim.
 *  - Бинд фейерверка: использование ракеты В ПОЛЁТЕ. Реализовано ЧЕРЕЗ Assist
 *    (Assist.useFirework, режим SpookyTime) — работает даже когда ракеты лежат в
 *    ОСНОВНОМ инвентаре (9..35). bypassGround выбирает технику свапа (main-hand / офф-хенд).
 *  - Авто-взлёт: прыжок с земли + старт глайда в воздухе.
 *  - Обход Grim: 3-тиковый bypass вокруг свапа брони (стоп-спринт + блок движения
 *    через MoveComponent.stop — замена EventMoveInput).
 *  - Обход Граунд: техника использования фейерверка (см. выше).
 *  - Sync InventoryWalk: в vesence нет модуля InventoryWalk — чекбокс сохранён для
 *    паритета, действие no-op (см. syncInventoryWalk()).
 *  - Проверка BossBar: при наличии боссбара принудительно надевать нагрудник.
 *
 * Адаптации 1.21.4/ru.whylol -&gt; 1.21.11/vesence:
 *  - EventBinding отсутствует -&gt; rising-edge опрос биндов в onUpdate (checkBinds).
 *  - EventMoveInput отсутствует -&gt; блок движения через MoveComponent.stop.
 *  - Инлайн стоп/восстановление спринта (как ServerHelper.beginBypass).
 *  - getInventory().getSelectedSlot()/setSelectedSlot() вместо поля selectedSlot.
 *  - Фейерверк маршрутизирован через ServerHelper (по требованию), устаревшие
 *    executePacketFireworkSwap/handlePacketSwap/handleFireworkReturn не портируются
 *    (мёртвый/заменённый код), живая логика не потеряна.
 */
@IModule(name = "ElytraSwap", description = "Автоматический свап элитр", category = Category.MISC, bind = -1)
@Environment(EnvType.CLIENT)
public class ElytraSwap extends Module {

   private static final Set<net.minecraft.item.Item> CHESTPLATES = Set.of(
         Items.NETHERITE_CHESTPLATE, Items.DIAMOND_CHESTPLATE, Items.IRON_CHESTPLATE,
         Items.GOLDEN_CHESTPLATE, Items.CHAINMAIL_CHESTPLATE, Items.LEATHER_CHESTPLATE);

   private final BindSettings elytraBind      = new BindSettings("Бинд элитры", -1);
   private final BindSettings fireworkBind    = new BindSettings("Бинд фейерверка", -1);
   private final BooleanSetting autofly        = new BooleanSetting("Авто-взлёт", true);
   private final BooleanSetting bypassgrim     = new BooleanSetting("Обходить Grim", true);
   private final BooleanSetting bypassGround   = new BooleanSetting("Обходить Граунд", true);
   private final BooleanSetting syncInventoryWalk = new BooleanSetting("Sync InventoryWalk", true);
   private final BooleanSetting checkBossBar   = new BooleanSetting("Проверка BossBar", true);

   private boolean swapElytraQueued;
   private boolean useFirework;
   private int bypassTicks;
   private int swapCooldown;
   private boolean sprintStopped;
   private boolean moveBlocked;
   private boolean wasElytraPressed;
   private boolean wasFireworkPressed;

   public ElytraSwap() {
      this.addSettings(this.elytraBind, this.fireworkBind, this.autofly, this.bypassgrim,
            this.bypassGround, this.syncInventoryWalk, this.checkBossBar);
   }

   @Override
   public void onEnable() {
      this.resetState();
      super.onEnable();
   }

   @Override
   public void onDisable() {
      this.resetState();
      super.onDisable();
   }

   private void resetState() {
      this.swapElytraQueued = false;
      this.useFirework = false;
      this.bypassTicks = 0;
      this.swapCooldown = 0;
      this.wasElytraPressed = false;
      this.wasFireworkPressed = false;
      this.restoreSprint();
      if (this.moveBlocked) {
         MoveComponent.stop = false;
         this.moveBlocked = false;
      }
   }

   @EventInit
   public void onUpdate(EventUpdate event) {
      if (mc.player == null || mc.level == null || mc.gameMode == null || mc.getConnection() == null) {
         return;
      }

      this.checkBinds();

      if (this.swapCooldown > 0) {
         this.swapCooldown--;
      }

      if (this.bypassTicks > 0) {
         if (this.bypassgrim.get()) {
            MoveComponent.stop = true;
            this.moveBlocked = true;
         }
         mc.player.setSprinting(false);
         this.bypassTicks--;
         if (this.bypassTicks == 1) {
            this.performSwap();
         }
         if (this.bypassTicks == 0) {
            this.endBypassCleanup();
         }
      } else {
         if (this.swapElytraQueued) {
            if (this.swapCooldown > 0) {
               this.swapElytraQueued = false;
               return;
            }

            if (this.bypassgrim.get()) {
               this.disableSprint();
               MoveComponent.stop = true;
               this.moveBlocked = true;
               this.bypassTicks = 3;
               this.swapCooldown = 1;
            } else {
               this.performSwap();
               this.swapCooldown = 1;
            }

            this.swapElytraQueued = false;
         }

         if (this.useFirework) {
            if (mc.player.isGliding()) {
               Assist helper = this.assist();
               boolean used = helper != null && helper.useFirework(this.bypassGround.get());
               if (!used) {
                  this.sendError("Нет Фейерверков!");
               }
            }

            this.useFirework = false;
         }

         if (this.autofly.get() && this.bypassTicks == 0) {
            ItemStack chestStack = mc.player.getEquippedStack(EquipmentSlot.CHEST);
            if (chestStack.isOf(Items.ELYTRA)
                  && !mc.player.isTouchingWater()
                  && !mc.player.isInLava()
                  && mc.player.onGround()
                  && !mc.options.jumpKey.isPressed()) {
               mc.player.jump();
            } else if (chestStack.isOf(Items.ELYTRA) && this.isElytraUsable(chestStack)
                  && !mc.player.isGliding() && !mc.player.onGround()) {
               mc.player.startGliding();
               mc.player.connection.sendPacket(new ServerboundPlayerCommandPacket(mc.player, Mode.START_FALL_FLYING));
            }
         }
      }
   }

   /** Rising-edge опрос биндов (в vesence нет EventBinding). */
   private void checkBinds() {
      // ВАЖНО: бинды работают ДАЖЕ когда открыт инвентарь — это и есть
      // «использовать фейерверк даже с инвентаря»: игрок открывает инвентарь,
      // где лежат ракеты, и жмёт бинд. Свап/использование идёт через
      // PlayerScreenHandler (syncId 0), который активен при открытом инвентаре.
      // Блокируем ТОЛЬКО чат, чтобы набор текста не триггерил бинды.
      if (mc.screen instanceof net.minecraft.client.gui.screen.ChatScreen) return;

      boolean elytraPressed = this.elytraBind.key != -1 && this.elytraBind.isPressed();
      if (elytraPressed && !this.wasElytraPressed) {
         this.swapElytraQueued = true;
      }
      this.wasElytraPressed = elytraPressed;

      boolean fireworkPressed = this.fireworkBind.key != -1 && this.fireworkBind.isPressed();
      if (fireworkPressed && !this.wasFireworkPressed) {
         this.useFirework = true;
      }
      this.wasFireworkPressed = fireworkPressed;
   }

   private Assist assist() {
      if (Vesence.get == null || Vesence.get.getManager() == null) return null;
      return Vesence.get.getManager().get(Assist.class);
   }

   private void performSwap() {
      int slotElytra = this.findBestElytraSlot();
      int chestSlot = this.findBestChestplateSlot();
      boolean wearingElytra = mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);
      boolean wearingChest = CHESTPLATES.contains(mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem());

      boolean needChestplate = wearingElytra || !wearingChest;

      // Если есть boss bar — принудительно надеваем нагрудник.
      if (this.hasBossBar() && !wearingChest) {
         needChestplate = true;
      }

      if (needChestplate) {
         if (chestSlot == -1) {
            this.sendError("Нет нагрудника!");
            this.endBypassCleanup();
            return;
         }
         this.doSwap(chestSlot);
      } else {
         if (slotElytra == -1) {
            this.sendError("Нет элитры!");
            this.endBypassCleanup();
            return;
         }
         this.doSwap(slotElytra);
      }

      mc.player.connection.sendPacket(new ServerboundContainerClosePacket(0));
   }

   private void doSwap(int slot) {
      this.syncInventoryWalk();
      if (slot >= 0 && slot < 9) {
         mc.gameMode.clickSlot(0, 6, slot, ClickType.SWAP, mc.player);
      } else {
         mc.gameMode.clickSlot(0, slot, 0, ClickType.SWAP, mc.player);
         mc.gameMode.clickSlot(0, 6, 0, ClickType.SWAP, mc.player);
         mc.gameMode.clickSlot(0, slot, 0, ClickType.SWAP, mc.player);
      }
   }

   private boolean hasBossBar() {
      if (!this.checkBossBar.get()) return false;
      try {
         BossBarHud bossBarHud = mc.gui.getBossBarHud();
         Map<UUID, BossBar> bars = ((BossBarHudAccessor) (Object) bossBarHud).getBossBars();
         return bars != null && !bars.isEmpty();
      } catch (Exception ignored) {
         return false;
      }
   }

   /**
    * В relevant синхронизировал пакет-фильтр инвентаря (InventoryWalk). В vesence
    * модуля InventoryWalk нет — чекбокс сохранён для паритета, действие no-op.
    */
   private void syncInventoryWalk() {
      // no-op: нет аналога InventoryWalk.stopTick(3) в vesence.
   }

   private void disableSprint() {
      if (this.sprintStopped) return;
      if (mc.player == null) return;
      if (mc.player.isSprinting()) {
         try {
            mc.player.connection.sendPacket(
                  new PlayerInputC2SPacket(new PlayerInput(false, false, false, false, false, false, false)));
            mc.player.setSprinting(false);
            mc.player.connection.sendPacket(new ServerboundPlayerCommandPacket(mc.player, Mode.STOP_SPRINTING));
            mc.options.sprintKey.setPressed(false);
         } catch (Throwable ignored) {}
      }
      this.sprintStopped = true;
   }

   private void restoreSprint() {
      if (!this.sprintStopped) return;
      this.sprintStopped = false;
      if (mc.player == null) return;
      try {
         mc.player.connection.sendPacket(new PlayerInputC2SPacket(mc.player.input.playerInput));
      } catch (Throwable ignored) {}
   }

   private void endBypassCleanup() {
      this.bypassTicks = 0;
      this.restoreSprint();
      if (this.moveBlocked) {
         MoveComponent.stop = false;
         this.moveBlocked = false;
      }
   }

   private boolean isElytraUsable(ItemStack stack) {
      return stack.getDamage() < stack.getMaxDamage() - 1;
   }

   private void sendError(String message) {
      if (mc.player != null) {
         mc.player.sendMessage(Component.literal(message).formatted(ChatFormatting.RED, ChatFormatting.BOLD), false);
      }
   }

   // ── слот-финдеры (порт InventoryUtils) ────────────────────────────────────

   private int findBestElytraSlot() {
      if (mc.player == null) return -1;
      int bestSlot = -1;
      double bestScore = -1.0;
      for (int slot = 0; slot < 36; slot++) {
         ItemStack stack = mc.player.getInventory().getItem(slot);
         if (stack.getItem() == Items.ELYTRA) {
            int protection = getEnchantmentLevel(stack, Enchantments.PROTECTION);
            int unbreaking = getEnchantmentLevel(stack, Enchantments.UNBREAKING);
            int mending = getEnchantmentLevel(stack, Enchantments.MENDING);
            int maxDurability = stack.getMaxDamage();
            int currentDamage = stack.getDamage();
            double durabilityRatio = maxDurability == 0 ? 1.0 : (double) (maxDurability - currentDamage) / maxDurability;
            double score = protection * 100 + unbreaking * 10 + (mending > 0 ? 1 : 0) + durabilityRatio * 10.0;
            if (score > bestScore) {
               bestScore = score;
               bestSlot = slot;
            }
         }
      }
      return bestSlot;
   }

   private int findBestChestplateSlot() {
      if (mc.player == null) return -1;
      int bestSlot = -1;
      double bestScore = -1.0;
      for (int slot = 0; slot < 36; slot++) {
         ItemStack stack = mc.player.getInventory().getItem(slot);
         int priority = getChestplatePriority(stack.getItem());
         if (priority <= 0) continue;
         int protection = getEnchantmentLevel(stack, Enchantments.PROTECTION);
         int unbreaking = getEnchantmentLevel(stack, Enchantments.UNBREAKING);
         int mending = getEnchantmentLevel(stack, Enchantments.MENDING);
         int maxDamage = stack.getMaxDamage();
         int damage = stack.getDamage();
         double durabilityRatio = maxDamage == 0 ? 1.0 : (double) (maxDamage - damage) / maxDamage;
         double score = priority * 10000.0 + protection * 100.0 + unbreaking * 10.0 + (mending > 0 ? 1 : 0) + durabilityRatio * 10.0;
         if (score > bestScore) {
            bestScore = score;
            bestSlot = slot;
         }
      }
      return bestSlot;
   }

   private static int getChestplatePriority(net.minecraft.item.Item item) {
      if (item == Items.NETHERITE_CHESTPLATE) return 5;
      if (item == Items.DIAMOND_CHESTPLATE) return 4;
      if (item == Items.IRON_CHESTPLATE) return 3;
      if (item == Items.GOLDEN_CHESTPLATE) return 2;
      if (item == Items.CHAINMAIL_CHESTPLATE) return 2;
      if (item == Items.LEATHER_CHESTPLATE) return 1;
      return 0;
   }

   private static int getEnchantmentLevel(ItemStack stack, ResourceKey<Enchantment> enchantmentKey) {
      ItemEnchantmentsComponent enchantments = stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
      for (Holder<Enchantment> enchantment : enchantments.getEnchantments()) {
         if (enchantment.matchesKey(enchantmentKey)) {
            return enchantments.getLevel(enchantment);
         }
      }
      return 0;
   }
}
