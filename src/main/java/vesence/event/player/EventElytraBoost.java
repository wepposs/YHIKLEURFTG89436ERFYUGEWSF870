package vesence.event.player;

import net.minecraft.world.phys.Vec3;
import vesence.event.Event;

public class EventElytraBoost extends Event {
    private Vec3 boost;

    public EventElytraBoost(Vec3 boost) {
        this.boost = boost;
    }

    public Vec3 getBoost() {
        return boost;
    }

    public void setBoost(Vec3 boost) {
        this.boost = boost;
    }
}
