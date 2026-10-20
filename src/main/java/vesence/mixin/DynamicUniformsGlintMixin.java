package vesence.mixin;

import net.minecraft.client.renderer.GlobalSettingsUniform;
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
// TODO(port26): net.minecraft.client.gl.DynamicUniforms removed in 26.3; retargeted to GlobalSettingsUniform pending runtime-mapping pass
@Mixin(GlobalSettingsUniform.class)
public class DynamicUniformsGlintMixin {

    // TODO(port26): DynamicUniforms.write(Vector4fc colorModulator, ...) removed in 26.3;
    // GlobalSettingsUniform.update(int,int,double,long,float,int,Vec3,boolean) no longer carries a
    // ColorModulator argument, so there is no hook point here for the glint color substitution.
    // @ModifyVariable(method = "write", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    @SuppressWarnings("unused")
    private Vector4fc vesence$modifyGlintColor(Vector4fc colorModulator) {
        if (EnchantmentColor.glintDrawing && EnchantmentColor.isActive()) {
            EnchantmentColor.glintDrawing = false; // Сбрасываем для следующего draw call
            return EnchantmentColor.getGlintColorModulator();
        }
        return colorModulator;
    }
}
