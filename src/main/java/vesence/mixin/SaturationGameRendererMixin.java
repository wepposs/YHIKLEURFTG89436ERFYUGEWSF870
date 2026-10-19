package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.module.impl.visuals.Ambience;
import vesence.renderengine.render.pipeline.SaturationPipeline;

@Environment(EnvType.CLIENT)
@Mixin(value = InGameHud.class, priority = 900)
public class SaturationGameRendererMixin {
    @Unique
    private SaturationPipeline saturationPipeline;

    @Inject(method = "render", at = @At("HEAD"))
    private void onBeforeHudRender(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!Ambience.isEnabled() || !Ambience.changeSaturation.get()) return;
        if (mc.player == null || mc.world == null) return;

        float saturation = Ambience.getSaturation();
        if (saturation == 1.0f) return;

        if (saturationPipeline == null) {
            saturationPipeline = new SaturationPipeline();
        }

        try {
            saturationPipeline.applySaturation(mc, saturation);
        } catch (Exception e) {
            System.err.println("[weppos] Saturation error: " + e.getMessage());
        }
    }
}
