package vesence.module.impl.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.Direction;
import org.joml.Vector2f;
import net.minecraft.world.phys.Vec3;
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
   private AABB crystalArea;
   private boolean blocked;
   private boolean internalInteract;
   private boolean bindWasPressed;

   public AutoExplosion() {
      this.addSettings(new Setting[]{modeBaxa, bind, explosionOnRightClick, keepCrystal});
   }

   @EventInit
   public void onPacket(EventPacket event) {
      if (mc.player == null || mc.level == null) {
         return;
      }
      if (!event.isSend()) {
         return;
      }
      if (this.internalInteract) {
         return;
      }

      if (event.getPacket() instanceof ServerboundUseItemOnPacket packet) {
         BlockHitResult hit = packet.hitResult();
         BlockPos clickedPos = hit.getBlockPos();
         BlockPos placePos = clickedPos.relative(hit.getDirection());

         if (this.isHoldingObsidian() && this.isInRange(placePos)
            && !mc.player.getCooldowns().isOnCooldown(new ItemStack(Items.END_CRYSTAL))) {
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
      if (mc.player == null || mc.level == null) {
         this.reset();
         return;
      }

      if (this.modeBaxa.is("По бинду")) {
         boolean pressed = this.bind.isPressed();
         if (pressed && !this.bindWasPressed && mc.gui.screen() == null) {
            this.placeObsidianByCrosshair();
         }
         this.bindWasPressed = pressed;
      }

      if (this.needSync) {
         this.needSync = false;
         this.restoreSelectedSlot();
      }

      if (this.targetPos != null) {
         if (mc.level.getBlockState(this.targetPos).isAir()) {
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

      this.rotateTo(Vec3.atCenterOf(pos));

      this.oldSlot = mc.player.getInventory().getSelectedSlot();
      mc.getConnection().send(new ServerboundSetCarriedItemPacket(this.targetSlot));
      mc.player.getInventory().setSelectedSlot(this.targetSlot);

      Vec3 hitVec = Vec3.atCenterOf(pos).add(0.0, 0.5, 0.0);
      BlockHitResult result = new BlockHitResult(hitVec, Direction.UP, pos, false);
      this.sendInteract(InteractionHand.MAIN_HAND, result);
      mc.player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);

      this.needSync = true;
      this.crystalArea = this.boxFromBlock(pos.above()).inflate(0.1);
      this.targetPos = null;
   }

   private void processCrystalArea() {
      if (this.crystalArea == null) {
         return;
      }

      for (Entity entity : mc.level.getEntities((Entity) null, this.crystalArea, e -> true)) {
         if (!(entity instanceof EndCrystal crystal) || !crystal.isAlive()) {
            continue;
         }

         if (!crystal.getBoundingBox().contains(mc.player.getEyePosition())) {
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
      if (mc.player.getCooldowns().isOnCooldown(new ItemStack(Items.END_CRYSTAL))) {
         return false;
      }
      if (this.isHoldingBlockForPlace()) {
         return false;
      }

      Block block = mc.level.getBlockState(clickedPos).getBlock();
      if (block != Blocks.OBSIDIAN && block != Blocks.BEDROCK) {
         return false;
      }

      return mc.level.getBlockState(clickedPos.above()).isAir();
   }

   private boolean placeCrystalFromOffhand(BlockHitResult hit, BlockPos clickedPos) {
      int slot = this.findScreenSlot(Items.END_CRYSTAL);
      if (slot == -1 && mc.player.getOffhandItem().getItem() != Items.END_CRYSTAL) {
         return false;
      }

      boolean swapped = false;
      if (mc.player.getOffhandItem().getItem() != Items.END_CRYSTAL) {
         this.swapSlotToOffhand(slot);
         swapped = true;
      }

      this.sendInteract(InteractionHand.OFF_HAND, hit);
      mc.player.swing(InteractionHand.OFF_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
      this.crystalArea = this.boxFromBlock(clickedPos.above()).inflate(0.1);

      if (swapped) {
         this.swapSlotToOffhand(slot);
         mc.player.connection.send(new ServerboundContainerClosePacket(0));
      }
      return true;
   }

   private void placeObsidianByCrosshair() {
      int obsidianSlot = this.findScreenSlot(Items.OBSIDIAN);
      int crystalSlot = this.findCrystalSlot();
      if (obsidianSlot == -1 || crystalSlot == -1) {
         return;
      }
      if (!(mc.hitResult instanceof BlockHitResult hit)) {
         return;
      }
      if (hit.getType() != HitResult.Type.BLOCK) {
         return;
      }
      if (mc.level.getBlockState(hit.getBlockPos()).isAir()) {
         return;
      }

      BlockPos placePos = hit.getBlockPos().relative(hit.getDirection());
      this.targetPos = placePos;
      this.targetSlot = crystalSlot;
      this.blocked = true;

      this.swapSlotToOffhand(obsidianSlot);
      this.sendInteract(InteractionHand.OFF_HAND, hit);
      mc.player.swing(InteractionHand.OFF_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
      this.swapSlotToOffhand(obsidianSlot);
      mc.player.connection.send(new ServerboundContainerClosePacket(0));
   }

   private void attackCrystal(EndCrystal crystal) {
      // TODO(port26): ServerboundInteractPacket.attack() удалён в 26.3 — атака кодируется hand=null.
      mc.getConnection().send(new ServerboundInteractPacket(crystal.getId(), null, Vec3.ZERO, false));
      mc.player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
   }

   private void sendInteract(InteractionHand hand, BlockHitResult hitResult) {
      this.internalInteract = true;
      try {
         mc.getConnection().send(new ServerboundUseItemOnPacket(hand, hitResult, 0));
      } finally {
         this.internalInteract = false;
      }
   }

   private void rotateTo(Vec3 vec) {
      Vector2f rotation = AuraUtil.getRotations(vec);
      URotations.update(new Rotation(rotation.x, rotation.y), 360, 360, 360, 360, 1, 2, false);
   }

   private boolean canPlaceCrystal(BlockPos pos) {
      BlockPos up1 = pos.above();
      BlockPos up2 = pos.above(2);

      if (!mc.level.getBlockState(up1).isAir()) {
         return false;
      }
      if (!mc.level.getBlockState(up2).isAir()) {
         return false;
      }

      AABB box = new AABB(
         up1.getX(), up1.getY(), up1.getZ(),
         up1.getX() + 1.0, up1.getY() + 2.0, up1.getZ() + 1.0
      );

      for (Entity entity : mc.level.getEntities((Entity) null, box, e -> true)) {
         if (!(entity instanceof EndCrystal)) {
            return false;
         }
      }
      return true;
   }

   private int findCrystalSlot() {
      for (int i = 0; i < 9; i++) {
         if (mc.player.getInventory().getItem(i).getItem() == Items.END_CRYSTAL) {
            return i;
         }
      }
      return -1;
   }

   private int findScreenSlot(Item item) {
      for (int i = 9; i < 45; i++) {
         ItemStack stack = mc.player.inventoryMenu.getSlot(i).getItem();
         if (stack.getItem() == item) {
            return i;
         }
      }
      return -1;
   }

   private void swapSlotToOffhand(int slot) {
      if (slot >= 36 && slot <= 44) {
         mc.gameMode.handleContainerInput(0, 45, slot - 36, ContainerInput.SWAP, mc.player);
         return;
      }

      mc.gameMode.handleContainerInput(0, slot, 0, ContainerInput.SWAP, mc.player);
      mc.gameMode.handleContainerInput(0, 45, 0, ContainerInput.SWAP, mc.player);
      mc.gameMode.handleContainerInput(0, slot, 0, ContainerInput.SWAP, mc.player);
   }

   private void restoreSelectedSlot() {
      if (this.oldSlot != -1) {
         mc.getConnection().send(new ServerboundSetCarriedItemPacket(this.oldSlot));
         mc.player.getInventory().setSelectedSlot(this.oldSlot);
         this.oldSlot = -1;
      }
   }

   private AABB boxFromBlock(BlockPos pos) {
      return new AABB(
         pos.getX(), pos.getY(), pos.getZ(),
         pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0
      );
   }

   private boolean isHoldingObsidian() {
      return mc.player.getMainHandItem().getItem() == Items.OBSIDIAN
         || mc.player.getOffhandItem().getItem() == Items.OBSIDIAN;
   }

   private boolean isHoldingBlockForPlace() {
      Item main = mc.player.getMainHandItem().getItem();
      Item off = mc.player.getOffhandItem().getItem();

      return main instanceof BlockItem && main != Items.PLAYER_HEAD
         || off instanceof BlockItem && off != Items.PLAYER_HEAD;
   }

   private boolean isInRange(BlockPos pos) {
      return mc.player.getEyePosition().distanceTo(Vec3.atCenterOf(pos)) <= INTERACT_RANGE;
   }

   private void reset() {
      if (this.oldSlot != -1 && mc.player != null && mc.getConnection() != null) {
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
