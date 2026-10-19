package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vesence.module.impl.visuals.Ambience;

@Environment(EnvType.CLIENT)
@Mixin(World.class)
public class ClientWorldTimeMixin {

    @Inject(method = "getTimeOfDay", at = @At("RETURN"), cancellable = true)
    private void onGetTimeOfDay(CallbackInfoReturnable<Long> cir) {
        if ((Object) this instanceof net.minecraft.client.world.ClientWorld && Ambience.isEnabled()) {
            cir.setReturnValue(Ambience.getVisualTime(cir.getReturnValue()));
        }
    }
}
