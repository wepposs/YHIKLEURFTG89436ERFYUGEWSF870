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
@Mixin(DeltaTracker.Dynamic.class)
public class RenderTickCounterMixin {
    @Shadow @Mutable
    private float tickTime;

    @Inject(method = "beginRenderTick(J)I", at = @At("HEAD"))
    private void applyTimerSpeed(long timeMillis, CallbackInfoReturnable<Integer> cir) {
        float timer = TimerManager.getTimer();
        tickTime = 50.0F / (timer == 0.0F ? 1.0F : timer);
    }
}
