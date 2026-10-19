package vesence.event.player;

import net.minecraft.util.math.Vec3d;
import vesence.event.Event;

public class EventElytraBoost extends Event {
    private Vec3d boost;

    public EventElytraBoost(Vec3d boost) {
        this.boost = boost;
    }

    public Vec3d getBoost() {
        return boost;
    }

    public void setBoost(Vec3d boost) {
        this.boost = boost;
    }
}
