package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin({MultiPlayerGameMode.class})
public interface ClientPlayerInteractionManagerAccessor {
   @Accessor("blockBreakingCooldown")
   void setBlockBreakingCooldown(int var1);

   @Accessor("blockBreakingCooldown")
   int getBlockBreakingCooldown();
}
