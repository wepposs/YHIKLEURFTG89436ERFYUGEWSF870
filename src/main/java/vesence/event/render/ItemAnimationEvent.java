package vesence.event.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.InteractionHand;
import vesence.event.Event;

@Environment(EnvType.CLIENT)
public class ItemAnimationEvent extends Event {
   private final PoseStack matrix;
   private final InteractionHand hand;
   private final float swingProgress;
   private final float equipProgress;

   public ItemAnimationEvent(PoseStack matrix, InteractionHand hand, float swingProgress, float equipProgress) {
      this.matrix = matrix;
      this.hand = hand;
      this.swingProgress = swingProgress;
      this.equipProgress = equipProgress;
   }

   public PoseStack getMatrix() {
      return this.matrix;
   }

   public InteractionHand getHand() {
      return this.hand;
   }

   public boolean isRightHand() {
      return this.hand == InteractionHand.MAIN_HAND;
   }

   public float getSwingProgress() {
      return this.swingProgress;
   }

   public float getEquipProgress() {
      return this.equipProgress;
   }
}
