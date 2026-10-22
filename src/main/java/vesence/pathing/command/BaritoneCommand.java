package vesence.pathing.command;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.List;

/**
 * Команда Baritone.
 *
 * <p>Имя команды указывается БЕЗ решётки: {@code farm}, а не {@code #farm}.
 * Решётка — это префикс ввода, а не часть имени, поэтому она добавляется
 * ровно в одном месте — в {@link BaritoneCommandRegistry}.
 */
@Environment(EnvType.CLIENT)
public interface BaritoneCommand {

    /** Основное имя без решётки. */
    String name();

    /** Синонимы без решётки. */
    List<String> aliases();

    /** Как пользоваться — печатается при неверном вводе. */
    String usage();

    /** Что делает — печатается в {@code #help}. */
    String description();

    void execute(BaritoneCommandContext ctx, String args) throws BaritoneCommandException;
}
