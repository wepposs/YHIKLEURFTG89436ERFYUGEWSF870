package vesence.module.impl.combat.auraComponent.rotationComponent.impl;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.event.EventInit;
import vesence.event.player.EventInput;
import vesence.module.impl.combat.auraComponent.rotationComponent.Component;

@Environment(EnvType.CLIENT)
public class MoveComponent extends Component {

    public static int stopTicks = 0;
    public static boolean stop = false;

    @EventInit
    public void onEvent(EventInput event) {
        if (stop) {
            event.setStrafe(0.0F);
            event.setForward(0.0F);
            event.setJump(false);
            if (mc.options != null && mc.options.sprintKey != null) {
                mc.options.sprintKey.setPressed(false);
            }
            if (mc.player != null) {
                mc.player.setSprinting(false);
            }

            if (stopTicks > 0) {
                stopTicks--;
                if (stopTicks == 0) {
                    stop = false;
                }
            }
        }
    }
}
