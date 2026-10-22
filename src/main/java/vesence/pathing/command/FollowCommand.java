package vesence.pathing.command;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import vesence.pathing.VesenceBaritone;
import vesence.pathing.process.FollowProcess;

import java.util.List;

/**
 * {@code #follow <ник>} — идти за игроком.
 */
@Environment(EnvType.CLIENT)
public final class FollowCommand implements BaritoneCommand {

    @Override
    public String name() {
        return "follow";
    }

    @Override
    public List<String> aliases() {
        return List.of("идти_за", "за");
    }

    @Override
    public String usage() {
        return "#follow <ник>";
    }

    @Override
    public String description() {
        return "идти за игроком по нику";
    }

    @Override
    public void execute(BaritoneCommandContext ctx, String args) throws BaritoneCommandException {
        Minecraft mc = ctx.client();
        if (mc.player == null || mc.level == null) {
            throw new BaritoneCommandException("нужно быть в мире");
        }

        String[] parts = ctx.parts();
        if (parts.length == 0) {
            throw new BaritoneCommandException("не указан ник");
        }

        BaritoneCommandsSupport.enableEngine();
        VesenceBaritone.get().setProcess(new FollowProcess(parts[0]));
        ctx.success("иду за " + parts[0]);
    }
}
