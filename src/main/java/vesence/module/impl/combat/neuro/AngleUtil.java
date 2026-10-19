package vesence.module.impl.combat.neuro;

import net.minecraft.util.Mth;

public class AngleUtil {
    public float meanX;
    public float meanY;
    public float meanZ;
    public int count;

    private static final float DEFAULT_MEAN_X = 0.5f;
    private static final float DEFAULT_MEAN_Y = 0.72f;
    private static final float DEFAULT_MEAN_Z = 0.5f;

    public AngleUtil(float x, float y, float z) {
        this.meanX = x;
        this.meanY = y;
        this.meanZ = z;
        this.count = 0;
    }

    public AngleUtil() {
        this(DEFAULT_MEAN_X, DEFAULT_MEAN_Y, DEFAULT_MEAN_Z);
    }

    public void addSample(float x, float y, float z) {
        x = Mth.clamp(x, 0.0f, 1.0f);
        y = Mth.clamp(y, 0.0f, 1.0f);
        z = Mth.clamp(z, 0.0f, 1.0f);
        
        this.count++;
        float weight = 1.0f / (float) this.count;
        this.meanX += (x - this.meanX) * weight;
        this.meanY += (y - this.meanY) * weight;
        this.meanZ += (z - this.meanZ) * weight;
    }

    public void resetIfInvalid(float defaultX, float defaultY, float defaultZ) {
        if (this.count < 0) {
            this.count = 0;
        }
        if (!Float.isFinite(this.meanX)) {
            this.meanX = defaultX;
        }
        if (!Float.isFinite(this.meanY)) {
            this.meanY = defaultY;
        }
        if (!Float.isFinite(this.meanZ)) {
            this.meanZ = defaultZ;
        }
        this.meanX = Mth.clamp(this.meanX, 0.0f, 1.0f);
        this.meanY = Mth.clamp(this.meanY, 0.0f, 1.0f);
        this.meanZ = Mth.clamp(this.meanZ, 0.0f, 1.0f);
    }
}
