package vesence.module.impl.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import vesence.event.EventInit;
import vesence.event.impl.EventPacket;
import vesence.event.impl.EventUpdate;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BindSettings;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.impl.combat.auraComponent.AuraUtil;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.Rotation;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.URotations;
import vesence.utils.network.NetworkUtils;

@IModule(name = "AutoExplosion", description = "Автоматически взрывает кристалл", category = Category.COMBAT, bind = -1)
@Environment(EnvType.CLIENT)
public final class AutoExplosion extends Module {

   private final ModeSetting modeBaxa = new ModeSetting("Режим взрыва", "Авто", "Авто", "По бинду");
   private final BindSettings bind = new BindSettings("Бинд", -1)
      .hidden(() -> !modeBaxa.is("По бинду"));
   private final BooleanSetting explosionOnRightClick = new BooleanSetting("Взрыв по ПКМ", true);
   private final BooleanSetting keepCrystal = new BooleanSetting("Оставлять кристалл", false);

   private static final double INTERACT_RANGE = 4.5;

   private BlockPos targetPos;
   private int targetSlot = -1;
   private int oldSlot = -1;
   private boolean needSync;
   private Box crystalArea;
   private boolean blocked;
   private boolean internalInteract;
   private boolean bindWasPressed;

   public AutoExplosion() {
      this.addSettings(new Setting[]{modeBaxa, bind, explosionOnRightClick, keepCrystal});
   }

   @EventInit
   public void onPacket(EventPacket event) {
      if (mc.player == null || mc.world == null) {
         return;
      }
      if (!event.isSend()) {
         return;
      }
      if (this.internalInteract) {
         return;
      }

      if (event.getPacket() instanceof PlayerInteractBlockC2SPacket packet) {
         BlockHitResult hit = packet.getBlockHitResult();
         BlockPos clickedPos = hit.getBlockPos();
         BlockPos placePos = clickedPos.offset(hit.getSide());

         if (this.isHoldingObsidian() && this.isInRange(placePos)
            && !mc.player.getItemCooldownManager().isCoolingDown(new ItemStack(Items.END_CRYSTAL))) {
            int crystalSlot = this.findCrystalSlot();
            if (crystalSlot != -1) {
               this.targetPos = placePos;
               this.targetSlot = crystalSlot;
               this.blocked = true;
            }
         }

         if (this.explosionOnRightClick.get() && this.shouldPlaceByRightClick(clickedPos)) {
            if (this.placeCrystalFromOffhand(hit, clickedPos)) {
               event.cancel();
            }
         }
      }
   }

   @EventInit
   public void onTick(EventUpdate event) {
      if (mc.player == null || mc.world == null) {
         this.reset();
         return;
      }

      if (this.modeBaxa.is("По бинду")) {
         boolean pressed = this.bind.isPressed();
         if (pressed && !this.bindWasPressed && mc.currentScreen == null) {
            this.placeObsidianByCrosshair();
         }
         this.bindWasPressed = pressed;
      }

      if (this.needSync) {
         this.needSync = false;
         this.restoreSelectedSlot();
      }

      if (this.targetPos != null) {
         if (mc.world.getBlockState(this.targetPos).isAir()) {
            this.targetPos = null;
         } else if (this.blocked) {
            this.blocked = false;
         } else {
            this.tryPlaceCrystalFast(this.targetPos);
         }
      }

      this.processCrystalArea();
   }

   private void tryPlaceCrystalFast(BlockPos pos) {
      if (this.targetSlot < 0 || this.targetSlot > 8 || !this.canPlaceCrystal(pos)) {
         return;
      }

      this.rotateTo(Vec3d.ofCenter(pos));

      this.oldSlot = mc.player.getInventory().getSelectedSlot();
      mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(this.targetSlot));
      mc.player.getInventory().setSelectedSlot(this.targetSlot);

      Vec3d hitVec = Vec3d.ofCenter(pos).add(0.0, 0.5, 0.0);
      BlockHitResult result = new BlockHitResult(hitVec, Direction.UP, pos, false);
      this.sendInteract(Hand.MAIN_HAND, result);
      mc.player.swingHand(Hand.MAIN_HAND);

      this.needSync = true;
      this.crystalArea = this.boxFromBlock(pos.up()).expand(0.1);
      this.targetPos = null;
   }

   private void processCrystalArea() {
      if (this.crystalArea == null) {
         return;
      }

      for (Entity entity : mc.world.getOtherEntities(null, this.crystalArea)) {
         if (!(entity instanceof EndCrystalEntity crystal) || !crystal.isAlive()) {
            continue;
         }

         if (!crystal.getBoundingBox().contains(mc.player.getEyePos())) {
            this.rotateTo(crystal.getBoundingBox().getCenter());
         }
         this.attackCrystal(crystal);
         this.crystalArea = null;
         if (!this.keepCrystal.get()) {
            this.restoreSelectedSlot();
         }
         return;
      }
   }

   private boolean shouldPlaceByRightClick(BlockPos clickedPos) {
      if (mc.player.getItemCooldownManager().isCoolingDown(new ItemStack(Items.END_CRYSTAL))) {
         return false;
      }
      if (this.isHoldingBlockForPlace()) {
         return false;
      }

      Block block = mc.world.getBlockState(clickedPos).getBlock();
      if (block != Blocks.OBSIDIAN && block != Blocks.BEDROCK) {
         return false;
      }

      return mc.world.getBlockState(clickedPos.up()).isAir();
   }

   private boolean placeCrystalFromOffhand(BlockHitResult hit, BlockPos clickedPos) {
      int slot = this.findScreenSlot(Items.END_CRYSTAL);
      if (slot == -1 && mc.player.getOffHandStack().getItem() != Items.END_CRYSTAL) {
         return false;
      }

      boolean swapped = false;
      if (mc.player.getOffHandStack().getItem() != Items.END_CRYSTAL) {
         this.swapSlotToOffhand(slot);
         swapped = true;
      }

      this.sendInteract(Hand.OFF_HAND, hit);
      mc.player.swingHand(Hand.OFF_HAND);
      this.crystalArea = this.boxFromBlock(clickedPos.up()).expand(0.1);

      if (swapped) {
         this.swapSlotToOffhand(slot);
         mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(0));
      }
      return true;
   }

   private void placeObsidianByCrosshair() {
      int obsidianSlot = this.findScreenSlot(Items.OBSIDIAN);
      int crystalSlot = this.findCrystalSlot();
      if (obsidianSlot == -1 || crystalSlot == -1) {
         return;
      }
      if (!(mc.crosshairTarget instanceof BlockHitResult hit)) {
         return;
      }
      if (hit.getType() != HitResult.Type.BLOCK) {
         return;
      }
      if (mc.world.getBlockState(hit.getBlockPos()).isAir()) {
         return;
      }

      BlockPos placePos = hit.getBlockPos().offset(hit.getSide());
      this.targetPos = placePos;
      this.targetSlot = crystalSlot;
      this.blocked = true;

      this.swapSlotToOffhand(obsidianSlot);
      this.sendInteract(Hand.OFF_HAND, hit);
      mc.player.swingHand(Hand.OFF_HAND);
      this.swapSlotToOffhand(obsidianSlot);
      mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(0));
   }

   private void attackCrystal(EndCrystalEntity crystal) {
      mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(crystal, false));
      mc.player.swingHand(Hand.MAIN_HAND);
   }

   private void sendInteract(Hand hand, BlockHitResult hitResult) {
      this.internalInteract = true;
      try {
         mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(hand, hitResult, 0));
      } finally {
         this.internalInteract = false;
      }
   }

   private void rotateTo(Vec3d vec) {
      Vec2f rotation = AuraUtil.getRotations(vec);
      URotations.update(new Rotation(rotation.x, rotation.y), 360, 360, 360, 360, 1, 2, false);
   }

   private boolean canPlaceCrystal(BlockPos pos) {
      BlockPos up1 = pos.up();
      BlockPos up2 = pos.up(2);

      if (!mc.world.getBlockState(up1).isAir()) {
         return false;
      }
      if (!mc.world.getBlockState(up2).isAir()) {
         return false;
      }

      Box box = new Box(
         up1.getX(), up1.getY(), up1.getZ(),
         up1.getX() + 1.0, up1.getY() + 2.0, up1.getZ() + 1.0
      );

      for (Entity entity : mc.world.getOtherEntities(null, box)) {
         if (!(entity instanceof EndCrystalEntity)) {
            return false;
         }
      }
      return true;
   }

   private int findCrystalSlot() {
      for (int i = 0; i < 9; i++) {
         if (mc.player.getInventory().getStack(i).getItem() == Items.END_CRYSTAL) {
            return i;
         }
      }
      return -1;
   }

   private int findScreenSlot(Item item) {
      for (int i = 9; i < 45; i++) {
         ItemStack stack = mc.player.playerScreenHandler.getSlot(i).getStack();
         if (stack.getItem() == item) {
            return i;
         }
      }
      return -1;
   }

   private void swapSlotToOffhand(int slot) {
      if (slot >= 36 && slot <= 44) {
         mc.interactionManager.clickSlot(0, 45, slot - 36, SlotActionType.SWAP, mc.player);
         return;
      }

      mc.interactionManager.clickSlot(0, slot, 0, SlotActionType.SWAP, mc.player);
      mc.interactionManager.clickSlot(0, 45, 0, SlotActionType.SWAP, mc.player);
      mc.interactionManager.clickSlot(0, slot, 0, SlotActionType.SWAP, mc.player);
   }

   private void restoreSelectedSlot() {
      if (this.oldSlot != -1) {
         mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(this.oldSlot));
         mc.player.getInventory().setSelectedSlot(this.oldSlot);
         this.oldSlot = -1;
      }
   }

   private Box boxFromBlock(BlockPos pos) {
      return new Box(
         pos.getX(), pos.getY(), pos.getZ(),
         pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0
      );
   }

   private boolean isHoldingObsidian() {
      return mc.player.getMainHandStack().getItem() == Items.OBSIDIAN
         || mc.player.getOffHandStack().getItem() == Items.OBSIDIAN;
   }

   private boolean isHoldingBlockForPlace() {
      Item main = mc.player.getMainHandStack().getItem();
      Item off = mc.player.getOffHandStack().getItem();

      return main instanceof BlockItem && main != Items.PLAYER_HEAD
         || off instanceof BlockItem && off != Items.PLAYER_HEAD;
   }

   private boolean isInRange(BlockPos pos) {
      return mc.player.getEyePos().distanceTo(Vec3d.ofCenter(pos)) <= INTERACT_RANGE;
   }

   private void reset() {
      if (this.oldSlot != -1 && mc.player != null && mc.getNetworkHandler() != null) {
         this.restoreSelectedSlot();
      }
      this.targetPos = null;
      this.targetSlot = -1;
      this.needSync = false;
      this.crystalArea = null;
      this.blocked = false;
      this.internalInteract = false;
      this.bindWasPressed = false;
   }

   @Override
   public void onEnable() {
      super.onEnable();
      this.reset();
   }

   @Override
   public void onDisable() {
      super.onDisable();
      this.reset();
   }
}
