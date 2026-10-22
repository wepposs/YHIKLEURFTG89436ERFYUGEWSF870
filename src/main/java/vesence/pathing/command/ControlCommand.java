package vesence.pathing.command;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.pathing.VesenceBaritone;

import java.util.List;

/**
 * Управление ходом: {@code #stop}, {@code #cancel}, {@code #pause},
 * {@code #resume}, {@code #status}.
 */
@Environment(EnvType.CLIENT)
public final class ControlCommand implements BaritoneCommand {

    @Override
    public String name() {
        return "stop";
    }

    @Override
    public List<String> aliases() {
        return List.of("cancel", "pause", "resume", "status", "стоп", "пауза");
    }

    @Override
    public String usage() {
        return "#stop | #pause | #resume | #status";
    }

    @Override
    public String description() {
        return "остановить, приостановить, продолжить или показать состояние";
    }

    @Override
    public void execute(BaritoneCommandContext ctx, String args) {
        VesenceBaritone engine = VesenceBaritone.get();
        String invoked = GoalCommand.invokedName(ctx);

        switch (invoked) {
            case "pause", "пауза" -> {
                engine.setPaused(true);
                ctx.success("пауза, цель сохранена");
            }
            case "resume" -> {
                engine.setPaused(false);
                ctx.success("продолжаю");
            }
            case "status" -> this.status(ctx, engine);
            default -> {
                // stop и cancel — одно и то же: разница в Baritone историческая,
                // и держать два разных поведения значит путать игрока.
                engine.cancelEverything();
                ctx.success("остановился");
            }
        }
    }

    private void status(BaritoneCommandContext ctx, VesenceBaritone engine) {
        if (engine.process() != null) {
            ctx.info("процесс: " + engine.process().name());
        }
        if (engine.isPaused()) {
            ctx.info("на паузе");
        }
        if (!engine.isPathing()) {
            ctx.info("цели нет");
            return;
        }
        String goal = engine.goal() == null ? "?" : engine.goal().describe();
        ctx.info("цель: " + goal);
        if (engine.currentPath() != null) {
            int left = engine.currentPath().remaining().size();
            ctx.info("осталось узлов: " + left);
        }
    }
}
