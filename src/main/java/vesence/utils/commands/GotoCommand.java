package vesence.utils.commands;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import vesence.pathing.BaritoneMessages;
import vesence.pathing.VesenceBaritone;

import java.util.List;

/**
 * Команда управления путём: {@code .goto <x> <y> <z>}, {@code .goto stop}.
 *
 * <p>Работает и без включённого модуля — сама включает Baritone, когда просят
 * идти, потому что иначе команда выглядела бы сломанной.
 *
 * <p>Точка входа другая (точка, а не решётка), но это та же подсистема, поэтому
 * и сообщения те же — {@code [Baritone]}. Иначе одна и та же ходьба отвечала бы
 * игроку двумя разными голосами.
 */
@Environment(EnvType.CLIENT)
public final class GotoCommand implements Command {

    private static final GotoCommand INSTANCE = new GotoCommand();
    private static final List<String> ALIASES = List.of(".goto", ".g", ".walk", ".path");

    private GotoCommand() {
    }

    public static GotoCommand getInstance() {
        return INSTANCE;
    }

    @Override
    public String name() {
        return "Goto";
    }

    @Override
    public List<String> aliases() {
        return ALIASES;
    }

    @Override
    public String usage() {
        return ".goto <x> <y> <z> | .goto <x> <z> | .goto stop";
    }

    @Override
    public String description() {
        return "Идёт в точку как живой игрок";
    }

    @Override
    public void execute(CommandContext ctx, String args) throws CommandException {
        Minecraft mc = ctx.client();
        if (mc.player == null || mc.level == null) {
            throw new CommandException("Нужно быть в мире");
        }

        String raw = args == null ? "" : args.trim();
        if (raw.isEmpty()) {
            ctx.sendInfo("Использование: " + usage());
            return;
        }

        String lower = raw.toLowerCase();
        if (lower.equals("stop") || lower.equals("стоп")) {
            VesenceBaritone.get().cancelEverything();
            BaritoneMessages.success("остановился");
            return;
        }

        if (lower.equals("status") || lower.equals("статус")) {
            VesenceBaritone engine = VesenceBaritone.get();
            BaritoneMessages.info(engine.isPathing()
                    ? "иду к " + (engine.goal() == null ? "?" : engine.goal().describe())
                    : "цели нет");
            return;
        }

        String[] parts = raw.split("\\s+");
        BlockPos target;
        try {
            if (parts.length >= 3) {
                target = new BlockPos(
                        (int) Math.round(Double.parseDouble(parts[0])),
                        (int) Math.round(Double.parseDouble(parts[1])),
                        (int) Math.round(Double.parseDouble(parts[2])));
            } else if (parts.length == 2) {
                // Две координаты — X и Z, высоту подберёт поиск.
                target = new BlockPos(
                        (int) Math.round(Double.parseDouble(parts[0])),
                        mc.player.getBlockY(),
                        (int) Math.round(Double.parseDouble(parts[1])));
            } else {
                throw new CommandException("Нужно 2 или 3 координаты");
            }
        } catch (NumberFormatException e) {
            throw new CommandException("Не число: " + raw);
        }

        // Движок встроенный: включаем его напрямую, иначе команда молча ничего
        // не делала бы — тик идёт только у включённого движка.
        VesenceBaritone.get().setEnabled(true);

        VesenceBaritone.get().setGoalAndPath(
                new vesence.pathing.goal.GoalNear(target, 2));
        BaritoneMessages.success("иду в " + target.getX() + " " + target.getY() + " " + target.getZ());
    }
}
