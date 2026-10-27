package vesence.mixin.optimization;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.state.ItemClusterRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import vesence.optimization.Opt;

/**
 * Одна копия на лежащий стак.
 *
 * <p>Ваниль рисует упавший стак не одним предметом, а стопкой из 1–5 копий
 * ({@code getRenderedAmount}): 64 блока — это 5 полноценных моделей предмета,
 * каждая со своими квадами, светом и оверлеем. На полу у ферм и после смертей
 * таких стаков сотни, и именно они дают основную просадку.
 *
 * <p>Здесь всегда одна копия: стака визуально чуть тоньше, зато стоимость
 * отрисовки лежащих предметов падает до пяти раз.
 */
@Environment(EnvType.CLIENT)
@Mixin(ItemClusterRenderState.class)
public class ItemClusterRenderStateMixin {

   @ModifyReturnValue(method = "getRenderedAmount", at = @At("RETURN"))
   private static int vesence$singleCopy(int original) {
      return Opt.on() && Opt.itemSingleCopy() ? 1 : original;
   }
}
