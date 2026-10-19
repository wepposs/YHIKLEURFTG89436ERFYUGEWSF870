package vesence.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.gl.DynamicUniforms;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import vesence.module.impl.visuals.EnchantmentColor;

/**
 * Подменяет ColorModulator (2-й параметр Vector4fc) в DynamicUniforms.write()
 * когда рисуется glint (флаг EnchantmentColor.glintDrawing).
 */
@Mixin(DynamicUniforms.class)
public class DynamicUniformsGlintMixin {

    @ModifyVariable(method = "write", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private Vector4fc vesence$modifyGlintColor(Vector4fc colorModulator) {
        if (EnchantmentColor.glintDrawing && EnchantmentColor.isActive()) {
            EnchantmentColor.glintDrawing = false; // Сбрасываем для следующего draw call
            return EnchantmentColor.getGlintColorModulator();
        }
        return colorModulator;
    }
}
