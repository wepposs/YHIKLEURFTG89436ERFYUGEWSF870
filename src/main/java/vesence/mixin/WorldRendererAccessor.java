package vesence.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WorldRenderer.class)
public interface WorldRendererAccessor {

    @Accessor("entityOutlineFramebuffer")
    RenderTarget vesence$getEntityOutlineFramebufferRaw();
}
