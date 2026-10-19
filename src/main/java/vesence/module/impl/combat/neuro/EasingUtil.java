package vesence.module.impl.combat.neuro;

import net.minecraft.util.math.MathHelper;

public class EasingUtil {
    public boolean rayAligned;
    public float distance;
    public float yawError;
    public float pitchError;
    public float targetSpeed;
    public float yawGain;
    public float pitchGain;
    public float yawStepAbs;
    public float pitchStepAbs;

    private static final float MIN_DISTANCE = 0.5f;
    private static final float MAX_DISTANCE = 15.0f;
    private static final float MAX_YAW_ERROR = 180.0f;
    private static final float MAX_PITCH_ERROR = 90.0f;
    private static final float MAX_TARGET_SPEED = 50.0f;
    private static final float MIN_GAIN = 0.01f;
    private static final float MAX_YAW_GAIN = 1.5f;
    private static final float MAX_PITCH_GAIN = 1.3f;
    private static final float MAX_STEP = 90.0f;

    public void clamp() {
        this.distance = MathHelper.clamp(this.distance, MIN_DISTANCE, MAX_DISTANCE);
        this.yawError = MathHelper.clamp(this.yawError, 0.0f, MAX_YAW_ERROR);
        this.pitchError = MathHelper.clamp(this.pitchError, 0.0f, MAX_PITCH_ERROR);
        this.targetSpeed = MathHelper.clamp(this.targetSpeed, 0.0f, MAX_TARGET_SPEED);
        this.yawGain = MathHelper.clamp(this.yawGain, MIN_GAIN, MAX_YAW_GAIN);
        this.pitchGain = MathHelper.clamp(this.pitchGain, MIN_GAIN, MAX_PITCH_GAIN);
        this.yawStepAbs = MathHelper.clamp(this.yawStepAbs, 0.0f, MAX_STEP);
        this.pitchStepAbs = MathHelper.clamp(this.pitchStepAbs, 0.0f, MAX_STEP);
    }

    public boolean isValid() {
        return Float.isFinite(this.distance) && 
               Float.isFinite(this.yawError) && 
               Float.isFinite(this.pitchError) && 
               Float.isFinite(this.targetSpeed) && 
               Float.isFinite(this.yawGain) && 
               Float.isFinite(this.pitchGain) && 
               Float.isFinite(this.yawStepAbs) && 
               Float.isFinite(this.pitchStepAbs) && 
               this.distance > 0.0f && 
               this.yawError >= 0.0f && 
               this.pitchError >= 0.0f && 
               this.yawGain > 0.0f && 
               this.pitchGain > 0.0f && 
               this.yawStepAbs >= 0.0f && 
               this.pitchStepAbs >= 0.0f;
    }
}
