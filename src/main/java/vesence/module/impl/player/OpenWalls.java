package vesence.module.impl.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.block.Block;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import vesence.event.EventInit;
import vesence.event.impl.EventUpdate;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;

@IModule(
   name = "OpenWalls",
   description = "Позволяет открывать контейнеры сквозь блоки",
   category = Category.PLAYER,
   bind = -1
)
@Environment(EnvType.CLIENT)
public class OpenWalls extends Module {

   private static OpenWalls instance;

   public OpenWalls() {
      instance = this;
   }

   public static OpenWalls getInstance() {
      return instance;
   }

   public static boolean isEnabled() {
      return instance != null && instance.enable;
   }

   @EventInit
   public void onUpdate(EventUpdate e) {
      if (!this.enable) return;
      if (mc.player == null || mc.level == null) return;
      if (!mc.options.keyUse.isDown()) return;
      if (mc.gui.screen() instanceof AbstractContainerScreen) return;

      Vec3 eyePos = mc.player.getEyePosition();
      Vec3 lookVec = mc.player.getViewVector(1.0F);
      double reach = 5.0;

      Vec3 endPos = eyePos.add(lookVec.scale(reach));

      BlockHitResult initialHit = mc.level.clip(
         new ClipContext(eyePos, endPos,
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            mc.player));

      if (initialHit != null && initialHit.getType() == HitResult.Type.BLOCK) {
         Block block = mc.level.getBlockState(initialHit.getBlockPos()).getBlock();
         if (isContainer(block)) return;
      }

      for (double d = 0.5; d <= reach; d += 0.5) {
         Vec3 checkPos = eyePos.add(lookVec.scale(d));
         BlockPos blockPos = BlockPos.containing(checkPos);

         Block block = mc.level.getBlockState(blockPos).getBlock();
         if (isContainer(block)) {
            mc.player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
               new BlockHitResult(
                  new Vec3(blockPos.getX() + 0.5, blockPos.getY() + 1.0, blockPos.getZ() + 0.5),
                  Direction.UP, blockPos, false));
            return;
         }
      }
   }

   private boolean isContainer(Block block) {
      return block instanceof net.minecraft.world.level.block.ChestBlock
         || block instanceof net.minecraft.world.level.block.BarrelBlock
         || block instanceof net.minecraft.world.level.block.EnderChestBlock
         || block instanceof net.minecraft.world.level.block.ShulkerBoxBlock
         || block instanceof net.minecraft.world.level.block.HopperBlock;
   }
}
