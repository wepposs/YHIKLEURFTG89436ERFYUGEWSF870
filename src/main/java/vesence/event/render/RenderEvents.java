package vesence.event.render;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;

public class RenderEvents {

	public static final Event<RenderEvent<PoseStack>> AFTER_WORLD = create();
	public static final Event<RenderEvent<GuiGraphicsExtractor>> HUD = create();

	private static <T> Event<RenderEvent<T>> create() {
		return EventFactory.createArrayBacked(RenderEvent.class, listeners -> element -> {
			ProfilerFiller prof = Profiler.get();
			for (RenderEvent<T> listener : listeners) {
				prof.push("handler: "+listener.toString());
				listener.rendered(element);
				prof.pop();
			}
		});
	}

	@FunctionalInterface
	public interface RenderEvent<T> {
		void rendered(T context);
	}
}
