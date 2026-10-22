package vesence.pathing.command;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.List;

/**
 * {@code #help} — список всех команд Baritone.
 */
@Environment(EnvType.CLIENT)
public final class HelpCommand implements BaritoneCommand {

    @Override
    public String name() {
        return "help";
    }

    @Override
    public List<String> aliases() {
        return List.of("commands", "помощь", "?");
    }

    @Override
    public String usage() {
        return "#help";
    }

    @Override
    public String description() {
        return "список команд";
    }

    @Override
    public void execute(BaritoneCommandContext ctx, String args) {
        ctx.info("команды (префикс #):");
        for (BaritoneCommand command : BaritoneCommandRegistry.getInstance().all()) {
            // Показываем именно основное имя: синонимы нужны для привычки, но в
            // списке от них только рябит в глазах.
            ctx.info("#" + command.name() + " — " + command.description());
        }
    }
}
