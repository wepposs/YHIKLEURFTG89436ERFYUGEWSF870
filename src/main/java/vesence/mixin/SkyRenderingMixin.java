package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.SkyRenderer;
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
 *
 * <p>26.3: renderTopSky/renderSkyDark/renderGlowingSky/renderSun/renderMoon/renderEndSky
 * заменены на renderSkyDisc/renderDarkDisc/renderSunriseAndSunset/renderSun/renderMoon/renderEndSky
 * с RenderPass-пайплайном.
 */
@Environment(EnvType.CLIENT)
@Mixin(SkyRenderer.class)
public class SkyRenderingMixin {

    @org.spongepowered.asm.mixin.Shadow
    @org.spongepowered.asm.mixin.Final
    private com.mojang.blaze3d.pipeline.RenderTarget renderTarget;

    private static boolean vesence$skyActive() {
        try {
            Ambience amb = Ambience.getInstance();
            return amb != null && amb.enable && Ambience.skyShader.get();
        } catch (Throwable t) {
            return false;
        }
    }

    @org.spongepowered.asm.mixin.Unique
    private boolean vesence$skyRendered;

    // Upload and draw before vanilla opens its RenderPass: encoders cannot be nested.
    @Inject(method = "render", at = @At("HEAD"))
    private void vesence$prepareSky(com.mojang.renderpearl.api.buffers.GpuBufferSlice fog,
                                    net.minecraft.client.renderer.state.level.SkyRenderState state, CallbackInfo ci) {
        vesence$skyRendered = false;
        if (!vesence$skyActive()) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            Camera camera = mc.gameRenderer != null ? mc.gameRenderer.mainCamera() : null;
            if (camera != null) {
                Ambience amb = Ambience.getInstance();
                boolean fogOn = Ambience.fog.is(Ambience.FOG_OVERRIDE) || Ambience.fog.is(Ambience.FOG_CLEAR);
                int fogRgb = amb.getFogColorValue();
                float fogBlend = Ambience.fog.is(Ambience.FOG_OVERRIDE) ? 0.85F : 0.45F;
                boolean rendered = vesence.renderengine.render.SkyShaderRenderer.getInstance().render(
                        renderTarget, Ambience.skyShaderMode.currentMode, camera,
                        Ambience.skySpeed.get().floatValue(), Ambience.skyIntensity.get().floatValue(),
                        fogOn, fogRgb, fogBlend);
                vesence$skyRendered = rendered;
            }
        } catch (Exception e) {
            System.err.println("[vesence] Sky shader render failed: " + e.getMessage());
        }
    }

    @Inject(method = "renderSkyDisc", at = @At("HEAD"), cancellable = true)
    private void vesence$renderTopSky(com.mojang.renderpearl.api.commands.RenderPass pass, org.joml.Vector3fc color, CallbackInfo ci) {
        if (vesence$skyRendered) ci.cancel();
    }

    @Inject(method = "renderDarkDisc", at = @At("HEAD"), cancellable = true)
    private void vesence$renderSkyDark(com.mojang.renderpearl.api.commands.RenderPass pass, CallbackInfo ci) {
        if (vesence$skyRendered) ci.cancel();
    }

    @Inject(method = "renderSunriseAndSunset", at = @At("HEAD"), cancellable = true)
    private void vesence$renderGlowingSky(com.mojang.renderpearl.api.commands.RenderPass pass,
                                          com.mojang.blaze3d.vertex.PoseStack matrices,
                                          float tickDelta, org.joml.Vector4fc color, CallbackInfo ci) {
        if (vesence$skyRendered) ci.cancel();
    }

    @Inject(method = "renderEndSky", at = @At("HEAD"), cancellable = true)
    private void vesence$renderEndSky(com.mojang.renderpearl.api.commands.RenderPass pass, CallbackInfo ci) {
        if (vesence$skyRendered) ci.cancel();
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
    private void vesence$renderSun(com.mojang.renderpearl.api.commands.RenderPass pass, float alpha,
                                   com.mojang.blaze3d.vertex.PoseStack matrices, CallbackInfo ci) {
        if (vesence$hideSunMoon()) ci.cancel();
    }

    @Inject(method = "renderMoon", at = @At("HEAD"), cancellable = true)
    private void vesence$renderMoon(com.mojang.renderpearl.api.commands.RenderPass pass,
                                    net.minecraft.world.level.MoonPhase phase, float alpha,
                                    com.mojang.blaze3d.vertex.PoseStack matrices, CallbackInfo ci) {
        if (vesence$hideSunMoon()) ci.cancel();
    }
}
