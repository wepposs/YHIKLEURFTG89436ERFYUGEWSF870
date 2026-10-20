package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 26.3: поле {@code blockBreakingCooldown} переименовано в {@code destroyDelay}
 * (см. {@code javap -p MultiPlayerGameMode}). Имена методов оставлены прежними,
 * чтобы не трогать вызывающий код — меняется только имя поля в {@link Accessor}.
 */
@Environment(EnvType.CLIENT)
@Mixin({MultiPlayerGameMode.class})
public interface ClientPlayerInteractionManagerAccessor {
   @Accessor("destroyDelay")
   void setBlockBreakingCooldown(int var1);

   @Accessor("destroyDelay")
   int getBlockBreakingCooldown();
}
