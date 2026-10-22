package vesence.pathing;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Наблюдение за состоянием персонажа.
 *
 * <p>Здесь осталось только одно наблюдение — «упёрлись и не двигаемся». Всё
 * остальное, что тут жило, оказалось вредным: подскоки по таймеру давали рваный
 * ритм «бежит — прыгнул — бежит», а прыжок в Minecraft выгоден только в момент
 * приземления. Теперь прыжки держит исполнитель, а таймер не нужен вовсе.
 */
@Environment(EnvType.CLIENT)
public final class Humanizer {

    private double lastX;
    private double lastZ;
    private int blockedTicks;

    /** Один тик наблюдения. Вызывается до расчёта движения. */
    public void tick(boolean moving, boolean usingItem, boolean sprinting) {
        this.updateBlockedState(moving);
    }

    private void updateBlockedState(boolean moving) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        double dx = mc.player.getX() - this.lastX;
        double dz = mc.player.getZ() - this.lastZ;
        this.lastX = mc.player.getX();
        this.lastZ = mc.player.getZ();

        boolean barelyMoved = dx * dx + dz * dz < 0.0016;
        if (moving && barelyMoved) {
            this.blockedTicks++;
        } else {
            this.blockedTicks = 0;
        }
    }

    /** Упёрлись в препятствие и уже несколько тиков не двигаемся. */
    public boolean isBlocked() {
        return this.blockedTicks >= 2;
    }

    public int blockedTicks() {
        return this.blockedTicks;
    }

    /** Сброс при смене цели, чтобы старые наблюдения не тянулись в новый маршрут. */
    public void reset() {
        this.blockedTicks = 0;
    }
}
