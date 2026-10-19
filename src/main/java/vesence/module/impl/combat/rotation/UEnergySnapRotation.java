package vesence.module.impl.combat.rotation;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.Rotation;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.URotations;

import static vesence.utils.other.IMinecraft.mc;

/**
 * Snap - 1:1 port of Energy Snap rotation
 * (original class o5lOMjuxc5ezLSPO, id "Snap").
 *
 * Snap does not aim at the hitbox centre. Each tick it samples the target
 * bounding box on a 3x3x3 grid and picks the sample that is closest in angle
 * to the current look, requiring a clear line of sight, and preferring the
 * closest reachable one. It then drives the rotation engine with 360-degree
 * speeds and a 6-tick timeout, so the crosshair snaps onto the chosen point.
 *
 * Energy re-samples only when the tick or target changes; that caching is
 * reproduced with the lastTick/lastTargetId fields below. On attack the swing
 * window is re-armed (Energy arms it through the attack callback).
 */
@Environment(EnvType.CLIENT)
public class UEnergySnapRotation {

    private static final int TIMEOUT = 6;
    private static final float SPEED = 360.0F;
    private static final double EPS = 1.0E-7;
    private static final int AIM_SLOT = Integer.MIN_VALUE;

    private static boolean armed = true;
    private static int lastTick = AIM_SLOT;
    private static int lastTargetId = AIM_SLOT;
    private static Rotation pending;

    public static void rotation(LivingEntity target, float attackDist, float rotateDist, Vec3 point) {
        if (mc.player == null || mc.level == null || target == null) return;

        if (lastTick == mc.player.tickCount && lastTargetId == target.getId()) return;
        lastTick = mc.player.tickCount;
        lastTargetId = target.getId();

        Vec3 eye = mc.player.getEyePosition();
        Vec3 look = mc.player.getRotationVec(1.0F);
        Rotation from = new Rotation(mc.player.getYRot(), mc.player.getXRot());

        Rotation chosen = armed && !mc.player.isSneaking()
                ? sampleBox(target, eye, from)
                : sampleAway(target, eye, look, from);

        Rotation rot = armed && !mc.player.isSneaking()
                ? new Rotation(chosen.yaw, chosen.pitch)
                : chosen;

        URotations.update(rot, SPEED, SPEED, SPEED, SPEED, TIMEOUT, 0, false);
        armed = !mc.player.isSneaking();
        pending = rot;
    }

    /** Energy: pick the closest visible box sample. */
    private static Rotation sampleBox(LivingEntity target, Vec3 eye, Rotation from) {
        AABB box = target.getBoundingBox();
        double reach = reachDist(target);
        Rotation best = null;
        Rotation reachable = null;
        Rotation visible = null;
        double bestDist = Double.POSITIVE_INFINITY;
        double reachDist = Double.POSITIVE_INFINITY;
        double visDist = Double.POSITIVE_INFINITY;
        boolean anyVisible = false;

        for (Vec3 sample : samples(box)) {
            Rotation rot = toRotation(sample.subtract(eye));
            double dist = angleDist(from, rot);
            boolean traced = hasLineOfSight(eye, sample);
            anyVisible |= traced;
            if (dist < bestDist) { bestDist = dist; best = rot; }

            Vec3 probe = eye.add(sample.subtract(eye).normalize().multiply(reach));
            if (box.contains(eye) || box.raycast(eye, probe).isPresent()) {
                if (dist < reachDist) { reachDist = dist; reachable = rot; }
                if (traced && dist < visDist) { visDist = dist; visible = rot; }
            }
        }

        Rotation chosen = anyVisible ? visible : reachable;
        if (chosen != null) return chosen;
        return best != null ? best : from;
    }

    /** Energy: when not armed, aim along the current view direction. */
    private static Rotation sampleAway(LivingEntity target, Vec3 eye, Vec3 look, Rotation from) {
        double reach = reachDist(target);
        Vec3 probe = eye.add(look.multiply(reach));
        return target.getBoundingBox().raycast(eye, probe).isPresent()
                ? toRotation(probe.subtract(eye)) : from;
    }

    private static double reachDist(LivingEntity target) {
        return mc.player.distanceTo(target) + 1.0;
    }

    /** Energy: 3x3x3 grid over the hitbox. */
    private static List<Vec3> samples(AABB box) {
        List<Vec3> out = new ArrayList<>();
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                for (int z = 0; z < 3; z++) {
                    double fx = box.minX + (box.maxX - box.minX) * (x / 2.0);
                    double fy = box.minY + (box.maxY - box.minY) * (y / 2.0);
                    double fz = box.minZ + (box.maxZ - box.minZ) * (z / 2.0);
                    out.add(new Vec3(fx, fy, fz));
                }
            }
        }
        return out;
    }

    private static boolean hasLineOfSight(Vec3 eye, Vec3 point) {
        ClipContext ctx = new ClipContext(eye, point, ClipContext.ShapeType.COLLIDER,
                ClipContext.FluidHandling.NONE, mc.player);
        HitResult hit = mc.level.raycast(ctx);
        return hit.getType() == HitResult.Type.MISS
                || point.squaredDistanceTo(eye) <= hit.getPos().squaredDistanceTo(eye) + EPS;
    }

    private static double angleDist(Rotation a, Rotation b) {
        double dYaw = Math.abs(Mth.wrapDegrees(a.yaw - b.yaw));
        double dPitch = Math.abs(a.pitch - b.pitch);
        return Math.sqrt(dYaw * dYaw + dPitch * dPitch);
    }

    private static Rotation toRotation(Vec3 v) {
        float yaw = (float) Math.toDegrees(Math.atan2(-v.x, v.z));
        float pitch = (float) Mth.clamp(
                -Math.toDegrees(Math.atan2(v.y, Math.hypot(v.x, v.z))), -90.0F, 90.0F);
        return new Rotation(yaw, pitch);
    }

    /** Energy onAttack -> re-arms the snap. */
    public static void onAttack() {
        armed = true;
    }

    public static void state() {
        armed = true;
        lastTick = AIM_SLOT;
        lastTargetId = AIM_SLOT;
        pending = null;
    }
}
