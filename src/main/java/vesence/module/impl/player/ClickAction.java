package vesence.module.impl.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import vesence.event.EventInit;
import vesence.event.impl.EventUpdate;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BindSettings;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.MoveComponent;
import vesence.utils.friends.FriendStorage;
import vesence.utils.player.InventoryActionUtil;

@IModule(name = "ClickAction", description = "Действия по кнопке: друзья, жемчуг, выстрел из арбалета", category = Category.PLAYER, bind = -1)
@Environment(EnvType.CLIENT)
public class ClickAction extends Module {

   private final BooleanSetting friendEnabled = new BooleanSetting("Друг по кнопке", true);
   private final SliderSetting friendDistance = new SliderSetting("Дистанция добавления", 4.0, 3.0, 6.0, 1.0)
      .hidden(() -> !friendEnabled.get());
   private final BindSettings friendBind = new BindSettings("Кнопка друга", -1)
      .hidden(() -> !friendEnabled.get());

   private final BooleanSetting pearlEnabled = new BooleanSetting("Жемчуг по кнопке", true);
   private final ModeSetting pearlMode = new ModeSetting("Тип кидания", "Пакетный", "Пакетный", "Легитный")
      .hidden(() -> !pearlEnabled.get());
   private final BindSettings pearlBind = new BindSettings("Кнопка жемчуга", -1)
      .hidden(() -> !pearlEnabled.get());

   private final BooleanSetting crossbowEnabled = new BooleanSetting("Выстрел из арбалета", true);
   private final BindSettings crossbowBind = new BindSettings("Кнопка выстрела", -1)
      .hidden(() -> !crossbowEnabled.get());

   private boolean friendBindWasPressed = false;

   private boolean pearlBindWasPressed = false;
   private int state = 0;
   private int savedCurrentSlot = -1;
   private int savedInvSlot = -1;
   private int swapSlot = 8;
   private boolean wasFull = false;
   private enum LegitState { IDLE, SWITCHING, THROWING, RETURNING }
   private LegitState legitState = LegitState.IDLE;
   private long legitStateTime = 0;
   private int legitPearlSlot = -1;
   private int legitOldSlot = -1;

   private boolean crossbowBindWasPressed = false;
   private boolean crossbowCharging = false;

   public ClickAction() {
      this.addSettings(new Setting[]{
         friendEnabled, friendDistance, friendBind,
         pearlEnabled, pearlMode, pearlBind,
         crossbowEnabled, crossbowBind
      });
   }

   @EventInit
   public void onUpdate(EventUpdate e) {
      if (!this.enable || mc.player == null || mc.world == null) {
         return;
      }

      if (this.friendEnabled.get()) {
         this.handleFriend();
      }

      if (this.pearlEnabled.get()) {
         this.handlePearl();
      } else {
         this.state = 0;
         this.legitState = LegitState.IDLE;
      }

      if (this.crossbowEnabled.get()) {
         this.handleCrossbow();
      }
   }

   private void handleFriend() {
      float tickDelta = mc.getRenderTickCounter().getTickProgress(true);
      Vec3d eyePos = mc.player.getCameraPosVec(tickDelta);
      Vec3d lookVec = mc.player.getRotationVec(tickDelta);
      double maxDist = this.friendDistance.get();
      Vec3d endVec = eyePos.add(lookVec.multiply(maxDist));
      Box searchBox = mc.player.getBoundingBox().stretch(lookVec.multiply(maxDist)).expand(1.0);

      EntityHitResult hitResult = ProjectileUtil.raycast(
         mc.player, eyePos, endVec, searchBox,
         entity -> entity instanceof PlayerEntity && !entity.isSpectator() && entity.isAlive() && entity.canHit(),
         maxDist * maxDist
      );

      if (hitResult != null) {
         PlayerEntity target = (PlayerEntity) hitResult.getEntity();
         if (mc.player.distanceTo(target) <= maxDist) {
            String name = target.getName().getString();

            if (this.friendBind.key != -1 && this.friendBind.isKeyDown(this.friendBind.key)) {
               if (!this.friendBindWasPressed) {
                  this.friendBindWasPressed = true;
                  if (!FriendStorage.isFriend(name)) {
                     FriendStorage.addFriend(name);
                  } else {
                     FriendStorage.removeFriend(name);
                  }
               }
            } else {
               this.friendBindWasPressed = false;
            }
         }
      }
   }

   private void handleCrossbow() {
      boolean pressed = this.crossbowBind.key != -1 && this.crossbowBind.isPressed();

      if (!pressed) {

         if (this.crossbowCharging) {
            mc.options.useKey.setPressed(false);
            this.crossbowCharging = false;
         }
         this.crossbowBindWasPressed = false;
         return;
      }

      ItemStack main = mc.player.getMainHandStack();
      ItemStack off = mc.player.getOffHandStack();

      boolean mainCharged = main.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(main);
      boolean offCharged = off.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(off);
      boolean mainCrossbow = main.getItem() instanceof CrossbowItem;

      if (mainCharged || offCharged || this.findChargedCrossbowSlot() != -1) {

         if (this.crossbowCharging) {
            mc.options.useKey.setPressed(false);
            this.crossbowCharging = false;
         }
         if (!this.crossbowBindWasPressed) {
            this.crossbowBindWasPressed = true;
            this.shootCrossbow();
         }
         return;
      }

      if (mainCrossbow) {
         mc.options.useKey.setPressed(true);
         this.crossbowCharging = true;
      } else if (this.crossbowCharging) {
         mc.options.useKey.setPressed(false);
         this.crossbowCharging = false;
      }
      this.crossbowBindWasPressed = true;
   }

   private int findChargedCrossbowSlot() {
      for (int i = 0; i < 9; i++) {
         ItemStack stack = mc.player.getInventory().getStack(i);
         if (stack.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(stack)) {
            return i;
         }
      }
      return -1;
   }

   private void shootCrossbow() {
      if (mc.interactionManager == null) {
         return;
      }
      ItemStack main = mc.player.getMainHandStack();
      ItemStack off = mc.player.getOffHandStack();

      if (main.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(main)) {
         InventoryActionUtil.useHand(Hand.MAIN_HAND);
      } else if (off.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(off)) {
         InventoryActionUtil.useHand(Hand.OFF_HAND);
      } else {
         int currentSlot = mc.player.getInventory().getSelectedSlot();
         int chargedSlot = this.findChargedCrossbowSlot();
         if (chargedSlot != -1) {
            InventoryActionUtil.selectSlot(chargedSlot);
            InventoryActionUtil.useHand(Hand.MAIN_HAND);
            if (currentSlot >= 0 && currentSlot <= 8 && currentSlot != chargedSlot) {
               InventoryActionUtil.selectSlot(currentSlot);
            }
         }
      }
   }

   private void handlePearl() {
      if (mc.interactionManager == null || mc.getNetworkHandler() == null) {
         return;
      }
      if (mc.currentScreen != null) {
         return;
      }

      if (this.legitState != LegitState.IDLE) {
         long elapsed = System.currentTimeMillis() - this.legitStateTime;
         if (this.legitState == LegitState.SWITCHING) {
            if (elapsed >= 100) {
               InventoryActionUtil.useHand(Hand.MAIN_HAND);
               this.legitState = LegitState.THROWING;
               this.legitStateTime = System.currentTimeMillis();
            }
         } else if (this.legitState == LegitState.THROWING) {
            if (elapsed >= 300) {
               this.legitState = LegitState.RETURNING;
               this.legitStateTime = System.currentTimeMillis();

               if (this.state == 0) {
                  mc.player.getInventory().setSelectedSlot(this.legitOldSlot);
                  mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(this.legitOldSlot));
                  this.resetState();
               }
            }
         }
      }

      switch (this.state) {
         case 1: {
            mc.interactionManager.clickSlot(
               mc.player.currentScreenHandler.syncId,
               this.savedInvSlot, this.swapSlot, SlotActionType.SWAP, mc.player
            );
            InventoryActionUtil.closeHandledScreenIfNeeded();
            this.wasFull = true;
            if (this.pearlMode.is("Пакетный")) {
               this.state = 2;
            } else {
               this.startLegitThrow(this.savedCurrentSlot, this.swapSlot);
               this.state = 3;
            }
            return;
         }
         case 2: {
            this.throwPacket(this.savedCurrentSlot, this.swapSlot);
            this.state = 3;
            return;
         }
         case 3: {
            if (this.pearlMode.is("Легитный")) {
               if (this.legitState == LegitState.RETURNING && System.currentTimeMillis() - this.legitStateTime >= 400) {
                  mc.player.getInventory().setSelectedSlot(this.legitOldSlot);
                  mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(this.legitOldSlot));
                  this.restoreInventorySlot();
                  this.resetState();
               }
            } else {
               this.restoreInventorySlot();
               this.resetState();
            }
            return;
         }
         case 4: {
            if (this.pearlMode.is("Пакетный")) {
               this.throwPacket(this.savedCurrentSlot, this.swapSlot);
               this.resetState();
            } else {
               this.startLegitThrow(this.savedCurrentSlot, this.swapSlot);
               this.state = 0;
            }
            return;
         }
      }

      if (this.pearlBind.key != -1 && this.pearlBind.isPressed()) {
         if (!this.pearlBindWasPressed) {
            this.pearlBindWasPressed = true;
            if (!mc.player.getItemCooldownManager().isCoolingDown(new ItemStack(Items.ENDER_PEARL))) {
               this.throwPearl();
            }
         }
      } else {
         this.pearlBindWasPressed = false;
      }
   }

   private void throwPearl() {
      if (mc.player.getOffHandStack().isOf(Items.ENDER_PEARL)) {
         InventoryActionUtil.useHand(Hand.OFF_HAND);
         return;
      }

      int currentSlot = mc.player.getInventory().getSelectedSlot();
      int hotbarSlot = this.findInHotbar();
      if (hotbarSlot != -1) {
         MoveComponent.stop = true;
         this.savedCurrentSlot = currentSlot;
         this.swapSlot = hotbarSlot;
         this.state = 4;
         return;
      }

      int invSlot = -1;
      for (int i = 9; i < 36; i++) {
         if (mc.player.getInventory().getStack(i).getItem() == Items.ENDER_PEARL) {
            invSlot = i;
            break;
         }
      }
      if (invSlot == -1) {
         return;
      }

      MoveComponent.stop = true;
      this.savedCurrentSlot = currentSlot;
      this.savedInvSlot = invSlot;
      this.swapSlot = currentSlot;
      this.state = 1;
   }

   private void resetState() {
      MoveComponent.stop = false;
      MoveComponent.stopTicks = 0;
      this.state = 0;
      this.wasFull = false;
      this.legitState = LegitState.IDLE;
   }

   private void startLegitThrow(int currentSlot, int pearlSlot) {
      this.legitOldSlot = currentSlot;
      this.legitPearlSlot = pearlSlot;
      InventoryActionUtil.selectSlot(pearlSlot);
      this.legitState = LegitState.SWITCHING;
      this.legitStateTime = System.currentTimeMillis();
   }

   private void throwPacket(int currentSlot, int pearlSlot) {
      InventoryActionUtil.useHotbarSlot(pearlSlot, false);
      InventoryActionUtil.selectSlot(currentSlot);
   }

   private void restoreInventorySlot() {
      if (this.wasFull) {
         if (this.savedInvSlot != -1) {
            mc.interactionManager.clickSlot(
               mc.player.currentScreenHandler.syncId,
               this.savedInvSlot, this.swapSlot, SlotActionType.SWAP, mc.player
            );
            InventoryActionUtil.closeHandledScreenIfNeeded();
         }
         this.wasFull = false;
      } else {
         mc.interactionManager.clickSlot(
            mc.player.currentScreenHandler.syncId,
            this.swapSlot + 36, 0, SlotActionType.QUICK_MOVE, mc.player
         );
         InventoryActionUtil.closeHandledScreenIfNeeded();
      }
   }

   private int findInHotbar() {
      for (int i = 0; i < 9; i++) {
         if (mc.player.getInventory().getStack(i).getItem() == Items.ENDER_PEARL) {
            return i;
         }
      }
      return -1;
   }

   @Override
   public void onDisable() {
      this.state = 0;
      this.legitState = LegitState.IDLE;
      this.pearlBindWasPressed = false;
      this.friendBindWasPressed = false;
      this.crossbowBindWasPressed = false;
      if (this.crossbowCharging) {
         mc.options.useKey.setPressed(false);
         this.crossbowCharging = false;
      }
      MoveComponent.stop = false;
      MoveComponent.stopTicks = 0;
      super.onDisable();
   }
}
