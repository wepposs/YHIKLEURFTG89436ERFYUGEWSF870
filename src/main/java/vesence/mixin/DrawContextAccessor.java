package vesence.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GuiGraphics.class)
public interface DrawContextAccessor {
	@Accessor("state")
	GuiRenderState getState();

	@Accessor("scissorStack")
	GuiGraphics.ScissorStack getScissorStack();
}
