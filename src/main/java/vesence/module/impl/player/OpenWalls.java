package vesence.module.impl.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
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
      if (mc.player == null || mc.world == null) return;
      if (!mc.options.useKey.isPressed()) return;
      if (mc.currentScreen instanceof HandledScreen) return;

      Vec3d eyePos = mc.player.getEyePos();
      Vec3d lookVec = mc.player.getRotationVec(1.0F);
      double reach = 5.0;

      Vec3d endPos = eyePos.add(lookVec.multiply(reach));

      BlockHitResult initialHit = mc.world.raycast(
         new RaycastContext(eyePos, endPos,
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            mc.player));

      if (initialHit != null && initialHit.getType() == HitResult.Type.BLOCK) {
         Block block = mc.world.getBlockState(initialHit.getBlockPos()).getBlock();
         if (isContainer(block)) return;
      }

      for (double d = 0.5; d <= reach; d += 0.5) {
         Vec3d checkPos = eyePos.add(lookVec.multiply(d));
         BlockPos blockPos = BlockPos.ofFloored(checkPos);

         Block block = mc.world.getBlockState(blockPos).getBlock();
         if (isContainer(block)) {
            mc.player.swingHand(Hand.MAIN_HAND);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
               new BlockHitResult(
                  new Vec3d(blockPos.getX() + 0.5, blockPos.getY() + 1.0, blockPos.getZ() + 0.5),
                  Direction.UP, blockPos, false));
            return;
         }
      }
   }

   private boolean isContainer(Block block) {
      return block instanceof net.minecraft.block.ChestBlock
         || block instanceof net.minecraft.block.BarrelBlock
         || block instanceof net.minecraft.block.EnderChestBlock
         || block instanceof net.minecraft.block.ShulkerBoxBlock
         || block instanceof net.minecraft.block.HopperBlock;
   }
}
