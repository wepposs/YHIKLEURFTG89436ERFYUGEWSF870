package vesence.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.fog.FogRenderer;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
	@Accessor
	CrossFrameResourcePool getResourcePool();
	@Accessor
	FogRenderer getFogRenderer();
}
