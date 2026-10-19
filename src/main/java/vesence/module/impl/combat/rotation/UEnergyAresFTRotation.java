package vesence.module.impl.combat.rotation;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.Rotation;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.URotations;

import static vesence.utils.other.IMinecraft.mc;

/**
 * Ares/FT - 1:1 port of Energy Ares/FT rotation
 * (original class rPLzNOwUg2Zo11au, id "Ares/FT").
 *
 * Energy drives the aim with two multi-tone sine/cosine generators, one for
 * yaw and one for pitch, both keyed on (player.tickCount + targetId * 0.137):
 *
 *   yawDrift(t) = sin(t*0.48*2.17)*24.0*0.19*speed
 *               + cos(t*0.72*2.17 + 0.84)*16.5*0.55*speed
 *               + sin(t*0.91*2.17 + 0.38) * cos(t*0.38*2.17 + 1.62) * 16.5*0.32*speed
 *
 *   pitchDrift(t) = cos(t*0.54 + 0.32)*0.72*0.91
 *                 + sin(t*0.91 + 2.17)*0.72*0.38
 *                 + cos(t*0.91 + 0.38) * sin(t*0.38) * 0.72*0.55
 *
 * plus a "jump boost" window and a first-tick yaw snap. All constants are
 * kept verbatim from the Energy static block.
 */
@Environment(EnvType.CLIENT)
public class UEnergyAresFTRotation {

    private static final float BASE = 24.0F;
    private static final float YAW_TONE_A = 2.17F;
    private static final float YAW_TONE_B = 1.9F;
    private static final float YAW_JUMP_DIV = 0.7F;
    private static final float YAW_JUMP_DIV2 = 0.3F;
    private static final float ENGINE_YAW_SPEED = 360.0F;
    private static final float ENGINE_PITCH_SPEED = 360.0F;
    private static final float ENGINE_RET_Y = 360.0F;
    private static final float ENGINE_RET_P = 360.0F;
    private static final float TARGET_ID_SCALE = 0.137F;
    private static final float PITCH_FALLBACK = 0.41F;

    private static final double YAW_A_FREQ = 0.48;
    private static final double YAW_A_AMP = 24.0;
    private static final double YAW_A_SCALE = 0.19;
    private static final double YAW_B_FREQ = 0.72;
    private static final double YAW_B_PHASE = 0.84;
    private static final double YAW_B_AMP = 16.5;
    private static final double YAW_B_SCALE = 0.55;
    private static final double YAW_C_FREQ = 0.91;
    private static final double YAW_C_PHASE = 0.38;
    private static final double YAW_C_AMP = 16.5;
    private static final double YAW_C_SCALE = 0.32;
    private static final double YAW_D_FREQ = 0.38;
    private static final double YAW_D_PHASE = 1.62;

    private static final double PITCH_A_FREQ = 0.54;
    private static final double PITCH_A_PHASE = 0.32;
    private static final double PITCH_A_AMP = 0.72;
    private static final double PITCH_A_SCALE = 0.91;
    private static final double PITCH_B_FREQ = 0.91;
    private static final double PITCH_B_PHASE = 2.17;
    private static final double PITCH_B_AMP = 0.72;
    private static final double PITCH_B_SCALE = 0.38;
    private static final double PITCH_C_FREQ = 0.91;
    private static final double PITCH_C_PHASE = 0.38;
    private static final double PITCH_C_AMP = 0.72;
    private static final double PITCH_C_SCALE = 0.55;
    private static final double PITCH_D_FREQ = 0.38;

    private static final float BOOST_YAW = 2.75F;
    private static final float BOOST_PITCH = 1.9F;
    private static final float BOOST_LERP_Y = 0.7F;
    private static final float BOOST_LERP_P = 0.3F;
    private static final int BOOST_TICKS = 6;
    private static final int AIM_SLOT = Integer.MIN_VALUE;

    private static int lastTargetId = AIM_SLOT;
    private static int lastTick = AIM_SLOT;
    private static float phaseOffset;
    private static float smoothedYaw;
    private static float smoothedPitch;
    private static float lastPitchBase;
    private static boolean boosting;
    private static int boostTicksLeft;

    public static void rotation(LivingEntity target, float attackDist, float rotateDist, Vec3 point) {
        if (mc.player == null || target == null) return;

        boolean newTarget = target.getId() != lastTargetId;
        if (newTarget) {
            onTargetChanged(target);
        }
        if (!newTarget && lastTick == mc.player.tickCount) return;
        lastTick = mc.player.tickCount;

        Vec3 eye = mc.player.getEyePosition();
        // ТЗ: аим-поинт — ближайшая ВИДИМАЯ точка хитбокса.
        Vec3 aimPoint = URotationMath.visibleAimPoint(target, rotateDist, false);
        if (aimPoint == null) aimPoint = target.getBoundingBox().getCenter();
        Rotation aim = toRotation(aimPoint.subtract(eye));

        float t = mc.player.tickCount + phaseOffset;
        float jump = boosting ? BOOST_YAW : 1.0F;
        float jumpPitch = boosting ? BOOST_PITCH : 1.0F;
        if (mc.player.isSneaking()) jump /= 2.0F;

        float yawDrift = yawDrift(t, jump);
        float pitchDrift = pitchDrift(t);
        // ТЗ: в окне атаки jitter-дрейф не накладывается — иначе он ложится
        // ровно на тик удара.
        if (URotationMath.inAttackWindow()) {
            yawDrift = 0.0F;
            pitchDrift = 0.0F;
        }
        float targetYaw = aim.yaw + yawDrift;
        float targetPitch = Mth.clamp(lastPitchBase + pitchDrift, -90.0F, 90.0F);

        float outYaw;
        if (mc.player.isSneaking()) {
            smoothedYaw = targetYaw;
            outYaw = targetYaw;
        } else {
            smoothedYaw = lerpAngle(smoothedYaw, targetYaw, BOOST_LERP_Y);
            outYaw = smoothedYaw;
        }

        if (mc.player.isSneaking() && !isVisible(mc.player.getYRot(), targetPitch, target)) {
            lastPitchBase = lerpAngle(lastPitchBase, aim.pitch, BOOST_LERP_P);
            targetPitch = Mth.clamp(lastPitchBase + pitchDrift, -90.0F, 90.0F);
        }
        smoothedPitch = targetPitch;

        Rotation rot = new Rotation(outYaw, smoothedPitch);
        URotations.update(rot, ENGINE_YAW_SPEED, ENGINE_PITCH_SPEED, ENGINE_RET_Y, ENGINE_RET_P, 1, 6, false);

        if (boosting && --boostTicksLeft <= 0) {
            boosting = false;
            boostTicksLeft = 0;
        }
    }

    private static float yawDrift(float t, float speed) {
        double a = Math.sin(t * YAW_A_FREQ * YAW_TONE_A) * YAW_A_AMP * YAW_A_SCALE * speed;
        double b = Math.cos(t * YAW_B_FREQ * YAW_TONE_A + YAW_B_PHASE) * YAW_B_AMP * YAW_B_SCALE * speed;
        double c = Math.sin(t * YAW_C_FREQ * YAW_TONE_A + YAW_C_PHASE)
                 * Math.cos(t * YAW_D_FREQ * YAW_TONE_A + YAW_D_PHASE) * YAW_C_AMP * YAW_C_SCALE * speed;
        return (float) (a + b + c);
    }

    private static float pitchDrift(float t) {
        double a = Math.cos(t * PITCH_A_FREQ + PITCH_A_PHASE) * PITCH_A_AMP * PITCH_A_SCALE;
        double b = Math.sin(t * PITCH_B_FREQ + PITCH_B_PHASE) * PITCH_B_AMP * PITCH_B_SCALE;
        double c = Math.cos(t * PITCH_C_FREQ + PITCH_C_PHASE)
                 * Math.sin(t * PITCH_D_FREQ) * PITCH_C_AMP * PITCH_C_SCALE;
        return (float) (a + b + c);
    }

    private static void onTargetChanged(LivingEntity target) {
        lastTargetId = target.getId();
        phaseOffset = target.getId() * TARGET_ID_SCALE;
        smoothedYaw = mc.player.getYRot();
        smoothedPitch = mc.player.getXRot();
        lastPitchBase = mc.player.getXRot();
        lastTick = AIM_SLOT;
    }

    private static float lerpAngle(float from, float to, float amount) {
        return from + Mth.wrapDegrees(to - from) * amount;
    }

    private static boolean isVisible(float yaw, float pitch, LivingEntity target) {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 end = eye.add(Vec3.fromPolar(pitch, yaw).multiply(24.0));
        return target.getBoundingBox().raycast(eye, end).isPresent();
    }

    private static Rotation toRotation(Vec3 v) {
        float yaw = (float) Math.toDegrees(Math.atan2(-v.x, v.z));
        float pitch = (float) Mth.clamp(
                -Math.toDegrees(Math.atan2(v.y, Math.hypot(v.x, v.z))), -90.0F, 90.0F);
        return new Rotation(yaw, pitch);
    }

    /** Energy onAttack -> arms the jump-boost window. */
    public static void onAttack() {
        boosting = true;
        boostTicksLeft = BOOST_TICKS;
    }

    public static void state() {
        lastTargetId = AIM_SLOT;
        lastTick = AIM_SLOT;
        phaseOffset = 0.0F;
        smoothedYaw = 0.0F;
        smoothedPitch = 0.0F;
        lastPitchBase = 0.0F;
        boosting = false;
        boostTicksLeft = 0;
    }
}
