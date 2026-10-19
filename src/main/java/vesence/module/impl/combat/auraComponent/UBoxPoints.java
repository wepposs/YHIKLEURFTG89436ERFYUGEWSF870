package vesence.module.impl.combat.auraComponent;

import lombok.Generated;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import vesence.utils.other.IMinecraft;
import vesence.utils.render.animation.util.Easings;

import java.util.*;

@Environment(EnvType.CLIENT)
public final class UBoxPoints implements IMinecraft {
    private static final Random random = new Random();
    private static int currentZone = 0;
    private static long zoneSwitchTime = 0;
    private static long zoneDuration = 1200;
    private static Vec3d currentPoint = null;
    private static Vec3d lastTargetPos = Vec3d.ZERO;
    private static Vec3d targetVel = Vec3d.ZERO;
    private static Vec3d targetVelSmooth = Vec3d.ZERO;
    private static Entity lastTarget = null;
    private static Vec3d attentionDrift = Vec3d.ZERO;
    private static Vec3d attentionMomentum = Vec3d.ZERO;
    private static float noisePhase = 0.0f;
    private static boolean inError = false;
    private static long errorStart = 0;
    private static Vec3d errorOffset = Vec3d.ZERO;
    private static long nextError = 0;
    private static float confidence = 1.0f;
    private static long lastHitTime = 0;
    private static long lastUpdateTime = 0;
    private static long lastSwitchTime = 0;
    private static List<Vec3d> cachedOffsets = new ArrayList<>();
    private static int currentPointIndex = 0;
    private static Vec3d rotationPoint = Vec3d.ZERO;
    private static Vec3d rotationMotion = Vec3d.ZERO;
    private static Vec2f organicRotOffset = Vec2f.ZERO;
    private static Vec2f organicRotVelocity = Vec2f.ZERO;
    private static Vec2f organicRotTarget = Vec2f.ZERO;
    private static long organicLastPhysicsUpdate = 0;
    private static long organicLastTargetPick = 0;
    private static final Random organicRandom = new Random();
    private static enum AttentionZone { HEAD, NECK, CHEST, BELLY, PELVIS, LSHOULDER, RSHOULDER, LKNEE, RKNEE, FEET }
    private static AttentionZone organicZone = AttentionZone.CHEST;
    private static long organicZoneSwitchTime = 0;
    private static long organicZoneDwell = 500;
    private static Vec2f organicSmoothOffset = Vec2f.ZERO;
    private static Vec2f organicSmoothVel = Vec2f.ZERO;
    private static double organicLastEntityX = 0, organicLastEntityY = 0, organicLastEntityZ = 0;
    private static long organicLastTrackTime = 0;
    private static long targetAcquiredTime = 0;
    private static Vec3d organicPredictedLead = Vec3d.ZERO;
    private static boolean organicInSaccade = false;
    private static long organicSaccadeEnd = 0;
    private static long organicSaccadeStart = 0;
    private static Vec2f organicSaccadeFrom = Vec2f.ZERO;
    private static Vec2f organicSaccadeTo = Vec2f.ZERO;
    private static int gridAnimIndex = 0;
    private static int gridAnimTicks = 0;
    private static Vec3d localWander = Vec3d.ZERO;
    private static Vec3d localWanderVel = Vec3d.ZERO;
    private static long lastWanderTime = 0;
    private static Vec3d lastWanderWorld = Vec3d.ZERO;
    private static Vec3d wanderWorldVel = Vec3d.ZERO;
    private static boolean inSaccade = false;
    private static long saccadeEnd = 0;
    private static long saccadeStart = 0;
    private static Vec3d saccadeFrom = Vec3d.ZERO;
    private static Vec3d saccadeTo = Vec3d.ZERO;
    private static long nextSaccadeChance = 0;
    private static boolean inDwell = false;
    private static long dwellEnd = 0;
    private static long nextDwellChance = 0;
    private static Vec3d dwellCenter = Vec3d.ZERO;
    private static Vec3d trackedPoint = Vec3d.ZERO;
    private static Vec3d trackedVel = Vec3d.ZERO;
    private static final float TRACK_LAG = 0.18f;
    private static float circlePhase = 0f;
    private static boolean inCircle = false;
    private static long circleEnd = 0;
    private static float circleRadius = 0f;
    private static float circleSpeed = 0f;
    private static float perlinPhase = 0f;
    private static float depthFocus = 0.5f;
    private static float depthFocusVel = 0f;
    private static long lastDepthUpdate = 0;
    private static float tremorPhase = 0f;
    private static Vec3d handTremor = Vec3d.ZERO;
    private static Vec3d predictedTargetPos = Vec3d.ZERO;
    private static Vec3d predictionError = Vec3d.ZERO;

    public static Vec3d getHumanizedAimPoint(Entity target, float attackDistance) {
        long now = System.currentTimeMillis();

        if (target != null && target != lastTarget) {
            onNewTarget(target);
        }

        updateTargetVelocity(target, now);

        if (now - zoneSwitchTime > zoneDuration) {
            pickZone(target, attackDistance, now);
            zoneSwitchTime = now;
            zoneDuration = 300 + random.nextInt(2800);
        }

        handleErrorState(now, attackDistance);

        Vec3d base = getZonePoint(target, currentZone);

        Vec3d swim = calculateSwim(now, attackDistance);

        Vec3d lead = calculateLead(target, attackDistance);

        Vec3d drift = calculateDrift(now);

        Vec3d err = inError ? errorOffset : Vec3d.ZERO;

        Vec3d targetPoint = base.add(swim).add(lead).add(drift).add(err);

        targetPoint = applyAttentionInertia(targetPoint, now);

        targetPoint = clampPointSpeed(targetPoint, attackDistance);

        if (now - lastHitTime > 500) {
            confidence = Math.min(1.0f, confidence + 0.002f);
        }

        currentPoint = targetPoint;
        return targetPoint;
    }

    private static void pickZone(Entity target, float distance, long now) {
        double headW, neckW, chestW, bellyW, pelvisW, lShoulderW, rShoulderW, lKneeW, rKneeW, feetW;

        float confFactor = confidence;

        if (distance < 1.5f) {
            headW = 0.08; neckW = 0.12; chestW = 0.22; bellyW = 0.18;
            pelvisW = 0.12; lShoulderW = 0.08; rShoulderW = 0.08;
            lKneeW = 0.06; rKneeW = 0.06; feetW = 0.00;
        } else if (distance < 3.0f) {
            headW = 0.22; neckW = 0.14; chestW = 0.20; bellyW = 0.14;
            pelvisW = 0.08; lShoulderW = 0.05; rShoulderW = 0.05;
            lKneeW = 0.04; rKneeW = 0.04; feetW = 0.04;
        } else if (distance < 6.0f) {
            headW = 0.38; neckW = 0.12; chestW = 0.16; bellyW = 0.10;
            pelvisW = 0.06; lShoulderW = 0.03; rShoulderW = 0.03;
            lKneeW = 0.04; rKneeW = 0.04; feetW = 0.04;
        } else if (distance < 10.0f) {
            headW = 0.48; neckW = 0.10; chestW = 0.14; bellyW = 0.08;
            pelvisW = 0.04; lShoulderW = 0.02; rShoulderW = 0.02;
            lKneeW = 0.04; rKneeW = 0.04; feetW = 0.04;
        } else {
            headW = 0.58; neckW = 0.08; chestW = 0.12; bellyW = 0.06;
            pelvisW = 0.04; lShoulderW = 0.02; rShoulderW = 0.02;
            lKneeW = 0.03; rKneeW = 0.03; feetW = 0.02;
        }

        if (confFactor < 0.5f) {
            headW *= 0.6f;
            chestW *= 1.3f;
            bellyW *= 1.2f;
        }

        if (target instanceof LivingEntity living && living.isBlocking()) {
            lKneeW += 0.15; rKneeW += 0.15;
            feetW += 0.15;
            headW -= 0.20; chestW -= 0.10; neckW -= 0.05;
        }

        if (inError) {
            headW *= 0.4f;
            chestW *= 1.2f;
            bellyW *= 1.3f;
        }

        double total = headW + neckW + chestW + bellyW + pelvisW + lShoulderW + rShoulderW + lKneeW + rKneeW + feetW;
        double r = random.nextDouble() * total;

        double acc = 0;
        acc += headW;      if (r < acc) { currentZone = 0; return; }
        acc += neckW;      if (r < acc) { currentZone = 1; return; }
        acc += chestW;     if (r < acc) { currentZone = 2; return; }
        acc += bellyW;     if (r < acc) { currentZone = 3; return; }
        acc += pelvisW;    if (r < acc) { currentZone = 4; return; }
        acc += lShoulderW; if (r < acc) { currentZone = 5; return; }
        acc += rShoulderW; if (r < acc) { currentZone = 6; return; }
        acc += lKneeW;     if (r < acc) { currentZone = 7; return; }
        acc += rKneeW;     if (r < acc) { currentZone = 8; return; }
        currentZone = 9;
    }

    private static Vec3d getZonePoint(Entity target, int zone) {
        Box box = target.getBoundingBox();
        double cx = (box.minX + box.maxX) / 2.0;
        double cz = (box.minZ + box.maxZ) / 2.0;
        double h = box.maxY - box.minY;
        double w = box.maxX - box.minX;
        double d = box.maxZ - box.minZ;

        double x, y, z, spreadX, spreadZ;

        switch (zone) {
            case 0:
                y = box.minY + h * (0.88 + random.nextDouble() * 0.10);
                x = cx + gaussian() * w * 0.06;
                z = cz + gaussian() * d * 0.06;
                break;
            case 1:
                y = box.minY + h * (0.74 + random.nextDouble() * 0.10);
                x = cx + gaussian() * w * 0.05;
                z = cz + gaussian() * d * 0.05;
                break;
            case 2:
                y = box.minY + h * (0.58 + random.nextDouble() * 0.12);
                x = cx + gaussian() * w * 0.10;
                z = cz + gaussian() * d * 0.08;
                break;
            case 3:
                y = box.minY + h * (0.42 + random.nextDouble() * 0.12);
                x = cx + gaussian() * w * 0.10;
                z = cz + gaussian() * d * 0.10;
                break;
            case 4:
                y = box.minY + h * (0.28 + random.nextDouble() * 0.10);
                x = cx + gaussian() * w * 0.08;
                z = cz + gaussian() * d * 0.08;
                break;
            case 5:
                y = box.minY + h * (0.62 + random.nextDouble() * 0.10);
                x = cx - w * 0.22 + gaussian() * w * 0.06;
                z = cz + gaussian() * d * 0.06;
                break;
            case 6:
                y = box.minY + h * (0.62 + random.nextDouble() * 0.10);
                x = cx + w * 0.22 + gaussian() * w * 0.06;
                z = cz + gaussian() * d * 0.06;
                break;
            case 7:
                y = box.minY + h * (0.18 + random.nextDouble() * 0.10);
                x = cx - w * 0.12 + gaussian() * w * 0.05;
                z = cz + gaussian() * d * 0.05;
                break;
            case 8:
                y = box.minY + h * (0.18 + random.nextDouble() * 0.10);
                x = cx + w * 0.12 + gaussian() * w * 0.05;
                z = cz + gaussian() * d * 0.05;
                break;
            case 9:
                y = box.minY + h * (0.04 + random.nextDouble() * 0.10);
                x = cx + gaussian() * w * 0.08;
                z = cz + gaussian() * d * 0.08;
                break;
            default:
                y = box.minY + h * 0.64;
                x = cx; z = cz;
        }

        Vec3d vel = targetVelSmooth;
        x += vel.x * 0.03;
        z += vel.z * 0.03;

        return new Vec3d(x, y, z);
    }

    private static Vec3d calculateSwim(long now, float distance) {
        float t = now / 1000.0f;
        double amp = 0.006 + Math.min(distance / 200.0, 0.025);

        double x = Math.sin(t * 0.45 + noisePhase) * amp
                + Math.cos(t * 0.23 + noisePhase * 1.7) * amp * 0.5;
        double y = Math.sin(t * 0.32 + noisePhase * 1.3) * amp * 0.6
                + Math.cos(t * 0.71 + noisePhase * 0.8) * amp * 0.3;
        double z = Math.cos(t * 0.38 + noisePhase * 0.9) * amp
                + Math.sin(t * 0.19 + noisePhase * 2.1) * amp * 0.5;

        return new Vec3d(x, y, z);
    }

    private static Vec3d calculateLead(Entity target, float distance) {
        if (lastTargetPos.equals(Vec3d.ZERO)) return Vec3d.ZERO;

        double speed = targetVelSmooth.length();
        if (speed < 0.03) return Vec3d.ZERO;

        double leadAmt = Math.min(distance / 18.0, 0.9) * 0.06;
        leadAmt *= (1.0 + speed * 2.5);
        leadAmt *= (0.80 + random.nextDouble() * 0.35);

        return targetVelSmooth.multiply(leadAmt);
    }

    private static Vec3d calculateDrift(long now) {
        attentionMomentum = attentionMomentum.add(
                (random.nextDouble() - 0.5) * 0.003,
                (random.nextDouble() - 0.5) * 0.002,
                (random.nextDouble() - 0.5) * 0.003
        );
        attentionMomentum = attentionMomentum.multiply(0.95);

        attentionDrift = attentionDrift.add(attentionMomentum);
        attentionDrift = attentionDrift.multiply(0.96);

        if (random.nextInt(100) < 3) {
            attentionDrift = attentionDrift.add(
                    (random.nextDouble() - 0.5) * 0.012,
                    (random.nextDouble() - 0.5) * 0.008,
                    (random.nextDouble() - 0.5) * 0.012
            );
        }

        return attentionDrift;
    }

    private static void handleErrorState(long now, float distance) {
        if (!inError && now > nextError && random.nextInt(100) < 5) {
            inError = true;
            errorStart = now;
            confidence *= 0.65f;

            double baseAmp = 0.03 + (distance / 100.0);
            double amp = baseAmp * (1.15 - confidence * 0.35);

            errorOffset = new Vec3d(
                    gaussian() * amp,
                    gaussian() * amp * 0.5,
                    gaussian() * amp
            );

            nextError = now + 1000 + random.nextInt(3000);
        }

        if (inError && now - errorStart > 80 + random.nextInt(350)) {
            inError = false;
            errorOffset = errorOffset.multiply(0.6);
            if (errorOffset.length() < 0.001) {
                errorOffset = Vec3d.ZERO;
            }
        }
    }

    private static Vec3d applyAttentionInertia(Vec3d targetPoint, long now) {
        if (currentPoint == null) return targetPoint;

        double lerpFactor;
        long timeSinceSwitch = now - zoneSwitchTime;

        if (timeSinceSwitch < 100) {
            lerpFactor = 0.40 + random.nextDouble() * 0.25;
        } else if (timeSinceSwitch < 350) {
            lerpFactor = 0.18 + random.nextDouble() * 0.12;
        } else {
            lerpFactor = 0.05 + random.nextDouble() * 0.05;
        }

        lerpFactor *= (0.45 + confidence * 0.55);

        return new Vec3d(
                lerp(currentPoint.x, targetPoint.x, lerpFactor),
                lerp(currentPoint.y, targetPoint.y, lerpFactor),
                lerp(currentPoint.z, targetPoint.z, lerpFactor)
        );
    }

    private static Vec3d clampPointSpeed(Vec3d targetPoint, float distance) {
        if (currentPoint == null) return targetPoint;

        Vec3d delta = targetPoint.subtract(currentPoint);
        double maxSpeed = 0.05 + (distance / 100.0);

        if (System.currentTimeMillis() - zoneSwitchTime < 180) {
            maxSpeed *= 2.2;
        }

        if (delta.length() > maxSpeed) {
            delta = delta.normalize().multiply(maxSpeed);
            return currentPoint.add(delta);
        }

        return targetPoint;
    }

    private static void updateTargetVelocity(Entity target, long now) {
        if (target == null) return;
        Vec3d pos = target.getEntityPos();

        if (!lastTargetPos.equals(Vec3d.ZERO)) {
            Vec3d rawVel = pos.subtract(lastTargetPos);
            targetVel = rawVel;
            targetVelSmooth = targetVelSmooth.add(targetVel.subtract(targetVelSmooth).multiply(0.20));
        }

        lastTargetPos = pos;
    }

    private static void onNewTarget(Entity target) {
        lastTarget = target;
        lastTargetPos = target.getEntityPos();
        targetVel = Vec3d.ZERO;
        targetVelSmooth = Vec3d.ZERO;
        currentPoint = null;
        attentionDrift = Vec3d.ZERO;
        attentionMomentum = Vec3d.ZERO;
        noisePhase = random.nextFloat() * 6.28f;
        inError = false;
        confidence = 0.7f + random.nextFloat() * 0.3f;
        nextError = System.currentTimeMillis() + 500 + random.nextInt(1500);
        initWander(target);
        trackedPoint = target.getEntityPos();
        trackedVel = Vec3d.ZERO;
        inSaccade = false;
        inDwell = false;
        inCircle = false;
        targetAcquiredTime = System.currentTimeMillis();
        perlinPhase = random.nextFloat() * 100f;
        depthFocus = 0.5f;
        depthFocusVel = 0f;
        tremorPhase = random.nextFloat() * 6.28f;
        handTremor = Vec3d.ZERO;
        predictedTargetPos = target.getEntityPos();
        predictionError = Vec3d.ZERO;
    }

    private static double gaussian() {
        return random.nextGaussian() * 0.5;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * MathHelper.clamp(t, 0.0, 1.0);
    }

    public static Vec3d getAimPoint(Entity target, float attackDistance) {
        return getHumanizedAimPoint(target, attackDistance);
    }

    private static void initWander(Entity entity) {
        Box box = entity.getBoundingBox();
        double h = box.maxY - box.minY;
        localWander = getZoneLocalCenter(h);
        localWanderVel = Vec3d.ZERO;
        lastWanderWorld = Vec3d.ZERO;
        wanderWorldVel = Vec3d.ZERO;
        lastWanderTime = 0;
        circlePhase = random.nextFloat() * 6.28f;
    }

    private static Vec3d getZoneLocalCenter(double h) {
        double y;
        switch (currentZone) {
            case 0: y = h * 0.93; break;
            case 1: y = h * 0.79; break;
            case 2: y = h * 0.64; break;
            case 3: y = h * 0.48; break;
            case 4: y = h * 0.33; break;
            case 5: y = h * 0.67; break;
            case 6: y = h * 0.67; break;
            case 7: y = h * 0.23; break;
            case 8: y = h * 0.23; break;
            case 9: y = h * 0.09; break;
            default: y = h * 0.64;
        }
        return new Vec3d(0, y, 0);
    }

    private static void updateWander(Entity entity, long now) {
        if (lastWanderTime == 0) {
            initWander(entity);
            lastWanderTime = now;
            return;
        }

        double dt = Math.min((now - lastWanderTime) / 1000.0, 0.05);
        if (dt <= 0) return;

        Box box = entity.getBoundingBox();
        double w = box.maxX - box.minX;
        double h = box.maxY - box.minY;
        double d = box.maxZ - box.minZ;
        double margin = 0.05;

        double minX = -w / 2 + margin, maxX = w / 2 - margin;
        double minY = margin, maxY = h - margin;
        double minZ = -d / 2 + margin, maxZ = d / 2 - margin;

        Vec3d zoneCenter = getZoneLocalCenter(h);

        if (!inSaccade && now > nextSaccadeChance && random.nextFloat() < 0.008f) {
            inSaccade = true;
            saccadeStart = now;
            saccadeEnd = now + 45 + random.nextInt(110);
            saccadeFrom = localWander;
            float sacAngle = random.nextFloat() * (float)(Math.PI * 2);
            float sacDist = 0.15f + random.nextFloat() * 0.35f;
            saccadeTo = new Vec3d(
                    MathHelper.clamp(localWander.x + Math.cos(sacAngle) * sacDist * w, minX, maxX),
                    MathHelper.clamp(localWander.y + (random.nextFloat() - 0.5f) * sacDist * h * 0.6, minY, maxY),
                    MathHelper.clamp(localWander.z + Math.sin(sacAngle) * sacDist * d, minZ, maxZ)
            );
            nextSaccadeChance = now + 200 + random.nextInt(1800);
        }

        if (inSaccade) {
            if (now > saccadeEnd) {
                inSaccade = false;
                localWander = saccadeTo;
                localWanderVel = Vec3d.ZERO;
                if (random.nextFloat() < 0.55f) {
                    inDwell = true;
                    dwellEnd = now + 120 + random.nextInt(380);
                    dwellCenter = localWander;
                }
            } else {
                float dur = saccadeEnd - saccadeStart;
                float prog = MathHelper.clamp((now - saccadeStart) / dur, 0.0f, 1.0f);
                float ease = 1.0f - (float)Math.pow(1.0 - prog, 3.0);
                localWander = new Vec3d(
                        saccadeFrom.x + (saccadeTo.x - saccadeFrom.x) * ease,
                        saccadeFrom.y + (saccadeTo.y - saccadeFrom.y) * ease,
                        saccadeFrom.z + (saccadeTo.z - saccadeFrom.z) * ease
                );
                lastWanderTime = now;
                updateWanderWorldVel(entity);
                return;
            }
        }

        if (inDwell) {
            if (now > dwellEnd) {
                inDwell = false;
            } else {
                float dwellT = (now - (dwellEnd - (120 + random.nextInt(380)))) / 1000.0f;
                localWander = new Vec3d(
                        dwellCenter.x + Math.sin(dwellT * 2.1) * 0.02,
                        dwellCenter.y + Math.cos(dwellT * 1.7) * 0.015,
                        dwellCenter.z + Math.sin(dwellT * 2.9) * 0.02
                );
                localWanderVel = Vec3d.ZERO;
                lastWanderTime = now;
                updateWanderWorldVel(entity);
                return;
            }
        }

        if (!inCircle && random.nextFloat() < 0.003f) {
            inCircle = true;
            circleEnd = now + 300 + random.nextInt(700);
            circleRadius = 0.08f + random.nextFloat() * 0.18f;
            circleSpeed = 1.5f + random.nextFloat() * 3.0f;
        }

        if (inCircle) {
            if (now > circleEnd) {
                inCircle = false;
            } else {
                circlePhase += dt * circleSpeed;
                float t = circlePhase;
                localWander = new Vec3d(
                        zoneCenter.x + Math.cos(t) * circleRadius * w * 0.3,
                        zoneCenter.y + Math.sin(t * 0.7) * circleRadius * h * 0.15,
                        zoneCenter.z + Math.sin(t) * circleRadius * d * 0.3
                );
                localWanderVel = Vec3d.ZERO;
                lastWanderTime = now;
                updateWanderWorldVel(entity);
                return;
            }
        }

        Vec3d accel = new Vec3d(
                (random.nextDouble() - 0.5) * 2.2,
                (random.nextDouble() - 0.5) * 1.4,
                (random.nextDouble() - 0.5) * 2.2
        );

        Vec3d toZone = zoneCenter.subtract(localWander);
        accel = accel.add(toZone.multiply(1.5));

        double centerDistXZ = Math.sqrt(localWander.x * localWander.x + localWander.z * localWander.z);
        double centerDistY = Math.abs(localWander.y - zoneCenter.y);
        double zoneRadiusXZ = w * 0.12;
        double zoneRadiusY = h * 0.10;
        if (centerDistXZ < zoneRadiusXZ && centerDistY < zoneRadiusY) {
            double angle = random.nextDouble() * Math.PI * 2;
            accel = accel.add(new Vec3d(
                    Math.cos(angle) * 1.0,
                    (random.nextDouble() - 0.5) * 0.6,
                    Math.sin(angle) * 1.0
            ));
        }

        localWanderVel = localWanderVel.add(accel.multiply(dt * 2.8));
        localWanderVel = localWanderVel.multiply(0.92);

        double maxSpeed = 0.7;
        if (localWanderVel.length() > maxSpeed) {
            localWanderVel = localWanderVel.normalize().multiply(maxSpeed);
        }

        localWander = localWander.add(localWanderVel.multiply(dt));

        if (localWander.x < minX) { localWander = new Vec3d(minX, localWander.y, localWander.z); localWanderVel = new Vec3d(Math.abs(localWanderVel.x) * 0.25, localWanderVel.y, localWanderVel.z); }
        if (localWander.x > maxX) { localWander = new Vec3d(maxX, localWander.y, localWander.z); localWanderVel = new Vec3d(-Math.abs(localWanderVel.x) * 0.25, localWanderVel.y, localWanderVel.z); }
        if (localWander.y < minY) { localWander = new Vec3d(localWander.x, minY, localWander.z); localWanderVel = new Vec3d(localWanderVel.x, Math.abs(localWanderVel.y) * 0.25, localWanderVel.z); }
        if (localWander.y > maxY) { localWander = new Vec3d(localWander.x, maxY, localWander.z); localWanderVel = new Vec3d(localWanderVel.x, -Math.abs(localWanderVel.y) * 0.25, localWanderVel.z); }
        if (localWander.z < minZ) { localWander = new Vec3d(localWander.x, localWander.y, minZ); localWanderVel = new Vec3d(localWanderVel.x, localWanderVel.y, Math.abs(localWanderVel.z) * 0.25); }
        if (localWander.z > maxZ) { localWander = new Vec3d(localWander.x, localWander.y, maxZ); localWanderVel = new Vec3d(localWanderVel.x, localWanderVel.y, -Math.abs(localWanderVel.z) * 0.25); }

        if (random.nextFloat() < 0.002f && !inDwell) {
            inDwell = true;
            dwellEnd = now + 100 + random.nextInt(250);
            dwellCenter = localWander;
        }

        lastWanderTime = now;
        updateWanderWorldVel(entity);
    }

    private static void updateWanderWorldVel(Entity entity) {
        Vec3d currentWorld = getWanderWorldPoint(entity);
        if (!lastWanderWorld.equals(Vec3d.ZERO)) {
            wanderWorldVel = currentWorld.subtract(lastWanderWorld).multiply(20.0); // blocks/sec
        } else {
            wanderWorldVel = Vec3d.ZERO;
        }
        lastWanderWorld = currentWorld;
    }

    private static Vec3d getWanderWorldPoint(Entity entity) {
        Box box = entity.getBoundingBox();
        double cx = (box.minX + box.maxX) / 2.0;
        double cz = (box.minZ + box.maxZ) / 2.0;
        return new Vec3d(cx + localWander.x, box.minY + localWander.y, cz + localWander.z);
    }

    public static Vec3d getWanderWorldVel() {
        return wanderWorldVel;
    }

    public static Vec3d getOrganicPoint(Entity entity) {
        return getOrganicPoint(entity, 0.0f);
    }

    public static Vec3d getOrganicPoint(Entity entity, float tickDelta) {
        if (entity == null || mc.player == null) {
            return mc.player != null ? mc.player.getEyePos() : Vec3d.ZERO;
        }

        long now = System.currentTimeMillis();
        Box box = entity.getBoundingBox();
        Vec3d eyes = mc.player.getEyePos();
        double dist = eyes.distanceTo(entity.getEntityPos());

        if (entity != lastTarget) {
            onNewTarget(entity);
        }

        if (targetAcquiredTime == 0) {
            targetAcquiredTime = now;
        }

        updateTargetVelocity(entity, now);
        updateWander(entity, now);
        updateDepthFocus(now, dist);
        updateHandTremor(now);
        updatePrediction(entity, now);

        if (now - zoneSwitchTime > zoneDuration) {
            pickZone(entity, (float) dist, now);
            zoneSwitchTime = now;
            zoneDuration = 300 + random.nextInt(2800);
        }

        handleErrorState(now, (float) dist);

        Vec3d rawPoint = getWanderWorldPoint(entity);
        rawPoint = rawPoint.add(organicPredictedLead);

        Vec3d perlinOffset = calculatePerlinOffset(now, dist);
        rawPoint = rawPoint.add(perlinOffset);

        Vec3d depthOffset = calculateDepthOffset(box, dist);
        rawPoint = rawPoint.add(depthOffset);

        rawPoint = rawPoint.add(handTremor);

        Vec3d swim = calculateSwim(now, (float) dist);
        Vec3d drift = calculateDrift(now);
        Vec3d err = inError ? errorOffset : Vec3d.ZERO;

        Vec3d targetPoint = rawPoint.add(swim).add(drift).add(err);
        targetPoint = applyAttentionInertia(targetPoint, now);
        targetPoint = clampPointSpeed(targetPoint, (float) dist);

        Vec3d toTarget = targetPoint.subtract(trackedPoint);
        trackedVel = trackedVel.add(toTarget.multiply(TRACK_LAG));
        trackedVel = trackedVel.multiply(0.82);
        trackedPoint = trackedPoint.add(trackedVel);

        Vec3d organicOffsetPoint = calculateOrganicOffsetPoint(now, dist, tickDelta);
        Vec3d finalPoint = trackedPoint.add(organicOffsetPoint);

        Vec2f finalRot = getVanillaRotate(finalPoint);

        currentPoint = trackedPoint;
        return organicRotationToPoint(eyes, finalRot.x, finalRot.y, box, dist);
    }

    private static void updateDepthFocus(long now, double dist) {
        if (now - lastDepthUpdate > 300 + random.nextInt(700)) {
            lastDepthUpdate = now;
            float targetDepth = 0.3f + random.nextFloat() * 0.4f;
            depthFocusVel = (targetDepth - depthFocus) * 0.02f;
        }
        depthFocus += depthFocusVel;
        depthFocusVel *= 0.95f;
        depthFocus = MathHelper.clamp(depthFocus, 0.2f, 0.8f);
    }

    private static Vec3d calculateDepthOffset(Box box, double dist) {
        double depthAmt = (depthFocus - 0.5) * 0.08 * (dist / 10.0);
        Vec3d toPlayer = mc.player.getEyePos().subtract(new Vec3d(
                (box.minX + box.maxX) / 2, (box.minY + box.maxY) / 2, (box.minZ + box.maxZ) / 2
        )).normalize();
        return toPlayer.multiply(depthAmt);
    }

    private static void updateHandTremor(long now) {
        tremorPhase += 0.15f;
        float tremorAmp = 0.003f;
        handTremor = new Vec3d(
                Math.sin(tremorPhase * 3.7) * Math.cos(tremorPhase * 2.3) * tremorAmp,
                Math.sin(tremorPhase * 4.1) * Math.sin(tremorPhase * 1.9) * tremorAmp * 0.8,
                Math.cos(tremorPhase * 2.9) * tremorAmp * 0.5
        );
    }

    private static Vec3d calculatePerlinOffset(long now, double dist) {
        double scale = 0.3 + Math.min(dist / 20.0, 0.5);
        double t = now / 1000.0;
        double px = perlinNoise(t * scale, 50, 100) * 0.02;
        double py = perlinNoise(100, t * scale + 50, 200) * 0.015;
        double pz = perlinNoise(200, 300, t * scale + 100) * 0.02;
        return new Vec3d(px, py, pz);
    }

    private static void updatePrediction(Entity entity, long now) {
        if (organicLastTrackTime == 0) {
            organicLastEntityX = entity.getX();
            organicLastEntityY = entity.getY();
            organicLastEntityZ = entity.getZ();
            organicLastTrackTime = now;
            predictedTargetPos = entity.getEntityPos();
            return;
        }
        long dt = now - organicLastTrackTime;
        if (dt > 40 && dt < 400) {
            double dx = entity.getX() - organicLastEntityX;
            double dy = entity.getY() - organicLastEntityY;
            double dz = entity.getZ() - organicLastEntityZ;
            double factor = MathHelper.clamp(90.0 / dt, 0.8, 3.0);
            organicPredictedLead = new Vec3d(dx * factor * 0.50, dy * factor * 0.30, dz * factor * 0.50);
            Vec3d predictedNow = predictedTargetPos.add(new Vec3d(dx, dy, dz).multiply(factor));
            predictionError = entity.getEntityPos().subtract(predictedNow).multiply(0.3);
            predictedTargetPos = entity.getEntityPos();
        } else {
            organicPredictedLead = organicPredictedLead.multiply(0.60);
            predictionError = predictionError.multiply(0.7);
        }
        organicLastEntityX = entity.getX();
        organicLastEntityY = entity.getY();
        organicLastEntityZ = entity.getZ();
        organicLastTrackTime = now;
    }

    private static Vec3d calculateOrganicOffsetPoint(long now, double dist, float tickDelta) {
        float t = now / 1000.0f;
        float amp = dist < 1.8 ? 0.015f : dist < 3.5 ? 0.025f : 0.04f;
        double wanderSpeed = wanderWorldVel.length();
        float wanderDamp = MathHelper.clamp((float)(1.0 - wanderSpeed * 2.0), 0.30f, 1.0f);
        amp *= wanderDamp;
        long timeOnTarget = now - targetAcquiredTime;
        float fatigue = MathHelper.clamp(timeOnTarget / 8000f, 0f, 0.35f);
        amp *= (1.0f + fatigue);

        double ox = Math.sin(t * 0.618) * Math.cos(t * 0.414) * amp;
        double oy = Math.sin(t * 0.314) * amp * 0.6;
        double oz = Math.cos(t * 0.272) * Math.sin(t * 0.523) * amp;

        float microT = (now + tickDelta * 50.0f) / 77.0f;
        ox += Math.sin(microT * 6.7) * Math.cos(microT * 5.3) * amp * 0.15;
        oy += Math.sin(microT * 7.1) * Math.sin(microT * 4.9) * amp * 0.12;

        if (!organicInSaccade && organicRandom.nextFloat() < 0.005f) {
            organicInSaccade = true;
            organicSaccadeStart = now;
            organicSaccadeEnd = now + 30 + organicRandom.nextInt(80);
            organicSaccadeFrom = organicSmoothOffset;
            float sacAmp = 0.02f + organicRandom.nextFloat() * 0.035f;
            float sacAngle = organicRandom.nextFloat() * (float)(Math.PI * 2);
            organicSaccadeTo = new Vec2f(
                    organicSaccadeFrom.x + (float)Math.cos(sacAngle) * sacAmp,
                    organicSaccadeFrom.y + (float)Math.sin(sacAngle) * sacAmp * 0.72f
            );
        }
        Vec2f sacOffset = applyOrganicSaccade(now, Vec2f.ZERO);
        ox += sacOffset.x;
        oy += sacOffset.y;

        return new Vec3d(ox, oy, oz);
    }

    private static Vec2f applyOrganicSaccade(long now, Vec2f offset) {
        if (!organicInSaccade) return offset;
        if (now > organicSaccadeEnd) {
            organicInSaccade = false;
            return offset;
        }
        float dur = organicSaccadeEnd - organicSaccadeStart;
        float prog = MathHelper.clamp((now - organicSaccadeStart) / dur, 0.0f, 1.0f);
        float ease = 1.0f - (float)Math.pow(1.0 - prog, 3.0);
        return new Vec2f(
                organicSaccadeFrom.x + (organicSaccadeTo.x - organicSaccadeFrom.x) * ease,
                organicSaccadeFrom.y + (organicSaccadeTo.y - organicSaccadeFrom.y) * ease
        );
    }

    private static Vec3d organicRotationToPoint(Vec3d eyes, float yaw, float pitch, Box box, double dist) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double dirX = -Math.sin(yawRad) * Math.cos(pitchRad);
        double dirY = -Math.sin(pitchRad);
        double dirZ = Math.cos(yawRad) * Math.cos(pitchRad);
        Vec3d dir = new Vec3d(dirX, dirY, dirZ);

        double reach = dist + box.getLengthX() + box.getLengthY() + box.getLengthZ() + 3.0;
        Optional<Vec3d> hit = box.raycast(eyes, eyes.add(dir.multiply(reach)));

        if (hit.isPresent()) {
            Vec3d p = hit.get();
            double m = 0.05;
            return new Vec3d(
                    MathHelper.clamp(p.x, box.minX + m, box.maxX - m),
                    MathHelper.clamp(p.y, box.minY + m, box.maxY - m),
                    MathHelper.clamp(p.z, box.minZ + m, box.maxZ - m)
            );
        }

        return new Vec3d(
                MathHelper.clamp(eyes.x, box.minX, box.maxX),
                MathHelper.clamp(eyes.y, box.minY, box.maxY),
                MathHelper.clamp(eyes.z, box.minZ, box.maxZ)
        );
    }

    private static double perlinNoise(double x, double y, double z) {
        int X = (int)Math.floor(x) & 255;
        int Y = (int)Math.floor(y) & 255;
        int Z = (int)Math.floor(z) & 255;
        x -= Math.floor(x); y -= Math.floor(y); z -= Math.floor(z);
        double u = fade(x); double v = fade(y); double w = fade(z);
        int A = p[X] + Y, AA = p[A] + Z, AB = p[A + 1] + Z;
        int B = p[X + 1] + Y, BA = p[B] + Z, BB = p[B + 1] + Z;
        return lerp(w, lerp(v, lerp(u, grad(p[AA], x, y, z), grad(p[BA], x - 1, y, z)),
                        lerp(u, grad(p[AB], x, y - 1, z), grad(p[BB], x - 1, y - 1, z))),
                lerp(v, lerp(u, grad(p[AA + 1], x, y, z - 1), grad(p[BA + 1], x - 1, y, z - 1)),
                        lerp(u, grad(p[AB + 1], x, y - 1, z - 1), grad(p[BB + 1], x - 1, y - 1, z - 1))));
    }

    private static double fade(double t) { return t * t * t * (t * (t * 6 - 15) + 10); }

    private static double grad(int hash, double x, double y, double z) {
        int h = hash & 15;
        double u = h < 8 ? x : y;
        double v = h < 4 ? y : h == 12 || h == 14 ? x : z;
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }

    private static final int[] p = new int[512];
    private static final int[] permutation = {
            151,160,137,91,90,15,131,13,201,95,96,53,194,233,7,225,
            140,36,103,30,69,142,8,99,37,240,21,10,23,190,6,148,
            247,120,234,75,0,26,197,62,94,252,219,203,117,35,11,32,
            57,177,33,88,237,149,56,87,174,20,125,136,171,168,68,175,
            74,165,71,134,139,48,27,166,77,146,158,231,83,111,229,122,
            60,211,133,230,220,105,92,41,55,46,245,40,244,102,143,54,
            65,25,63,161,1,216,80,73,209,76,132,187,208,89,18,169,
            200,196,135,130,116,188,159,86,164,100,109,198,173,186,3,64,
            52,217,226,250,124,123,5,202,38,147,118,126,255,82,85,212,
            207,206,59,227,47,16,58,17,182,189,28,42,223,183,170,213,
            119,248,152,2,44,154,163,70,221,153,101,155,167,43,172,9,
            129,22,39,253,19,98,108,110,79,113,224,232,178,185,112,104,
            218,246,97,228,251,34,242,193,238,210,144,12,191,179,162,241,
            81,51,145,235,249,14,239,107,49,192,214,31,181,199,106,157,
            184,84,204,176,115,121,50,45,127,4,150,254,138,236,205,93,
            222,114,67,29,24,72,243,141,128,195,78,66,215,61,156,180
    };

    static { for (int i = 0; i < 256; i++) p[256 + i] = p[i] = permutation[i]; }

    public static Vec3d getBestVector3dOnEntityBox(Entity entity, boolean alwaysMultipoints) {
        if (entity == null) return mc.player.getEyePos();
        Box aabb = entity.getBoundingBox();
        double[] whh = new double[]{entity.getWidth(), entity.getHeight(), entity.getHeight() / 1.05F};
        double[] xyz = new double[]{entity.getX(), entity.getY(), entity.getZ()};
        if (aabb != null) {
            whh = new double[]{aabb.maxX - aabb.minX, aabb.maxY - aabb.minY, (aabb.maxY - aabb.minY) / 1.1F};
            xyz = new double[]{aabb.minX + whh[0] / 2.D, aabb.minY, aabb.minZ + whh[0] / 2.D};
        }
        double[] diffs = new double[]{mc.player.getY() - xyz[1], getDistanceXZ(mc.player, xyz[0], xyz[2])};
        double ddtn = clamp(Easings.QUART_OUT.ease((diffs[1] - whh[0] / 2.F) / (5.D + whh[0] / 2.D)), 0.1D, .95D);
        double pca = clamp(ddtn * ddtn, 0.D, 1.D);
        final double pitchPointHeight = clamp((whh[2] / 2.D * pca + (whh[2] / 2.D) * (clamp(diffs[0] + pca, 0.D, 1.D))), 0, whh[2]);
        Vec3d defaultVec = new Vec3d(xyz[0], xyz[1] + pitchPointHeight, xyz[2]);
        if (!alwaysMultipoints && !seenOnceVector3d(mc.player, defaultVec))
            defaultVec = defaultVec.add(0.D, -pitchPointHeight / 2.D, 0.D);
        if (whh[1] <= 1D || !alwaysMultipoints && seenOnceVector3d(mc.player, defaultVec)) {
            return defaultVec;
        } else {
            final List<Vec3d> normalVecs = entityBoxVec3dsAlternate(entity, aabb);
            float factorDown = 1.F - (float) Math.max(Math.min((diffs[1] - 2.F) / 3.F, 1.F), 0.F);
            final Vec3d toSortVec = new Vec3d(mc.player.getX(), mc.player.getY() + .6F + lerp(pitchPointHeight, pitchPointHeight / 2.5F, factorDown), mc.player.getZ());
            if (normalVecs != null && normalVecs.size() > 1)
                normalVecs.sort(Comparator.comparing(vec3 -> getDistanceAtVec3dToVec3d(toSortVec, vec3)));
            return normalVecs != null && normalVecs.size() > 0 ? normalVecs.get(0) : defaultVec;
        }
    }

    public static Vec3d getBestVector3dOnEntityBox(Entity entity) {
        return getBestVector3dOnEntityBox(entity, mc.player.getEntityPos().distanceTo(entity.getEntityPos()) > entity.getWidth() * 1.37F);
    }

    public static Vec3d getBestVector3dOnEntityBox(Box aabb) {
        return getBestVector3dOnEntityBox(aabb, true, false);
    }

    public static Vec3d getBestVector3dOnEntityBox(Box aabb, boolean alwaysMultipoints) {
        return getBestVector3dOnEntityBox(aabb, alwaysMultipoints, false);
    }

    public static Vec3d getBestVector3dOnEntityBox(Box aabb, boolean alwaysMultipoints, boolean animate) {
        if (aabb == null) {
            return mc.player.getEyePos();
        } else {
            double[] whh = new double[]{aabb.maxX - aabb.minX, aabb.maxY - aabb.minY, (aabb.maxY - aabb.minY) / 1.1F};
            double[] xyz = new double[]{aabb.minX + whh[0] / 2.D, aabb.minY, aabb.minZ + whh[0] / 2.D};
            double[] diffs = new double[]{mc.player.getY() - xyz[1], getDistanceXZ(mc.player, xyz[0], xyz[2])};
            double ddtn = clamp(Easings.QUART_OUT.ease((diffs[1] - whh[0] / 2.F) / (5.D + whh[0] / 2.D)), 0.1D, .95D);
            double pca = clamp(ddtn * ddtn, 0.D, 1.D);
            final double pitchPointHeight = clamp((whh[2] / 2.D * pca + (whh[2] / 2.D) * (clamp(diffs[0] + pca, 0.D, 1.D))), 0, whh[2]);
            Vec3d defaultVec = new Vec3d(xyz[0], xyz[1] + pitchPointHeight, xyz[2]);
            if (!alwaysMultipoints && !seenOnceVector3d(mc.player, defaultVec))
                defaultVec = defaultVec.add(0.D, -pitchPointHeight / 2.D, 0.D);
            if (whh[1] <= 1D || !alwaysMultipoints && seenOnceVector3d(mc.player, defaultVec)) {
                return defaultVec;
            } else {
                final List<Vec3d> normalVecs = entityBoxVec3dsAlternate(aabb);
                float factorDown = 1.F - (float) Math.max(Math.min((diffs[1] - 2.F) / 3.F, 1.F), 0.F);
                final Vec3d toSortVec = new Vec3d(mc.player.getX(), mc.player.getY() + .6F + lerp(pitchPointHeight, pitchPointHeight / 2.5F, factorDown), mc.player.getZ());
                if (normalVecs != null && normalVecs.size() > 1)
                    normalVecs.sort(Comparator.comparing(vec3 -> getDistanceAtVec3dToVec3d(toSortVec, vec3)));
                if (animate && normalVecs != null && !normalVecs.isEmpty()) {
                    gridAnimTicks++;
                    if (gridAnimTicks >= 3) {
                        gridAnimTicks = 0;
                        gridAnimIndex = (gridAnimIndex + 1) % normalVecs.size();
                    }
                    return normalVecs.get(gridAnimIndex);
                }
                return normalVecs != null && normalVecs.size() > 0 ? normalVecs.get(0) : defaultVec;
            }
        }
    }

    public static Vec2f getBestRotateVector2fOnEntityBox(Entity entity, boolean alwaysMultipoints) {
        Vec3d vec = UBoxPoints.getBestVector3dOnEntityBox(entity, alwaysMultipoints);
        return UBoxPoints.getVanillaRotate(vec);
    }

    public static Vec2f getBestRotateVector2fOnEntityBox(Entity entity) {
        return getBestRotateVector2fOnEntityBox(entity, true);
    }

    public static Vec3d getClosestRotationPoint(Entity entity, boolean visibilityCheck) {
        if (entity == null || mc.player == null) {
            return mc.player != null ? mc.player.getEyePos() : Vec3d.ZERO;
        }

        List<Vec3d> candidates = entityBoxVec3dsAlternate(entity, entity.getBoundingBox());
        if (candidates == null || candidates.isEmpty()) {
            return closestPoint(entity);
        }

        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();

        Vec3d bestPoint = null;
        double bestAngle = Double.MAX_VALUE;

        for (Vec3d point : candidates) {
            if (visibilityCheck && !seenOnceVector3d(mc.player, point)) {
                continue;
            }

            Vec2f rot = getVanillaRotate(point);
            float yawDiff = Math.abs(MathHelper.wrapDegrees(rot.x - currentYaw));
            float pitchDiff = Math.abs(MathHelper.wrapDegrees(rot.y - currentPitch));
            double angleDist = Math.hypot(yawDiff, pitchDiff);

            if (angleDist < bestAngle) {
                bestAngle = angleDist;
                bestPoint = point;
            }
        }

        return bestPoint != null ? bestPoint : closestPoint(entity);
    }

    public static Vec3d getClosestRotationPoint(Entity entity) {
        return getClosestRotationPoint(entity, true);
    }

    public static Vec3d closestPoint(Entity entity) {
        if (entity == null) return Vec3d.ZERO;
        Vec3d playerEyes = mc.player.getEyePos();
        Box box = entity.getBoundingBox();
        double closestX = MathHelper.clamp(playerEyes.x, box.minX, box.maxX);
        double closestY = MathHelper.clamp(playerEyes.y, box.minY, box.maxY);
        double closestZ = MathHelper.clamp(playerEyes.z, box.minZ, box.maxZ);
        return new Vec3d(closestX, closestY, closestZ);
    }

    public static Vec3d smartRandomPoint(Entity entity, boolean alwaysMultipoints, int pointCount, float switchDelayMs) {
        if (entity == null) return mc.player.getEyePos();
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime > 1000 || cachedOffsets.isEmpty()) {
            generateSmartPointsV2(entity, pointCount, alwaysMultipoints);
            currentPointIndex = 0;
            lastUpdateTime = currentTime;
            lastSwitchTime = currentTime;
        }
        if (currentTime - lastSwitchTime > switchDelayMs) {
            currentPointIndex = selectNextBestPoint(entity);
            lastSwitchTime = currentTime;
        }
        if (cachedOffsets.isEmpty()) return getBestVector3dOnEntityBox(entity, alwaysMultipoints);
        Vec3d entityPos = entity.getEntityPos();
        Vec3d selectedOffset = cachedOffsets.get(currentPointIndex);
        double tremor = 0.005D;
        long tremorPhase = currentTime / 16L;
        double tremorX = Math.sin(tremorPhase * 0.8D) * tremor;
        double tremorY = Math.cos(tremorPhase * 0.6D) * tremor * 0.8D;
        double tremorZ = Math.sin(tremorPhase * 0.9D) * tremor;
        return entityPos.add(selectedOffset).add(tremorX, tremorY, tremorZ);
    }

    public static Vec3d smartRandomPoint(Entity entity) {
        return smartRandomPoint(entity, mc.player.getEntityPos().distanceTo(entity.getEntityPos()) > entity.getWidth() * 1.37F, 75, 300.0F);
    }

    public static Vec3d getMultipoint(Entity target, double distance) {
        float minMotionXZ = 0.005f, maxMotionXZ = 0.015f;
        float minMotionY = 0.0015f, maxMotionY = 0.015f;
        double lenghtX = target.getBoundingBox().getLengthX();
        double lenghtY = target.getBoundingBox().getLengthY();
        double lenghtZ = target.getBoundingBox().getLengthZ();
        if (rotationMotion.equals(Vec3d.ZERO))
            rotationMotion = new Vec3d(random.nextFloat() * 0.04f - 0.02f, random.nextFloat() * 0.04f - 0.02f, random.nextFloat() * 0.04f - 0.02f);
        if (rotationPoint.equals(Vec3d.ZERO))
            rotationPoint = new Vec3d(0, lenghtY * 0.5, 0);
        rotationPoint = rotationPoint.add(rotationMotion);
        double safeX = (lenghtX - 0.1) / 2f;
        double safeZ = (lenghtZ - 0.1) / 2f;
        if (rotationPoint.x >= safeX)
            rotationMotion = new Vec3d(-(random.nextFloat() * (maxMotionXZ - minMotionXZ) + minMotionXZ), rotationMotion.y, rotationMotion.z);
        else if (rotationPoint.x <= -safeX)
            rotationMotion = new Vec3d(random.nextFloat() * (maxMotionXZ - minMotionXZ) + minMotionXZ, rotationMotion.y, rotationMotion.z);
        if (rotationPoint.y >= lenghtY * 0.75)
            rotationMotion = new Vec3d(rotationMotion.x, -(random.nextFloat() * (maxMotionY - minMotionY) + minMotionY), rotationMotion.z);
        else if (rotationPoint.y <= lenghtY * 0.3)
            rotationMotion = new Vec3d(rotationMotion.x, random.nextFloat() * (maxMotionY - minMotionY) + minMotionY, rotationMotion.z);
        if (rotationPoint.z >= safeZ)
            rotationMotion = new Vec3d(rotationMotion.x, rotationMotion.y, -(random.nextFloat() * (maxMotionXZ - minMotionXZ) + minMotionXZ));
        else if (rotationPoint.z <= -safeZ)
            rotationMotion = new Vec3d(rotationMotion.x, rotationMotion.y, random.nextFloat() * (maxMotionXZ - minMotionXZ) + minMotionXZ);
        rotationPoint.add(random.nextFloat() * 0.1f - 0.05f, 0f, random.nextFloat() * 0.1f - 0.05f);
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d lookVec = mc.player.getRotationVector();
        if (!rayTraceToBox(eyePos, lookVec, distance, target.getBoundingBox())) {
            float halfBox = (float) (lenghtX / 2f) * 0.8f;
            for (float x1 = -halfBox; x1 <= halfBox; x1 += 0.1f) {
                for (float z1 = -halfBox; z1 <= halfBox; z1 += 0.1f) {
                    for (float y1 = (float) (lenghtY * 0.9); y1 >= lenghtY * 0.3; y1 -= 0.1f) {
                        Vec3d v1 = new Vec3d(target.getX() + x1, target.getY() + y1, target.getZ() + z1);
                        Vec2f rot = getVanillaRotate(v1);
                        float yawRad = (float) Math.toRadians(rot.x);
                        float pitchRad = (float) Math.toRadians(rot.y);
                        double x = -Math.sin(yawRad) * Math.cos(pitchRad);
                        double y = -Math.sin(pitchRad);
                        double z = Math.cos(yawRad) * Math.cos(pitchRad);
                        Vec3d direction = new Vec3d(x, y, z);
                        if (rayTraceToBox(eyePos, direction, distance, target.getBoundingBox())) {
                            rotationPoint = new Vec3d(x1, y1, z1);
                            return target.getEntityPos().add(rotationPoint);
                        }
                    }
                }
            }
        }
        return target.getEntityPos().add(rotationPoint);
    }

    private static int selectNextBestPoint(Entity entity) {
        if (cachedOffsets.size() <= 1) return 0;
        if (random.nextFloat() < 0.7f) {
            return (currentPointIndex + 1) % cachedOffsets.size();
        } else {
            return random.nextInt(Math.min(3, cachedOffsets.size()));
        }
    }

    private static void generateSmartPointsV2(Entity entity, int pointCount, boolean alwaysMultipoints) {
        cachedOffsets.clear();
        Box box = entity.getBoundingBox();
        double width = box.maxX - box.minX;
        double height = box.maxY - box.minY;
        double depth = box.maxZ - box.minZ;
        Vec3d playerEyes = mc.player.getEyePos();
        List<TargetZone> zones = new ArrayList<>();
        double headHeight = 0.85D;
        zones.add(new TargetZone(0.3D, headHeight, 0.3D, 1.0f));
        zones.add(new TargetZone(0.5D, headHeight, 0.5D, 0.8f));
        zones.add(new TargetZone(0.3D, 0.6D, 0.4D, 1.2f));
        zones.add(new TargetZone(0.5D, 0.55D, 0.5D, 1.0f));
        zones.add(new TargetZone(0.3D, 0.2D, 0.3D, 0.6f));
        List<WeightedPoint> candidates = new ArrayList<>();
        for (TargetZone zone : zones) {
            int pointsForZone = (int) (pointCount * zone.weight / 3.5f);
            pointsForZone = Math.max(2, pointsForZone);
            for (int i = 0; i < pointsForZone; i++) {
                double offsetX = (random.nextGaussian() * 0.15D + zone.xOffset) * width - width/2;
                double offsetY = (random.nextGaussian() * 0.1D + zone.yOffset) * height;
                double offsetZ = (random.nextGaussian() * 0.15D + zone.zOffset) * depth - depth/2;
                Vec3d absolutePoint = new Vec3d(box.minX + width/2 + offsetX, box.minY + offsetY, box.minZ + depth/2 + offsetZ);
                if (!alwaysMultipoints && !seenOnceVector3d(mc.player, absolutePoint)) continue;
                float score = calculatePointScore(absolutePoint, playerEyes, entity, zone.weight);
                score += random.nextFloat() * 0.1f;
                candidates.add(new WeightedPoint(absolutePoint.subtract(entity.getEntityPos()), score));
            }
        }
        if (candidates.size() < 3) {
            Vec3d bestVec = getBestVector3dOnEntityBox(entity, alwaysMultipoints);
            candidates.add(new WeightedPoint(bestVec.subtract(entity.getEntityPos()), 0.5f));
        }
        candidates.sort((a, b) -> Float.compare(b.score, a.score));
        int takeCount = Math.min(pointCount, candidates.size());
        for (int i = 0; i < takeCount; i++) cachedOffsets.add(candidates.get(i).offset);
        if (cachedOffsets.size() > 3) Collections.shuffle(cachedOffsets.subList(3, cachedOffsets.size()), random);
    }

    private static float calculatePointScore(Vec3d point, Vec3d playerEyes, Entity entity, float baseWeight) {
        float score = baseWeight * 10.0f;
        Vec2f currentRot = new Vec2f(mc.player.getYaw(), mc.player.getPitch());
        Vec2f targetRot = getVanillaRotate(point);
        float yawDiff = Math.abs(MathHelper.wrapDegrees(targetRot.x - currentRot.x));
        float pitchDiff = Math.abs(targetRot.y - currentRot.y);
        float angleDiff = (yawDiff + pitchDiff) / 2.0f;
        float aimPenalty = (angleDiff / 180.0f) * (angleDiff / 180.0f) * 5.0f;
        score -= aimPenalty;
        double dist = playerEyes.distanceTo(point);
        score -= (dist / 10.0f);
        Vec3d center = entity.getEntityPos().add(0, entity.getHeight() / 2, 0);
        double distToCenter = point.distanceTo(center);
        score -= distToCenter * 2.0f;
        double relativeHeight = (point.y - entity.getY()) / entity.getHeight();
        if (relativeHeight > 0.7 && relativeHeight < 0.95) score += 2.0f;
        HitResult ray = traceBlock(playerEyes, point, ShapeType.COLLIDER, FluidHandling.NONE);
        if (ray.getType() == Type.BLOCK) score -= 10.0f;
        return Math.max(0.1f, score);
    }

    public static Vec2f getVanillaRotate(Vec3d vec) {
        final Vec3d eyesPos = mc.player.getEyePos();
        final Vec3d rot = vec.add(-eyesPos.x, -eyesPos.y, -eyesPos.z);
        final double xzD = MathHelper.sqrt((float) (rot.x * rot.x + rot.z * rot.z));
        float yaw = (float) (Math.atan2(rot.z, rot.x) * 180.F / Math.PI - 90.F);
        float pitch = (float) Math.toDegrees(-Math.atan2(rot.y, xzD));
        return new Vec2f(yaw, pitch);
    }

    public static HitResult traceBlock(Vec3d startVec, Vec3d endVec, ShapeType blockMode, FluidHandling fluidMode) {
        return mc.world.raycast(new RaycastContext(startVec, endVec, blockMode, fluidMode, mc.player));
    }

    private static double getDistanceXZ(ClientPlayerEntity self, double x, double z) {
        double d0 = self.getX() - x;
        double d1 = self.getZ() - z;
        return MathHelper.sqrt((float) (d0 * d0 + d1 * d1));
    }

    private static boolean seenOnce3(ClientPlayerEntity self, double x, double y, double z) {
        Vec3d vector3d1 = new Vec3d(x, y, z);
        return mc.world != null && traceBlock(self.getEyePos(), vector3d1, ShapeType.COLLIDER, FluidHandling.NONE).getType() != Type.BLOCK;
    }

    public static boolean seenOnceVector3d(ClientPlayerEntity self, Vec3d vec) {
        Vec3d vector3d = new Vec3d(self.getX(), self.getEyeY(), self.getZ());
        return mc.world != null && traceBlock(vector3d, vec, ShapeType.COLLIDER, FluidHandling.NONE).getType() != Type.BLOCK;
    }

    private static boolean localSeen(ClientPlayerEntity selfEntity, final Vec3d xyz, final float scale) {
        return scale == 0 ? seenOnce3(selfEntity, xyz.x, xyz.y, xyz.z) :
                seenOnce3(selfEntity, xyz.x, xyz.y, xyz.z) &&
                        seenOnce3(selfEntity, xyz.x, xyz.y + scale, xyz.z) &&
                        seenOnce3(selfEntity, xyz.x, xyz.y - scale, xyz.z) &&
                        seenOnce3(selfEntity, xyz.x + scale, xyz.y, xyz.z) &&
                        seenOnce3(selfEntity, xyz.x - scale, xyz.y, xyz.z) &&
                        seenOnce3(selfEntity, xyz.x, xyz.y, xyz.z + scale) &&
                        seenOnce3(selfEntity, xyz.x, xyz.y, xyz.z - scale);
    }

    public static List<Vec3d> entityBoxVec3dsAlternates(Entity entity) {
        return entityBoxVec3dsAlternate(entity, entity.getBoundingBox());
    }

    public static List<Vec3d> entityBoxVec3dsAlternate(Entity entity, Box aabb) {
        if (entity == null) return null;
        final List<Vec3d> vecs = new ArrayList<>();
        double offsetXYZ = .02F;
        int maxPointsCountXZ = 14, minPointsCountXZ = 5;
        int maxPointsCountY = 27, minPointsCountY = 9;
        double[] whh = new double[]{entity.getWidth() - offsetXYZ * 2D, entity.getHeight() - offsetXYZ * 2D, (entity.getHeight() - offsetXYZ * 2D) / 1.05D};
        double[] xyz = new double[]{entity.getX(), entity.getY(), entity.getZ()};
        double[] xyz1 = new double[]{xyz[0] + whh[0] / 2.D, xyz[1] + whh[1], xyz[2] + whh[0] / 2.D};
        double[] xyz2 = new double[]{xyz[0] - whh[0] / 2.D, xyz[1], xyz[2] - whh[0] / 2.D};
        if (aabb != null) {
            aabb = aabb.contract(offsetXYZ);
            whh = new double[]{aabb.maxX - aabb.minX, aabb.maxY - aabb.minY, (aabb.maxY - aabb.minY) / 1.05D};
            xyz = new double[]{aabb.minX + whh[0] / 2.D, aabb.minY, aabb.minZ + whh[0] / 2.D};
            xyz1 = new double[]{aabb.minX, aabb.minY, aabb.minZ};
            xyz2 = new double[]{aabb.maxX, aabb.maxY, aabb.maxZ};
        } else {
            xyz2 = new double[]{xyz[0] + whh[0] / 2.D, xyz[1] + whh[1], xyz[2] + whh[0] / 2.D};
            xyz1 = new double[]{xyz[0] - whh[0] / 2.D, xyz[1], xyz[2] - whh[0] / 2.D};
        }
        float sqrtWHH0CubeD2 = (float) Math.sqrt(whh[0] * whh[0] + whh[0] * whh[0] + whh[0] * whh[0]) / 2.F;
        final ClientPlayerEntity me = mc.player;
        if (me == null) return null;
        Vec3d mePos = new Vec3d(me.getX(), me.getY(), me.getZ());
        final float factorCount = (1.F - Math.min((float) mePos.distanceTo(new Vec3d(xyz[0], xyz[1], xyz[2])) / 5.F, 1.F)) * Math.min((float) mePos.distanceTo(new Vec3d(xyz[0], me.getY(), xyz[2])) / .6F, 1.F);
        final int pointsCountXZ = lerp(minPointsCountXZ, maxPointsCountXZ, factorCount);
        final int pointsCountY = lerp(minPointsCountY, maxPointsCountY, factorCount);
        float scaleSeenCheck = .0F;
        double mePosX = me.getX(), mePosY = me.getY(), mePosZ = me.getZ();
        double eyeOffset = -me.getEyeHeight(EntityPose.STANDING);
        for (int xsI = 0; xsI < pointsCountXZ; xsI++) {
            final boolean edgeX = xsI == 0 || xsI == pointsCountXZ - 1;
            final double xs = lerp(xyz1[0], xyz2[0], xsI / (float) (pointsCountXZ - 1));
            for (int zsI = 0; zsI < pointsCountXZ; zsI++) {
                final boolean edgeZ = zsI == 0 || zsI == pointsCountXZ - 1;
                final double zs = lerp(xyz1[2], xyz2[2], zsI / (float) (pointsCountXZ - 1));
                for (int ysI = 0; ysI < pointsCountY; ysI++) {
                    final boolean edgeY = ysI == 0 || ysI == pointsCountY - 1;
                    final double ys = lerp(xyz1[1], xyz2[1], ysI / (float) (pointsCountY - 1));
                    if (!edgeX && !edgeZ && !edgeY) {
                        double dx = xs - mePosX, dy = (ys + eyeOffset) - mePosY, dz = zs - mePosZ;
                        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        if (dist >= sqrtWHH0CubeD2) continue;
                    }
                    final Vec3d vec = new Vec3d(xs, ys, zs);
                    if (!localSeen(me, vec, scaleSeenCheck)) continue;
                    vecs.add(vec);
                }
            }
        }
        return vecs;
    }

    public static List<Vec3d> entityBoxVec3dsAlternate(Box aabb) {
        if (aabb == null) return null;
        final List<Vec3d> vecs = new ArrayList<>();
        double offsetXYZ = .02F;
        int maxPointsCountXZ = 14, minPointsCountXZ = 5;
        int maxPointsCountY = 27, minPointsCountY = 9;
        aabb = aabb.contract(offsetXYZ);
        double[] whh = new double[]{aabb.maxX - aabb.minX, aabb.maxY - aabb.minY, (aabb.maxY - aabb.minY) / 1.05D};
        double[] xyz = new double[]{aabb.minX + whh[0] / 2.D, aabb.minY, aabb.minZ + whh[0] / 2.D};
        double[] xyz1 = new double[]{aabb.minX, aabb.minY, aabb.minZ};
        double[] xyz2 = new double[]{aabb.maxX, aabb.maxY, aabb.maxZ};
        float sqrtWHH0CubeD2 = (float) Math.sqrt(whh[0] * whh[0] + whh[0] * whh[0] + whh[0] * whh[0]) / 2.F;
        final ClientPlayerEntity me = mc.player;
        if (me == null) return null;
        Vec3d mePos = new Vec3d(me.getX(), me.getY(), me.getZ());
        final float factorCount = (1.F - Math.min((float) mePos.distanceTo(new Vec3d(xyz[0], xyz[1], xyz[2])) / 5.F, 1.F)) * Math.min((float) mePos.distanceTo(new Vec3d(xyz[0], me.getY(), xyz[2])) / .6F, 1.F);
        final int pointsCountXZ = lerp(minPointsCountXZ, maxPointsCountXZ, factorCount);
        final int pointsCountY = lerp(minPointsCountY, maxPointsCountY, factorCount);
        float scaleSeenCheck = .0F;
        double mePosX = me.getX(), mePosY = me.getY(), mePosZ = me.getZ();
        double eyeOffset = -me.getEyeHeight(EntityPose.STANDING);
        for (int xsI = 0; xsI < pointsCountXZ; xsI++) {
            final boolean edgeX = xsI == 0 || xsI == pointsCountXZ - 1;
            final double xs = lerp(xyz1[0], xyz2[0], xsI / (float) (pointsCountXZ - 1));
            for (int zsI = 0; zsI < pointsCountXZ; zsI++) {
                final boolean edgeZ = zsI == 0 || zsI == pointsCountXZ - 1;
                final double zs = lerp(xyz1[2], xyz2[2], zsI / (float) (pointsCountXZ - 1));
                for (int ysI = 0; ysI < pointsCountY; ysI++) {
                    final boolean edgeY = ysI == 0 || ysI == pointsCountY - 1;
                    final double ys = lerp(xyz1[1], xyz2[1], ysI / (float) (pointsCountY - 1));
                    if (!edgeX && !edgeZ && !edgeY) {
                        double dx = xs - mePosX, dy = (ys + eyeOffset) - mePosY, dz = zs - mePosZ;
                        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        if (dist >= sqrtWHH0CubeD2) continue;
                    }
                    final Vec3d vec = new Vec3d(xs, ys, zs);
                    if (!localSeen(me, vec, scaleSeenCheck)) continue;
                    vecs.add(vec);
                }
            }
        }
        return vecs;
    }

    private static double getDistanceAtVec3dToVec3d(Vec3d first, Vec3d second) {
        final double xDiff, yDiff, zDiff;
        return Math.sqrt((xDiff = first.x - second.x) * xDiff + (yDiff = first.y - second.y) * yDiff + (zDiff = first.z - second.z) * zDiff);
    }

    private static boolean rayTraceToBox(Vec3d start, Vec3d direction, double distance, Box box) {
        Vec3d end = start.add(direction.multiply(distance));
        return box.raycast(start, end).isPresent();
    }

    private static class TargetZone {
        final double xOffset, yOffset, zOffset;
        final float weight;
        TargetZone(double x, double y, double z, float weight) {
            this.xOffset = MathHelper.clamp(x, 0.0, 1.0);
            this.yOffset = MathHelper.clamp(y, 0.0, 1.0);
            this.zOffset = MathHelper.clamp(z, 0.0, 1.0);
            this.weight = weight;
        }
    }

    private static class WeightedPoint {
        final Vec3d offset;
        final float score;
        WeightedPoint(Vec3d offset, float score) {
            this.offset = offset;
            this.score = score;
        }
    }

    public static int clamp(int value, int min, int max) {
        return Math.min(max, Math.max(value, min));
    }

    public static float clamp(float value, float min, float max) {
        return Math.min(max, Math.max(value, min));
    }

    public static double clamp(double value, double min, double max) {
        return Math.min(max, Math.max(value, min));
    }

    public static int lerp(int a, int b, float f) {
        return a + (int) (f * (b - a));
    }

    public static float lerp(float a, float b, float f) {
        return a + f * (b - a);
    }

    @Generated
    private UBoxPoints() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}