package vesence.module.impl.combat.neuro;

import net.minecraft.util.Mth;

public class InterpolationUtil {
    public int count;
    public float yawResponse = 0.28f;
    public float pitchResponse = 0.22f;
    public float yawJitter = 0.18f;
    public float pitchJitter = 0.1f;
    public float smoothness = 0.48f;
    public float holdChance = 0.08f;

    private static final float DEFAULT_YAW_RESPONSE = 0.28f;
    private static final float DEFAULT_PITCH_RESPONSE = 0.22f;
    private static final float DEFAULT_YAW_JITTER = 0.18f;
    private static final float DEFAULT_PITCH_JITTER = 0.1f;
    private static final float DEFAULT_SMOOTHNESS = 0.48f;
    private static final float DEFAULT_HOLD_CHANCE = 0.08f;

    private static final float MIN_RESPONSE = 0.02f;
    private static final float MAX_YAW_RESPONSE = 1.25f;
    private static final float MAX_PITCH_RESPONSE = 1.1f;
    private static final float MIN_JITTER = 0.01f;
    private static final float MAX_YAW_JITTER = 4.0f;
    private static final float MAX_PITCH_JITTER = 2.7f;
    private static final float MIN_SMOOTHNESS = 0.1f;
    private static final float MAX_SMOOTHNESS = 0.92f;
    private static final float MAX_HOLD_CHANCE = 0.4f;

    public void validate() {
        if (this.count < 0) {
            this.count = 0;
        }
        if (!Float.isFinite(this.yawResponse)) {
            this.yawResponse = DEFAULT_YAW_RESPONSE;
        }
        if (!Float.isFinite(this.pitchResponse)) {
            this.pitchResponse = DEFAULT_PITCH_RESPONSE;
        }
        if (!Float.isFinite(this.yawJitter)) {
            this.yawJitter = DEFAULT_YAW_JITTER;
        }
        if (!Float.isFinite(this.pitchJitter)) {
            this.pitchJitter = DEFAULT_PITCH_JITTER;
        }
        if (!Float.isFinite(this.smoothness)) {
            this.smoothness = DEFAULT_SMOOTHNESS;
        }
        if (!Float.isFinite(this.holdChance)) {
            this.holdChance = DEFAULT_HOLD_CHANCE;
        }

        this.yawResponse = Mth.clamp(this.yawResponse, MIN_RESPONSE, MAX_YAW_RESPONSE);
        this.pitchResponse = Mth.clamp(this.pitchResponse, MIN_RESPONSE, MAX_PITCH_RESPONSE);
        this.yawJitter = Mth.clamp(this.yawJitter, MIN_JITTER, MAX_YAW_JITTER);
        this.pitchJitter = Mth.clamp(this.pitchJitter, MIN_JITTER, MAX_PITCH_JITTER);
        this.smoothness = Mth.clamp(this.smoothness, MIN_SMOOTHNESS, MAX_SMOOTHNESS);
        this.holdChance = Mth.clamp(this.holdChance, 0.0f, MAX_HOLD_CHANCE);
    }

    public void addSample(float targetYawError, float targetPitchError, float actualYawStep, float actualPitchStep,
                         float prevYawStep, float prevPitchStep, float mouseSensitivity) {
        this.count++;

        float yawGain = Mth.clamp(
            Math.abs(actualYawStep) / Math.max(Math.abs(targetYawError), mouseSensitivity),
            0.02f, 1.25f
        );
        float pitchGain = Mth.clamp(
            Math.abs(actualPitchStep) / Math.max(Math.abs(targetPitchError), mouseSensitivity),
            0.02f, 1.1f
        );

        float yawJitterSample = Mth.clamp(Math.abs(actualYawStep - prevYawStep), 0.02f, 3.5f);
        float pitchJitterSample = Mth.clamp(Math.abs(actualPitchStep - prevPitchStep), 0.01f, 2.4f);

        float totalMovement = Math.abs(actualYawStep) + Math.abs(actualPitchStep) + 
                             Math.abs(prevYawStep) + Math.abs(prevPitchStep) + mouseSensitivity;
        float smoothnessSample = 1.0f - Mth.clamp(
            (Math.abs(actualYawStep - prevYawStep) + Math.abs(actualPitchStep - prevPitchStep)) / totalMovement,
            0.0f, 1.0f
        );

        boolean isHold = Math.abs(targetYawError) < 7.5f && Math.abs(targetPitchError) < 5.5f &&
                        Math.abs(actualYawStep) <= mouseSensitivity * 1.15f &&
                        Math.abs(actualPitchStep) <= mouseSensitivity * 1.15f;
        float holdSample = isHold ? 1.0f : 0.0f;

        float weight = 1.0f / (float) this.count;
        this.yawResponse += (yawGain - this.yawResponse) * weight;
        this.pitchResponse += (pitchGain - this.pitchResponse) * weight;
        this.yawJitter += (yawJitterSample - this.yawJitter) * weight;
        this.pitchJitter += (pitchJitterSample - this.pitchJitter) * weight;
        this.smoothness += (smoothnessSample - this.smoothness) * weight;
        this.holdChance += (holdSample - this.holdChance) * weight;

        this.validate();
    }
}
