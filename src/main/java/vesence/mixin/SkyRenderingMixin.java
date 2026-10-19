package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.render.SkyRendering;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.module.impl.visuals.Ambience;

/**
 * Порт Relevant SkyRenderingMixin на 1.21.11.
 *
 * <p>На стадии неба (перед геометрией) отменяем ванильную отрисовку неба и
 * рисуем кастомный шейдер. Т.к. это происходит ДО мира, геометрия потом
 * перекрывает небо естественно — нет бага с чернением при взгляде вверх.
 */
@Environment(EnvType.CLIENT)
@Mixin(SkyRendering.class)
public class SkyRenderingMixin {

    private static boolean vesence$skyActive() {
        try {
            Ambience amb = Ambience.getInstance();
            return amb != null && amb.enable && Ambience.skyShader.get();
        } catch (Throwable t) {
            return false;
        }
    }

    @Inject(method = "renderTopSky", at = @At("HEAD"), cancellable = true)
    private void vesence$renderTopSky(int color, CallbackInfo ci) {
        if (!vesence$skyActive()) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            Camera camera = mc.gameRenderer != null ? mc.gameRenderer.getCamera() : null;
            if (camera != null) {
                Ambience amb = Ambience.getInstance();
                boolean fogOn = Ambience.fog.is(Ambience.FOG_OVERRIDE) || Ambience.fog.is(Ambience.FOG_CLEAR);
                int fogRgb = amb.getFogColorValue();
                float fogBlend = Ambience.fog.is(Ambience.FOG_OVERRIDE) ? 0.85F : 0.45F;
                boolean rendered = vesence.renderengine.render.SkyShaderRenderer.getInstance().render(
                        Ambience.skyShaderMode.currentMode, camera,
                        Ambience.skySpeed.get().floatValue(), Ambience.skyIntensity.get().floatValue(),
                        fogOn, fogRgb, fogBlend);
                if (rendered) ci.cancel();
            }
        } catch (Exception e) {
            System.err.println("[vesence] Sky shader render failed: " + e.getMessage());
        }
    }

    @Inject(method = "renderSkyDark", at = @At("HEAD"), cancellable = true)
    private void vesence$renderSkyDark(CallbackInfo ci) {
        if (vesence$skyActive()) ci.cancel();
    }

    @Inject(method = "renderGlowingSky", at = @At("HEAD"), cancellable = true)
    private void vesence$renderGlowingSky(net.minecraft.client.util.math.PoseStack matrices, float tickDelta, int color, CallbackInfo ci) {
        if (vesence$skyActive()) ci.cancel();
    }

    @Inject(method = "renderEndSky", at = @At("HEAD"), cancellable = true)
    private void vesence$renderEndSky(CallbackInfo ci) {
        if (vesence$skyActive()) ci.cancel();
    }

    private static boolean vesence$hideSunMoon() {
        try {
            return Ambience.shouldHideSunMoon();
        } catch (Throwable t) {
            return false;
        }
    }

    // «Убирать солнце и луну»: отменяем только диски солнца/луны, звёзды остаются
    @Inject(method = "renderSun", at = @At("HEAD"), cancellable = true)
    private void vesence$renderSun(float alpha, net.minecraft.client.util.math.PoseStack matrices, CallbackInfo ci) {
        if (vesence$hideSunMoon()) ci.cancel();
    }

    @Inject(method = "renderMoon", at = @At("HEAD"), cancellable = true)
    private void vesence$renderMoon(net.minecraft.world.MoonPhase phase, float alpha, net.minecraft.client.util.math.PoseStack matrices, CallbackInfo ci) {
        if (vesence$hideSunMoon()) ci.cancel();
    }
}
