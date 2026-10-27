package vesence.pathing.goal;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;

/**
 * Прийти в точку по X/Z на любой высоте — так работает {@code #goto 100 200}.
 * Игрок сам выберет удобный уровень (поверхность, пол пещеры, край обрыва).
 *
 * <p>Высоту можно ограничить окном. Это нужно для ослабления целей: «подойди к
 * этому блоку» без ограничения по высоте означает «окажись где угодно над ним»,
 * и цель на дне шахты оказывается выполненной, едва ты встал на её край.
 */
@Environment(EnvType.CLIENT)
public final class GoalXZ implements Goal {

    public final int x;
    public final int z;

    /** Границы окна по высоте включительно. */
    private final int minY;
    private final int maxY;

    public GoalXZ(int x, int z) {
        this(x, z, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    public GoalXZ(int x, int z, int minY, int maxY) {
        this.x = x;
        this.z = z;
        this.minY = minY;
        this.maxY = maxY;
    }

    /** Ограничена ли высота окном (иначе колонка «до неба»). */
    public boolean hasHeightWindow() {
        return this.minY != Integer.MIN_VALUE;
    }

    /** Нижняя граница окна по высоте включительно. */
    public int minY() {
        return this.minY;
    }

    /** Верхняя граница окна по высоте включительно. */
    public int maxY() {
        return this.maxY;
    }

    /**
     * Колонка с окном по высоте вокруг {@code y}.
     *
     * <p>Так ослабляется {@link GoalNear}: горизонталь можно простить, высоту —
     * нет. Иначе цель «спустись в шахту» считается достигнутой на поверхности,
     * потому что X и Z уже совпали.
     */
    public static GoalXZ column(int x, int z, int y, int tolerance) {
        int t = Math.max(0, tolerance);
        return new GoalXZ(x, z, y - t, y + t);
    }

    @Override
    public boolean isInGoal(int x, int y, int z) {
        return x == this.x && z == this.z && y >= this.minY && y <= this.maxY;
    }

    @Override
    public double heuristic(int x, int y, int z) {
        double dx = x - this.x;
        double dz = z - this.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        // Высоту тоже учитываем, иначе поиск считает цель достигнутой «по
        // горизонтали» и не старается спуститься или подняться.
        double vertical = 0.0;
        if (y < this.minY) {
            vertical = this.minY - y;
        } else if (y > this.maxY) {
            vertical = y - this.maxY;
        }
        return Math.sqrt(horizontal * horizontal + vertical * vertical);
    }

    @Override
    public BlockPos anchor() {
        return new BlockPos(this.x, this.minY == Integer.MIN_VALUE ? 64 : (this.minY + this.maxY) / 2, this.z);
    }

    @Override
    public String describe() {
        if (this.minY == Integer.MIN_VALUE) {
            return "GoalXZ[" + x + ", " + z + "]";
        }
        return "GoalXZ[" + x + ", " + z + " y=" + this.minY + ".." + this.maxY + "]";
    }
}
