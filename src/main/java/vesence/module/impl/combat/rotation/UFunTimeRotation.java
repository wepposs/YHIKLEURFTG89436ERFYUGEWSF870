package vesence.module.impl.combat.rotation;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.FreeLookUtil;

import static vesence.utils.other.IMinecraft.mc;

/**
 * UFunTimeRotation — порт FunTime-ротации из DELUXE 38.1.5 (sg.ec.Class102#m1367).
 *
 * <p>Отличия от прежнего профиля: аим-поинт строится внутри хитбокса
 * (ближайшая точка contracted-бокса + дотяжка к центру по мере сближения),
 * после удара углы замораживаются на 200–250 мс, в простое накладывается
 * cooldown-зависимая тряска с чередующимся знаком, скорость доворота —
 * пружина с кубико-квартичным изингом от attack-cooldown.
 */
@Environment(EnvType.CLIENT)
public class UFunTimeRotation {

    private static final URotationMath.AttackEdge attackEdge = new URotationMath.AttackEdge();

    /** Счётчик ударов для чередования знака тряски (Deluxe: aura hit counter). */
    private static int hitCounter;

    /** Пружина ротации (град/тик, накопленная скорость по осям). */
    private static float springYaw;
    private static float springPitch;

    /** Замороженные после удара углы и время их жизни. */
    private static long holdStartMs;
    private static long holdDurationMs;
    private static float holdYaw;
    private static float holdPitch;

    public static void rotation(LivingEntity target, boolean attack) {
        rotation(target, attack, 3.0F, 1.0F, null);
    }

    public static void rotation(LivingEntity target, boolean attack, float attackDist, float rotateDist, Vec3 point) {
        if (mc.player == null || mc.level == null || target == null) {
            return;
        }

        Vec3 eye = mc.player.getEyePosition();

        // Аим-поинт: бокс цели, сдвинутый по вектору движения, ближайшая к глазу
        // точка с дотяжкой к центру, когда цель ближе радиуса удара.
        Vec3 velocity = new Vec3(target.getX() - target.xOld,
                target.getY() - target.yOld, target.getZ() - target.zOld);
        AABB box = target.getBoundingBox()
                .move(velocity.lengthSqr() > 0.36 ? velocity.normalize().scale(0.6) : velocity)
                .deflate(0.08);
        Vec3 closest = new Vec3(
                Mth.clamp(eye.x, box.minX, box.maxX),
                Mth.clamp(eye.y, box.minY + 0.15,
                        Math.max(box.minY + 0.15, box.minY + (box.maxY - box.minY) * 0.7)),
                Mth.clamp(eye.z, box.minZ, box.maxZ));
        Vec3 toCenter = new Vec3(
                (box.minX + box.maxX) * 0.5 - closest.x, 0.0,
                (box.minZ + box.maxZ) * 0.5 - closest.z);
        Vec3 aim = closest.add(toCenter.scale(Mth.clamp(
                (attackDist - 0.15 - eye.distanceTo(closest)) / Math.max(0.05, toCenter.length()), 0.0, 0.8)));
        AABB inner = target.getBoundingBox().deflate(0.1);
        Vec3 dir = new Vec3(
                Mth.clamp(aim.x, inner.minX, inner.maxX),
                Mth.clamp(aim.y, inner.minY, inner.maxY),
                Mth.clamp(aim.z, inner.minZ, inner.maxZ))
                .subtract(eye);

        float goalYaw = (float) Mth.wrapDegrees(Math.toDegrees(Math.atan2(dir.z, dir.x)) - 90.0);
        float goalPitch = (float) Mth.clamp(
                -Math.toDegrees(Math.atan2(dir.y, Math.hypot(dir.x, dir.z))), -90.0, 90.0);

        float curYaw = mc.player.getYRot();
        float curPitch = mc.player.getXRot();

        long now = System.currentTimeMillis();
        if (attack) {
            holdStartMs = now;
            holdDurationMs = (long) URotationMath.rand(200.0F, 250.0F);
            holdYaw = goalYaw;
            holdPitch = goalPitch;
        } else if (now - holdStartMs < holdDurationMs) {
            goalYaw = holdYaw;
            goalPitch = holdPitch;
        }

        // Тряска в простое: растёт по мере отката кулдауна, знак чередуется
        // от удара к удару.
        float cooldownLeft = 1.0F - mc.player.getAttackStrengthScale(1.0F);
        float shake = attack ? 0.0F : cooldownLeft * cooldownLeft;
        float sign = hitCounter % 2 == 0 ? 1.0F : -1.0F;
        if (!attack) {
            goalYaw += (float) Math.ceil(shake * (URotationMath.rand(5.0F, 15.0F)
                    * Mth.cos(mc.player.tickCount * 0.5F) + URotationMath.rand(310.0F, 530.0F) * sign));
            goalPitch += (float) Math.ceil(shake * (URotationMath.rand(5.0F, 10.0F)
                    * Mth.sin(mc.player.tickCount * 0.5F) + URotationMath.rand(310.0F, 530.0F) * sign));
        }

        float yawDelta = Mth.wrapDegrees(goalYaw - curYaw);
        float pitchDelta = goalPitch - curPitch;
        float total = (float) Math.hypot(yawDelta, pitchDelta);

        float speedFactor = Mth.clamp(
                Mth.sin((float) (cooldownSpeed() * Math.PI)), 0.15F, 0.9F);
        float accel = total * speedFactor;
        float accelCap = 6.0F + 26.0F * speedFactor;

        springYaw += Mth.clamp(accel * (total < 0.001F ? 0.0F : yawDelta / total) - springYaw,
                -accelCap, accelCap);
        springPitch += Mth.clamp(accel * (total < 0.001F ? 0.0F : pitchDelta / total) - springPitch,
                -accelCap * 0.72F, accelCap * 0.72F);
        springYaw = Mth.clamp(springYaw, -Math.abs(yawDelta), Math.abs(yawDelta));
        springPitch = Mth.clamp(springPitch, -Math.abs(pitchDelta), Math.abs(pitchDelta));

        float newYaw = curYaw + springYaw + URotationMath.rand(-0.08F, 0.08F);
        float newPitch = Mth.clamp(curPitch + springPitch + URotationMath.rand(-0.04F, 0.04F), -90.0F, 90.0F);

        if (attackEdge.update(attack)) {
            hitCounter++;
        }

        float yawStep = Math.abs(Mth.wrapDegrees(newYaw - curYaw));
        float pitchStep = Math.abs(newPitch - curPitch);
        URotationMath.submit(yawStep, pitchStep, 23.0F, 23.0F, 0, 15, newYaw, newPitch);
    }

    /**
     * Кубико-квартичный изинг скорости от attack-cooldown (Deluxe: Class102#m1366).
     * Возвращает множитель фазы синуса (0..0.6).
     */
    private static float cooldownSpeed() {
        float progress = mc.player.getAttackStrengthScale(1.0F);
        double attackSpeed = mc.player.getAttributeValue(Attributes.ATTACK_SPEED);
        if (attackSpeed > 1.6) {
            progress *= (float) (1.6 / attackSpeed);
        }

        float eased;
        if (attackSpeed < 2.0) {
            eased = 2.70158F * progress * progress * progress - 1.70158F * progress * progress;
        } else if (progress < 0.5F) {
            eased = 8.0F * progress * progress * progress * progress;
        } else {
            float inv = progress - 1.0F;
            eased = 1.0F - 26.0F * inv * inv * inv * inv;
        }
        return (1.0F + 29.0F * eased) / 50.0F;
    }

    public static boolean smoothBack() {
        if (mc.player == null) {
            return true;
        }
        float toYaw = FreeLookUtil.freeYaw;
        float toPitch = FreeLookUtil.freePitch;
        boolean done = Math.abs(URotationMath.wrap(toYaw - mc.player.getYRot())) < 1.0F
                && Math.abs(toPitch - mc.player.getXRot()) < 1.0F;
        if (!done) {
            URotationMath.submit(24.0F, 22.0F, 24.0F, 22.0F, 0, 15, toYaw, toPitch);
        }
        return done;
    }

    public static void state() {
        attackEdge.reset();
        hitCounter = 0;
        springYaw = 0.0F;
        springPitch = 0.0F;
        holdStartMs = 0L;
        holdDurationMs = 0L;
        holdYaw = 0.0F;
        holdPitch = 0.0F;
    }
}
