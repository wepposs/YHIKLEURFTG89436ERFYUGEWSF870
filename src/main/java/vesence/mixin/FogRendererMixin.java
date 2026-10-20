package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vesence.module.impl.visuals.Ambience;

@Environment(EnvType.CLIENT)
@Mixin(FogRenderer.class)
public class FogRendererMixin {
    // 26.3: getFogColor стал приватным computeFogColor, который пишет результат в out-параметр Vector4f.
    @Inject(method = "computeFogColor", at = @At("RETURN"))
    private void onGetFogColor(Camera camera, float tickProgress, ClientLevel world, int viewDistance, float skyDarkness, Vector4f out, CallbackInfo ci) {
        Ambience ambience = Ambience.getInstance();
        if (ambience != null && Ambience.isEnabled() && ambience.shouldOverrideFog()) {
            int color = ambience.getFogColorValue();
            float r = ((color >> 16) & 0xFF) / 255.0f;
            float g = ((color >> 8) & 0xFF) / 255.0f;
            float b = (color & 0xFF) / 255.0f;
            out.set(r, g, b, 1.0f);
        }
    }

    /**
     * Реальное управление дистанцией тумана (полный порт Relevant WorldTweaks).
     *
     * <p>Приватный {@code applyFog(ByteBuffer, int, Vector4f, environmentalStart,
     * environmentalEnd, renderDistanceStart, renderDistanceEnd, skyEnd, cloudEnd)}
     * получает уже вычисленные дистанции. Float-аргументы по ordinal:
     * 0=environmentalStart, 1=environmentalEnd, 2=renderDistanceStart,
     * 3=renderDistanceEnd, 4=skyEnd, 5=cloudEnd.
     *
     * <p>Правим renderDistanceStart(2)/renderDistanceEnd(3) — именно они дают
     * атмосферный туман, заполняющий мир. "Переопределить" масштабирует полосу
     * тумана множителями. "Очистить" уводит туман за горизонт.
     */
    @ModifyVariable(
            method = "updateBuffer(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V",
            at = @At("HEAD"),
            ordinal = 2,
            argsOnly = true,
            require = 0
    )
    private float vesence$fogStart(float renderDistanceStart) {
        Ambience amb = Ambience.getInstance();
        if (amb == null || !Ambience.isEnabled()) return renderDistanceStart;
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client == null || client.gameRenderer == null || client.level == null || client.player == null) {
            return renderDistanceStart;
        }
        if (amb.isFogClearMode()) return 1.0E7f;
        if (amb.isFogOverrideMode()) {
            // Порт Relevant: fogEnd = viewDistance * endMul; fogStart = fogEnd * startMul.
            // 26.3: getFarPlaneDistance() удалён — дальняя плоскость живёт в CameraRenderState.depthFar.
            float viewDistance = vesence$farPlaneDistance(client);
            float fogEnd = viewDistance * amb.getFogEndMultiplier();
            return fogEnd * amb.getFogStartMultiplier();
        }
        return renderDistanceStart;
    }

    @ModifyVariable(
            method = "updateBuffer(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V",
            at = @At("HEAD"),
            ordinal = 3,
            argsOnly = true,
            require = 0
    )
    private float vesence$fogEnd(float renderDistanceEnd) {
        Ambience amb = Ambience.getInstance();
        if (amb == null || !Ambience.isEnabled()) return renderDistanceEnd;
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client == null || client.gameRenderer == null || client.level == null || client.player == null) {
            return renderDistanceEnd;
        }
        if (amb.isFogClearMode()) return 1.0E7f + 1.0f;
        if (amb.isFogOverrideMode()) {
            float viewDistance = vesence$farPlaneDistance(client);
            return viewDistance * amb.getFogEndMultiplier();
        }
        return renderDistanceEnd;
    }

    /**
     * 26.3: {@code GameRenderer#getFarPlaneDistance()} удалён. Дальняя плоскость кадра
     * считается в extract-фазе и лежит в {@code CameraRenderState.depthFar}; если состояние
     * ещё не заполнено (первый кадр), откатываемся на «дальность прорисовки в блоках».
     */
    @org.spongepowered.asm.mixin.Unique
    private static float vesence$farPlaneDistance(net.minecraft.client.Minecraft client) {
        try {
            var state = client.gameRenderer.gameRenderState();
            if (state != null && state.levelRenderState != null
                    && state.levelRenderState.cameraRenderState != null
                    && state.levelRenderState.cameraRenderState.depthFar > 0.0f) {
                return state.levelRenderState.cameraRenderState.depthFar;
            }
        } catch (Throwable ignored) {
        }
        return client.options.getEffectiveRenderDistance() * 64.0F;
    }
}

