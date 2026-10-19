package vesence.mixin;

import net.minecraft.client.render.TextureTransform;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vesence.module.impl.visuals.EnchantmentColor;

/**
 * Перехватывает getTransformSupplier() у glint-текстурирования.
 * В 1.21.11 это вызывается при сборке render-команды для glint.
 * Здесь выставляем глобальный ColorModulator для glint shader через
 * статический field, который подхватывается рендер-пайплайном.
 *
 * <p>Реальная подмена цвета: записываем наш цвет в поле
 * {@link EnchantmentColor#currentGlintColor}, которое потом читается
 * через отдельный mixin на uniform upload.
 */
@Mixin(TextureTransform.class)
public class EnchantGlintColorMixin {

    @Inject(method = "getTransformSupplier", at = @At("RETURN"))
    private void vesence$injectGlintColor(CallbackInfoReturnable<Matrix4f> cir) {
        TextureTransform self = (TextureTransform) (Object) this;
        if (self == TextureTransform.GLINT_TEXTURING
                || self == TextureTransform.ENTITY_GLINT_TEXTURING
                || self == TextureTransform.ARMOR_ENTITY_GLINT_TEXTURING) {
            EnchantmentColor.glintDrawing = true;
        } else {
            EnchantmentColor.glintDrawing = false;
        }
    }
}
