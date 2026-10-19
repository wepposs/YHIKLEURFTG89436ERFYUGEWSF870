package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.block.CobwebBlock;
import net.minecraft.world.entity.Entity;
import net.minecraft.entity.EntityCollisionHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.Vesence;

@Environment(EnvType.CLIENT)
@Mixin(CobwebBlock.class)
public abstract class CobwebBlockMixin {

   @Inject(method = "onEntityCollision", at = @At("HEAD"), cancellable = true)
   private void onOnEntityCollision(BlockState state, Level world, BlockPos pos, Entity entity, EntityCollisionHandler handler, boolean bl, CallbackInfo ci) {
      if (!Vesence.isModInitialized()) return;
      if (!(entity instanceof LocalPlayer)) return;

      vesence.module.impl.combat.Criticals criticals = vesence.module.impl.combat.Criticals.getInstance();
      if (criticals == null || !criticals.enable) return;

      if (criticals.isWeb()) {
         ci.cancel();
      }
   }
}
