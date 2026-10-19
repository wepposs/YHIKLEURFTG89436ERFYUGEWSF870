package vesence.module.impl.movement;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public class FreecamState {
    public static Vec3 pos = null;
    public static Vec3 prevPos = null;
    public static float yaw;
    public static float pitch;
    public static float prevYaw;
    public static float prevPitch;
}
