package vesence.utils;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class TimerManager {
    private static float timerSpeed = 1.0F;

    public static void setTimer(float speed) {
        timerSpeed = speed;
    }

    public static float getTimer() {
        return timerSpeed;
    }
}
