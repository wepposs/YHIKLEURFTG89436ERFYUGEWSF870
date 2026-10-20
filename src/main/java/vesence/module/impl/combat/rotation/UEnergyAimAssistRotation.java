package vesence.module.impl.combat.rotation;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.Rotation;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.URotations;

import static vesence.utils.other.IMinecraft.mc;

/**
 * AimAssist - 1:1 port of Energy's AimAssist rotation
 * (original class {@code EX76VpQXyzsXIbJW}, id "AimAssist").
 *
 * <p>Energy source, verbatim:
 * <pre>
 *   float prevYaw = player.getYRot(), prevPitch = player.getXRot();
 *   boolean visible = isVisible(yaw, pitch, 100.0, target);
 *   if (visible) {
 *       RotationEngine.reset();
 *   } else {
 *       Vec3 v = target.getBoundingBox().getCenter().subtract(player.getEyePosition());
 *       float yaw   = (float) wrapDegrees(Math.toDegrees(Math.atan2(v.z, v.x)) - 90.0);
 *       float pitch = (float) (-Math.toDegrees(Math.atan2(v.y, Math.hypot(v.x, v.z))));
 *       pitch = clamp(pitch, -89.9F, 89.9F);
 *       RotationEngine.update(new Rotation(yaw, pitch),
 *           force, force, force, force, 1, 0, true);
 *   }
 * </pre>
 *
 * <p>The aim-force value is Energy's "aim force" slider (default 0.5F, min
 * 0.05F); it scales all four speeds. It is exposed through
 * {@link #setForce(float)} so the module setting can drive it.
 */
@Environment(EnvType.CLIENT)
public class UEnergyAimAssistRotation {

    private static final float PITCH_MIN = -89.9F;
    private static final float PITCH_MAX = 89.9F;
    private static final double VISIBLE_RANGE = 100.0;

    /** Energy's aim-force slider value (default 0.5F). */
    public static float force = 0.5F;

    public static void rotation(LivingEntity target, float attackDist, float rotateDist, Vec3 point) {
        if (mc.player == null || mc.level == null || target == null) return;

        float yaw = mc.player.getYRot();
        float pitch = mc.player.getXRot();

        if (isVisible(yaw, pitch, target, false)) {
            // Energy: RotationEngine.reset();
            URotations.currentTask = URotations.RotationTask.IDLE;
            return;
        }

        Vec3 eye = mc.player.getEyePosition();
        // ТЗ: аим-поинт — ближайшая ВИДИМАЯ точка хитбокса, не центр коробки.
        Vec3 aim = point != null ? point : URotationMath.visibleAimPoint(target, rotateDist, false);
        if (aim == null) aim = target.getBoundingBox().getCenter();
        Vec3 v = aim.subtract(eye);

        float aimYaw = Mth.wrapDegrees(
                (float) Math.toDegrees(Math.atan2(v.z, v.x)) - 90.0F);
        float aimPitch = Mth.clamp(
                (float) (-Math.toDegrees(Math.atan2(v.y, Math.hypot(v.x, v.z)))),
                PITCH_MIN, PITCH_MAX);

        Rotation rot = new Rotation(aimYaw, aimPitch);
        URotations.update(rot, force, force, force, force, 1, 0, true);
    }

    public static void setForce(float value) {
        force = value;
    }

    /**
     * Energy's shared visibility helper. {@code aimAssist} selects the slightly
     * larger ray used by the AimAssist variant.
     */
    public static boolean isVisible(float yaw, float pitch, LivingEntity target, boolean aimAssist) {
        if (mc.player == null || target == null) return false;
        double range = aimAssist ? VISIBLE_RANGE : 100.0;
        Vec3 eye = mc.player.getEyePosition();
        Vec3 look = Vec3.directionFromRotation(pitch, yaw).scale(range);
        AABB box = target.getBoundingBox().inflate(0.05);
        return box.clip(eye, eye.add(look)).isPresent() || box.contains(eye);
    }

    public static void state() {
        force = 0.5F;
    }
}
