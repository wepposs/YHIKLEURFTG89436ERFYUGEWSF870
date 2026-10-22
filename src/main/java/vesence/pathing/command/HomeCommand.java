package vesence.pathing.command;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * {@code #home} — запомнить точку и возвращаться в неё.
 *
 * <p>Точка хранится в памяти клиента: после перезапуска игры её надо задать
 * заново. Это осознанно — постоянное хранилище здесь означало бы ещё один файл
 * конфигурации со своим форматом, своей миграцией и своими багами ради одной
 * пары координат.
 */
@Environment(EnvType.CLIENT)
public final class HomeCommand implements BaritoneCommand {

    /** Запомненная точка или {@code null}. */
    private static BlockPos home;

    @Override
    public String name() {
        return "home";
    }

    @Override
    public List<String> aliases() {
        return List.of("дом");
    }

    @Override
    public String usage() {
        return "#home | #home set | #home clear";
    }

    @Override
    public String description() {
        return "запомнить точку и возвращаться в неё";
    }

    @Override
    public void execute(BaritoneCommandContext ctx, String args) throws BaritoneCommandException {
        Minecraft mc = ctx.client();
        if (mc.player == null || mc.level == null) {
            throw new BaritoneCommandException("нужно быть в мире");
        }

        String sub = ctx.first();

        if (sub.equals("set") || sub.equals("запомнить")) {
            home = mc.player.blockPosition();
            ctx.success("дом запомнен: " + home.getX() + " " + home.getY() + " " + home.getZ());
            return;
        }

        if (sub.equals("clear") || sub.equals("сброс")) {
            home = null;
            ctx.success("дом забыт");
            return;
        }

        if (home == null) {
            throw new BaritoneCommandException("дом не задан, сначала #home set");
        }

        BaritoneCommandsSupport.startPath(ctx, home);
    }
}
