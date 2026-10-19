package vesence.utils.math;

import net.minecraft.util.Mth;

public class AngleUtil {
    public static float interpolateAngle(float current, float target, float factor) {
        float difference = Mth.wrapDegrees(target - current);
        return current + difference * factor;
    }

    public static float normalizeYaw(float yaw) {
        yaw %= 360;
        if (yaw >= 180.0F) yaw -= 360.0F;
        if (yaw < -180.0F) yaw += 360.0F;
        return yaw;
    }

    public static float normalizePitch(float pitch) {
        return Mth.clamp(pitch, -90.0F, 90.0F);
    }
}
