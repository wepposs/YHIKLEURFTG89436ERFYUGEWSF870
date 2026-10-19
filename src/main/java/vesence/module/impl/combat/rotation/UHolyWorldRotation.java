package vesence.module.impl.combat.rotation;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import vesence.module.impl.combat.auraComponent.Attack;
import vesence.module.impl.combat.auraComponent.RayTraceUtil;
import vesence.module.impl.combat.auraComponent.UBoxPoints;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.Rotation;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.URotations;
import vesence.utils.other.Mathf;
import vesence.utils.player.MoveUtil;

import java.security.SecureRandom;
import java.util.concurrent.ThreadLocalRandom;

import static vesence.utils.other.IMinecraft.mc;

@Environment(EnvType.CLIENT)
public class UHolyWorldRotation {
    private static final float MAX_YAW = 360.0f;
    private static final float MAX_PITCH = 90.0f;
    private static final float PHYSIOLOGICAL_TREMOR_FREQ = 10.0f;
    private static final ThreadLocalRandom fastRand = ThreadLocalRandom.current();
    private static final SecureRandom cryptoRand = new SecureRandom();
    private enum EmotionalState { CALM, ALERT, PANIC, FATIGUED, BORED }
    private static EmotionalState currentEmotion = EmotionalState.CALM;
    private static long emotionShiftTime = 0;
    private static long nextEmotionCheck = 0;
    private static float emotionBlend = 0.0f;

    private static class MotorProfile {
        float aggro;
        float smooth;
        float tremor;
        float overshoot;
        float predict;
        float wobble;
        float micro;
        float inertia;
        float reaction;
        float confidence;
        float curiosity;
    }
    private static MotorProfile currentProfile = new MotorProfile();
    private static MotorProfile targetProfile = new MotorProfile();
    private static long profileShiftTime = 0;
    private static long nextProfileShift = 0;
    private static long reactionEnd = 0;
    private static long accelStart = 0;
    private static int lastTargetId = -1;
    private static Vec2f lastTargetRot = Vec2f.ZERO;
    private static long rayTraceLostTime = -1;
    private static boolean inOvershoot = false;
    private static long overshootEnd = 0;
    private static Vec2f overshootDelta = Vec2f.ZERO;
    private static Vec2f overshootVel = Vec2f.ZERO;
    private static long nextOvershootChance = 0;
    private static boolean inPreHit = false;
    private static long preHitEnd = 0;
    private static float preHitStr = 1.0f;
    private static boolean inFlickBurst = false;
    private static long flickBurstEnd = 0;
    private static float flickBurstStr = 0;
    private static boolean inMicro = false;
    private static long microEnd = 0;
    private static Vec2f microDelta = Vec2f.ZERO;
    private static int microCount = 0;
    private static long nextMicro = 0;
    private static boolean inHesitation = false;
    private static long hesEnd = 0;
    private static Vec2f hesDelta = Vec2f.ZERO;
    private static boolean inWobble = false;
    private static long wobbleEnd = 0;
    private static Vec2f wobbleDelta = Vec2f.ZERO;
    private static Vec2f wobbleVel = Vec2f.ZERO;
    private static boolean inMouseLift = false;
    private static long mouseLiftEnd = 0;
    private static Vec2f mouseLiftDelta = Vec2f.ZERO;
    private static boolean inCorrection = false;
    private static long correctionEnd = 0;
    private static Vec2f correctionDelta = Vec2f.ZERO;
    private static boolean inDoubleCheck = false;
    private static long doubleCheckEnd = 0;
    private static Vec2f doubleCheckDelta = Vec2f.ZERO;
    private static Vec2f mouseVel = Vec2f.ZERO;
    private static Vec2f mouseAccel = Vec2f.ZERO;
    private static float mouseMass = 1.0f;
    private static float mouseSpring = 0.0f;
    private static float mouseDamping = 0.0f;
    private static float stictionThreshold = 0.0f;
    private static float tremorPhase = 0.0f;
    private static Vec2f tremorOffset = Vec2f.ZERO;
    private static Vec2f tremorVelocity = Vec2f.ZERO;
    private static Vec3d perceivedTargetPos = Vec3d.ZERO;
    private static Vec2f perceivedTargetRot = Vec2f.ZERO;
    private static long cognitiveLag = 0;
    private static float fatigue = 0.0f;
    private static long lastFatigueTick = 0;
    private static float stress = 0.0f;
    private static long lastStressUpdate = 0;
    private static float arousal = 0.5f;
    private static Vec2f noisePos = Vec2f.ZERO;
    private static Vec2f noiseVel = Vec2f.ZERO;
    private static float noiseAccum = 0.0f;
    private static Vec2f brownianNoise = Vec2f.ZERO;
    private static final float[] yawHist = new float[8];
    private static final float[] pitchHist = new float[8];
    private static int histIdx = 0;
    private static float lastYawSpd = 0;
    private static float lastPitchSpd = 0;
    private static float deadZoneYaw = 0.0f;
    private static float deadZonePitch = 0.0f;
    private static float deadZonePhase = 0.0f;
    private static long nextAttentionBlink = 0;
    private static boolean inAttentionBlink = false;

    private static final int[] PERM = new int[512];
    static {
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) p[i] = i;
        for (int i = 255; i > 0; i--) {
            int j = cryptoRand.nextInt(i + 1);
            int tmp = p[i]; p[i] = p[j]; p[j] = tmp;
        }
        for (int i = 0; i < 512; i++) PERM[i] = p[i & 255];
    }

    static {
        regenProfile();
        regenEmotion();
        updateMousePhysicsParams();
    }

    public static void rotation(LivingEntity target, float attackDist, float rotateDist) {
        if (target == null || mc.player == null) return;

        long now = System.currentTimeMillis();
        long tick = mc.player.age;

        if (now > nextEmotionCheck) {
            nextEmotionCheck = now + 8000 + cryptoRand.nextInt(12000);
            regenEmotion();
        }
        blendProfile(now);
        updateEmotion(now);

        updateCognitivePerception(target, now);

        updateFatigue(tick);
        updateStress(now, target);
        updateArousal();
        updateAttentionBlink(now);

        updateOvershoot(now);
        updatePreHit(now);
        updateMicro(now);
        updateHesitation(now);
        updateWobble(now);
        updateMouseLift(now);
        updateFlickBurst(now);
        updateCorrections(now);
        updateDoubleCheck(now);

        updatePhysiologicalTremor(now);

        updateBrownianNoise(now);

        updateDeadZone(now);

        if (target.getId() != lastTargetId) {
            onTargetChange(target, now);
        }

        if (now < reactionEnd) {
            doReactionPhase(target, attackDist, rotateDist, now);
            return;
        }

        Vec3d point = UBoxPoints.getOrganicPoint(target);
        Vec2f base = UBoxPoints.getVanillaRotate(point);

        Vec3d vel = UBoxPoints.getWanderWorldVel();
        double eSpeed = vel.length();
        float predErr = (1.0f - currentProfile.predict) * 0.5f + stress * 0.1f;
        float predMult = 0.8f + (float)(gaussian(fastRand) * predErr * 0.15);
        predMult = MathHelper.clamp(predMult, 0.3f, 1.4f);

        base = new Vec2f(
                base.x + (float)(vel.x * predMult * (0.85 + gaussian(fastRand) * 0.1)),
                base.y + (float)(vel.y * predMult * (0.65 + gaussian(fastRand) * 0.08))
        );

        Vec2f breath = getBreath(now, eSpeed);
        Vec2f strafe = getStrafe(target);
        Vec2f hurt = getHurt();
        Vec2f panic = getPanic(target);
        Vec2f tremor = getTremor();
        Vec2f brownian = getBrownian();

        Vec2f raw = new Vec2f(
                base.x
                        + (inOvershoot ? overshootDelta.x : overshootVel.x)
                        + tremor.x
                        + brownian.x
                        + noisePos.x
                        + breath.x
                        + strafe.x
                        + hurt.x
                        + panic.x
                        + (inMicro ? microDelta.x : 0)
                        + (inHesitation ? hesDelta.x : 0)
                        + (inWobble ? wobbleDelta.x : 0)
                        + (inMouseLift ? mouseLiftDelta.x : 0)
                        + (inCorrection ? correctionDelta.x : 0)
                        + (inDoubleCheck ? doubleCheckDelta.x : 0),
                base.y
                        + (inOvershoot ? overshootDelta.y : overshootVel.y)
                        + tremor.y
                        + brownian.y
                        + noisePos.y
                        + breath.y
                        + strafe.y
                        + hurt.y
                        + panic.y
                        + (inMicro ? microDelta.y : 0)
                        + (inHesitation ? hesDelta.y : 0)
                        + (inWobble ? wobbleDelta.y : 0)
                        + (inMouseLift ? mouseLiftDelta.y : 0)
                        + (inCorrection ? correctionDelta.y : 0)
                        + (inDoubleCheck ? doubleCheckDelta.y : 0)
        );
        raw = new Vec2f(raw.x, MathHelper.clamp(raw.y, -90.0f, 90.0f));

        // ТЗ: в окне атаки ротация чистая. Здесь jitter набирается из десятка
        // независимых источников (тремор, броуновский шум, дыхание, паника,
        // wobble...) — в окне атаки все они снимаются, остаётся голый аим-поинт,
        // иначе удар уходит с шумом ровно в тик удара.
        if (URotationMath.inAttackWindow()) {
            raw = new Vec2f(base.x, MathHelper.clamp(base.y, -90.0f, 90.0f));
        }

        float yawD = MathHelper.wrapDegrees(raw.x - mc.player.getYaw());
        float pitchD = MathHelper.wrapDegrees(raw.y - mc.player.getPitch());
        float angDist = (float) Math.sqrt(yawD * yawD + pitchD * pitchD);

        float fDist = attackDist + rotateDist + 0.1f;
        boolean ray = RayTraceUtil.rayTraceSingleEntity(
                mc.player.getYaw(), mc.player.getPitch(), attackDist + fDist, target
        );

        float speed = calcNeuroSpeed(yawD, pitchD, angDist, ray, now, eSpeed, target, attackDist, fDist);

        float yawSpd = speed * (0.9f + currentProfile.aggro * 0.4f + stress * 0.15f);
        float pitchSpd = speed * (0.7f + currentProfile.smooth * 0.25f + fatigue * 0.1f);

        yawSpd *= (1.0f - fatigue * 0.25f);
        pitchSpd *= (1.0f - fatigue * 0.3f);

        if (inPreHit) {
            yawSpd *= preHitStr * (0.9f + fastRand.nextFloat() * 0.2f);
            pitchSpd *= preHitStr * 0.85f * (0.9f + fastRand.nextFloat() * 0.2f);
        }
        if (inFlickBurst) {
            yawSpd *= (1.4f + flickBurstStr * 0.9f);
            pitchSpd *= (1.2f + flickBurstStr * 0.6f);
        }
        if (inWobble) {
            yawSpd *= 0.7f + fastRand.nextFloat() * 0.1f;
            pitchSpd *= 0.75f + fastRand.nextFloat() * 0.1f;
        }
        if (inMouseLift) {
            yawSpd *= 0.15f + fastRand.nextFloat() * 0.1f;
            pitchSpd *= 0.15f + fastRand.nextFloat() * 0.1f;
        }
        if (inCorrection) {
            yawSpd *= 1.15f + fastRand.nextFloat() * 0.1f;
            pitchSpd *= 1.1f + fastRand.nextFloat() * 0.1f;
        }
        if (inDoubleCheck) {
            yawSpd *= 1.3f;
            pitchSpd *= 1.2f;
        }
        if (inAttentionBlink) {
            yawSpd *= 0.4f;
            pitchSpd *= 0.4f;
        }

        if (Math.abs(yawD) < deadZoneYaw && Math.abs(pitchD) < deadZonePitch && ray) {
            float dzFactor = 0.3f + fastRand.nextFloat() * 0.3f;
            yawSpd *= dzFactor;
            pitchSpd *= dzFactor;
        }

        yawSpd = MathHelper.clamp(yawSpd, 0.05f, MAX_YAW);
        pitchSpd = MathHelper.clamp(pitchSpd, 0.05f, MAX_PITCH);

        yawSpd = neuroMotorCurve(yawSpd, yawD);
        pitchSpd = neuroMotorCurve(pitchSpd, pitchD);

        yawSpd = adaptiveHistorySmooth(yawSpd, yawHist, 0.15f + currentProfile.smooth * 0.15f);
        pitchSpd = adaptiveHistorySmooth(pitchSpd, pitchHist, 0.18f + currentProfile.smooth * 0.12f);

        Vec2f physics = applyMouseBiophysics(yawD, pitchD, yawSpd, pitchSpd);
        yawSpd = physics.x;
        pitchSpd = physics.y;

        lastYawSpd = yawSpd;
        lastPitchSpd = pitchSpd;

        float yawMove = Math.min(Math.abs(yawD), yawSpd);
        float pitchMove = Math.min(Math.abs(pitchD), pitchSpd);

        float yDir = Math.signum(yawD);
        float pDir = Math.signum(pitchD);

        if (fastRand.nextFloat() < 0.08f + fatigue * 0.06f + stress * 0.04f) {
            float err = 0.015f + fatigue * 0.04f + stress * 0.02f;
            yDir *= (1.0f + (fastRand.nextFloat() - 0.5f) * err * 2.0f);
            pDir *= (1.0f + (fastRand.nextFloat() - 0.5f) * err * 2.0f);
        }

        if (fastRand.nextFloat() < 0.003f && !inHesitation && angDist < 5.0f) {
            yawMove *= 0.2f;
            pitchMove *= 0.2f;
        }

        float fYaw = mc.player.getYaw() + yawMove * yDir;
        float fPitch = MathHelper.clamp(mc.player.getPitch() + pitchMove * pDir, -90.0f, 90.0f);

        Vec2f snap = humanGCDv2(fYaw, fPitch, yawSpd, pitchSpd, angDist);
        fYaw = snap.x;
        fPitch = MathHelper.clamp(snap.y, -90.0f, 90.0f);

        Rotation rot = new Rotation(fYaw, fPitch);
        float yRet = 18.0f + fastRand.nextFloat() * 14.0f;
        float pRet = yRet * (0.7f + fastRand.nextFloat() * 0.3f);

        if (mc.player.isGliding()) {
            URotations.update(rot, 360.0f, 360.0f, 0, 1);
        } else {
            URotations.update(rot, yawSpd, pitchSpd, yRet, pRet, 0, 15, true);
        }

        yawHist[histIdx] = yawSpd;
        pitchHist[histIdx] = pitchSpd;
        histIdx = (histIdx + 1) % yawHist.length;

        lastTargetRot = raw;
    }

    private static float calcNeuroSpeed(float yawD, float pitchD, float angDist,
                                        boolean ray, long now, double eSpeed,
                                        LivingEntity target, float aDist, float fDist) {
        float base;

        if (ray) {
            if (mc.player.distanceTo(target) > 0.5f) {
                boolean small = RayTraceUtil.rayTraceSmallHitBox(
                        mc.player.getYaw(), mc.player.getPitch(), fDist, target
                );

                if (!small) {
                    float prox = MathHelper.clamp(1.0f - (angDist / 35.0f), 0.0f, 1.0f);
                    float ease = prox * prox * (3.0f - 2.0f * prox);
                    ease = ease * 0.7f + (float)Math.sin(ease * Math.PI) * 0.3f; // Добавляем "пружинность"
                    base = Mathf.randomValue(18.0f, 30.0f) + ease * Mathf.randomValue(8.0f, 22.0f);
                    base *= (1.0f + currentProfile.aggro * 0.25f + stress * 0.1f);
                } else if (swingCd() < 0.25f && MoveUtil.isMoving()) {
                    base = Mathf.randomValue(0.4f, 3.2f);
                } else {
                    base = Mathf.randomValue(6.0f, 14.0f);
                }
            } else {
                base = Mathf.randomValue(12.0f, 20.0f);
            }
        } else {
            if (rayTraceLostTime < 0) rayTraceLostTime = now;
            long since = now - rayTraceLostTime;
            float accel = 160.0f + currentProfile.smooth * 120.0f;

            if (since < accel) {
                float t = (float) since / accel;
                t = t * t * (3.0f - 2.0f * t);
                t = t * 1.1f - 0.05f;
                t = MathHelper.clamp(t, 0.0f, 1.0f);
                base = Mathf.randomValue(35.0f, 50.0f) * t;
            } else {
                base = lastYawSpd + (float)gaussian(fastRand) * 4.0f;
                base = MathHelper.clamp(base, 24.0f, 55.0f);
            }
        }

        long onTarget = now - accelStart;
        float momT = MathHelper.clamp(onTarget / 450.0f, 0.0f, 1.0f);
        float momentum = 1.0f + (float)(Math.log1p(momT * 2.0) / Math.log(3.0)) * 0.6f;

        float distCurve;
        if (angDist > 25.0f) {
            distCurve = 1.0f + (1.0f - (float)Math.exp(-(angDist - 25.0f) / 30.0f)) * 0.5f;
        } else if (angDist > 8.0f) {
            float t = 1.0f - (angDist - 8.0f) / 17.0f;
            distCurve = 0.5f + 0.5f * (t * t * (3.0f - 2.0f * t));
        } else {
            float t = 1.0f - angDist / 8.0f;
            distCurve = 0.35f + 0.3f * t * t;
        }

        base *= momentum * distCurve;

        if (eSpeed > 0.008) {
            float boost = MathHelper.clamp((float)(eSpeed * 2.5), 0.0f, 0.25f);
            base *= (1.0f + boost * (1.0f + currentProfile.aggro * 0.5f));
        }
        if (eSpeed > 0.3) {
            base *= 0.88f + (1.0f - currentProfile.predict) * 0.1f;
        }

        switch (currentEmotion) {
            case PANIC: base *= 1.15f + fastRand.nextFloat() * 0.2f; break;
            case FATIGUED: base *= 0.75f + fastRand.nextFloat() * 0.15f; break;
            case BORED: base *= 0.85f; break;
            case ALERT: base *= 1.05f; break;
            default: break;
        }

        base *= (1.0f + stress * 0.15f + arousal * 0.1f);

        if (inAttentionBlink) base *= 0.3f;

        return MathHelper.clamp(base, 0.05f, MAX_YAW);
    }

    private static Vec2f applyMouseBiophysics(float yawD, float pitchD, float yawSpd, float pitchSpd) {
        float targetYawVel = Math.signum(yawD) * yawSpd;
        float targetPitchVel = Math.signum(pitchD) * pitchSpd;

        float yawStiction = Math.abs(targetYawVel) < stictionThreshold ? 0.3f : 1.0f;
        float pitchStiction = Math.abs(targetPitchVel) < stictionThreshold ? 0.3f : 1.0f;

        float inertia = 0.15f + currentProfile.inertia * 0.35f;
        mouseVel = new Vec2f(
                mouseVel.x + (targetYawVel * yawStiction - mouseVel.x) * inertia,
                mouseVel.y + (targetPitchVel * pitchStiction - mouseVel.y) * inertia
        );

        float damping = 0.88f + currentProfile.smooth * 0.08f;
        mouseVel = new Vec2f(mouseVel.x * damping, mouseVel.y * damping);

        mouseAccel = new Vec2f(
                mouseAccel.x + (mouseVel.x - mouseAccel.x) * 0.1f,
                mouseAccel.y + (mouseVel.y - mouseAccel.y) * 0.1f
        );

        float finalYaw = Math.abs(mouseVel.x + mouseAccel.x * 0.4f);
        float finalPitch = Math.abs(mouseVel.y + mouseAccel.y * 0.4f);

        return new Vec2f(finalYaw, finalPitch);
    }

    private static void updateMousePhysicsParams() {
        mouseMass = 0.8f + fastRand.nextFloat() * 0.4f;
        mouseSpring = 0.5f + fastRand.nextFloat() * 0.3f;
        mouseDamping = 0.82f + fastRand.nextFloat() * 0.1f;
        stictionThreshold = 0.5f + fastRand.nextFloat() * 1.5f;
    }

    private static void updatePhysiologicalTremor(long now) {
        float dt = 0.05f;
        tremorPhase += dt * PHYSIOLOGICAL_TREMOR_FREQ * (0.8f + fastRand.nextFloat() * 0.4f);

        float baseAmp = 0.008f;
        float stressAmp = stress * 0.04f;
        float fatigueAmp = fatigue * 0.02f;
        float panicAmp = currentEmotion == EmotionalState.PANIC ? 0.03f : 0.0f;
        float amp = baseAmp + stressAmp + fatigueAmp + panicAmp;

        float t = tremorPhase;
        float tx = (float)(Math.sin(t * 1.0) * 0.5 + Math.sin(t * 2.3) * 0.3 + Math.sin(t * 4.7) * 0.2);
        float ty = (float)(Math.cos(t * 1.1) * 0.5 + Math.cos(t * 2.7) * 0.3 + Math.cos(t * 5.1) * 0.2);

        tremorVelocity = new Vec2f(
                (tremorVelocity.x + (tx * amp - tremorOffset.x) * 0.15f) * 0.7f,
                (tremorVelocity.y + (ty * amp - tremorOffset.y) * 0.15f) * 0.7f
        );
        tremorOffset = new Vec2f(
                tremorOffset.x + tremorVelocity.x,
                tremorOffset.y + tremorVelocity.y
        );
    }

    private static Vec2f getTremor() {
        return tremorOffset;
    }

    private static void updateBrownianNoise(long now) {
        float brScale = 0.002f + fatigue * 0.003f;
        brownianNoise = new Vec2f(
                brownianNoise.x + (float)gaussian(fastRand) * brScale,
                brownianNoise.y + (float)gaussian(fastRand) * brScale * 0.7f
        );
        brownianNoise = new Vec2f(brownianNoise.x * 0.98f, brownianNoise.y * 0.98f);
    }

    private static Vec2f getBrownian() {
        return brownianNoise;
    }

    private static void updateCognitivePerception(LivingEntity target, long now) {
        long targetLag = (long)(150 + currentProfile.reaction * 100 + fastRand.nextInt(80));
        cognitiveLag = targetLag;

        Vec3d currentPos = target.getEntityPos();
        if (perceivedTargetPos.equals(Vec3d.ZERO)) {
            perceivedTargetPos = currentPos;
        } else {
            float lerp = 0.3f + currentProfile.predict * 0.4f;
            perceivedTargetPos = new Vec3d(
                    lerp(perceivedTargetPos.x, currentPos.x, lerp),
                    lerp(perceivedTargetPos.y, currentPos.y, lerp),
                    lerp(perceivedTargetPos.z, currentPos.z, lerp)
            );
        }
    }

    private static void updateDeadZone(long now) {
        deadZonePhase += 0.03f;
        float baseYaw = 0.15f + currentProfile.confidence * 0.4f;
        float basePitch = 0.1f + currentProfile.confidence * 0.3f;
        deadZoneYaw = baseYaw + (float)Math.sin(deadZonePhase) * 0.08f + (float)Math.sin(deadZonePhase * 1.7) * 0.04f;
        deadZonePitch = basePitch + (float)Math.cos(deadZonePhase * 1.3) * 0.06f + (float)Math.cos(deadZonePhase * 2.1) * 0.03f;
    }

    private static void updateAttentionBlink(long now) {
        if (inAttentionBlink) {
            if (now > nextAttentionBlink) {
                inAttentionBlink = false;
                nextAttentionBlink = now + 2000 + fastRand.nextInt(6000);
            }
            return;
        }
        if (now > nextAttentionBlink && fastRand.nextFloat() < 0.02f + fatigue * 0.03f) {
            inAttentionBlink = true;
            nextAttentionBlink = now + 40 + fastRand.nextInt(120);
        }
    }

    private static void regenEmotion() {
        float r = cryptoRand.nextFloat();
        EmotionalState prev = currentEmotion;
        if (r < 0.4f) currentEmotion = EmotionalState.CALM;
        else if (r < 0.65f) currentEmotion = EmotionalState.ALERT;
        else if (r < 0.8f) currentEmotion = EmotionalState.BORED;
        else if (r < 0.92f) currentEmotion = EmotionalState.FATIGUED;
        else currentEmotion = EmotionalState.PANIC;

        if (prev != currentEmotion) {
            emotionBlend = 0.0f;
            emotionShiftTime = System.currentTimeMillis();
        }
    }

    private static void updateEmotion(long now) {
        if (emotionBlend < 1.0f) {
            long dt = now - emotionShiftTime;
            emotionBlend = MathHelper.clamp(dt / 2000.0f, 0.0f, 1.0f);
            emotionBlend = emotionBlend * emotionBlend * (3.0f - 2.0f * emotionBlend); // smoothstep
        }
    }

    private static void regenProfile() {
        targetProfile.aggro = 0.3f + cryptoRand.nextFloat() * 0.5f;
        targetProfile.smooth = 0.2f + cryptoRand.nextFloat() * 0.5f;
        targetProfile.tremor = 0.1f + cryptoRand.nextFloat() * 0.4f;
        targetProfile.overshoot = 0.15f + cryptoRand.nextFloat() * 0.4f;
        targetProfile.predict = 0.35f + cryptoRand.nextFloat() * 0.45f;
        targetProfile.wobble = 0.1f + cryptoRand.nextFloat() * 0.4f;
        targetProfile.micro = 0.2f + cryptoRand.nextFloat() * 0.5f;
        targetProfile.inertia = 0.3f + cryptoRand.nextFloat() * 0.5f;
        targetProfile.reaction = 0.6f + cryptoRand.nextFloat() * 0.9f;
        targetProfile.confidence = 0.4f + cryptoRand.nextFloat() * 0.5f;
        targetProfile.curiosity = 0.2f + cryptoRand.nextFloat() * 0.6f;

        nextProfileShift = System.currentTimeMillis() + 15000 + cryptoRand.nextInt(45000);
        profileShiftTime = System.currentTimeMillis();
        emotionBlend = 0.0f;
    }

    private static void blendProfile(long now) {
        if (now > nextProfileShift) {
            currentProfile = targetProfile;
            regenProfile();
        }

        float t = MathHelper.clamp((now - profileShiftTime) / 3000.0f, 0.0f, 1.0f);
        t = t * t * (3.0f - 2.0f * t);

        currentProfile.aggro = lerp(currentProfile.aggro, targetProfile.aggro, t);
        currentProfile.smooth = lerp(currentProfile.smooth, targetProfile.smooth, t);
        currentProfile.tremor = lerp(currentProfile.tremor, targetProfile.tremor, t);
        currentProfile.overshoot = lerp(currentProfile.overshoot, targetProfile.overshoot, t);
        currentProfile.predict = lerp(currentProfile.predict, targetProfile.predict, t);
        currentProfile.wobble = lerp(currentProfile.wobble, targetProfile.wobble, t);
        currentProfile.micro = lerp(currentProfile.micro, targetProfile.micro, t);
        currentProfile.inertia = lerp(currentProfile.inertia, targetProfile.inertia, t);
        currentProfile.reaction = lerp(currentProfile.reaction, targetProfile.reaction, t);
        currentProfile.confidence = lerp(currentProfile.confidence, targetProfile.confidence, t);
        currentProfile.curiosity = lerp(currentProfile.curiosity, targetProfile.curiosity, t);
    }

    private static void updateOvershoot(long now) {
        if (inOvershoot) {
            if (now > overshootEnd) {
                inOvershoot = false;
                nextOvershootChance = now + 500 + fastRand.nextInt(2000);
                overshootVel = new Vec2f(overshootDelta.x * 0.15f, overshootDelta.y * 0.15f);
            }
        } else {
            overshootVel = overshootVel.multiply(0.8f + currentProfile.smooth * 0.12f);
            if (overshootVel.length() < 0.01f) overshootVel = Vec2f.ZERO;

            if (now > nextOvershootChance && fastRand.nextFloat() < (0.06f + currentProfile.overshoot * 0.1f)) {
                inOvershoot = true;
                overshootEnd = now + 40 + fastRand.nextInt(120);
                float dx = fastRand.nextBoolean() ? 1 : -1;
                float dy = fastRand.nextBoolean() ? 1 : -1;
                float amp = 0.8f + currentProfile.overshoot * 2.5f;
                float distFactor = MathHelper.clamp(lastTargetRot.length() / 20.0f, 0.5f, 2.0f);
                overshootDelta = new Vec2f(
                        dx * (0.5f + fastRand.nextFloat() * amp) * distFactor,
                        dy * (0.4f + fastRand.nextFloat() * amp * 0.7f) * distFactor
                );
            }
        }
    }

    private static void updatePreHit(long now) {
        if (inPreHit) {
            if (now > preHitEnd) inPreHit = false;
            return;
        }
        long total = Attack.getMsCooldown();
        long elapsed = (long) Attack.msCooldownReach();
        long rem = total - elapsed;
        if (rem > 15 && rem < 130 && Attack.msCooldownPC01() > 0.4f) {
            if (fastRand.nextFloat() < (0.2f + currentProfile.aggro * 0.25f + stress * 0.1f)) {
                inPreHit = true;
                preHitEnd = now + 15 + fastRand.nextInt(50);
                preHitStr = 1.2f + fastRand.nextFloat() * 0.8f * currentProfile.aggro;
            }
        }
    }

    private static void updateMicro(long now) {
        if (inMicro) {
            if (now > microEnd) {
                inMicro = false;
                if (microCount < 2 && fastRand.nextFloat() < 0.25f) startMicro(now);
            }
            return;
        }
        if (now > nextMicro && fastRand.nextFloat() < 0.025f + currentProfile.micro * 0.012f && microCount < 3) {
            startMicro(now);
        }
    }

    private static void startMicro(long now) {
        inMicro = true;
        microEnd = now + 10 + fastRand.nextInt(30);
        float dx = fastRand.nextBoolean() ? 1 : -1;
        float dy = fastRand.nextBoolean() ? 1 : -1;
        float amp = 0.1f + currentProfile.tremor * 0.4f;
        microDelta = new Vec2f(
                dx * (0.08f + fastRand.nextFloat() * amp * 1.2f),
                dy * (0.06f + fastRand.nextFloat() * amp * 0.6f)
        );
        nextMicro = now + 300 + fastRand.nextInt(900);
        microCount++;
    }

    private static void updateHesitation(long now) {
        if (inHesitation) {
            if (now > hesEnd) {
                inHesitation = false;
            } else {
                float t = now / 50.0f;
                float i = 0.12f + currentProfile.tremor * 0.35f;
                hesDelta = new Vec2f(
                        (float)(Math.sin(t * 2.7) * Math.cos(t * 1.9) * Math.sin(t * 0.7) * i),
                        (float)(Math.cos(t * 3.5) * Math.sin(t * 2.1) * Math.cos(t * 1.3) * i * 0.5f)
                );
            }
            return;
        }
        long total = Attack.getMsCooldown();
        long elapsed = (long) Attack.msCooldownReach();
        long rem = total - elapsed;
        if (rem > 20 && rem < 90 && Attack.msCooldownPC01() > 0.48f) {
            if (fastRand.nextFloat() < (0.08f + currentProfile.tremor * 0.1f)) {
                inHesitation = true;
                hesEnd = now + 10 + fastRand.nextInt(25);
            }
        }
    }

    private static void updateWobble(long now) {
        if (inWobble) {
            if (now > wobbleEnd) {
                inWobble = false;
                wobbleDelta = Vec2f.ZERO;
                wobbleVel = Vec2f.ZERO;
                return;
            }
            float t = now / 70.0f;
            float amp = 0.25f + currentProfile.wobble * 0.6f;
            float tx = (float)(Math.sin(t * 2.3) * Math.cos(t * 1.1) * amp + Math.sin(t * 5.7) * amp * 0.3);
            float ty = (float)(Math.cos(t * 2.9) * Math.sin(t * 1.5) * amp * 0.5f + Math.cos(t * 6.1) * amp * 0.2);

            wobbleVel = new Vec2f(
                    (wobbleVel.x + (tx - wobbleDelta.x) * 0.05f) * 0.7f,
                    (wobbleVel.y + (ty - wobbleDelta.y) * 0.05f) * 0.7f
            );
            wobbleDelta = new Vec2f(
                    wobbleDelta.x + wobbleVel.x,
                    wobbleDelta.y + wobbleVel.y
            );
        }
    }

    private static void updateMouseLift(long now) {
        if (inMouseLift) {
            if (now > mouseLiftEnd) inMouseLift = false;
            return;
        }
        if (fastRand.nextFloat() < 0.0005f) {
            inMouseLift = true;
            mouseLiftEnd = now + 12 + fastRand.nextInt(40);
            mouseLiftDelta = new Vec2f(
                    (fastRand.nextFloat() - 0.5f) * 2.5f,
                    (fastRand.nextFloat() - 0.5f) * 1.8f
            );
        }
    }

    private static void updateCorrections(long now) {
        if (inCorrection) {
            if (now > correctionEnd) {
                inCorrection = false;
                correctionDelta = Vec2f.ZERO;
            }
            return;
        }
        if (fastRand.nextFloat() < 0.008f + currentProfile.micro * 0.012f) {
            inCorrection = true;
            correctionEnd = now + 25 + fastRand.nextInt(50);
            float dx = fastRand.nextBoolean() ? 1 : -1;
            float dy = fastRand.nextBoolean() ? 1 : -1;
            float amp = 0.3f + currentProfile.micro * 0.8f;
            correctionDelta = new Vec2f(
                    dx * (0.2f + fastRand.nextFloat() * amp),
                    dy * (0.15f + fastRand.nextFloat() * amp * 0.7f)
            );
        }
    }

    private static void updateDoubleCheck(long now) {
        if (inDoubleCheck) {
            if (now > doubleCheckEnd) {
                inDoubleCheck = false;
                doubleCheckDelta = Vec2f.ZERO;
            }
            return;
        }
        if (fastRand.nextFloat() < 0.003f + currentProfile.curiosity * 0.005f) {
            inDoubleCheck = true;
            doubleCheckEnd = now + 60 + fastRand.nextInt(100);
            float angle = fastRand.nextFloat() * (float)(Math.PI * 2);
            float dist = 0.3f + fastRand.nextFloat() * 0.8f;
            doubleCheckDelta = new Vec2f(
                    (float)Math.cos(angle) * dist,
                    (float)Math.sin(angle) * dist * 0.6f
            );
        }
    }

    private static void updateFlickBurst(long now) {
        if (inFlickBurst && now > flickBurstEnd) inFlickBurst = false;
    }

    private static void startFlickBurst(long now) {
        inFlickBurst = true;
        flickBurstEnd = now + 40 + fastRand.nextInt(70);
        flickBurstStr = 0.6f + fastRand.nextFloat() * 0.9f;
    }

    private static void onTargetChange(LivingEntity target, long now) {
        lastTargetId = target.getId();
        float reactionBase = currentProfile.reaction;
        if (currentEmotion == EmotionalState.PANIC) reactionBase *= 0.7f;
        if (currentEmotion == EmotionalState.FATIGUED) reactionBase *= 1.4f;
        if (currentEmotion == EmotionalState.BORED) reactionBase *= 1.2f;

        long reactionTime = (long)(80 * reactionBase + fastRand.nextInt((int)(60 * reactionBase)));
        reactionEnd = now + reactionTime;

        accelStart = now;
        fatigue = Math.max(fatigue - 0.03f, 0f);
        microCount = 0;
        inMicro = false;
        inHesitation = false;
        inOvershoot = false;
        inMouseLift = false;
        inWobble = false;
        inCorrection = false;
        inDoubleCheck = false;
        inAttentionBlink = false;
        rayTraceLostTime = -1L;
        mouseVel = Vec2f.ZERO;
        mouseAccel = Vec2f.ZERO;
        brownianNoise = Vec2f.ZERO;

        updateMousePhysicsParams();

        if (fastRand.nextFloat() < 0.3f + currentProfile.aggro * 0.4f) {
            startFlickBurst(now);
        }
    }

    private static void doReactionPhase(LivingEntity target, float aDist, float rDist, long now) {
        Vec3d p = UBoxPoints.getOrganicPoint(target);
        Vec2f b = UBoxPoints.getVanillaRotate(p);
        float yd = MathHelper.wrapDegrees(b.x - mc.player.getYaw());
        float pd = MathHelper.wrapDegrees(b.y - mc.player.getPitch());

        float progress = 1.0f - ((float)(reactionEnd - now) / (reactionEnd - accelStart + 1));
        progress = progress * progress * (3.0f - 2.0f * progress);

        float sp = Mathf.randomValue(0.8f, 3.5f);
        sp *= 0.3f + progress * 0.9f;

        float tremor = (1.0f - progress) * 0.3f;
        float ym = Math.min(Math.abs(yd), sp) * Math.signum(yd);
        float pm = Math.min(Math.abs(pd), sp * 0.6f) * Math.signum(pd);

        ym += (float)gaussian(fastRand) * tremor;
        pm += (float)gaussian(fastRand) * tremor * 0.6f;

        float fy = mc.player.getYaw() + ym;
        float fp = MathHelper.clamp(mc.player.getPitch() + pm, -90.0f, 90.0f);
        Vec2f s = humanGCDv2(fy, fp, sp, sp * 0.7f, (float)Math.sqrt(yd*yd + pd*pd));
        URotations.update(new Rotation(s.x, s.y), sp, sp * 0.75f, 14.0f, 12.0f, 0, 15, false);
    }

    private static Vec2f humanGCDv2(float yaw, float pitch, float yawSpd, float pitchSpd, float angDist) {
        float g = getGCD();
        if (g < 0.001f) return new Vec2f(yaw, pitch);

        float speedFactor = MathHelper.clamp((yawSpd + pitchSpd) / 60.0f, 0.5f, 1.5f);
        float distFactor = MathHelper.clamp(angDist / 45.0f, 0.8f, 1.2f);
        float fatigueFactor = 1.0f + fatigue * 0.3f;

        float gy = g * speedFactor * distFactor * fatigueFactor * (0.9f + fastRand.nextFloat() * 0.2f);
        float gp = g * speedFactor * distFactor * fatigueFactor * (0.85f + fastRand.nextFloat() * 0.15f);

        if (fastRand.nextFloat() < 0.08f + stress * 0.05f) {
            return new Vec2f(yaw, pitch);
        }

        float ry = yaw % gy;
        float rp = pitch % gp;
        float ey = (fastRand.nextFloat() - 0.5f) * gy * 0.25f;
        float ep = (fastRand.nextFloat() - 0.5f) * gp * 0.2f;

        return new Vec2f(yaw - ry + ey, pitch - rp + ep);
    }

    private static float getGCD() {
        Double s = mc.options.getMouseSensitivity().getValue();
        float f = (float)(s * 0.6f + 0.2f);
        return f * f * f * 1.2f;
    }

    private static float neuroMotorCurve(float speed, float delta) {
        float norm = MathHelper.clamp(Math.abs(delta) / 25.0f, 0.0f, 1.0f);
        float easeIn = norm * norm;
        float easeOut = 1.0f - (1.0f - norm) * (1.0f - norm);
        float blend = 0.4f + currentProfile.smooth * 0.3f;
        float curve = easeIn * blend + easeOut * (1.0f - blend);

        return speed * (0.5f + curve * 0.65f);
    }

    private static float adaptiveHistorySmooth(float cur, float[] hist, float f) {
        float avg = 0;
        for (float v : hist) avg += v;
        avg /= hist.length;

        float diff = Math.abs(cur - avg);
        float adapt = MathHelper.clamp(diff / 20.0f, 0.0f, 1.0f);
        float finalF = f * (1.0f - adapt * 0.5f) + fastRand.nextFloat() * 0.04f;

        return avg + (cur - avg) * finalF;
    }

    private static Vec2f getBreath(long now, double speed) {
        float phase = now / (900.0f - currentProfile.smooth * 250.0f);
        float y = (float)(Math.sin(phase) * Math.cos(phase * 0.317) * 0.18);
        float p = (float)(Math.sin(phase * 0.618) * 0.1);
        if (MoveUtil.isMoving()) {
            y *= 1.25f;
            p *= 1.15f;
        }
        if (mc.player.isSprinting()) {
            y *= 1.15f;
            p *= 1.08f;
        }
        y *= (1.0f + fatigue * 0.25f);
        p *= (1.0f + fatigue * 0.2f);
        return new Vec2f(y, p);
    }

    private static Vec2f getStrafe(LivingEntity target) {
        if (mc.player.distanceTo(target) >= 3.5f) return Vec2f.ZERO;
        Vec3d v = mc.player.getVelocity();
        float dist = mc.player.distanceTo(target);
        float mult = MathHelper.clamp(1.0f - dist / 3.5f, 0.0f, 1.0f);
        return new Vec2f((float)(-v.x * 2.0f * mult), (float)(v.z * 0.25f * mult));
    }

    private static Vec2f getHurt() {
        int h = mc.player.hurtTime;
        if (h <= 0) return Vec2f.ZERO;
        float i = h / 10.0f;
        inPreHit = false;
        return new Vec2f(
                (fastRand.nextFloat() - 0.5f) * 2.0f * i,
                (fastRand.nextFloat() - 0.5f) * 1.4f * i
        );
    }

    private static Vec2f getPanic(LivingEntity target) {
        if (stress < 0.15f) return Vec2f.ZERO;
        float t = System.currentTimeMillis() / 220.0f;
        float amp = stress * 0.3f;
        return new Vec2f(
                (float)(Math.sin(t * 4.5) * amp + Math.sin(t * 11.3) * amp * 0.3),
                (float)(Math.cos(t * 3.9) * amp * 0.5f + Math.cos(t * 9.7) * amp * 0.2)
        );
    }

    private static void updateStress(long now, LivingEntity target) {
        if (now - lastStressUpdate < 120) return;
        lastStressUpdate = now;

        float targetStress = 0.0f;
        if (mc.player.getHealth() < 10.0f) targetStress += 0.2f;
        if (mc.player.getHealth() < 5.0f) targetStress += 0.25f;
        if (target instanceof PlayerEntity && mc.player.distanceTo(target) < 3.0f) targetStress += 0.2f;
        if (mc.player.hurtTime > 0) targetStress += 0.15f;
        if (!RayTraceUtil.rayTraceSingleEntity(mc.player.getYaw(), mc.player.getPitch(), 6.0f, target)) {
            targetStress += 0.1f; // Стресс от потери цели
        }

        targetStress = MathHelper.clamp(targetStress, 0.0f, 0.8f);
        stress += (targetStress - stress) * 0.05f;
    }

    private static void updateArousal() {
        float targetArousal = stress * 0.7f + (1.0f - fatigue) * 0.3f;
        arousal += (targetArousal - arousal) * 0.02f;
    }

    private static void updateFatigue(long tick) {
        if (tick - lastFatigueTick > 40) {
            lastFatigueTick = tick;
            if (lastTargetId != -1) {
                fatigue = MathHelper.clamp(fatigue + 0.005f, 0f, 0.5f);
            } else {
                fatigue = MathHelper.clamp(fatigue - 0.012f, 0f, 0.5f);
            }
        }
    }

    private static float swingCd() {
        return MathHelper.clamp(
                (float) mc.player.handSwingTicks / (8.0f + fastRand.nextFloat() * 5.0f),
                0.0f, 1.0f
        );
    }

    private static float fade(float t) { return t * t * t * (t * (t * 6 - 15) + 10); }

    private static float grad(int hash, float x, float y, float z) {
        int h = hash & 15;
        float u = h < 8 ? x : y;
        float v = h < 4 ? y : h == 12 || h == 14 ? x : z;
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }

    private static double gaussian(ThreadLocalRandom rand) {
        return rand.nextGaussian();
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * MathHelper.clamp(t, 0.0f, 1.0f);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * MathHelper.clamp(t, 0.0, 1.0);
    }

    public static void state() {
        rayTraceLostTime = -1L;
        lastYawSpd = 0;
        lastPitchSpd = 0;
        mouseVel = Vec2f.ZERO;
        mouseAccel = Vec2f.ZERO;
        inOvershoot = false;
        nextOvershootChance = 0;
        overshootVel = Vec2f.ZERO;
        overshootDelta = Vec2f.ZERO;
        inPreHit = false;
        preHitEnd = 0;
        preHitStr = 1.0f;
        tremorOffset = Vec2f.ZERO;
        tremorVelocity = Vec2f.ZERO;
        tremorPhase = 0;
        lastTargetId = -1;
        inFlickBurst = false;
        flickBurstEnd = 0;
        flickBurstStr = 0;
        inMicro = false;
        microCount = 0;
        nextMicro = 0;
        microDelta = Vec2f.ZERO;
        inHesitation = false;
        hesEnd = 0;
        hesDelta = Vec2f.ZERO;
        inWobble = false;
        wobbleEnd = 0;
        wobbleDelta = Vec2f.ZERO;
        wobbleVel = Vec2f.ZERO;
        fatigue = 0f;
        lastFatigueTick = 0;
        stress = 0f;
        lastStressUpdate = 0;
        arousal = 0.5f;
        noisePos = Vec2f.ZERO;
        noiseVel = Vec2f.ZERO;
        noiseAccum = 0;
        mouseLiftEnd = 0;
        mouseLiftDelta = Vec2f.ZERO;
        inCorrection = false;
        correctionEnd = 0;
        correctionDelta = Vec2f.ZERO;
        inDoubleCheck = false;
        doubleCheckEnd = 0;
        doubleCheckDelta = Vec2f.ZERO;
        inAttentionBlink = false;
        nextAttentionBlink = 0;
        brownianNoise = Vec2f.ZERO;
        perceivedTargetPos = Vec3d.ZERO;
        histIdx = 0;
        for (int i = 0; i < yawHist.length; i++) {
            yawHist[i] = 0;
            pitchHist[i] = 0;
        }
        currentEmotion = EmotionalState.CALM;
        emotionBlend = 1.0f;
        regenProfile();
        updateMousePhysicsParams();
    }
}