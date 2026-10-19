package vesence.module.impl.combat.rotation;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.Rotation;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.URotations;

import static vesence.utils.other.IMinecraft.mc;

/**
 * ML - 1:1 port of Energy ML rotation
 * (original class dCiE3FCVCZMp7YTd, id "ML").
 *
 * ML is the same proportional-step family as HolyWorld, but without the
 * sampled-pull feedback and with a single 50-degree distance falloff:
 *
 *   aimPoint     = box centre + 0.15
 *   desiredYaw   = wrapDegrees(aimYaw - player.getYaw())
 *   desiredPitch = aimPitch - player.getPitch()
 *   speed        = abs(0.07)
 *   if abs(desiredYaw) > 50 then speed /= 1.05
 *   maxYaw   = clamp(abs(desiredYaw)   * mult,     0, 999)
 *   maxPitch = clamp(abs(desiredPitch) * mult / 2, 0, 999)
 *   newYaw   = yaw   + clamp(desiredYaw,   -maxYaw,   maxYaw)
 *   newPitch = clamp(pitch + clamp(desiredPitch, -maxPitch, maxPitch), -45, 45)
 *
 * Engine call: update(rot, 360, 360, 0, 0, 1, 1, firstPerson).
 */
@Environment(EnvType.CLIENT)
public class UEnergyMLRotation {

    private static final double AIM_LIFT = 0.15F;
    private static final float SPEED_BASE = 0.07F;
    private static final float SPEED_DIST = 1.05F;
    private static final float DIST_THRESHOLD = 50.0F;
    private static final float PITCH_LIMIT_TOP = 90.0F;
    private static final float PITCH_LIMIT_BOTTOM = -90.0F;
    private static final float CLAMP_YAW = 999.0F;
    private static final float CLAMP_PITCH = 999.0F;
    private static final float OUT_PITCH_MIN = -45.0F;
    private static final float OUT_PITCH_MAX = 45.0F;
    private static final float ENGINE_SPEED = 360.0F;
    private static final double SAMPLE_DIV_Y = 5.0;
    private static final double SAMPLE_DIV_X = 5.0;

    private static final java.util.List<float[]> samples = new java.util.ArrayList<>();

    public static void rotation(LivingEntity target, float attackDist, float rotateDist) {
        if (mc.player == null || target == null) return;

        // ТЗ: аим-поинт — ближайшая ВИДИМАЯ точка хитбокса, не центр коробки.
        Vec3d aimPoint = URotationMath.visibleAimPoint(target, rotateDist, false);
        if (aimPoint == null) {
            aimPoint = target.getBoundingBox().getCenter().add(0.0, AIM_LIFT, 0.0);
        }
        Rotation aim = toRotation(aimPoint.subtract(mc.player.getEyePos()));

        float playerYaw = mc.player.getYaw();
        float playerPitch = mc.player.getPitch();
        float desiredYaw = MathHelper.wrapDegrees(aim.yaw - playerYaw);
        float desiredPitch = aim.pitch - playerPitch;

        float base = Math.abs(SPEED_BASE);
        if (Math.abs(desiredYaw) > DIST_THRESHOLD) {
            base /= SPEED_DIST;
        }

        boolean onPitchLimit = playerPitch == PITCH_LIMIT_TOP || playerPitch == PITCH_LIMIT_BOTTOM;
        boolean boxesIntersect = mc.player.getBoundingBox().intersects(target.getBoundingBox());
        float yawMult = (onPitchLimit || (boxesIntersect && !mc.player.isOnGround())) ? 0.0F : base;
        float maxYaw = MathHelper.clamp(Math.abs(desiredYaw) * yawMult, 0.0F, CLAMP_YAW);
        float maxPitch = MathHelper.clamp(Math.abs(desiredPitch) * base / 2.0F, 0.0F, CLAMP_PITCH);

        float stepYaw = MathHelper.clamp(desiredYaw, -maxYaw, maxYaw);
        float stepPitch = MathHelper.clamp(desiredPitch, -maxPitch, maxPitch);

        float newYaw = playerYaw + stepYaw;
        float newPitch = MathHelper.clamp(playerPitch + stepPitch, OUT_PITCH_MIN, OUT_PITCH_MAX);

        Rotation rot = new Rotation(newYaw, newPitch);
        URotations.update(rot, ENGINE_SPEED, ENGINE_SPEED, 0.0F, 0.0F, 1, 1, mc.options.getPerspective().isFirstPerson());

        float dy = (float) (mc.player.getVelocity().y / SAMPLE_DIV_Y);
        float dx = (float) (mc.player.getVelocity().x / SAMPLE_DIV_X);
        samples.add(new float[]{dy, dx});
    }

    private static Rotation toRotation(Vec3d v) {
        float yaw = (float) Math.toDegrees(Math.atan2(-v.x, v.z));
        float pitch = (float) MathHelper.clamp(
                -Math.toDegrees(Math.atan2(v.y, Math.hypot(v.x, v.z))), -90.0F, 90.0F);
        return new Rotation(yaw, pitch);
    }

    public static void state() {
        samples.clear();
    }
}
