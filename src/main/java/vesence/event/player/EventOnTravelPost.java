package vesence.event.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.Vec3d;
import vesence.event.Event;

@Environment(EnvType.CLIENT)
public class EventOnTravelPost extends Event {
    private Vec3d oldVelocity;

    public EventOnTravelPost(Vec3d oldVelocity) {
        this.oldVelocity = oldVelocity;
    }

    public Vec3d getOldVelocity() {
        return oldVelocity;
    }

    public void setOldVelocity(Vec3d oldVelocity) {
        this.oldVelocity = oldVelocity;
    }
}
