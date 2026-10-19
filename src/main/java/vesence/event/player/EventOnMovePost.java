package vesence.event.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.phys.Vec3;
import vesence.event.Event;

@Environment(EnvType.CLIENT)
public class EventOnMovePost extends Event {
    private final float speed;
    private final Vec3 movementInput;

    public EventOnMovePost(float speed, Vec3 movementInput) {
        this.speed = speed;
        this.movementInput = movementInput;
    }

    public float getSpeed() {
        return speed;
    }

    public Vec3 getMovementInput() {
        return movementInput;
    }
}
