package vesence.mixin;

import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.BufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(BufferBuilder.class)
public interface BufferBuilderAccessor {
	@Accessor("vertexPointer")
	long getMeTheFuckingPointerOfThisBitch();

	@Invoker("beginVertex")
	long beginNewVertex();

	@Accessor("format")
	VertexFormat getVertexFormat();
}
