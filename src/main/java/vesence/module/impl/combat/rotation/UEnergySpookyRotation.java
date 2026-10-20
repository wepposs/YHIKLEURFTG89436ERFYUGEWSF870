package vesence.module.impl.combat.rotation;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import vesence.module.impl.combat.AttackAura;
import vesence.module.impl.combat.auraComponent.Attack;
import vesence.module.impl.combat.auraComponent.GCDUtil;

import java.util.concurrent.ThreadLocalRandom;

import static vesence.utils.other.IMinecraft.mc;

/**
 * Spooky — порт SpookyTime-ротации из DELUXE 38.1.5.
 *
 * <p>Диспатч как в оригинале (sg.ec.Class62#m979): если sidebar-скорборд
 * матчится на «70[24]» (Class62#m1014) — вариант sg.ec.Class212#m2388
 * («анархия»), иначе sg.ec.Class101#m1363 (основной).
 *
 * <p>Основной вариант: аим в точку на высоте-фракции хитбокса со случайными
 * боковыми смещениями, после удара углы замораживаются на 200–250 мс,
 * пружина с cross-coupling осями, «усталость прицела» при долгом наведении
 * и прогрев скорости за первые ~220 мс боя.
 *
 * <p>Адаптация под vesence: окно атаки читается из
 * {@link URotationMath#inAttackWindow()}, вывод идёт через
 * {@link URotationMath#submit} (там же GCD-снап), «удар скоро будет готов»
 * аппроксимируется кулдауном (Deluxe: Class83#m1205 с внутренним таймером
 * ауры, сюда не переносился).
 */
@Environment(EnvType.CLIENT)
public class UEnergySpookyRotation {

    // ── Состояние основного варианта (Class101) ──────────────────────────────

    private static float springYaw;
    private static float springPitch;
    private static float freezeYaw;
    private static float freezePitch;
    private static float pulseMarker;
    private static long freezeStartMs;
    private static long freezeDurationMs;
    private static long rerollStartMs;
    private static long rerollPeriodMs;
    private static float baseSpeed;
    private static float angleDivisor;
    private static float accelCapBase;
    private static float axisCoupling;
    private static float deadZone;
    private static long firstTickMs;
    private static long aimStartMs;
    private static float aimFatigue;
    private static float heightFrac;
    private static float aimOffsetX;
    private static float aimOffsetZ;

    // ── Вход ─────────────────────────────────────────────────────────────────

    public static void rotation(LivingEntity target, float attackDist, float rotateDist, Vec3 point) {
        if (mc.player == null || mc.level == null || target == null) {
            return;
        }
        boolean attack = URotationMath.inAttackWindow();
        if (isSpookyAnarchy()) {
            spookyAnarchy(target, attack, attackDist);
        } else {
            spookyDefault(target, attack, attackDist);
        }
    }

    /** Class62#m1014: в sidebar-объективе второй сегмент после «-» — 702/704. */
    private static boolean isSpookyAnarchy() {
        if (mc.level == null) {
            return false;
        }
        net.minecraft.world.scores.Objective objective = mc.level.getScoreboard()
                .getDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR);
        if (objective == null) {
            return false;
        }
        String[] parts = objective.getDisplayName().getString().split("-");
        return parts.length > 1 && parts[1].replaceAll("\\D", "").matches("70[24]");
    }

    /** Аура в состоянии крита/смэша (Deluxe: Class62#m1022). */
    private static boolean critState() {
        return !mc.player.onGround() && (AttackAura.extraSettings.get("Умные криты")
                || AttackAura.extraSettings.get("Смэш булавой")
                || mc.player.getMainHandItem().is(Items.MACE));
    }

    /** Кулдаун почти готов (Deluxe: Class83#m1205(-30) без внутреннего таймера). */
    private static boolean cooldownReadySoon() {
        return mc.player.getAttackStrengthScale(2.0F) >= 0.9F;
    }

    // ── Основной вариант (Class101#m1363) ────────────────────────────────────

    private static void spookyDefault(LivingEntity target, boolean attack, float range) {
        boolean crit = critState();
        long now = System.currentTimeMillis();
        if (firstTickMs == 0L) {
            firstTickMs = now;
        }
        if (heightFrac == 0.0F) {
            heightFrac = URotationMath.rand(0.24F, 0.72F);
        }

        if (now - rerollStartMs > rerollPeriodMs) {
            rerollStartMs = now;
            rerollPeriodMs = (long) URotationMath.rand(25.0F, 50.0F);
            baseSpeed = URotationMath.rand(142.0F, 200.0F);
            angleDivisor = URotationMath.rand(34.0F, 54.0F);
            accelCapBase = URotationMath.rand(7.0F, 14.0F);
            axisCoupling = URotationMath.rand(0.04F, 0.12F)
                    * (ThreadLocalRandom.current().nextBoolean() ? 1.0F : -1.0F);
            deadZone = URotationMath.rand(0.4F, 0.9F);
        }

        Vec3 camera = mc.player.getEyePosition(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        Vec3 center = target.getBoundingBox().getCenter();
        boolean throughNonFull = AttackAura.misc.get("Бить через блоки")
                && !Attack.clearLineOfSight(camera, center)
                && Attack.clearIgnoringNonFull(camera, center);

        double offsetX = !crit && !throughNonFull ? aimOffsetX : 0.0;
        double offsetZ = !crit && !throughNonFull ? aimOffsetZ : 0.0;
        Vec3 aim = aimPoint(target, crit ? 1.0 : heightFrac, offsetX, offsetZ)
                .subtract(camera)
                .normalize();
        float goalYaw = (float) Math.toDegrees(Math.atan2(-aim.x, aim.z));
        float goalPitch = (float) Mth.clamp(
                -Math.toDegrees(Math.atan2(aim.y, Math.hypot(aim.x, aim.z))), -90.0, 90.0);

        float curYaw = mc.player.getYRot();
        float curPitch = mc.player.getXRot();

        boolean pulse = false;
        if (attack && distanceToBox(target) < range) {
            pulseMarker = URotationMath.rand(1.9F, 2.3F);
            freezeStartMs = now;
            freezeDurationMs = (long) ThreadLocalRandom.current().nextDouble(200.0, 250.0);
            heightFrac = URotationMath.rand(0.54F, 0.72F);
            aimOffsetX = URotationMath.rand(-0.12F, 0.12F);
            aimOffsetZ = URotationMath.rand(-0.12F, 0.12F);
        }
        if (pulseMarker > URotationMath.rand(0.43F, 0.75F)) {
            pulse = true;
            pulseMarker = URotationMath.rand(0.13F, 0.25F);
        }
        if (pulse) {
            freezeYaw = goalYaw;
            freezePitch = goalPitch;
        } else if (now - freezeStartMs < freezeDurationMs) {
            goalYaw = freezeYaw;
            goalPitch = freezePitch;
        }

        if (rayHitsBox(mc.player.getEyePosition(), direction(curYaw, curPitch),
                target.getBoundingBox(), range + 1.0)) {
            if (aimStartMs == 0L) {
                aimStartMs = now;
            }
        } else {
            aimStartMs = 0L;
        }

        float fatigueTarget = Mth.clamp(
                ((float) (aimStartMs == 0L ? 0L : now - aimStartMs) - 250.0F) / 300.0F, 0.0F, 1.0F);
        if (fatigueTarget >= aimFatigue) {
            aimFatigue = fatigueTarget;
        } else {
            aimFatigue = aimFatigue + (fatigueTarget - aimFatigue) * 4.0F;
        }

        float yawDelta = Mth.wrapDegrees(goalYaw - curYaw);
        float pitchDelta = goalPitch - curPitch;
        float total = (float) Math.hypot(yawDelta, pitchDelta);
        float speed = baseSpeed
                * Mth.clamp(total / angleDivisor, 0.0F, 1.0F)
                * Mth.lerp(Mth.clamp((float) (now - firstTickMs) / 220.0F, 0.0F, 1.0F), 0.78F, 1.0F)
                * (1.0F - Mth.clamp(aimFatigue, 0.0F, 1.0F) * (crit ? 0.35F : 1.0F))
                * (pulse ? 1.25F : 1.0F)
                * (crit ? 3.2F : 1.0F)
                // 26.3: поле handSwingTicks недоступно, заменено на isSwinging()
                * ((mc.player.isSwinging() ? 1 : 0) > URotationMath.rand(0.2F, 0.4F) && !crit ? 0.5F : 1.0F);
        float accelCap = accelCapBase * (pulse ? 4.0F : 1.0F) * (crit ? 2.6F : 1.0F);

        springYaw += Mth.clamp(
                speed * (total < 0.001F ? 0.0F : yawDelta / total) * 1.25F
                        / (intersecting(target) ? 2.0F : 1.0F) - springYaw,
                -accelCap * (crit ? 0.6F : 1.2F), accelCap * (crit ? 0.6F : 1.2F));
        springPitch += Mth.clamp(
                speed * (total < 0.001F ? 0.0F : pitchDelta / total)
                        + springYaw * axisCoupling - springPitch,
                -accelCap * 0.68F, accelCap * 0.68F);
        springYaw = Mth.clamp(springYaw, -Math.abs(yawDelta), Math.abs(yawDelta));
        springPitch = Mth.clamp(springPitch,
                -Math.abs(pitchDelta) - Math.abs(springYaw * axisCoupling),
                Math.abs(pitchDelta) + Math.abs(springYaw * axisCoupling));
        if (total < deadZone) {
            springYaw = 0.0F;
            springPitch = 0.0F;
        }

        float yawSpeed = Math.min(Math.abs(springYaw),
                yawSpeedFor(goalYaw, crit ? 0.6F : 1.0F, crit ? 1.0F : 1.5F)) * (crit ? 3.4F : 1.0F);
        float pitchSpeed = Math.min(Math.abs(springPitch),
                pitchSpeedFor(goalPitch, crit ? 1.0F : 2.5F, crit ? 1.8F : 4.0F)) * (crit ? 3.4F : 1.0F);
        URotationMath.submit(yawSpeed, pitchSpeed, 25.0F, 25.0F, 0, 15, goalYaw, goalPitch);
    }

    /** Class101#m1365: точка на высоте-фракции хитбокса со смещением. */
    private static Vec3 aimPoint(LivingEntity target, double frac, double offsetX, double offsetZ) {
        return target.position().add(offsetX,
                Mth.clamp(target.getBbHeight() * frac, 0.1,
                        Math.max(0.1, target.getBbHeight() - 0.1)),
                offsetZ);
    }

    // ── Вариант «анархия 702/704» (Class212#m2388) ───────────────────────────

    private static void spookyAnarchy(LivingEntity target, boolean attack, float range) {
        float curYaw = mc.player.getYRot();
        float curPitch = mc.player.getXRot();
        Vec3 eye = mc.player.getEyePosition();
        AABB box = target.getBoundingBox();
        Vec3 prediction = new Vec3(target.getX() - target.xOld, 0.0, target.getZ() - target.zOld)
                .subtract(mc.player.getDeltaMovement().x, 0.0, mc.player.getDeltaMovement().z)
                .scale(1.5);
        Vec3 aim = new Vec3(box.getCenter().x, box.minY + box.getYsize() * 0.82, box.getCenter().z)
                .add(prediction)
                .subtract(eye);
        boolean veryClose = aim.x * aim.x + aim.z * aim.z < 0.03F;
        double horizDist = Math.max(0.05, Math.hypot(aim.x, aim.z));
        ThreadLocalRandom random = ThreadLocalRandom.current();
        float distFactor = (float) Mth.clamp(horizDist / 2.0, 0.7, 1.0);
        Vec3 pitchAim = new Vec3(box.getCenter().x, box.minY + box.getYsize() * 0.87, box.getCenter().z)
                .subtract(eye);
        float yawDelta = veryClose ? 0.0F
                : Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-aim.x, aim.z)) - curYaw);
        float pitchDelta = (float) (-Math.toDegrees(
                Math.atan2(pitchAim.y, Math.max(0.7, Math.hypot(pitchAim.x, pitchAim.z))))) - curPitch;
        float angularSize = (float) Math.toDegrees(Math.atan(box.getXsize() * 0.5 / horizDist));
        boolean windowYaw = attack || cooldownReadySoon();
        boolean windowPitch = attack || cooldownReadySoon();
        float cooldown = mc.player.getAttackStrengthScale(0.5F);
        float wobbleScale = windowYaw ? 0.0F : 0.55F + 0.45F * cooldown + 0.2F;
        double time = System.currentTimeMillis() / 60.0;
        float wobbleYaw = ((float) (Math.cos(time * 0.7) + Math.sin(time * 1.7) * 0.5)
                * (angularSize * 1.8F + 7.0F) + random.nextFloat(-4.0F, 4.0F)) * wobbleScale;
        float wobblePitch = ((float) (Math.sin(time * 0.55) + Math.cos(time * 1.3) * 0.4) * 6.0F
                + random.nextFloat(-2.0F, 2.0F)) * wobbleScale;
        float speedFactor = Mth.clamp(
                (windowYaw ? random.nextFloat(0.85F, 1.0F)
                        : random.nextFloat(0.55F, 0.85F) * (1.0F - cooldown * 0.35F))
                        * (1.0F + Math.min(Math.abs(yawDelta), 90.0F) / 300.0F)
                        * distFactor,
                0.1F, 1.0F);
        float maxStep = random.nextFloat(55.0F, 80.0F) * distFactor;
        float yawStep = Mth.clamp((yawDelta + wobbleYaw) * speedFactor, -maxStep, maxStep);
        if (attack && !box.contains(eye)
                && box.clip(eye, eye.add(direction(curYaw + yawStep, curPitch).scale(range + 1.0)))
                        .isEmpty()) {
            yawStep = yawDelta;
        }
        float pitchStep = windowPitch
                ? pitchDelta * random.nextFloat(0.8F, 0.95F)
                : wobblePitch * 0.35F + pitchDelta * (Math.abs(pitchDelta) > 18.0F ? 0.3F : 0.15F);
        if (!attack && cooldownReadySoon()) {
            yawStep *= 0.35F;
            pitchStep *= 0.35F;
        }

        float newYaw = curYaw + yawStep;
        float newPitch = Mth.clamp(curPitch + pitchStep, -89.0F, 89.0F);
        URotationMath.submit(veryClose ? 8.0F : 35.0F, 22.0F, 30.0F, 30.0F, 0, 15, newYaw, newPitch);
    }

    // ── Общие хелперы (Deluxe: Class359) ─────────────────────────────────────

    /** Class359#m3920: вектор взгляда по yaw/pitch. */
    private static Vec3 direction(float yaw, float pitch) {
        return Attack.directionVec(yaw, pitch);
    }

    /** Class359#m3926: дистанция от глаз до хитбокса. */
    private static double distanceToBox(Entity entity) {
        Vec3 eye = mc.player.getEyePosition();
        AABB box = entity.getBoundingBox();
        double dx = Math.max(Math.max(box.minX - eye.x, eye.x - box.maxX), 0.0);
        double dy = Math.max(Math.max(box.minY - eye.y, eye.y - box.maxY), 0.0);
        double dz = Math.max(Math.max(box.minZ - eye.z, eye.z - box.maxZ), 0.0);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Class359#m3909: slab-тест пересечения луча с боксом. */
    private static boolean rayHitsBox(Vec3 start, Vec3 dir, AABB box, double maxDist) {
        double enter = 0.0;
        double exit = maxDist;
        double sx = start.x;
        double sy = start.y;
        double sz = start.z;
        for (int axis = 0; axis < 3; axis++) {
            double d = axis == 0 ? dir.x : (axis == 1 ? dir.y : dir.z);
            double s = axis == 0 ? sx : (axis == 1 ? sy : sz);
            double min = axis == 0 ? box.minX : (axis == 1 ? box.minY : box.minZ);
            double max = axis == 0 ? box.maxX : (axis == 1 ? box.maxY : box.maxZ);
            if (Math.abs(d) < 1.0E-9) {
                if (s < min || s > max) {
                    return false;
                }
                continue;
            }
            double inv = 1.0 / d;
            double t1 = (min - s) * inv;
            double t2 = (max - s) * inv;
            if (t1 > t2) {
                double tmp = t1;
                t1 = t2;
                t2 = tmp;
            }
            enter = Math.max(enter, t1);
            exit = Math.min(exit, t2);
            if (enter > exit) {
                return false;
            }
        }
        return exit >= 0.0 && enter <= maxDist;
    }

    /** Class359#m3908/#m3901: хитбокс игрока пересекается с хитбоксом цели. */
    private static boolean intersecting(LivingEntity target) {
        return mc.player.getBoundingBox().intersects(target.getBoundingBox());
    }

    /** Class359#m3907: скорость yaw пропорциональна оставшемуся углу. */
    private static float yawSpeedFor(float goalYaw, float minDiv, float maxDiv) {
        return Math.max(GCDUtil.getGCDValue(), Math.abs(Mth.wrapDegrees(goalYaw - mc.player.getYRot()))
                / Math.max(1.0F, URotationMath.rand(minDiv, maxDiv)));
    }

    /** Class359#m3911: то же для pitch. */
    private static float pitchSpeedFor(float goalPitch, float minDiv, float maxDiv) {
        return Math.max(GCDUtil.getGCDValue(), Math.abs(goalPitch - mc.player.getXRot())
                / Math.max(1.0F, URotationMath.rand(minDiv, maxDiv)));
    }

    // ── Сброс ────────────────────────────────────────────────────────────────

    public static void state() {
        pulseMarker = 0.0F;
        freezeYaw = 0.0F;
        freezePitch = 0.0F;
        freezeStartMs = 0L;
        freezeDurationMs = 0L;
        springYaw = 0.0F;
        springPitch = 0.0F;
        rerollStartMs = 0L;
        rerollPeriodMs = 0L;
        firstTickMs = 0L;
        aimStartMs = 0L;
        aimFatigue = 0.0F;
        heightFrac = 0.0F;
        aimOffsetX = 0.0F;
        aimOffsetZ = 0.0F;
    }
}
