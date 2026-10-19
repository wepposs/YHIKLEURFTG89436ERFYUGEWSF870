package vesence.module.impl.combat.neuro;

import java.util.ArrayList;

public class NeuroSampler {
    private static final int DEFAULT_MAX_SAMPLES = 30000;
    private static final int HARD_CAP_SAMPLES = 100000;
    private static final int MIN_CAP_SAMPLES = 1000;

    public ArrayList<EasingUtil> samples = new ArrayList<>();
    public AngleUtil rotationPoint = new AngleUtil(0.5f, 0.72f, 0.5f);
    public AngleUtil hitPoint = new AngleUtil(0.5f, 0.68f, 0.5f);
    public InterpolationUtil behavior = new InterpolationUtil();
    public int targetMaxSamples = DEFAULT_MAX_SAMPLES;

    public void reset() {
        this.samples = new ArrayList<>();
        this.rotationPoint = new AngleUtil(0.5f, 0.72f, 0.5f);
        this.hitPoint = new AngleUtil(0.5f, 0.68f, 0.5f);
        this.behavior = new InterpolationUtil();
    }

    public boolean addSample(EasingUtil sample) {
        validate();
        if (sample == null || !sample.isValid() || isTrainingComplete()) {
            return false;
        }
        sample.clamp();
        this.samples.add(sample);
        return true;
    }

    public void validate() {
        if (this.samples == null) {
            this.samples = new ArrayList<>();
        }
        if (this.rotationPoint == null) {
            this.rotationPoint = new AngleUtil(0.5f, 0.72f, 0.5f);
        }
        if (this.hitPoint == null) {
            this.hitPoint = new AngleUtil(0.5f, 0.68f, 0.5f);
        }
        if (this.behavior == null) {
            this.behavior = new InterpolationUtil();
        }
    }

    public int getSampleCount() {
        validate();
        return Math.min(this.samples.size(), getMaxSamples());
    }

    public void cleanup() {
        validate();
        this.samples.removeIf(s -> s == null || !s.isValid());
        
        int max = getMaxSamples();
        if (this.samples.size() > max) {
            this.samples = new ArrayList<>(this.samples.subList(this.samples.size() - max, this.samples.size()));
        }
        
        this.samples.forEach(EasingUtil::clamp);
        this.rotationPoint.resetIfInvalid(0.5f, 0.72f, 0.5f);
        this.hitPoint.resetIfInvalid(0.5f, 0.68f, 0.5f);
        this.behavior.validate();
    }

    public int getMaxSamples() {
        if (this.targetMaxSamples < MIN_CAP_SAMPLES || this.targetMaxSamples > HARD_CAP_SAMPLES) {
            this.targetMaxSamples = DEFAULT_MAX_SAMPLES;
        }
        return this.targetMaxSamples;
    }

    public void setMaxSamples(int max) {
        if (max < MIN_CAP_SAMPLES) {
            max = MIN_CAP_SAMPLES;
        }
        if (max > HARD_CAP_SAMPLES) {
            max = HARD_CAP_SAMPLES;
        }
        this.targetMaxSamples = max;
    }

    public boolean isTrainingComplete() {
        validate();
        return this.samples.size() >= getMaxSamples();
    }
}
