package vesence.event.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import vesence.event.Event;

/**
 * Вызывается прямо перед HeldItemRenderer.renderItem(...), когда все ваниль-трансформы
 * equip/swing/handpose уже применены. Здесь поворот и масштаб предмета происходят
 * вокруг его собственного центра, не сдвигая его с места.
 */
@Environment(EnvType.CLIENT)
public class ItemTransformEvent extends Event {
   private final PoseStack matrices;
   private final ItemStack stack;
   private final InteractionHand hand;

   public ItemTransformEvent(PoseStack matrices, ItemStack stack, InteractionHand hand) {
      this.matrices = matrices;
      this.stack = stack;
      this.hand = hand;
   }

   public PoseStack getMatrices() {
      return this.matrices;
   }

   public ItemStack getStack() {
      return this.stack;
   }

   public InteractionHand getHand() {
      return this.hand;
   }
}
