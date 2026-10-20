package vesence.hmi.layers;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlas;

/**
 * Порт HMI ShadedRenderLayers под 1.21.11.
 *
 * Оригинал собирал собственный MultiPhase-слой для затенённых (occluded) блоков
 * через RenderType.of(...MultiPhaseParameters...). В 1.21.11 этого API больше нет.
 * Блоки в первом лице теперь сабмитятся напрямую через
 * OrderedRenderCommandQueue.submitBlock(...), поэтому отдельный слой нужен только как
 * запасной вариант для совместимости — используем стандартный cutout-слой атласа блоков.
 */
public class ShadedRenderLayers {
    public static final RenderType ShadedOccludedLayer =
            RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS);
}
