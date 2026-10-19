package vesence.module.impl.movement;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public class FreecamState {
    public static Vec3d pos = null;
    public static Vec3d prevPos = null;
    public static float yaw;
    public static float pitch;
    public static float prevYaw;
    public static float prevPitch;
}
