package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.render.state.SkyRenderState;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.module.impl.visuals.Ambience;

@Environment(EnvType.CLIENT)
@Mixin({SkyRendering.class})
public class ClientWorldMixin {
    @Inject(
            method = {"updateRenderState"},
            at = {@At("RETURN")}
    )
    private void modifySkyColor(ClientWorld world, float tickProgress, Camera camera, SkyRenderState state, CallbackInfo ci) {
        Ambience ambience = Ambience.getInstance();
        if (ambience != null && Ambience.isEnabled()) {
            if (ambience.shouldOverrideFog() || Ambience.skyShader.get()) {
                int color = ambience.getFogColorValue();
                state.skyColor = color;
                state.sunriseAndSunsetColor = color;
            }
        }
    }
}
