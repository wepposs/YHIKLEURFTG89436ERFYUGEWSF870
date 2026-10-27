package vesence.mixin.optimization;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vesence.optimization.Opt;

/**
 * Дальность прорисовки блок-сущностей.
 *
 * <p>Ванильный {@code getViewDistance()} — 64 блока для сундуков, табличек, голов и
 * подобного. Каждая блок-сущность в этом радиусе каждый кадр проходит extract
 * (создание render state) и submit, а сундук ещё и анимирует крышку. На базах со
 * складами это сотни объектов на кадр.
 *
 * <p>{@code tryExtractRenderState} — точка до создания render state, поэтому
 * отсечённый объект не стоит ничего, кроме трёх вычитаний и одного сравнения.
 * Рендереры с собственной увеличенной дальностью (маяки, врата Края) не трогаем:
 * их видно издалека специально.
 */
@Environment(EnvType.CLIENT)
@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityDispatcherMixin {

   @Shadow
   private Vec3 cameraPos;

   @Inject(method = "tryExtractRenderState", at = @At("HEAD"), cancellable = true)
   private <E extends BlockEntity, S extends BlockEntityRenderState> void vesence$cullFar(
           E entity, float partialTick, ModelFeatureRenderer.CrumblingOverlay crumblingOverlay,
           boolean offScreen, CallbackInfoReturnable<S> cir) {
      if (entity == null || !Opt.on() || this.cameraPos == null) {
         return;
      }
      BlockEntityRenderer<?, ?> renderer = ((BlockEntityRenderDispatcher) (Object) this).getRenderer(entity);
      if (renderer == null || renderer.getViewDistance() > Opt.VANILLA_BLOCK_ENTITY_DISTANCE) {
         return;
      }
      var pos = entity.getBlockPos();
      double dx = (pos.getX() + 0.5) - this.cameraPos.x;
      double dy = (pos.getY() + 0.5) - this.cameraPos.y;
      double dz = (pos.getZ() + 0.5) - this.cameraPos.z;
      double limit = Opt.blockEntityDistance();
      if (dx * dx + dy * dy + dz * dz > limit * limit) {
         cir.setReturnValue(null);
      }
   }
}
