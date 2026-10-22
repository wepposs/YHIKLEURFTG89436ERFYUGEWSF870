package vesence.pathing.command;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import vesence.pathing.VesenceBaritone;
import vesence.pathing.process.ExploreProcess;

import java.util.List;

/**
 * {@code #explore} — идти в случайные стороны, пока не отменят.
 */
@Environment(EnvType.CLIENT)
public final class ExploreCommand implements BaritoneCommand {

    @Override
    public String name() {
        return "explore";
    }

    @Override
    public List<String> aliases() {
        return List.of("исследуй", "броди");
    }

    @Override
    public String usage() {
        return "#explore";
    }

    @Override
    public String description() {
        return "бродить по миру в случайных направлениях";
    }

    @Override
    public void execute(BaritoneCommandContext ctx, String args) throws BaritoneCommandException {
        Minecraft mc = ctx.client();
        if (mc.player == null || mc.level == null) {
            throw new BaritoneCommandException("нужно быть в мире");
        }

        BaritoneCommandsSupport.enableEngine();
        VesenceBaritone.get().setProcess(new ExploreProcess());
        ctx.success("исследую мир, #stop чтобы остановить");
    }
}
