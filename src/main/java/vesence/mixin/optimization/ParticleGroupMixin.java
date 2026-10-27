package vesence.mixin.optimization;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.ParticleGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import vesence.optimization.Opt;

/**
 * Потолок числа частиц в одной группе.
 *
 * <p>Ваниль держит на группу {@code MAX_PARTICLES = 16384} и начинает случайный
 * отброс только с 12288. Каждая частица — это квад, подготовка render state и
 * запись в атлас, поэтому в сценах с дождём, фермами и редстоуном именно частицы
 * съедают кадр. Здесь потолок опускается, а рампа случайного отброса масштабируется
 * вместе с ним, чтобы частицы исчезали постепенно, а не обрывом.
 */
@Environment(EnvType.CLIENT)
@Mixin(ParticleGroup.class)
public class ParticleGroupMixin {

   @ModifyConstant(method = "add", constant = @Constant(intValue = 16384))
   private int vesence$lowerParticleCap(int original) {
      return Opt.on() ? Opt.particleCap() : original;
   }

   @ModifyConstant(method = "add", constant = @Constant(intValue = 12288))
   private int vesence$lowerReservoirStart(int original) {
      return Opt.on() ? Opt.particleReservoirStart() : original;
   }

   @ModifyConstant(method = "add", constant = @Constant(floatValue = 4096.0F))
   private float vesence$scaleReservoirRamp(float original) {
      return Opt.on() ? (float) Opt.particleReservoirSize() : original;
   }
}
