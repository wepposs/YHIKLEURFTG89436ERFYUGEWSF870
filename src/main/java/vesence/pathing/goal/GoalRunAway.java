package vesence.pathing.goal;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * Убежать от набора точек минимум на {@code distance} блоков — «отойди от TNT»,
 * «спрячься от моба». Цель достигнута, когда мы достаточно далеко от КАЖДОЙ точки.
 */
@Environment(EnvType.CLIENT)
public final class GoalRunAway implements Goal {

    public final double distance;
    public final List<BlockPos> from;

    public GoalRunAway(double distance, List<BlockPos> from) {
        this.distance = distance;
        this.from = from;
    }

    @Override
    public boolean isInGoal(int x, int y, int z) {
        for (BlockPos p : this.from) {
            double dx = x - p.getX();
            double dy = y - p.getY();
            double dz = z - p.getZ();
            if (dx * dx + dy * dy + dz * dz < this.distance * this.distance) {
                return false;
            }
        }
        return true;
    }

    @Override
    public double heuristic(int x, int y, int z) {
        // Насколько ещё далеко до выхода из опасной зоны от самой близкой точки.
        double worst = 0.0;
        for (BlockPos p : this.from) {
            double dx = x - p.getX();
            double dy = y - p.getY();
            double dz = z - p.getZ();
            double remaining = this.distance - Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (remaining > worst) {
                worst = remaining;
            }
        }
        return worst;
    }

    @Override
    public String describe() {
        return "GoalRunAway[" + distance + " от " + from.size() + " точек]";
    }
}
