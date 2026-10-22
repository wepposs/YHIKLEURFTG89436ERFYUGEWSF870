package vesence.debug;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import vesence.pathing.command.BaritoneCommandRegistry;

import java.util.List;

/**
 * Автотест команд Baritone. Включается {@code VESENCE_CMDTEST=1}.
 *
 * <p>Проверяет то, что нельзя проверить глазами в логе иначе: что ввод с решёткой
 * перехватывается, что неизвестная команда отвечает «команда не найдена», что
 * каждая команда из списка действительно существует и что у неё есть описание.
 *
 * <p>Ответы команд уходят в чат, но {@code BaritoneMessages} дублирует их в
 * stdout — поэтому проверка сводится к чтению лога.
 */
@Environment(EnvType.CLIENT)
public final class CommandTest {

    private static final boolean ACTIVE = System.getenv("VESENCE_CMDTEST") != null;

    /** Сколько тиков ждём загрузки мира. */
    private static final int WARMUP_TICKS = 160;

    private static int ticks;
    private static boolean ready;
    private static boolean started;
    private static int phase;
    private static int phaseTicks;

    private CommandTest() {
    }

    public static void init() {
        if (!ACTIVE) {
            return;
        }
        ClientTickEvents.END_CLIENT_TICK.register(CommandTest::tick);
        System.out.println("[cmdtest] harness active");
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (!ready) {
            var feet = mc.player.blockPosition();
            if (!mc.level.hasChunk(feet.getX() >> 4, feet.getZ() >> 4)) {
                return;
            }
            ready = true;
        }
        if (ticks++ < WARMUP_TICKS) {
            return;
        }

        // Первая фаза — проверка разбора команд, она мгновенная.
        if (!started) {
            started = true;
            run(mc);
            // Вторая фаза — живая проверка процессов: команда должна не только
            // ответить, но и начать что-то делать. Без этого «работает» означало
            // бы только «печатает правильный текст».
            //
            // Берём dirt, а не руду: он гарантированно есть под ногами и в
            // пределах вытянутой руки. На руде проверялся бы не процесс, а
            // наличие руды в этом конкретном мире.
            System.out.println("[cmdtest] --- живая проверка процессов ---");
            BaritoneCommandRegistry.getInstance().handle("#farm 24");
            BaritoneCommandRegistry.getInstance().handle("#mine grass_block 12");
            return;
        }

        phaseTicks++;
        var engine = vesence.pathing.VesenceBaritone.get();
        if (phaseTicks % 20 == 0) {
            System.out.println("[cmdtest] live t=" + phaseTicks
                    + " процесс=" + (engine.process() == null ? "нет" : engine.process().name())
                    + " цель=" + (engine.goal() == null ? "нет" : engine.goal().describe())
                    + " путь=" + (engine.currentPath() == null ? "нет" : "есть")
                    + " pos=" + String.format("%.1f/%.1f/%.1f",
                    mc.player.getX(), mc.player.getY(), mc.player.getZ())
                    + " attack=" + mc.options.keyAttack.isDown()
                    + " hit=" + (mc.hitResult == null ? "null" : mc.hitResult.getType())
                    + (mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult bhr
                            ? " " + bhr.getBlockPos() : ""));
        }
        if (phaseTicks >= 300) {
            System.out.println("[cmdtest] live итог: процесс="
                    + (engine.process() == null ? "завершён" : engine.process().name()));
            System.out.println("[cmdtest] === DONE ===");
            ticks = Integer.MIN_VALUE;
        }
    }

    private static void run(Minecraft mc) {
        BaritoneCommandRegistry registry = BaritoneCommandRegistry.getInstance();
        registry.initialize();

        // 1. Список команд: каждая должна существовать и иметь описание.
        System.out.println("[cmdtest] --- команды ---");
        for (var command : registry.all()) {
            boolean ok = command.name() != null && !command.name().isBlank()
                    && command.description() != null && !command.description().isBlank()
                    && command.usage() != null && !command.usage().isBlank();
            System.out.println("[cmdtest] " + (ok ? "OK  " : "BAD ") + "#" + command.name()
                    + " aliases=" + command.aliases());
        }

        // 2. Перехват: решётка — наша, обычный текст — нет.
        System.out.println("[cmdtest] --- перехват ---");
        for (String probe : List.of("#help", "  #help", "#", "привет", "#привет")) {
            System.out.println("[cmdtest] handles(" + quote(probe) + ")=" + registry.handles(probe));
        }

        // 3. Неизвестная команда.
        System.out.println("[cmdtest] --- неизвестная ---");
        registry.handle("#popa");

        // 4. Помощь.
        System.out.println("[cmdtest] --- help ---");
        registry.handle("#help");

        // 5. Цель и управление.
        System.out.println("[cmdtest] --- goto/status/pause/resume/stop ---");
        var pos = mc.player.blockPosition();
        registry.handle("#goto " + (pos.getX() + 12) + " " + pos.getY() + " " + (pos.getZ() + 8));
        registry.handle("#status");
        registry.handle("#pause");
        registry.handle("#resume");
        registry.handle("#stop");

        // 6. Процессы: на пустом месте они должны честно сообщить, что работы нет.
        System.out.println("[cmdtest] --- farm ---");
        registry.handle("#farm 8");
        System.out.println("[cmdtest] --- mine ---");
        registry.handle("#mine diamond_ore 16");
        System.out.println("[cmdtest] --- follow ---");
        registry.handle("#follow нет_такого");

        // 7. Ошибки ввода не должны ронять игру.
        System.out.println("[cmdtest] --- ошибки ввода ---");
        registry.handle("#goto abc");
        registry.handle("#mine нетакого_блока");
        registry.handle("#farm abc");

        // 8. Подсказки в чате.
        //
        // Проверяем именно то, что увидит игрок: набрал решётку — есть список;
        // набрал начало имени — список сузился; набрал имя и пробел — есть
        // подсказки по аргументам. Без этого «подсказки добавлены» означало бы
        // только «класс существует».
        System.out.println("[cmdtest] --- подсказки ---");
        var suggestions = vesence.pathing.command.BaritoneCommandSuggestions.getInstance();
        for (String probe : List.of("#", "#g", "#go", "#goto ", "#mine ", "#help", "#zzz")) {
            var set = suggestions.collect(probe);
            int count = set == null ? 0 : set.entries().size();
            System.out.println("[cmdtest] suggest(" + quote(probe) + ")=" + count
                    + (count == 0 ? "" : " первая=" + set.entries().get(0).primaryText()
                    + " / " + set.entries().get(0).description()
                    + " / суффикс=" + set.entries().get(0).completionSuffix()));
        }
        System.out.println("[cmdtest] supports(\"#\")=" + suggestions.supportsInput("#")
                + " supports(\".config\")=" + suggestions.supportsInput(".config"));
        System.out.println("[cmdtest] manager.supports(\"#\")="
                + vesence.utils.commands.suggestions.CommandSuggestionManager.supportsInput("#")
                + " manager.collect(\"#\")="
                + (vesence.utils.commands.suggestions.CommandSuggestionManager.collect("#") == null
                ? 0
                : vesence.utils.commands.suggestions.CommandSuggestionManager.collect("#").entries().size()));

        System.out.println("[cmdtest] === DONE ===");
    }

    private static String quote(String s) {
        return "\"" + s + "\"";
    }
}
