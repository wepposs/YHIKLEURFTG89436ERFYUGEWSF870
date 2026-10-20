package vesence.module.impl.combat.neuro;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;

public class RotationUtil {
    private static final Minecraft mc = Minecraft.getInstance();
    private static final double SENSITIVITY_SCALE = 0.15;

    public static float getMouseSensitivity() {
        double mouseSens = mc.options.sensitivity().get();
        float step = (float) (Math.pow(mouseSens * 0.6 + 0.2, 3.0) * 8.0 * SENSITIVITY_SCALE);
        return Float.isFinite(step) && step > 0.0f ? step : 0.15f;
    }

    public static Vector2f getRotationTo(Vec3 targetPos) {
        if (mc.player == null) return null;
        
        Vec3 eyePos = mc.player.getEyePosition(1.0f);
        Vec3 diff = targetPos.subtract(eyePos);
        
        if (diff.lengthSqr() < 1.0E-6) {
            return null;
        }
        
        float yaw = (float) Mth.wrapDegrees(Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0);
        float pitch = (float) Mth.wrapDegrees(-Math.toDegrees(Math.atan2(diff.y, Math.hypot(diff.x, diff.z))));
        
        return new Vector2f(yaw, Mth.clamp(pitch, -89.9f, 89.9f));
    }

    public static Vec3 getClosestPointOnBox(Vec3 point, AABB box) {
        return new Vec3(
            Mth.clamp(point.x, box.minX, box.maxX),
            Mth.clamp(point.y, box.minY, box.maxY),
            Mth.clamp(point.z, box.minZ, box.maxZ)
        );
    }

    public static Vec3 getClosestPointOnEntity(Entity entity) {
        if (mc.player == null) return new Vec3(entity.getX(), entity.getY(), entity.getZ());
        
        Vec3 eyePos = mc.player.getEyePosition(1.0f);
        return getClosestPointOnBox(eyePos, entity.getBoundingBox());
    }
}
