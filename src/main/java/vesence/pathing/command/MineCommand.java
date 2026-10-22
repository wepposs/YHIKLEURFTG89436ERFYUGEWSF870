package vesence.pathing.command;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.Block;
import vesence.pathing.VesenceBaritone;
import vesence.pathing.process.MineProcess;

import java.util.List;

/**
 * {@code #mine <блок> [радиус]} — добыть указанный блок.
 */
@Environment(EnvType.CLIENT)
public final class MineCommand implements BaritoneCommand {

    @Override
    public String name() {
        return "mine";
    }

    @Override
    public List<String> aliases() {
        return List.of("копай", "добыть");
    }

    @Override
    public String usage() {
        return "#mine <блок> [радиус]   например: #mine diamond_ore 32";
    }

    @Override
    public String description() {
        return "добыть блок: iron_ore, diamond_ore, oak_log и так далее";
    }

    @Override
    public void execute(BaritoneCommandContext ctx, String args) throws BaritoneCommandException {
        Minecraft mc = ctx.client();
        if (mc.player == null || mc.level == null) {
            throw new BaritoneCommandException("нужно быть в мире");
        }

        String[] parts = ctx.parts();
        if (parts.length == 0) {
            throw new BaritoneCommandException("не указан блок");
        }

        Block block = MineProcess.resolveBlock(parts[0]);
        if (block == null) {
            throw new BaritoneCommandException("нет такого блока: " + parts[0]);
        }

        int radius = 32;
        if (parts.length >= 2) {
            try {
                radius = Math.abs(Integer.parseInt(parts[1]));
            } catch (NumberFormatException e) {
                throw new BaritoneCommandException("радиус должен быть числом: #mine iron_ore 32");
            }
        }
        radius = Math.max(2, Math.min(48, radius));

        BaritoneCommandsSupport.enableEngine();
        VesenceBaritone.get().setProcess(new MineProcess(block, parts[0], radius));
        ctx.success("добываю " + parts[0] + " в радиусе " + radius);
    }
}
