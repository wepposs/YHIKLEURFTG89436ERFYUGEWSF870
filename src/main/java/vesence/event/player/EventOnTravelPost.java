package vesence.event.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.phys.Vec3;
import vesence.event.Event;

@Environment(EnvType.CLIENT)
public class EventOnTravelPost extends Event {
    private Vec3 oldVelocity;

    public EventOnTravelPost(Vec3 oldVelocity) {
        this.oldVelocity = oldVelocity;
    }

    public Vec3 getOldVelocity() {
        return oldVelocity;
    }

    public void setOldVelocity(Vec3 oldVelocity) {
        this.oldVelocity = oldVelocity;
    }
}
