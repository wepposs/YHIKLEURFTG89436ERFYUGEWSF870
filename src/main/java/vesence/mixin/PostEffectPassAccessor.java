package vesence.mixin;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(PostPass.class)
public interface PostEffectPassAccessor {
	@Accessor("customUniforms")
	Map<String, GpuBuffer> getUniformBuffers();
}
