package vesence.mixin.optimization;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.QuadParticleGroup;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import vesence.optimization.ParticleLod;

/**
 * Прореживание дальних частиц.
 *
 * <p>{@code QuadParticleGroup#extractRenderState} для каждой видимой частицы вызывает
 * {@code SingleQuadParticle#extract}, который считает поворот, свет и пишет квад в
 * render state. Отброшенной частице этот проход не нужен, поэтому перехватываем именно
 * вызов {@code extract}, а не проверку фрактала: так доступна сама частица, и её маска
 * получается постоянной на всё время жизни — без мерцания между кадрами.
 */
@Environment(EnvType.CLIENT)
@Mixin(QuadParticleGroup.class)
public class QuadParticleGroupMixin {

   @WrapOperation(
           method = "extractRenderState",
           at = @At(value = "INVOKE",
                   target = "Lnet/minecraft/client/particle/SingleQuadParticle;extract("
                           + "Lnet/minecraft/client/renderer/state/level/QuadParticleRenderState;"
                           + "Lnet/minecraft/client/Camera;F)V"))
   private void vesence$skipFarParticles(SingleQuadParticle particle, QuadParticleRenderState state,
                                         Camera camera, float partialTick, Operation<Void> original) {
      if (!ParticleLod.keep(particle, camera)) {
         return;
      }
      original.call(particle, state, camera, partialTick);
   }
}
