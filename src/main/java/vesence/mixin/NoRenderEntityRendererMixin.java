package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vesence.Vesence;
import vesence.module.impl.visuals.NoRender;
import vesence.renderengine.EntityFramebufferCaptureManager;

/**
 * Скрывает тени сущностей (модуль NoRender → «Тени») и дополнительно тени/ники
 * во время прохода захвата силуэта Shader ESP
 * ({@link EntityFramebufferCaptureManager}) — чтобы силуэт был чистым
 * (эквивалент Wave: renderEntityStaticNoShadow + setRenderName(false)).
 *
 * <p>Подавляем ТОЛЬКО тень и ник; броня, предметы в руках, огонь и эффекты
 * рендерятся в силуэт как в оригинале.</p>
 */
@Environment(EnvType.CLIENT)
@Mixin(EntityRenderer.class)
public class NoRenderEntityRendererMixin {
    @Inject(method = "getShadowRadius", at = @At("HEAD"), cancellable = true)
    private void vesence$hideShadow(EntityRenderState state, CallbackInfoReturnable<Float> cir) {
        // Во время захвата силуэта Shader ESP тени не нужны.
        if (vesence$captureActive()) {
            cir.setReturnValue(0.0F);
            return;
        }
        if (!Vesence.isModInitialized()) return;
        NoRender nr = Vesence.get.manager.get(NoRender.class);
        if (nr != null && nr.enable && NoRender.elements.get("Тени")) {
            cir.setReturnValue(0.0F);
        }
    }

    // 26.3: renderLabelIfPresent → submitNameDisplay(state, matrices, queue, cameraState).
    @Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("HEAD"), cancellable = true)
    private void vesence$hideNameInCapture(EntityRenderState state, PoseStack matrices,
                                           SubmitNodeCollector queue, CameraRenderState cameraState,
                                           CallbackInfo ci) {
        // Ник не должен попадать в силуэт захвата (Wave: setRenderName(false)).
        if (vesence$captureActive()) {
            ci.cancel();
        }
    }

    private static boolean vesence$captureActive() {
        EntityFramebufferCaptureManager capture = EntityFramebufferCaptureManager.getInstance();
        return capture.isCaptureFrameActive() || capture.isExecutingCapturePass();
    }
}
