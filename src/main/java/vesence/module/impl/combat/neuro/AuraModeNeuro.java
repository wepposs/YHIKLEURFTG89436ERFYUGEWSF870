package vesence.module.impl.combat.neuro;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;
import vesence.module.impl.combat.AttackAura;

import java.util.ArrayList;
import java.util.PriorityQueue;
import java.util.concurrent.ThreadLocalRandom;

public class AuraModeNeuro {
    private static final Minecraft mc = Minecraft.getInstance();
    private static final float MIN_DISTANCE_SQ = 1.0E-6f;
    private static final double RAY_EXPAND = 0.05;
    private static final double ROTATION_POINT_REACH_BONUS = 7.5;
    private static final double ROTATION_POINT_EXPAND = 0.36;
    
    private int lastTargetId = Integer.MIN_VALUE;
    private long lastSaveTime;
    private int lastSampleCount = -1;
    private boolean rotationInitialized;
    private float lastYaw;
    private float lastPitch;
    private float prevYawStep;
    private float prevPitchStep;
    private float smoothedYawStep;
    private float smoothedPitchStep;
    private boolean hasTargetRotation;
    private float targetYaw;
    private float targetPitch;

    public void onActivate() {
        reset();
    }

    public void onDeactivate() {
        NeuroProfileStorage.save();
        reset();
    }

    public void reset() {
        this.rotationInitialized = false;
        this.lastTargetId = Integer.MIN_VALUE;
        this.prevYawStep = 0.0f;
        this.prevPitchStep = 0.0f;
        this.smoothedYawStep = 0.0f;
        this.smoothedPitchStep = 0.0f;
        this.hasTargetRotation = false;
    }

    public void updateRotation(AttackAura aura, LivingEntity target) {
        if (mc.player == null || target == null) {
            reset();
            return;
        }

        // Check for reset
        if (aura.neuroReset.get()) {
            NeuroProfileStorage.reset();
            aura.neuroReset.set(false);
            this.lastSampleCount = -1;
            this.lastSaveTime = 0L;
            reset();
        }

        NeuroSampler profile = NeuroProfileStorage.getProfile();
        profile.setMaxSamples(aura.neuroMaxSamples.get().intValue());

        // Reset tracking when target changes
        if (this.lastTargetId != target.getId()) {
            this.lastTargetId = target.getId();
            this.rotationInitialized = false;
            this.prevYawStep = 0.0f;
            this.prevPitchStep = 0.0f;
            this.smoothedYawStep = 0.0f;
            this.smoothedPitchStep = 0.0f;
        }

        if (!this.rotationInitialized) {
            this.lastYaw = mc.player.getYRot();
            this.lastPitch = mc.player.getXRot();
            this.rotationInitialized = true;
        }

        // Record rotation point during training
        recordRotationPoint(profile, aura, target);

        if (!profile.isTrainingComplete()) {
            // Training mode: record player's manual rotations
            recordTrainingSample(profile, aura, target);
            autoSave(profile);
            this.lastYaw = mc.player.getYRot();
            this.lastPitch = mc.player.getXRot();
            return;
        }

        // Prediction mode: use trained model
        applyNeuroRotation(profile, aura, target);
        this.lastYaw = mc.player.getYRot();
        this.lastPitch = mc.player.getXRot();
    }

    public void onAttack(LivingEntity target) {
        if (mc.player == null || target == null) {
            return;
        }

        NeuroSampler profile = NeuroProfileStorage.getProfile();
        if (profile.isTrainingComplete() || !AttackAura.neuroTrainHitPoint.get()) {
            return;
        }

        // Record hit point
        NeuroPoint hitPoint = calculateHitPoint(target, mc.player.getYRot(), 
            mc.player.getXRot(), AttackAura.attackRange.get() + AttackAura.preRange.get(), RAY_EXPAND);
        
        if (hitPoint != null) {
            profile.hitPoint.addSample(hitPoint.x, hitPoint.y, hitPoint.z);
            autoSave(profile);
        }
    }

    public boolean isReadyToAttack(AttackAura aura, LivingEntity target) {
        if (mc.player == null || target == null) {
            return false;
        }

        NeuroSampler profile = NeuroProfileStorage.getProfile();
        
        // During training, always allow attacking (player aims manually)
        if (!profile.isTrainingComplete()) {
            return true;
        }

        // During prediction, check if we're aimed at target
        double reach = aura.attackRange.get() + aura.preRange.get();
        return canHitTarget(mc.player.getYRot(), mc.player.getXRot(), reach, target);
    }

    private void recordRotationPoint(NeuroSampler profile, AttackAura aura, LivingEntity target) {
        if (profile.isTrainingComplete() || !AttackAura.neuroTrainRotationPoint.get()) {
            return;
        }

        double reach = aura.attackRange.get() + aura.preRange.get() + ROTATION_POINT_REACH_BONUS;
        NeuroPoint point = calculateHitPoint(target, mc.player.getYRot(), 
            mc.player.getXRot(), reach, ROTATION_POINT_EXPAND);
        
        if (point != null) {
            profile.rotationPoint.addSample(point.x, point.y, point.z);
        }
    }

    private void recordTrainingSample(NeuroSampler profile, AttackAura aura, LivingEntity target) {
        if (!(AttackAura.neuroTrainYawSpeed.get() || 
              AttackAura.neuroTrainPitchSpeed.get() || 
              AttackAura.neuroTrainBehavior.get())) {
            return;
        }

        Vec3 targetPos = getTargetPosition(target, profile, true);
        Vector2f targetRot = RotationUtil.getRotationTo(targetPos);
        if (targetRot == null) {
            return;
        }

        float currentYaw = mc.player.getYRot();
        float currentPitch = mc.player.getXRot();
        
        float actualYawStep = Mth.wrapDegrees(currentYaw - this.lastYaw);
        float actualPitchStep = currentPitch - this.lastPitch;
        
        float targetYawError = Mth.wrapDegrees(targetRot.x - this.lastYaw);
        float targetPitchError = targetRot.y - this.lastPitch;
        
        float mouseSens = RotationUtil.getMouseSensitivity();
        
        boolean playerMoved = Math.abs(actualYawStep) >= mouseSens * 0.45f || 
                             Math.abs(actualPitchStep) >= mouseSens * 0.45f;
        boolean targetInRange = Math.abs(targetYawError) >= mouseSens * 0.75f || 
                               Math.abs(targetPitchError) >= mouseSens * 0.75f;
        
        if (!playerMoved || !targetInRange) {
            return;
        }

        InterpolationUtil behavior = profile.behavior;
        EasingUtil sample = new EasingUtil();
        sample.distance = (float) mc.player.getEyePosition().distanceTo(targetPos);
        sample.yawError = Math.abs(targetYawError);
        sample.pitchError = Math.abs(targetPitchError);
        sample.targetSpeed = Mth.clamp((float) target.getDeltaMovement().length() * 20.0f, 0.0f, 50.0f);
        
        sample.yawGain = AttackAura.neuroTrainYawSpeed.get() ? 
            Mth.clamp(Math.abs(actualYawStep) / Math.max(Math.abs(targetYawError), mouseSens), 0.02f, 1.35f) :
            behavior.yawResponse;
            
        sample.pitchGain = AttackAura.neuroTrainPitchSpeed.get() ?
            Mth.clamp(Math.abs(actualPitchStep) / Math.max(Math.abs(targetPitchError), mouseSens), 0.02f, 1.2f) :
            behavior.pitchResponse;
            
        sample.yawStepAbs = Math.abs(actualYawStep);
        sample.pitchStepAbs = Math.abs(actualPitchStep);
        
        double reach = aura.attackRange.get() + aura.preRange.get();
        sample.rayAligned = canHitTarget(this.lastYaw, this.lastPitch, reach, target);
        
        if (isDuplicateSample(profile, sample)) {
            return;
        }

        if (profile.addSample(sample) && AttackAura.neuroTrainBehavior.get()) {
            behavior.addSample(targetYawError, targetPitchError, actualYawStep, actualPitchStep,
                this.prevYawStep, this.prevPitchStep, mouseSens);
        }

        this.prevYawStep = actualYawStep;
        this.prevPitchStep = actualPitchStep;
    }

    private boolean isDuplicateSample(NeuroSampler profile, EasingUtil sample) {
        ArrayList<EasingUtil> samples = profile.samples;
        int size = samples.size();
        if (size == 0) return false;

        int checkCount = Math.min(8, size);
        for (int i = size - 1; i >= size - checkCount; i--) {
            EasingUtil existing = samples.get(i);
            if (existing == null || existing.rayAligned != sample.rayAligned) continue;
            
            if (Math.abs(existing.yawError - sample.yawError) <= 0.35f &&
                Math.abs(existing.pitchError - sample.pitchError) <= 0.3f &&
                Math.abs(existing.distance - sample.distance) <= 0.2f &&
                Math.abs(existing.yawGain - sample.yawGain) <= 0.04f &&
                Math.abs(existing.pitchGain - sample.pitchGain) <= 0.04f) {
                return true;
            }
        }
        return false;
    }

    private void applyNeuroRotation(NeuroSampler profile, AttackAura aura, LivingEntity target) {
        Vec3 targetPos = getTargetPosition(target, profile, true);
        Vector2f targetRot = RotationUtil.getRotationTo(targetPos);
        if (targetRot == null) {
            return;
        }

        this.targetYaw = targetRot.x;
        this.targetPitch = targetRot.y;
        this.hasTargetRotation = true;

        float currentYaw = mc.player.getYRot();
        float currentPitch = mc.player.getXRot();
        
        float yawError = Mth.wrapDegrees(targetRot.x - currentYaw);
        float pitchError = targetRot.y - currentPitch;
        
        float mouseSens = RotationUtil.getMouseSensitivity();
        
        if (Math.abs(yawError) < mouseSens * 0.5f && Math.abs(pitchError) < mouseSens * 0.5f) {
            return;
        }

        double reach = aura.attackRange.get() + aura.preRange.get();
        boolean rayAligned = canHitTarget(currentYaw, currentPitch, reach, target);
        
        PredictedStep step = predictStep(profile, Math.abs(yawError), Math.abs(pitchError),
            (float) mc.player.getEyePosition().distanceTo(targetPos),
            Mth.clamp((float) target.getDeltaMovement().length() * 20.0f, 0.0f, 50.0f),
            rayAligned);

        InterpolationUtil behavior = profile.behavior;
        
        float yawStep = Mth.lerp(0.45f, Math.abs(yawError) * step.yawGain, step.yawStep);
        float pitchStep = Mth.lerp(0.45f, Math.abs(pitchError) * step.pitchGain, step.pitchStep);

        // Add micro-pauses
        if (Math.abs(yawError) < 6.5f && ThreadLocalRandom.current().nextFloat() < behavior.holdChance) {
            yawStep *= ThreadLocalRandom.current().nextFloat(0.08f, 0.35f);
        }
        if (Math.abs(pitchError) < 5.0f && ThreadLocalRandom.current().nextFloat() < behavior.holdChance * 0.85f) {
            pitchStep *= ThreadLocalRandom.current().nextFloat(0.08f, 0.3f);
        }

        // Add jitter
        float yawJitter = (ThreadLocalRandom.current().nextFloat() - 0.5f) * 2.0f * behavior.yawJitter * 
            (rayAligned ? 0.22f : 0.46f);
        float pitchJitter = (ThreadLocalRandom.current().nextFloat() - 0.5f) * 2.0f * behavior.pitchJitter * 
            (rayAligned ? 0.18f : 0.4f);

        float finalYawStep = Math.copySign(yawStep + yawJitter, yawError);
        float finalPitchStep = Math.copySign(pitchStep + pitchJitter, pitchError);

        // Apply smoothing
        float smoothness = Mth.clamp(behavior.smoothness, 0.1f, 0.92f);
        float smoothedYaw = Mth.lerp(smoothness, finalYawStep, this.smoothedYawStep);
        float smoothedPitch = Mth.lerp(smoothness, finalPitchStep, this.smoothedPitchStep);

        // Clamp to error bounds
        float maxYawStep = Math.abs(yawError) + Math.max(mouseSens, behavior.yawJitter * 0.3f);
        float maxPitchStep = Math.abs(pitchError) + Math.max(mouseSens, behavior.pitchJitter * 0.25f);
        
        smoothedYaw = Mth.clamp(smoothedYaw, -maxYawStep, maxYawStep);
        smoothedPitch = Mth.clamp(smoothedPitch, -maxPitchStep, maxPitchStep);

        // Snap to grid
        smoothedYaw = snapToMouseGrid(smoothedYaw, yawError, mouseSens);
        smoothedPitch = snapToMouseGrid(smoothedPitch, pitchError, mouseSens);

        this.smoothedYawStep = smoothedYaw;
        this.smoothedPitchStep = smoothedPitch;

        float newYaw = currentYaw + smoothedYaw;
        float newPitch = Mth.clamp(currentPitch + smoothedPitch, -89.9f, 89.9f);

        mc.player.setYRot(newYaw);
        mc.player.setXRot(newPitch);
    }

    private float snapToMouseGrid(float step, float error, float mouseSens) {
        if (Math.abs(error) <= mouseSens) {
            return error;
        }
        float gridStep = Math.max(mouseSens, (float) Math.round(Math.abs(step) / mouseSens) * mouseSens);
        if (gridStep > Math.abs(error) + mouseSens * 2.0f) {
            gridStep = Math.abs(error) + mouseSens * 2.0f;
        }
        return Math.copySign(gridStep, error);
    }

    private PredictedStep predictStep(NeuroSampler profile, float yawError, float pitchError,
                                     float distance, float targetSpeed, boolean rayAligned) {
        ArrayList<EasingUtil> samples = profile.samples;
        int size = samples.size();
        int stride = Math.max(1, size / 2400);
        
        PriorityQueue<double[]> nearest = new PriorityQueue<>(25, (a, b) -> Double.compare(a[0], b[0]));
        
        for (int i = 0; i < size; i += stride) {
            EasingUtil sample = samples.get(i);
            if (sample == null || !sample.isValid()) continue;
            
            double dist = Math.abs(sample.yawError - yawError) * 0.22 +
                         Math.abs(sample.pitchError - pitchError) * 0.28 +
                         Math.abs(sample.distance - distance) * 0.8 +
                         Math.abs(sample.targetSpeed - targetSpeed) * 0.08 +
                         (sample.rayAligned == rayAligned ? 0.0 : 1.25);
            
            double weight = 1.0 / (1.0 + dist * dist);
            if (weight < 0.015) continue;
            
            if (nearest.size() < 24) {
                nearest.offer(new double[]{weight, sample.yawGain, sample.pitchGain, sample.yawStepAbs, sample.pitchStepAbs});
            } else if (weight > nearest.peek()[0]) {
                nearest.poll();
                nearest.offer(new double[]{weight, sample.yawGain, sample.pitchGain, sample.yawStepAbs, sample.pitchStepAbs});
            }
        }

        double totalWeight = 0.0;
        double yawGainSum = 0.0;
        double pitchGainSum = 0.0;
        double yawStepSum = 0.0;
        double pitchStepSum = 0.0;

        for (double[] entry : nearest) {
            double weight = entry[0];
            totalWeight += weight;
            yawGainSum += entry[1] * weight;
            pitchGainSum += entry[2] * weight;
            yawStepSum += entry[3] * weight;
            pitchStepSum += entry[4] * weight;
        }

        if (totalWeight <= 0.0) {
            InterpolationUtil behavior = profile.behavior;
            return new PredictedStep(
                behavior.yawResponse,
                behavior.pitchResponse,
                Math.max(0.18f, yawError * behavior.yawResponse),
                Math.max(0.1f, pitchError * behavior.pitchResponse)
            );
        }

        return new PredictedStep(
            (float) (yawGainSum / totalWeight),
            (float) (pitchGainSum / totalWeight),
            (float) (yawStepSum / totalWeight),
            (float) (pitchStepSum / totalWeight)
        );
    }

    private Vec3 getTargetPosition(LivingEntity target, NeuroSampler profile, boolean interpolate) {
        AABB box = target.getBoundingBox();
        Vec3 rotPoint = new Vec3(
            Mth.lerp(profile.rotationPoint.meanX, (float) box.minX, (float) box.maxX),
            Mth.lerp(profile.rotationPoint.meanY, (float) box.minY, (float) box.maxY),
            Mth.lerp(profile.rotationPoint.meanZ, (float) box.minZ, (float) box.maxZ)
        );

        if (!interpolate) {
            return rotPoint;
        }

        Vector2f rot = RotationUtil.getRotationTo(rotPoint);
        if (rot == null) {
            return rotPoint;
        }

        float totalError = Math.abs(Mth.wrapDegrees(rot.x - mc.player.getYRot())) + 
                          Math.abs(rot.y - mc.player.getXRot());
        float lerpFactor = Mth.clamp(1.0f - totalError / 14.0f, 0.0f, 0.65f);

        Vec3 hitPoint = new Vec3(
            Mth.lerp(profile.hitPoint.meanX, (float) box.minX, (float) box.maxX),
            Mth.lerp(profile.hitPoint.meanY, (float) box.minY, (float) box.maxY),
            Mth.lerp(profile.hitPoint.meanZ, (float) box.minZ, (float) box.maxZ)
        );

        return rotPoint.lerp(hitPoint, lerpFactor);
    }

    private NeuroPoint calculateHitPoint(LivingEntity target, float yaw, float pitch, double reach, double expand) {
        Vec3 eyePos = mc.player.getEyePosition(1.0f);
        Vec3 rotVec = net.minecraft.world.phys.Vec3.directionFromRotation(pitch, yaw);
        Vec3 rayEnd = eyePos.add(rotVec.scale(reach));
        
        AABB box = target.getBoundingBox();
        AABB expandedBox = box.inflate(expand);
        
        Vec3 hit = expandedBox.clip(eyePos, rayEnd).orElse(null);
        if (hit == null) {
            return null;
        }

        double clampedX = Mth.clamp(hit.x, box.minX, box.maxX);
        double clampedY = Mth.clamp(hit.y, box.minY, box.maxY);
        double clampedZ = Mth.clamp(hit.z, box.minZ, box.maxZ);

        float normX = (float) ((clampedX - box.minX) / Math.max(box.getXsize(), 1.0E-4));
        float normY = (float) ((clampedY - box.minY) / Math.max(box.getYsize(), 1.0E-4));
        float normZ = (float) ((clampedZ - box.minZ) / Math.max(box.getZsize(), 1.0E-4));

        return new NeuroPoint(normX, normY, normZ);
    }

    private boolean canHitTarget(float yaw, float pitch, double reach, Entity target) {
        Vec3 eyePos = mc.player.getEyePosition(1.0f);
        Vec3 rotVec = net.minecraft.world.phys.Vec3.directionFromRotation(pitch, yaw);
        Vec3 rayEnd = eyePos.add(rotVec.scale(reach));
        return target.getBoundingBox().clip(eyePos, rayEnd).isPresent();
    }

    private void autoSave(NeuroSampler profile) {
        int currentCount = profile.getSampleCount();
        long currentTime = System.currentTimeMillis();
        
        if (profile.isTrainingComplete()) {
            if (currentCount != this.lastSampleCount) {
                NeuroProfileStorage.save();
                this.lastSampleCount = currentCount;
                this.lastSaveTime = currentTime;
            }
            return;
        }

        if (currentCount == this.lastSampleCount) {
            return;
        }

        boolean milestone = currentCount / 900 > this.lastSampleCount / 900;
        boolean timeout = currentTime - this.lastSaveTime >= 10000L;
        boolean firstSample = this.lastSampleCount < 0 && currentCount > 0;

        if (firstSample || milestone || timeout) {
            NeuroProfileStorage.save();
            this.lastSampleCount = currentCount;
            this.lastSaveTime = currentTime;
        }
    }

    private static class PredictedStep {
        float yawGain;
        float pitchGain;
        float yawStep;
        float pitchStep;

        PredictedStep(float yawGain, float pitchGain, float yawStep, float pitchStep) {
            this.yawGain = yawGain;
            this.pitchGain = pitchGain;
            this.yawStep = yawStep;
            this.pitchStep = pitchStep;
        }
    }
}
