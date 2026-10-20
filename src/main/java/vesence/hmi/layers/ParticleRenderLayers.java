package vesence.hmi.layers;

import java.util.function.Function;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * Порт HMI ParticleRenderLayers под рендер-пайплайн 1.21.11.
 *
 * В 1.21.6 HMI использовал собственные RenderPipeline (custom/custom_solid) с
 * аддитивным блендингом. В 1.21.11 система RenderType.MultiPhaseParameters была
 * удалена и заменена на RenderSetup; кастомные пайплайны теперь невозможно собрать
 * тем же способом. Поэтому аддитивные частицы рендерятся через эмиссивный
 * полупрозрачный слой сущностей, а обычные — через стандартный translucent-слой.
 * Это сохраняет визуальное поведение (свечение/полупрозрачность) на 1.21.11.
 */
public class ParticleRenderLayers {
    private static final Function<Identifier, RenderType> PARTICLE_SOLID =
            Util.memoize((Identifier tex) -> RenderTypes.entityTranslucent(tex));
    private static final Function<Identifier, RenderType> PARTICLE_ADDITIVE =
            Util.memoize((Identifier tex) -> RenderTypes.entityTranslucentEmissive(tex));

    public static RenderType getSolidRenderLayer(Identifier texture) {
        return PARTICLE_SOLID.apply(texture);
    }

    public static RenderType getAdditiveRenderLayer(Identifier texture) {
        return PARTICLE_ADDITIVE.apply(texture);
    }
}
