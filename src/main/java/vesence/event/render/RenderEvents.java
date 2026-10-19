package vesence.event.render;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.gui.GuiGraphics;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiler.Profilers;

public class RenderEvents {

	public static final Event<RenderEvent<PoseStack>> AFTER_WORLD = create();
	public static final Event<RenderEvent<GuiGraphics>> HUD = create();

	private static <T> Event<RenderEvent<T>> create() {
		return EventFactory.createArrayBacked(RenderEvent.class, listeners -> element -> {
			Profiler prof = Profilers.get();
			for (RenderEvent<T> listener : listeners) {
				prof.push("handler: "+listener.toString());
				listener.rendered(element);
				prof.popPose();
			}
		});
	}

	@FunctionalInterface
	public interface RenderEvent<T> {
		void rendered(T context);
	}
}
