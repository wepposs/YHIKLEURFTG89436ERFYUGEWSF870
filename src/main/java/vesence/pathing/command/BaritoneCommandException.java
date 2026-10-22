package vesence.pathing.command;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/** Ошибка выполнения команды Baritone: текст показывается игроку как есть. */
@Environment(EnvType.CLIENT)
public final class BaritoneCommandException extends Exception {

    public BaritoneCommandException(String message) {
        super(message);
    }
}
