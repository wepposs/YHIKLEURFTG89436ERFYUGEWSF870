package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vesence.module.impl.visuals.CameraClip;

@Environment(EnvType.CLIENT)
@Mixin(Camera.class)
public abstract class CameraClipMixin {

    @Inject(method = "clipToSpace", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void vesence$noCameraClip(float desiredCameraDistance, CallbackInfoReturnable<Float> cir) {
        if (CameraClip.isActive()) {
            cir.setReturnValue(desiredCameraDistance);
        }
    }
}
