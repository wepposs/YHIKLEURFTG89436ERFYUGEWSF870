package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.module.impl.visuals.Ambience;
import vesence.renderengine.render.pipeline.SaturationPipeline;

@Environment(EnvType.CLIENT)
@Mixin(value = Gui.class, priority = 900)
public class SaturationGameRendererMixin {
    @Unique
    private SaturationPipeline saturationPipeline;

    @Inject(method = "render", at = @At("HEAD"))
    private void onBeforeHudRender(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (!Ambience.isEnabled() || !Ambience.changeSaturation.get()) return;
        if (mc.player == null || mc.level == null) return;

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
