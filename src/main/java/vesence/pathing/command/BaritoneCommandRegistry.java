package vesence.pathing.command;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.pathing.BaritoneMessages;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Реестр команд Baritone и их разбор из чата.
 *
 * <p>Префикс ввода — решётка: {@code #farm}, {@code #goto 100 64 -200}. Решётка
 * выбрана не случайно: точкой уже заняты команды самого клиента
 * ({@code .config}, {@code .friend} и прочие), и смешивать два набора в одном
 * пространстве имён значило бы рано или поздно получить конфликт имён.
 *
 * <p>Неизвестная команда — не молчание и не отправка в общий чат сервера, а
 * понятный ответ {@code [Baritone] команда не найдена}. Молчание здесь особенно
 * вредно: игрок видит, что ввод «съеден», и не понимает, почему ничего не
 * произошло.
 */
@Environment(EnvType.CLIENT)
public final class BaritoneCommandRegistry {

    /** Префикс ввода. */
    public static final char PREFIX = '#';

    private static final BaritoneCommandRegistry INSTANCE = new BaritoneCommandRegistry();

    private final Map<String, BaritoneCommand> byName = new LinkedHashMap<>();

    private boolean initialized;

    private BaritoneCommandRegistry() {
    }

    public static BaritoneCommandRegistry getInstance() {
        return INSTANCE;
    }

    /** Регистрирует встроенные команды. Повторный вызов ничего не делает. */
    public synchronized void initialize() {
        if (this.initialized) {
            return;
        }
        this.initialized = true;

        this.register(new GoalCommand());
        this.register(new ControlCommand());
        this.register(new FarmCommand());
        this.register(new MineCommand());
        this.register(new FollowCommand());
        this.register(new ExploreCommand());
        this.register(new HomeCommand());
        this.register(new ProcCommand());
        this.register(new SetCommand());
        this.register(new HelpCommand());
    }

    private void register(BaritoneCommand command) {
        this.byName.put(command.name().toLowerCase(Locale.ROOT), command);
        for (String alias : command.aliases()) {
            this.byName.put(alias.toLowerCase(Locale.ROOT), command);
        }
    }

    /** Все команды без повторов — для {@code #help}. */
    public List<BaritoneCommand> all() {
        List<BaritoneCommand> unique = new ArrayList<>();
        for (BaritoneCommand command : this.byName.values()) {
            if (!unique.contains(command)) {
                unique.add(command);
            }
        }
        return unique;
    }

    /**
     * Начинается ли сообщение с решётки.
     *
     * <p>Проверяем именно решётку в начале: сообщения, где она встречается в
     * середине, — обычный чат и трогать их нельзя.
     */
    public boolean handles(String message) {
        if (message == null) {
            return false;
        }
        String trimmed = message.trim();
        return !trimmed.isEmpty() && trimmed.charAt(0) == PREFIX;
    }

    /**
     * Разбирает и выполняет сообщение с решёткой.
     *
     * @return {@code true} — сообщение обработано и в чат сервера уходить не должно
     */
    public boolean handle(String message) {
        if (!this.handles(message)) {
            return false;
        }
        this.initialize();

        String body = message.trim().substring(1).trim();
        if (body.isEmpty()) {
            BaritoneMessages.info("Введите " + PREFIX + "help — список команд");
            return true;
        }

        String name = body;
        String args = "";
        int space = body.indexOf(' ');
        if (space > 0) {
            name = body.substring(0, space);
            args = body.substring(space + 1).trim();
        }

        BaritoneCommand command = this.byName.get(name.toLowerCase(Locale.ROOT));
        if (command == null) {
            BaritoneMessages.error("команда не найдена");
            BaritoneMessages.info("Введите " + PREFIX + "help — список команд");
            return true;
        }

        BaritoneCommandContext ctx = new BaritoneCommandContext(message, args);
        try {
            command.execute(ctx, args);
        } catch (BaritoneCommandException e) {
            BaritoneMessages.error(e.getMessage());
            BaritoneMessages.info("Использование: " + command.usage());
        } catch (RuntimeException e) {
            // Команда не должна ронять игру: неизвестный блок, пустой инвентарь,
            // отсутствие цели — всё это ошибки ввода, а не повод для краша.
            BaritoneMessages.error("не удалось выполнить: " + e.getMessage());
        }
        return true;
    }
}
