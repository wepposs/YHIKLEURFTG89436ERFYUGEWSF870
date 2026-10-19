package vesence.module.impl.combat.neuro;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector2f;

public class RotationUtil {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final double SENSITIVITY_SCALE = 0.15;

    public static float getMouseSensitivity() {
        double mouseSens = mc.options.getMouseSensitivity().getValue();
        float step = (float) (Math.pow(mouseSens * 0.6 + 0.2, 3.0) * 8.0 * SENSITIVITY_SCALE);
        return Float.isFinite(step) && step > 0.0f ? step : 0.15f;
    }

    public static Vector2f getRotationTo(Vec3d targetPos) {
        if (mc.player == null) return null;
        
        Vec3d eyePos = mc.player.getCameraPosVec(1.0f);
        Vec3d diff = targetPos.subtract(eyePos);
        
        if (diff.lengthSquared() < 1.0E-6) {
            return null;
        }
        
        float yaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0);
        float pitch = (float) MathHelper.wrapDegrees(-Math.toDegrees(Math.atan2(diff.y, Math.hypot(diff.x, diff.z))));
        
        return new Vector2f(yaw, MathHelper.clamp(pitch, -89.9f, 89.9f));
    }

    public static Vec3d getClosestPointOnBox(Vec3d point, Box box) {
        return new Vec3d(
            MathHelper.clamp(point.x, box.minX, box.maxX),
            MathHelper.clamp(point.y, box.minY, box.maxY),
            MathHelper.clamp(point.z, box.minZ, box.maxZ)
        );
    }

    public static Vec3d getClosestPointOnEntity(Entity entity) {
        if (mc.player == null) return new Vec3d(entity.getX(), entity.getY(), entity.getZ());
        
        Vec3d eyePos = mc.player.getCameraPosVec(1.0f);
        return getClosestPointOnBox(eyePos, entity.getBoundingBox());
    }
}
