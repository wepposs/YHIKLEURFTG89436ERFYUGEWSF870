package vesence.pathing.goal;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;

/**
 * Прийти ровно в указанный блок (по всем трём координатам).
 */
@Environment(EnvType.CLIENT)
public final class GoalBlock implements Goal {

    public final int x;
    public final int y;
    public final int z;

    public GoalBlock(BlockPos pos) {
        this(pos.getX(), pos.getY(), pos.getZ());
    }

    public GoalBlock(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public boolean isInGoal(int x, int y, int z) {
        return x == this.x && y == this.y && z == this.z;
    }

    @Override
    public double heuristic(int x, int y, int z) {
        double dx = x - this.x;
        double dy = y - this.y;
        double dz = z - this.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @Override
    public String describe() {
        return "GoalBlock[" + x + ", " + y + ", " + z + "]";
    }

    /** Мягче — окрестность в два блока. Точный блок часто занят или внутри стены. */
    @Override
    public Goal relaxed() {
        return new GoalNear(this.x, this.y, this.z, 2);
    }

    @Override
    public BlockPos anchor() {
        return new BlockPos(this.x, this.y, this.z);
    }
}
