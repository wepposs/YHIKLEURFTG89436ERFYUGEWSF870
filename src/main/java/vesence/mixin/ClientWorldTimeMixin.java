package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vesence.module.impl.visuals.Ambience;

@Environment(EnvType.CLIENT)
@Mixin(Level.class)
public class ClientWorldTimeMixin {

    // 26.3: Level#getTimeOfDay удалён — время берётся из часов (clock-based), поэтому
    // перехватываем оба геттера времени, чтобы Ambience мог сдвигать визуальное время суток.
    @Inject(method = {"getOverworldClockTime", "getDefaultClockTime"}, at = @At("RETURN"), cancellable = true)
    private void onGetTimeOfDay(CallbackInfoReturnable<Long> cir) {
        if ((Object) this instanceof net.minecraft.client.multiplayer.ClientLevel && Ambience.isEnabled()) {
            cir.setReturnValue(Ambience.getVisualTime(cir.getReturnValue()));
        }
    }
}
