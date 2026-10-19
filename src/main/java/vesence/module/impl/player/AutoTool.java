package vesence.module.impl.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ToolComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import vesence.event.EventInit;
import vesence.event.impl.EventUpdate;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;

/**
 * AutoTool — полный порт логики из RelevantPremiumpp4 под vesence (1.21.11).
 * При копании автоматически берёт слот хотбара с лучшей скоростью добычи по
 * целевому блоку и возвращает исходный слот после остановки копания.
 */
@IModule(name = "AutoTool", description = "При копании берет лучший предмет", category = Category.MISC, bind = -1)
@Environment(EnvType.CLIENT)
public class AutoTool extends Module {

   public static AutoTool INSTANCE;

   private int previousSlot = -1;

   public AutoTool() {
      INSTANCE = this;
   }

   @EventInit
   public void onEvent(EventUpdate event) {
      if (mc.player != null && mc.world != null && mc.interactionManager != null && !mc.player.isCreative()) {
         if (mc.interactionManager.isBreakingBlock()) {
            if (this.previousSlot == -1) {
               this.previousSlot = mc.player.getInventory().getSelectedSlot();
            }

            int toolSlot = this.findOptimalTool();
            if (toolSlot != -1) {
               this.switchToSlot(toolSlot);
            }
         } else if (this.previousSlot != -1) {
            this.switchToSlot(this.previousSlot);
            this.previousSlot = -1;
         }
      } else {
         this.previousSlot = -1;
      }
   }

   private void switchToSlot(int slot) {
      if (slot >= 0 && slot <= 8) {
         if (mc.player.getInventory().getSelectedSlot() != slot) {
            mc.player.getInventory().setSelectedSlot(slot);
         }
      }
   }

   private int findOptimalTool() {
      if (mc.crosshairTarget instanceof BlockHitResult blockHitResult) {
         BlockState blockState = mc.world.getBlockState(blockHitResult.getBlockPos());
         return this.findBestToolSlot(blockState);
      } else {
         return -1;
      }
   }

   private int findBestToolSlot(BlockState blockState) {
      int bestSlot = -1;
      float bestSpeed = 1.0F;

      for (int i = 0; i < 9; i++) {
         float speed = getMiningSpeed(mc.player.getInventory().getStack(i), blockState);
         if (speed > bestSpeed) {
            bestSpeed = speed;
            bestSlot = i;
         }
      }

      return bestSlot;
   }

   /**
    * Скорость добычи предмета по блоку. В 1.21.11 у ItemStack нет
    * getMiningSpeedMultiplier — используем ToolComponent.getSpeed(state):
    * если блок подходит под правило инструмента, вернёт speed правила, иначе
    * defaultMiningSpeed (~1.0). Сравнение > 1.0 работает как в оригинале.
    */
   private static float getMiningSpeed(ItemStack stack, BlockState state) {
      ToolComponent tool = stack.get(DataComponentTypes.TOOL);
      if (tool == null) {
         return 1.0F;
      }
      return tool.getSpeed(state);
   }

   @Override
   public void onDisable() {
      this.previousSlot = -1;
      super.onDisable();
   }
}
