package vesence.event.impl;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import vesence.event.Event;

@Environment(EnvType.CLIENT)
public class BlockBreakingEvent extends Event {
   private final BlockPos blockPos;
   private final Direction direction;

   public BlockBreakingEvent(BlockPos blockPos, Direction direction) {
      this.blockPos = blockPos;
      this.direction = direction;
   }

   public BlockPos getBlockPos() { return blockPos; }
   public Direction getDirection() { return direction; }
}
