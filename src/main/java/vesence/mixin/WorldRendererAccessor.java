package vesence.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelRenderer.class)
public interface WorldRendererAccessor {

    @Accessor("entityOutlineTarget")
    RenderTarget vesence$getEntityOutlineFramebufferRaw();
}
