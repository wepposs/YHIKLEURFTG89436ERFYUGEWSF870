package vesence.pathing.command;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import vesence.pathing.VesenceBaritone;
import vesence.pathing.process.FarmProcess;

import java.util.List;

/**
 * {@code #farm [радиус]} — собрать созревший урожай вокруг.
 */
@Environment(EnvType.CLIENT)
public final class FarmCommand implements BaritoneCommand {

    @Override
    public String name() {
        return "farm";
    }

    @Override
    public List<String> aliases() {
        return List.of("harvest", "урожай");
    }

    @Override
    public String usage() {
        return "#farm [радиус]";
    }

    @Override
    public String description() {
        return "собрать созревший урожай в радиусе";
    }

    @Override
    public void execute(BaritoneCommandContext ctx, String args) throws BaritoneCommandException {
        Minecraft mc = ctx.client();
        if (mc.player == null || mc.level == null) {
            throw new BaritoneCommandException("нужно быть в мире");
        }

        int radius = FarmProcess.DEFAULT_RADIUS;
        String[] parts = ctx.parts();
        if (parts.length >= 1) {
            try {
                radius = Math.abs(Integer.parseInt(parts[0]));
            } catch (NumberFormatException e) {
                throw new BaritoneCommandException("радиус должен быть числом: #farm 24");
            }
        }
        radius = Math.max(2, Math.min(48, radius));

        BaritoneCommandsSupport.enableEngine();
        VesenceBaritone.get().setProcess(new FarmProcess(radius));
        ctx.success("собираю урожай в радиусе " + radius);
    }
}
