package vesence.event.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.Vec3d;
import vesence.event.Event;

@Environment(EnvType.CLIENT)
public class EventOnMovePost extends Event {
    private final float speed;
    private final Vec3d movementInput;

    public EventOnMovePost(float speed, Vec3d movementInput) {
        this.speed = speed;
        this.movementInput = movementInput;
    }

    public float getSpeed() {
        return speed;
    }

    public Vec3d getMovementInput() {
        return movementInput;
    }
}
