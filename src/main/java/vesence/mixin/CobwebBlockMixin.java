package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.Vesence;

@Environment(EnvType.CLIENT)
@Mixin(WebBlock.class)
public abstract class CobwebBlockMixin {

   @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
   private void onOnEntityCollision(BlockState state, Level world, BlockPos pos, Entity entity, InsideBlockEffectApplier handler, boolean bl, CallbackInfo ci) {
      if (!Vesence.isModInitialized()) return;
      if (!(entity instanceof LocalPlayer)) return;

      vesence.module.impl.combat.Criticals criticals = vesence.module.impl.combat.Criticals.getInstance();
      if (criticals == null || !criticals.enable) return;

      if (criticals.isWeb()) {
         ci.cancel();
      }
   }
}
