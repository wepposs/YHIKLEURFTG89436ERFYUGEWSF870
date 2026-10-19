package vesence.hmi.layers;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.texture.SpriteAtlasTexture;

/**
 * Порт HMI ShadedRenderLayers под 1.21.11.
 *
 * Оригинал собирал собственный MultiPhase-слой для затенённых (occluded) блоков
 * через RenderLayer.of(...MultiPhaseParameters...). В 1.21.11 этого API больше нет.
 * Блоки в первом лице теперь сабмитятся напрямую через
 * OrderedRenderCommandQueue.submitBlock(...), поэтому отдельный слой нужен только как
 * запасной вариант для совместимости — используем стандартный cutout-слой атласа блоков.
 */
public class ShadedRenderLayers {
    public static final RenderLayer ShadedOccludedLayer =
            RenderLayers.entityCutout(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
}
