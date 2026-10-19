package vesence.mixin;

import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.texture.AbstractTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Аксессор к protected-полю sampler у AbstractTexture.
 * Нужен чтобы задать LINEAR-фильтрацию для запечённых текстур
 * анимированного плаща (сглаживание вместо пиксельного nearest).
 */
@Mixin(AbstractTexture.class)
public interface AbstractTextureAccessor {
    @Accessor("sampler")
    void vesence$setSampler(GpuSampler sampler);
}
