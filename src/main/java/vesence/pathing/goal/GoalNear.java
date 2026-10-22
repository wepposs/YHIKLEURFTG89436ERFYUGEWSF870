package vesence.pathing.goal;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;

/**
 * Оказаться в пределах {@code radius} блоков от точки — «подойти к сундуку»,
 * «встать рядом с мобом». Радиус считается по центрам блоков.
 */
@Environment(EnvType.CLIENT)
public final class GoalNear implements Goal {

    public final int x;
    public final int y;
    public final int z;
    public final int radius;

    public GoalNear(BlockPos pos, int radius) {
        this(pos.getX(), pos.getY(), pos.getZ(), radius);
    }

    public GoalNear(int x, int y, int z, int radius) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = Math.max(0, radius);
    }

    @Override
    public boolean isInGoal(int x, int y, int z) {
        int dx = x - this.x;
        int dy = y - this.y;
        int dz = z - this.z;
        return dx * dx + dy * dy + dz * dz <= this.radius * this.radius;
    }

    @Override
    public double heuristic(int x, int y, int z) {
        double dx = x - this.x;
        double dy = y - this.y;
        double dz = z - this.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return Math.max(0.0, distance - this.radius);
    }

    @Override
    public String describe() {
        return "GoalNear[" + x + ", " + y + ", " + z + " r=" + radius + "]";
    }

    /**
     * Мягче — та же колонка, но с запасом по высоте.
     *
     * <p>Радиус считается по трём осям, и высота в нём участвует наравне с
     * остальными. Если земля у цели ушла на два блока вниз, ни один достижимый
     * блок в шар не попадает — и поиск возвращает неудачу при полностью
     * проходимом маршруте. Игроку же важен столб, а не конкретная высота.
     *
     * <p>Но высоту нельзя снимать совсем. Цель «спустись в шахту» без окна по
     * высоте выполняется сразу на поверхности — X и Z уже совпали, и бот
     * рапортует «пришёл», простояв на месте. Поэтому окно есть, просто щедрое.
     */
    @Override
    public Goal relaxed() {
        return GoalXZ.column(this.x, this.z, this.y, this.radius + 2);
    }

    @Override
    public BlockPos anchor() {
        return new BlockPos(this.x, this.y, this.z);
    }
}
