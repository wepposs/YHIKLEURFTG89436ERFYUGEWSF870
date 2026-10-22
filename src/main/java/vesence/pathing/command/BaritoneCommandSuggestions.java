package vesence.pathing.command;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.utils.commands.suggestions.CommandSuggestionProvider;
import vesence.utils.commands.suggestions.CommandSuggestions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Подсказки команд Baritone в поле чата.
 *
 * <p>Устроено так же, как подсказки клиентских команд с точкой: набираешь
 * решётку — и над строкой ввода появляется весь список с описаниями. Дальше
 * список сужается по мере ввода, а после имени команды показываются подсказки по
 * аргументам.
 *
 * <p>Список берётся из самого {@link BaritoneCommandRegistry}, а не из
 * захардкоженной таблицы. Это важно: новая команда появляется в подсказках сразу,
 * как только её зарегистрировали, и не может разойтись с {@code #help}.
 */
@Environment(EnvType.CLIENT)
public final class BaritoneCommandSuggestions implements CommandSuggestionProvider {

    private static final BaritoneCommandSuggestions INSTANCE = new BaritoneCommandSuggestions();

    /** Что можно подставить после конкретной команды. */
    private static final Map<String, List<String>> ARGUMENTS = new LinkedHashMap<>();

    static {
        ARGUMENTS.put("goto", List.of("<x> <y> <z>", "<x> <z>"));
        ARGUMENTS.put("goal", List.of("<x> <y> <z>", "<x> <z>"));
        ARGUMENTS.put("g", List.of("<x> <y> <z>", "<x> <z>"));
        ARGUMENTS.put("walk", List.of("<x> <y> <z>", "<x> <z>"));
        ARGUMENTS.put("path", List.of("<x> <y> <z>", "<x> <z>"));
        ARGUMENTS.put("thisway", List.of("<блоков>"));
        ARGUMENTS.put("come", List.of());
        ARGUMENTS.put("mine", List.of("diamond_ore", "iron_ore", "gold_ore", "coal_ore",
                "copper_ore", "redstone_ore", "lapis_ore", "emerald_ore", "ancient_debris",
                "oak_log", "birch_log", "stone"));
        ARGUMENTS.put("копай", List.of("diamond_ore", "iron_ore", "gold_ore", "coal_ore",
                "copper_ore", "redstone_ore", "lapis_ore", "emerald_ore", "ancient_debris",
                "oak_log", "birch_log", "stone"));
        ARGUMENTS.put("farm", List.of("16", "32", "48", "64"));
        ARGUMENTS.put("home", List.of("set", "clear"));
        ARGUMENTS.put("set", List.of("sprint", "fall", "showpath"));
        ARGUMENTS.put("proc", List.of("cancel"));
        ARGUMENTS.put("explore", List.of());
        ARGUMENTS.put("follow", List.of("<ник>"));
        ARGUMENTS.put("stop", List.of("pause", "resume", "status"));
        ARGUMENTS.put("help", List.of());
    }

    private BaritoneCommandSuggestions() {
    }

    public static BaritoneCommandSuggestions getInstance() {
        return INSTANCE;
    }

    @Override
    public List<String> aliases() {
        return List.of(String.valueOf(BaritoneCommandRegistry.PREFIX));
    }

    @Override
    public boolean supportsInput(String input) {
        return input != null && input.startsWith(String.valueOf(BaritoneCommandRegistry.PREFIX));
    }

    @Override
    public CommandSuggestions.SuggestionSet collect(String input) {
        if (!this.supportsInput(input)) {
            return null;
        }
        BaritoneCommandRegistry.getInstance().initialize();

        // После решётки ничего нет — показываем весь список.
        String body = input.substring(1);
        int space = body.indexOf(' ');
        if (space < 0) {
            return CommandSuggestions.of(this.commandEntries(body));
        }

        String name = body.substring(0, space);
        String rest = body.substring(space + 1);

        // Имя уже набрано целиком — подсказываем аргументы.
        if (!rest.isEmpty() && !rest.isBlank()) {
            return CommandSuggestions.of(List.of());
        }
        return CommandSuggestions.of(this.argumentEntries(name));
    }

    /**
     * Список команд, подходящих под начало ввода.
     *
     * <p>В подстановку идёт только остаток имени: решётка уже есть в поле ввода,
     * и вторая сделала бы {@code ##goto}. В самом пункте списка решётка есть —
     * так игрок видит, что это команда Baritone, а не клиентская.
     *
     * @param partial то, что уже набрано после решётки
     */
    private List<CommandSuggestions.SuggestionEntry> commandEntries(String partial) {
        String normalized = partial.toLowerCase(Locale.ROOT);
        List<CommandSuggestions.SuggestionEntry> entries = new ArrayList<>();

        for (BaritoneCommand command : BaritoneCommandRegistry.getInstance().all()) {
            String name = command.name();
            if (!normalized.isEmpty() && !name.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                continue;
            }

            // Подставляем остаток имени плюс пробел: команда почти всегда идёт с
            // аргументами, и лишний пробел игрок всё равно набрал бы сам.
            String completion = name.substring(normalized.length()) + " ";
            entries.add(new CommandSuggestions.SuggestionEntry(
                    String.valueOf(BaritoneCommandRegistry.PREFIX) + name,
                    command.description(),
                    completion,
                    false));
        }
        return entries;
    }

    /** Подсказки аргументов для уже выбранной команды. */
    private List<CommandSuggestions.SuggestionEntry> argumentEntries(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        List<String> hints = ARGUMENTS.get(key);
        if (hints == null || hints.isEmpty()) {
            return List.of();
        }
        List<CommandSuggestions.SuggestionEntry> entries = new ArrayList<>();
        for (String hint : hints) {
            // Угловые скобки означают «сюда надо вписать своё», подставлять их
            // в поле бессмысленно — поэтому такие подсказки только показываем.
            boolean placeholder = hint.startsWith("<");
            entries.add(new CommandSuggestions.SuggestionEntry(
                    hint,
                    placeholder ? "подставьте своё значение" : "",
                    placeholder ? null : hint,
                    false));
        }
        return entries;
    }

    @Override
    public List<CommandSuggestions.SuggestionEntry> collectAliasSuggestions(String input) {
        return List.of();
    }

    @Override
    public boolean matchesAliasPrefix(String input) {
        return this.supportsInput(input);
    }
}
