package vesence.mixin.optimization;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import vesence.optimization.Opt;

/**
 * Дистанция прорисовки сущностей.
 *
 * <p>Ваниль решает, рисовать ли сущность, в {@code Entity#shouldRenderAtSqrDistance}:
 * порог равен {@code размерBoundingBox * 64 * viewScale}. Множитель {@code viewScale}
 * каждый кадр приходит из настройки «Дист сущностей» через {@code Entity.setViewScale}.
 *
 * <p>Уменьшая его здесь, мы пропорционально приближаем порог отсечения: при 0.7
 * обычный моб перестаёт рисоваться примерно на 45 блоках вместо 64. Это убирает
 * из кадра сразу весь конвейер сущности — extract, слои, неймтег, submit.
 */
@Environment(EnvType.CLIENT)
@Mixin(Entity.class)
public class EntityViewScaleMixin {

   @ModifyVariable(method = "setViewScale", at = @At("HEAD"), ordinal = 0, argsOnly = true)
   private static double vesence$reduceEntityDistance(double scale) {
      return Opt.on() ? scale * Opt.entityDistanceScale() : scale;
   }
}
