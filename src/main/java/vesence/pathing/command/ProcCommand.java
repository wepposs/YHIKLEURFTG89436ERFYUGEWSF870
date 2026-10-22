package vesence.pathing.command;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.pathing.VesenceBaritone;

import java.util.List;

/**
 * {@code #proc} — что сейчас делает Baritone и как это остановить.
 *
 * <p>Нужна, когда бот чем-то занят, а чем именно — непонятно. Без неё
 * единственный способ узнать — догадаться по поведению.
 */
@Environment(EnvType.CLIENT)
public final class ProcCommand implements BaritoneCommand {

    @Override
    public String name() {
        return "proc";
    }

    @Override
    public List<String> aliases() {
        return List.of("process", "процесс");
    }

    @Override
    public String usage() {
        return "#proc | #proc cancel";
    }

    @Override
    public String description() {
        return "показать активный процесс или отменить его";
    }

    @Override
    public void execute(BaritoneCommandContext ctx, String args) {
        VesenceBaritone engine = VesenceBaritone.get();

        if (ctx.first().equals("cancel") || ctx.first().equals("отмена")) {
            engine.cancelEverything();
            ctx.success("процесс отменён");
            return;
        }

        if (engine.process() == null) {
            ctx.info("активного процесса нет");
        } else {
            ctx.info("процесс: " + engine.process().name());
        }

        if (engine.isPaused()) {
            ctx.info("на паузе");
        }
        if (engine.goal() != null) {
            ctx.info("цель: " + engine.goal().describe());
        }
    }
}
