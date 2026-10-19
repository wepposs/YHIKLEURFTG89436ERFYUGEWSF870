package vesence.event.render;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import vesence.event.Event;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
public class HandOffsetEvent extends Event {
   PoseStack matrices;
   ItemStack stack;
   InteractionHand hand;
   float scale;

   public HandOffsetEvent(PoseStack matrices, ItemStack stack, InteractionHand hand) {
      this.matrices = matrices;
      this.stack = stack;
      this.hand = hand;
      this.scale = 1.0F;
   }
}
