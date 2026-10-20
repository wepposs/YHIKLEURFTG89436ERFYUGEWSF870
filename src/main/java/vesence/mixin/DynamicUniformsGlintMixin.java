package vesence.mixin;

import net.minecraft.client.renderer.DynamicGpuData;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import vesence.module.impl.visuals.EnchantmentColor;

/**
 * Подмена ColorModulator'а у glint-прохода (модуль EnchantmentColor).
 *
 * <p>Порт 26.3: {@code com.mojang.blaze3d.systems.DynamicUniforms} удалён, его роль
 * перешла к {@code net.minecraft.client.renderer.DynamicGpuData}
 * ({@code RenderSystem.getDynamicUniforms()}). Нужная перегрузка —
 * {@code writeTransform(Matrix4f modelView, Vector4f colorModulator)}: второй
 * аргумент и есть ColorModulator, который умножается в шейдере.
 *
 * <p>Флаг {@link EnchantmentColor#glintDrawing} выставляет
 * {@link EnchantGlintColorMixin} когда glint-текстурирование готовит матрицу;
 * здесь он применяется и сбрасывается, чтобы подмена действовала ровно на один
 * glint-draw.
 */
@Mixin(DynamicGpuData.class)
public class DynamicUniformsGlintMixin {

    @ModifyVariable(
            method = "writeTransform(Lorg/joml/Matrix4f;Lorg/joml/Vector4f;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;",
            at = @At("HEAD"),
            argsOnly = true,
            require = 0
    )
    private Vector4f vesence$modifyGlintColor(Vector4f colorModulator) {
        if (EnchantmentColor.glintDrawing && EnchantmentColor.isActive()) {
            EnchantmentColor.glintDrawing = false;
            return EnchantmentColor.getGlintColorModulator();
        }
        return colorModulator;
    }
}
