package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.module.impl.visuals.Ambience;

@Environment(EnvType.CLIENT)
@Mixin({SkyRenderer.class})
public class ClientWorldMixin {
    @Inject(
            method = {"extractRenderState"},
            at = {@At("RETURN")}
    )
    private void modifySkyColor(ClientLevel world, float tickProgress, Camera camera, SkyRenderState state, CallbackInfo ci) {
        Ambience ambience = Ambience.getInstance();
        if (ambience != null && Ambience.isEnabled()) {
            if (ambience.shouldOverrideFog() || Ambience.skyShader.get()) {
                int color = ambience.getFogColorValue();
                float cr = ((color >> 16) & 0xFF) / 255.0f, cg = ((color >> 8) & 0xFF) / 255.0f, cb = (color & 0xFF) / 255.0f;
                // TODO(port26): SkyRenderState colors are Vector3fc/Vector4fc in 26.3
                state.skyColor = new org.joml.Vector3f(cr, cg, cb);
                state.sunriseAndSunsetColor = new org.joml.Vector4f(cr, cg, cb, 1.0f);
            }
        }
    }
}
