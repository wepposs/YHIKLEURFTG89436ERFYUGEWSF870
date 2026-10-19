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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vesence.module.impl.visuals.Ambience;

@Environment(EnvType.CLIENT)
@Mixin(FogRenderer.class)
public class FogRendererMixin {
    @Inject(method = "getFogColor", at = @At("RETURN"), cancellable = true)
    private static void onGetFogColor(Camera camera, float tickProgress, ClientLevel world, int viewDistance, float skyDarkness, CallbackInfoReturnable<Vector4f> cir) {
        Ambience ambience = Ambience.getInstance();
        if (ambience != null && Ambience.isEnabled() && ambience.shouldOverrideFog()) {
            int color = ambience.getFogColorValue();
            float r = ((color >> 16) & 0xFF) / 255.0f;
            float g = ((color >> 8) & 0xFF) / 255.0f;
            float b = (color & 0xFF) / 255.0f;
            cir.setReturnValue(new Vector4f(r, g, b, 1.0f));
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
            method = "applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V",
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
            float viewDistance = client.gameRenderer.getFarPlaneDistance();
            float fogEnd = viewDistance * amb.getFogEndMultiplier();
            return fogEnd * amb.getFogStartMultiplier();
        }
        return renderDistanceStart;
    }

    @ModifyVariable(
            method = "applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V",
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
            float viewDistance = client.gameRenderer.getFarPlaneDistance();
            return viewDistance * amb.getFogEndMultiplier();
        }
        return renderDistanceEnd;
    }
}

