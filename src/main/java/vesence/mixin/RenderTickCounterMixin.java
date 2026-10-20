package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vesence.utils.TimerManager;

@Environment(EnvType.CLIENT)
@Mixin(DeltaTracker.Timer.class)
public class RenderTickCounterMixin {
    // 26.3: RenderTickCounter.Dynamic#tickTime переименовано в DeltaTracker.Timer#msPerTick
    // (поле final — @Mutable снимает final, чтобы Timer-модуль мог менять скорость тика).
    @Shadow @Mutable
    private float msPerTick;

    @Inject(method = "advanceGameTime(J)I", at = @At("HEAD"))
    private void applyTimerSpeed(long timeMillis, CallbackInfoReturnable<Integer> cir) {
        float timer = TimerManager.getTimer();
        msPerTick = 50.0F / (timer == 0.0F ? 1.0F : timer);
    }
}
