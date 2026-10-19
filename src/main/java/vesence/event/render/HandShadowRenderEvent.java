package vesence.event.render;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import com.mojang.blaze3d.vertex.PoseStack;
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
    PoseStack matrices;
    float tickDelta;
}
