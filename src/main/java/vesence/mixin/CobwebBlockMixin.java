package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.CobwebBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCollisionHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.Vesence;

@Environment(EnvType.CLIENT)
@Mixin(CobwebBlock.class)
public abstract class CobwebBlockMixin {

   @Inject(method = "onEntityCollision", at = @At("HEAD"), cancellable = true)
   private void onOnEntityCollision(BlockState state, World world, BlockPos pos, Entity entity, EntityCollisionHandler handler, boolean bl, CallbackInfo ci) {
      if (!Vesence.isModInitialized()) return;
      if (!(entity instanceof ClientPlayerEntity)) return;

      vesence.module.impl.combat.Criticals criticals = vesence.module.impl.combat.Criticals.getInstance();
      if (criticals == null || !criticals.enable) return;

      if (criticals.isWeb()) {
         ci.cancel();
      }
   }
}
