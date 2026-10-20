package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vesence.module.impl.visuals.Animations;
import vesence.utils.render.math.animation.AnimationUtils;
import vesence.utils.render.math.animation.anim.util.Easings;

/**
 * Порт Relevant FOV-анимаций (режимы «Приближение камеры» и «Изменение
 * перспективы»). Зум 1.0→0.95 при открытии экрана и bump FOV 0.7→1.0 при
 * смене перспективы.
 */
// 26.3: GameRenderer#getFov удалён — FOV считается покадрово в Camera#calculateFov,
// поэтому цель миксина — Camera (ранее GameRenderer).
@Environment(EnvType.CLIENT)
@Mixin(Camera.class)
public abstract class GameRendererAnimationFovMixin {

    @Unique
    private net.minecraft.client.CameraType vesence$lastPerspective;

    @Unique
    private AnimationUtils vesence$zoomAnim;

    @Unique
    private AnimationUtils vesence$perspAnim;

    @Unique
    private AnimationUtils vesence$ensureZoom() {
        if (vesence$zoomAnim == null) {
            vesence$zoomAnim = new AnimationUtils(1.0F, 12.0F, Easings.CUBIC_OUT);
        }
        return vesence$zoomAnim;
    }

    @Unique
    private AnimationUtils vesence$ensurePersp() {
        if (vesence$perspAnim == null) {
            vesence$perspAnim = new AnimationUtils(1.0F, 12.0F, Easings.CUBIC_OUT);
        }
        return vesence$perspAnim;
    }

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void vesence$onGetFov(float tickDelta, CallbackInfoReturnable<Float> cir) {
        Animations animations = Animations.getInstance();
        if (animations == null || !animations.enable) return;

        float fov = cir.getReturnValueF();
        float multiplier = 1.0F;
        boolean changed = false;

        if (animations.isCameraZoomEnabled()) {
            AnimationUtils zoom = vesence$ensureZoom();
            boolean screenOpen = Minecraft.getInstance().gui.screen() != null;
            float target = screenOpen ? 0.95F : 1.0F;
            zoom.update(target);
            multiplier *= zoom.getValue();
            changed = true;
        }

        if (animations.isPerspectiveEnabled()) {
            net.minecraft.client.CameraType current = Minecraft.getInstance().options.getCameraType();
            if (vesence$lastPerspective == null) {
                vesence$lastPerspective = current;
            } else if (current != vesence$lastPerspective) {
                vesence$lastPerspective = current;
                vesence$ensurePersp().setValue(0.7F);
                vesence$ensurePersp().update(1.0F);
            }

            AnimationUtils persp = vesence$ensurePersp();
            if (persp.isAlive()) {
                persp.update(1.0F);
                multiplier *= Mth.clamp(persp.getValue(), 0.5F, 1.0F);
                changed = true;
            }
        }

        if (changed) {
            cir.setReturnValue(fov * multiplier);
        }
    }
}
