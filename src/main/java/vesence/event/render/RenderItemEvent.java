package vesence.event.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.InteractionHand;
import vesence.event.Event;

@Environment(EnvType.CLIENT)
public class RenderItemEvent extends Event {
   private static final Minecraft mc = Minecraft.getInstance();
   private final PoseStack matrix;
   private final InteractionHand hand;

   public RenderItemEvent(PoseStack matrix, InteractionHand hand) {
      this.matrix = matrix;
      this.hand = hand;
   }

   public PoseStack getMatrix() {
      return this.matrix;
   }

   public InteractionHand getHand() {
      return this.hand;
   }

   public boolean isRightHand() {
      if (mc != null && mc.player != null) {
         net.minecraft.util.Arm mainArm = mc.player.getMainArm();
         boolean isMainHandRight = (mainArm == net.minecraft.util.Arm.RIGHT && this.hand == InteractionHand.MAIN_HAND);
         boolean isOffHandRight = (mainArm == net.minecraft.util.Arm.LEFT && this.hand == InteractionHand.OFF_HAND);
         return isMainHandRight || isOffHandRight;
      }
      return this.hand == InteractionHand.MAIN_HAND;
   }
}
