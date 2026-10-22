package vesence.pathing.command;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.pathing.VesenceBaritone;

import java.util.List;

/**
 * {@code #set} — настройки Baritone из чата.
 *
 * <p>То же, что галочки в меню модуля, но без открытия меню. Полезно, когда
 * нужно поменять одну настройку посреди пути.
 */
@Environment(EnvType.CLIENT)
public final class SetCommand implements BaritoneCommand {

    @Override
    public String name() {
        return "set";
    }

    @Override
    public List<String> aliases() {
        return List.of("setting", "настройка");
    }

    @Override
    public String usage() {
        return "#set <sprint|fall|showpath> <значение>";
    }

    @Override
    public String description() {
        return "изменить настройку: sprint, fall, showpath";
    }

    @Override
    public void execute(BaritoneCommandContext ctx, String args) throws BaritoneCommandException {
        String[] parts = ctx.parts();
        if (parts.length < 2) {
            throw new BaritoneCommandException("нужно имя настройки и значение");
        }

        String key = parts[0].toLowerCase();
        String value = parts[1].toLowerCase();
        VesenceBaritone engine = VesenceBaritone.get();

        switch (key) {
            case "sprint" -> {
                boolean on = parseBool(value);
                engine.setAllowSprint(on);
                ctx.success("спринт: " + (on ? "вкл" : "выкл"));
            }
            case "fall", "maxfall" -> {
                int max = parseInt(value);
                engine.setMaxFall(max);
                ctx.success("макс. падение: " + engine.getMaxFall());
            }
            case "showpath", "path" -> {
                boolean on = parseBool(value);
                engine.setShowPath(on);
                ctx.success("линия пути: " + (on ? "вкл" : "выкл"));
            }
            default -> throw new BaritoneCommandException("нет такой настройки: " + key);
        }
    }

    private static boolean parseBool(String value) throws BaritoneCommandException {
        return switch (value) {
            case "on", "true", "1", "вкл", "да" -> true;
            case "off", "false", "0", "выкл", "нет" -> false;
            default -> throw new BaritoneCommandException("нужно вкл/выкл: " + value);
        };
    }

    private static int parseInt(String value) throws BaritoneCommandException {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new BaritoneCommandException("нужно число: " + value);
        }
    }
}
