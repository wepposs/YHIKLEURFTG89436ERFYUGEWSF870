package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.module.impl.visuals.Animations;
import vesence.utils.render.math.animation.AnimationUtils;

/**
 * Порт Relevant анимации открытия инвентаря (режим «Инвентарь»). Масштаб
 * 0.5→1.0 от центра экрана. Панель + слоты + предметы скейлятся одновременно,
 * затемнение фона не трогается.
 */
@Environment(EnvType.CLIENT)
@Mixin(HandledScreen.class)
public abstract class HandledScreenAnimationMixin {

    @Unique
    private boolean vesence$pushed;

    @Unique
    private boolean vesence$animEnabled() {
        Animations a = Animations.getInstance();
        return a != null && a.isInventoryEnabled();
    }

    @Inject(
            method = "renderBackground",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screen/ingame/HandledScreen;drawBackground(Lnet/minecraft/client/gui/GuiGraphics;FII)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void vesence$onBeforeDrawBackground(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!vesence$animEnabled()) {
            vesence$pushed = false;
            return;
        }
        AnimationUtils anim = Animations.getInstance().inventoryOpenAnim();
        anim.update(1.0f);
        float v = anim.getValue();

        Minecraft mc = Minecraft.getInstance();
        float pivotX = mc.getWindow().getScaledWidth() / 2f;
        float pivotY = mc.getWindow().getScaledHeight() / 2f;

        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(pivotX, pivotY);
        matrices.scale(v, v);
        matrices.translate(-pivotX, -pivotY);
        vesence$pushed = true;
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void vesence$onRenderReturn(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (vesence$pushed) {
            context.getMatrices().popMatrix();
            vesence$pushed = false;
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void vesence$onRemoved(CallbackInfo ci) {
        Animations a = Animations.getInstance();
        if (a != null) {
            a.inventoryOpenAnim().setValue(0.5f);
        }
    }
}
