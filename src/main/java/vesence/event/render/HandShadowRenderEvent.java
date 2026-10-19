package vesence.event.render;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.util.math.MatrixStack;
import vesence.event.Event;

@FieldDefaults(level = AccessLevel.PRIVATE)
@AllArgsConstructor
@Getter
@Setter
public class HandShadowRenderEvent extends Event {
    public enum Phase {
        PRE,
        POST
    }

    Phase phase;
    MatrixStack matrices;
    float tickDelta;
}
