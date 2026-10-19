package vesence.utils.player;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import lombok.Generated;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
import vesence.utils.other.IMinecraft;

@Environment(EnvType.CLIENT)
public final class PlayerUtil implements IMinecraft {
   public static boolean nullCheck() {
      return mc.player == null || mc.level == null;
   }

   public static boolean isBlockSolid(double x, double y, double z) {
      BlockPos pos = BlockPos.ofFloored(x, y, z);
      return mc.level.getBlockState(pos).isFullCube(mc.level, pos);
   }

   public static Block block(BlockPos pos) {
      return mc.level.getBlockState(pos).getBlock();
   }

   public static float calculateCorrectYawOffset(float yaw) {
      double xDiff = mc.player.getX() - mc.player.lastRenderX;
      double zDiff = mc.player.getZ() - mc.player.lastRenderZ;
      float distSquared = (float)(xDiff * xDiff + zDiff * zDiff);
      float renderYawOffset = mc.player.lastBodyYaw;
      float offset = renderYawOffset;
      if (distSquared > 0.0025000002F) {
         offset = (float)Mth.atan2(zDiff, xDiff) * 180.0F / (float) Math.PI - 90.0F;
      }

      if (mc.player != null && mc.player.handSwingProgress > 0.0F) {
         offset = yaw;
      }

      float yawOffsetDiff = Mth.wrapDegrees(yaw - (renderYawOffset + Mth.wrapDegrees(offset - renderYawOffset) * 0.3F));
      yawOffsetDiff = Mth.clamp(yawOffsetDiff, -32.0F, 32.0F);
      renderYawOffset = yaw - yawOffsetDiff;
      if (yawOffsetDiff * yawOffsetDiff > 2500.0F) {
         renderYawOffset += yawOffsetDiff * 0.2F;
      }

      return renderYawOffset;
   }

   public static boolean isPlayerInWeb() {
      AABB playerBox = mc.player.getBoundingBox();
      BlockPos playerPosition = mc.player.getBlockPos();
      return getNearbyBlockPositions(playerPosition).stream().anyMatch(pos -> isBlockCobweb(playerBox, pos));
   }

   private static boolean isBlockCobweb(AABB playerBox, BlockPos pos) {
      if (!mc.level.getBlockState(pos).isOf(Blocks.COBWEB)) {
         return false;
      } else {
         AABB blockBox = new AABB(pos);
         return playerBox.intersects(blockBox);
      }
   }

   private static List<BlockPos> getNearbyBlockPositions(BlockPos center) {
      List<BlockPos> positions = new ArrayList<>();

      for (int x = center.getX() - 2; x <= center.getX() + 2; x++) {
         for (int y = center.getY() - 1; y <= center.getY() + 4; y++) {
            for (int z = center.getZ() - 2; z <= center.getZ() + 2; z++) {
               positions.add(new BlockPos(x, y, z));
            }
         }
      }

      return positions;
   }

   public static boolean isServerContains(String searchString) {
      if (searchString == null || searchString.isEmpty()) {
         return false;
      } else if (!nullCheck() && mc.getConnection() != null) {
         String serverAddress = null;
         if (mc.getCurrentServerEntry() != null) {
            serverAddress = mc.getCurrentServerEntry().address;
         }

         if ((serverAddress == null || serverAddress.isEmpty()) && mc.getConnection().getConnection() != null) {
            SocketAddress address = mc.getConnection().getConnection().getAddress();
            if (address != null) {
               serverAddress = address.toString();
               if (serverAddress.startsWith("/")) {
                  serverAddress = serverAddress.substring(1);
               }
            }
         }

         if (mc.isConnectedToLocalServer()) {
            serverAddress = "localhost";
         }

         return serverAddress != null && !serverAddress.isEmpty() ? serverAddress.toLowerCase().contains(searchString.toLowerCase()) : false;
      } else {
         return false;
      }
   }

   @Generated
   private PlayerUtil() {
      throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
   }
}
