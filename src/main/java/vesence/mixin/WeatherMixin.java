package vesence.mixin;

import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vesence.module.impl.visuals.Ambience;

@Mixin(Level.class)
public abstract class WeatherMixin {

    @Inject(method = "getRainGradient(F)F", at = @At("RETURN"), cancellable = true)
    private void onGetRainGradient(float delta, CallbackInfoReturnable<Float> cir) {
        if (Ambience.isWeatherOverridden()) {
            cir.setReturnValue(Ambience.getRainGradient(cir.getReturnValue()));
        }
    }

    @Inject(method = "getThunderGradient(F)F", at = @At("RETURN"), cancellable = true)
    private void onGetThunderGradient(float delta, CallbackInfoReturnable<Float> cir) {
        if (Ambience.isWeatherOverridden()) {
            cir.setReturnValue(Ambience.getThunderGradient(cir.getReturnValue()));
        }
    }
}