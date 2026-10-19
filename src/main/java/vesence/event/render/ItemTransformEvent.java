package vesence.event.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import vesence.event.Event;

/**
 * Вызывается прямо перед HeldItemRenderer.renderItem(...), когда все ваниль-трансформы
 * equip/swing/handpose уже применены. Здесь поворот и масштаб предмета происходят
 * вокруг его собственного центра, не сдвигая его с места.
 */
@Environment(EnvType.CLIENT)
public class ItemTransformEvent extends Event {
   private final MatrixStack matrices;
   private final ItemStack stack;
   private final Hand hand;

   public ItemTransformEvent(MatrixStack matrices, ItemStack stack, Hand hand) {
      this.matrices = matrices;
      this.stack = stack;
      this.hand = hand;
   }

   public MatrixStack getMatrices() {
      return this.matrices;
   }

   public ItemStack getStack() {
      return this.stack;
   }

   public Hand getHand() {
      return this.hand;
   }
}
