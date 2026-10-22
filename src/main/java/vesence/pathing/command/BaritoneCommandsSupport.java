package vesence.pathing.command;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import vesence.pathing.VesenceBaritone;

/**
 * Мелкие общие действия команд Baritone.
 *
 * <p>Вынесено отдельно, чтобы каждая команда не повторяла одно и то же:
 * убедиться, что движок включён, и сообщить, куда идём.
 */
@Environment(EnvType.CLIENT)
public final class BaritoneCommandsSupport {

    private BaritoneCommandsSupport() {
    }

    /**
     * Включает движок, если он выключен.
     *
     * <p>Без этого команда молча ничего бы не делала: движок отключён — значит
     * тик не идёт, значит цель никто не обрабатывает. Игрок бы решил, что
     * команда сломана.
     */
    public static void enableEngine() {
        VesenceBaritone.get().setEnabled(true);
    }

    /** Сообщает, куда идём, и запускает путь. */
    public static void startPath(BaritoneCommandContext ctx, BlockPos target) {
        enableEngine();
        VesenceBaritone.get().setGoalAndPath(target);
        ctx.success("иду в " + target.getX() + " " + target.getY() + " " + target.getZ());
    }
}
