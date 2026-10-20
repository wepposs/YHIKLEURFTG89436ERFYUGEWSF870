package vesence.module.impl.combat.rotation;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import vesence.module.impl.combat.AttackAura;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.Rotation;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.URotations;

import java.util.concurrent.ThreadLocalRandom;

import static vesence.utils.other.IMinecraft.mc;

/**
 * РџРѕСЂС‚ sweetie.evaware.api.utils.rotation.rotations.UniversalRotation.
 *
 * РћС‚Р»РёС‡РёСЏ РѕС‚ РѕСЂРёРіРёРЅР°Р»Р° (РѕР±СЏР·Р°С‚РµР»СЊРЅС‹Рµ):
 *   1) Rotation РІ Vesence вЂ” С‡РёС‚Р°СЋС‚СЃСЏ РїРѕР»СЏ .yaw / .pitch.
 *   2) RotationUtil.calculateDelta в†’ РёРЅР»Р°Р№РЅ (wrapDegrees / РІС‹С‡РёС‚Р°РЅРёРµ).
 *   3) RaytracingUtil.raytraceEntity в†’ Р»РѕРєР°Р»СЊРЅС‹Р№ РјРµС‚РѕРґ С‡РµСЂРµР· ProjectileUtil.raycast.
 *   4) PlayerUtil.hasCollisionWith / getBlock в†’ Р»РѕРєР°Р»СЊРЅС‹Рµ РјРµС‚РѕРґС‹.
 *   5) AuraModule.getInstance().getAttackDistance() в†’ attackDist (РїР°СЂР°РјРµС‚СЂ).
 *   6) AuraModule.target в†’ РјС‹ РёСЃРїРѕР»СЊР·СѓРµРј РїРµСЂРµРґР°РЅРЅС‹Р№ LivingEntity target.
 *   7) Sink вЂ” URotations.update(...) РєР°Рє РІ РѕСЃС‚Р°Р»СЊРЅС‹С… U*Rotation Vesence.
 *
 * Р›РѕРіРёРєР° process (РІСЃРµ 15+ СѓСЃР»РѕРІРёР№ РЅР° maxYawSpeed/maxPitchSpeed, pitch-direction
 * РёР·РјРµРЅРµРЅРёРµ, spookySkeletons/jopa, huly-РІРµС‚РєРё) СЃРѕС…СЂР°РЅРµРЅР° СЃС‚СЂРѕРєР°-РІ-СЃС‚СЂРѕРєСѓ.
 */
@Environment(EnvType.CLIENT)
public final class UVulcanRotation {

    private UVulcanRotation() {
    }

    /* в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ РєРѕРЅС„РёРі (Р·Р°РґР°С‘С‚СЃСЏ РёР·РІРЅРµ) в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ */

    private static float dermoYaw   = 180f;
    private static float dermoPitch = 180f;
    private static boolean spookySkeletons = false;
    private static boolean huly = false;

    public static void configure(float maxYawSpeed, float maxPitchSpeed,
                                 boolean spookySkeletonsMode, boolean hulyMode) {
        dermoYaw = maxYawSpeed;
        dermoPitch = maxPitchSpeed;
        spookySkeletons = spookySkeletonsMode;
        huly = hulyMode;
    }

    /* в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ СЃРѕСЃС‚РѕСЏРЅРёРµ в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ */

    private static float lastYawDelta = 0f;
    private static float lastPitchDelta = 0f;
    private static int lastPitchChangeDirection = 0;
    private static int ticksSinceSwitchedDirection = 0;
    private static int jopa = 0;

    /* в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ sink в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ */

    private static final float YAW_LIMIT   = 360.0F;
    private static final float PITCH_LIMIT = 10.0F;
    private static final int   PRIORITY    = 1;
    private static final int   SLOT        = 5;

    /* =========================================================
       РџСѓР±Р»РёС‡РЅС‹Р№ API
       ========================================================= */

    public static void rotation(LivingEntity target,
                                float attackDist,
                                float preDist,
                                Vec3 fallbackPoint) {
        if (mc.player == null || mc.level == null || target == null) return;

        float range = Math.max(3.0F, attackDist + preDist);

        Rotation currentRotation = new Rotation(mc.player.getYRot(), mc.player.getXRot());

        Rotation targetRotation = new Rotation(
                calculateYaw(target),
                calculatePitch(target)
        );

        Rotation result = process(currentRotation, targetRotation, target, range);

        URotations.update(
                result,
                YAW_LIMIT, YAW_LIMIT,
                PITCH_LIMIT, PITCH_LIMIT,
                PRIORITY, SLOT, false
        );
    }

    public static void reset() {
        lastYawDelta = 0f;
        lastPitchDelta = 0f;
        lastPitchChangeDirection = 0;
        ticksSinceSwitchedDirection = 0;
        jopa = 0;
        stopRotation();
    }

    public static void state() { reset(); }

    /* =========================================================
       РЇРґСЂРѕ вЂ” 1:1 РёР· sweetie.UniversalRotation.process
       ========================================================= */

    private static Rotation process(Rotation currentRotation,
                                    Rotation targetRotation,
                                    Entity entity,
                                    float attackDist) {

        // РђРЅР°Р»РѕРі RotationUtil.calculateDelta(current, target)
        float yawDelta   = Mth.wrapDegrees(targetRotation.yaw - currentRotation.yaw);
        float pitchDelta = targetRotation.pitch - currentRotation.pitch;

        float maxYawSpeed   = dermoYaw   / 3f;
        float maxPitchSpeed = dermoPitch / 3f;

        // в”Ђв”Ђв”Ђ СЃРјРµРЅР° РЅР°РїСЂР°РІР»РµРЅРёСЏ pitch в”Ђв”Ђв”Ђ
        if ((pitchDelta < 0f && lastPitchDelta > 0f)
                || (pitchDelta > 0f && lastPitchDelta < 0f)) {
            ticksSinceSwitchedDirection = 0;
        } else {
            ++ticksSinceSwitchedDirection;
        }

        boolean invalid = ticksSinceSwitchedDirection == 0 && Math.abs(pitchDelta) > 5f;
        if (invalid) {
            pitchDelta -= 1f;
            pitchDelta *= 0.3f;
            maxPitchSpeed *= 0.4f;
        }

        // ТЗ: в окне атаки ротация чистая — микро-шум не накладываем, иначе он
        // ложится ровно на тик удара.
        boolean attackWindow = URotationMath.inAttackWindow();

        if (!attackWindow && Math.abs(pitchDelta) < 0.05f) {
            pitchDelta -= (float) (Math.random() * 0.05f - 0.225f);
        }

        if (!attackWindow && Math.abs(yawDelta - lastYawDelta) < 0.08f) {
            yawDelta -= (float) (Math.random() * 0.15f - 0.125f);
        }

        if (!attackWindow && Math.abs(pitchDelta) < 0.01f) {
            pitchDelta -= (float) (Math.random() * 0.01f - 0.005f);
        }

        // в”Ђв”Ђв”Ђ 15+ СѓСЃР»РѕРІРёР№ РЅР° СЃРєРѕСЂРѕСЃС‚СЊ в”Ђв”Ђв”Ђ
        if (Math.abs(yawDelta) > 180.25f) {
            maxYawSpeed *= 0.8f;
        }
        if (Math.abs(yawDelta) > 15.0f && Math.abs(pitchDelta) < 0.1f) {
            maxYawSpeed *= 0.7f;
        }
        if (Math.abs(yawDelta) < 0.05f && Math.abs(pitchDelta) < 0.05f) {
            maxYawSpeed *= 1.1f;
            maxPitchSpeed *= 1.1f;
        }
        if (yawDelta > 1.25f && lastYawDelta > 1.25f) {
            yawDelta -= lastYawDelta;
            maxYawSpeed *= 3;
        }
        if (Math.abs(yawDelta) > 2.75f && Math.abs(pitchDelta) == 0.0f) {
            maxYawSpeed *= 0.8f;
            maxPitchSpeed *= 1.1f;
        }
        if (Math.abs(yawDelta) > 0.5f && Math.abs(pitchDelta) < 0.05f) {
            maxYawSpeed *= 0.7f;
            maxPitchSpeed *= 1.05f;
        }
        if (Math.abs(yawDelta) > 1.825f && Math.abs(pitchDelta) == 0.0f) {
            maxYawSpeed *= 0.6f;
            maxPitchSpeed *= 0.9f;
        }
        if (Math.abs(yawDelta) > 20.0f && Math.abs(pitchDelta) < 0.1f) {
            maxYawSpeed *= 0.5f;
            maxPitchSpeed *= 1.1f;
        }
        if (Math.abs(yawDelta) > 0.25f && Math.abs(pitchDelta) > 0.25f
                && Math.abs(pitchDelta) < 20.0f && Math.abs(yawDelta) < 20.0f) {
            maxYawSpeed *= 0.95f;
            maxPitchSpeed *= 0.85f;
        }
        if (Math.abs(yawDelta) > 0.1f && Math.abs(pitchDelta) > 0.1f
                && Math.abs(yawDelta) < 20.0f && Math.abs(pitchDelta) < 20.0f) {
            maxYawSpeed *= 0.9f;
            maxPitchSpeed *= 0.8f;
        }
        if (Math.abs(yawDelta) > 0.05f && Math.abs(pitchDelta) == 0.0f) {
            maxYawSpeed *= 0.8f;
            maxPitchSpeed *= 0.95f;
        }
        if (Math.abs(yawDelta) > 0.05f && Math.abs(pitchDelta) < 0.05f) {
            maxYawSpeed *= 0.85f;
            maxPitchSpeed *= 1.1f;
        }
        if (Math.abs(yawDelta) > 0.75f && Math.abs(pitchDelta) > 0.75f) {
            maxYawSpeed *= 0.8f;
            maxPitchSpeed *= 0.75f;
        }
        if (Math.abs(yawDelta) > 0.03f && Math.abs(pitchDelta) > 0.03f) {
            maxYawSpeed *= 0.9f;
            maxPitchSpeed *= 0.8f;
        }

        int currentPitchChangeDirection = pitchDelta > 0 ? 1 : -1;
        if (lastPitchChangeDirection != 0
                && currentPitchChangeDirection != lastPitchChangeDirection) {
            maxPitchSpeed *= 0.2f;
        }
        lastPitchChangeDirection = currentPitchChangeDirection;

        // в”Ђв”Ђв”Ђ raytrace + collision checks в”Ђв”Ђв”Ђ
        EntityHitResult raytraceResult = raytraceEntity(attackDist, currentRotation);
        boolean check = raytraceResult != null && raytraceResult.getEntity() == entity;
        boolean checked = entity != null
                && hasCollisionWith(entity, 1f)
                && (getBlock(0, 2, 0) != Blocks.AIR
                && getBlock(0, -1, 0) != Blocks.AIR
                && getBlock(0, 2, 0) != Blocks.WATER
                && getBlock(0, -1, 0) != Blocks.WATER);

        // в”Ђв”Ђв”Ђ spooky skeletons / jopa в”Ђв”Ђв”Ђ
        if (spookySkeletons) {
            int maxJopa = 20;
            float deldeldel = !huly ? 30f : 13f;

            if (checked) {
                pitchDelta /= deldeldel;
                yawDelta /= deldeldel;
                jopa = maxJopa;
            }

            if (!huly && !checked && check) {
                maxPitchSpeed *= 1.3f;
                maxYawSpeed *= 1.1f;
            } else if (!huly && !checked) {
                maxPitchSpeed *= 1.1f;
                maxYawSpeed *= 1.25f;
            }

            if (jopa-- > 0) {
                float superJopa = Math.max(1f, (jopa / (float) maxJopa) * 15f);
                yawDelta /= superJopa;
                pitchDelta /= superJopa;
            }
        }

        lastYawDelta = yawDelta;
        lastPitchDelta = pitchDelta;

        return new Rotation(
                currentRotation.yaw + Mth.clamp(yawDelta, -maxYawSpeed, maxYawSpeed),
                currentRotation.pitch + Mth.clamp(pitchDelta, -maxPitchSpeed, maxPitchSpeed)
        );
    }

    /* =========================================================
       Aim point в†’ yaw/pitch
       ========================================================= */

    private static float calculateYaw(LivingEntity target) {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 point = closestPointOnBox(eye, target.getBoundingBox());
        Vec3 dir = point.subtract(eye).normalize();
        return Mth.wrapDegrees(
                (float) Math.toDegrees(Math.atan2(dir.z, dir.x)) - 90.0F);
    }

    private static float calculatePitch(LivingEntity target) {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 point = closestPointOnBox(eye, target.getBoundingBox());
        Vec3 dir = point.subtract(eye).normalize();
        return (float) -Math.toDegrees(
                Math.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)));
    }

    private static Vec3 closestPointOnBox(Vec3 p, AABB box) {
        return new Vec3(
                Mth.clamp(p.x, box.minX, box.maxX),
                Mth.clamp(p.y, box.minY, box.maxY),
                Mth.clamp(p.z, box.minZ, box.maxZ)
        );
    }

    /* =========================================================
       Raytrace
       ========================================================= */

    private static EntityHitResult raytraceEntity(float range, Rotation rotation) {
        if (mc.player == null || mc.level == null) return null;

        Vec3 look = Vec3.directionFromRotation(rotation.pitch, rotation.yaw);
        Vec3 eye = mc.player.getEyePosition();
        Vec3 end = eye.add(look.scale(range));
        AABB box = mc.player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);

        return ProjectileUtil.getEntityHitResult(
                mc.player, eye, end, box,
                e -> e != mc.player && e.isAlive() && e.isPickable(),
                range * range
        );
    }

    /* =========================================================
       PlayerUtil-СЌРєРІРёРІР°Р»РµРЅС‚С‹
       ========================================================= */

    private static boolean hasCollisionWith(Entity entity, float expand) {
        if (mc.player == null) return false;
        return mc.player.getBoundingBox().inflate(expand)
                .intersects(entity.getBoundingBox());
    }

    private static Block getBlock(int dx, int dy, int dz) {
        if (mc.player == null || mc.level == null) return Blocks.AIR;
        BlockPos pos = mc.player.blockPosition().offset(dx, dy, dz);
        return mc.level.getBlockState(pos).getBlock();
    }

    /* =========================================================
       РЈС‚РёР»РёС‚С‹
       ========================================================= */

    private static void stopRotation() {
        URotations.currentTask = URotations.RotationTask.IDLE;
        URotations.currentPriority = 0;
    }
}
