package vesence.utils.other;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.HashMap;
import java.util.Map;

/**
 * Разовая миграция биндов с GLFW-кодов на SDL-сканкоды (порт 26.3).
 *
 * <p>До 26.3 {@code Module#bind} и {@code BindSettings#key} хранили <b>GLFW</b>-коды
 * (A = 65, ENTER = 257, F1 = 290 …). В 26.3 GLFW нет, а {@code KeyEvent#key()} — это
 * <b>SDL-сканкод</b> (A = 4, ENTER = 40, F1 = 58 …). Без миграции все старые бинды
 * указывали бы на другие клавиши (A превратилась бы в «3», F1 — в «пробел» и т.п.).
 *
 * <p>Конфиг, сохранённый новым клиентом, содержит {@code "KeyFormat": "sdl"}, поэтому
 * миграция применяется ровно один раз — к старым файлам.
 *
 * <p>ВАЖНО: конфиги без маркера считаются <b>уже</b> в SDL. Порт на 26.3 писал бинды
 * напрямую из {@code KeyEvent#key()}, то есть в SDL-сканкодах, и автоматическая
 * перекодировка таких файлов испортила бы рабочие бинды (SDL-код 82 — это стрелка вверх,
 * а GLFW-код 82 — клавиша R). Поэтому конвертация включается только если файл явно
 * помечен как GLFW-совместимый.
 */
@Environment(EnvType.CLIENT)
public final class KeyCodeMigration {

    /** Маркер формата в корне конфига. */
    public static final String FORMAT_FIELD = "KeyFormat";
    public static final String FORMAT_SDL = "sdl";
    public static final String FORMAT_GLFW = "glfw";

    private static final Map<Integer, Integer> GLFW_TO_SDL = new HashMap<>();

    /** true, пока грузится конфиг старого формата. */
    private static boolean legacy = false;

    static {
        // A-Z
        for (int i = 0; i < 26; i++) {
            GLFW_TO_SDL.put(65 + i, InputConstants.KEY_A + i);
        }
        // 0-9: в GLFW 48..57 подряд, в SDL сначала 1..9 (30..38), затем 0 (39)
        for (int d = 1; d <= 9; d++) {
            GLFW_TO_SDL.put(48 + d, InputConstants.KEY_1 + (d - 1));
        }
        GLFW_TO_SDL.put(48, InputConstants.KEY_0);
        // F1-F12 и F13-F24
        for (int i = 0; i < 12; i++) {
            GLFW_TO_SDL.put(290 + i, InputConstants.KEY_F1 + i);
            GLFW_TO_SDL.put(302 + i, InputConstants.KEY_F13 + i);
        }
        // Пунктуация
        GLFW_TO_SDL.put(32, InputConstants.KEY_SPACE);
        GLFW_TO_SDL.put(39, InputConstants.KEY_APOSTROPHE);
        GLFW_TO_SDL.put(44, InputConstants.KEY_COMMA);
        GLFW_TO_SDL.put(45, InputConstants.KEY_MINUS);
        GLFW_TO_SDL.put(46, InputConstants.KEY_PERIOD);
        GLFW_TO_SDL.put(47, InputConstants.KEY_SLASH);
        GLFW_TO_SDL.put(59, InputConstants.KEY_SEMICOLON);
        GLFW_TO_SDL.put(61, InputConstants.KEY_EQUALS);
        GLFW_TO_SDL.put(91, InputConstants.KEY_LBRACKET);
        GLFW_TO_SDL.put(92, InputConstants.KEY_BACKSLASH);
        GLFW_TO_SDL.put(93, InputConstants.KEY_RBRACKET);
        GLFW_TO_SDL.put(96, InputConstants.KEY_GRAVE);
        // Управление и навигация
        GLFW_TO_SDL.put(256, InputConstants.KEY_ESCAPE);
        GLFW_TO_SDL.put(257, InputConstants.KEY_RETURN);
        GLFW_TO_SDL.put(258, InputConstants.KEY_TAB);
        GLFW_TO_SDL.put(259, InputConstants.KEY_BACKSPACE);
        GLFW_TO_SDL.put(260, InputConstants.KEY_INSERT);
        GLFW_TO_SDL.put(261, InputConstants.KEY_DELETE);
        GLFW_TO_SDL.put(262, InputConstants.KEY_RIGHT);
        GLFW_TO_SDL.put(263, InputConstants.KEY_LEFT);
        GLFW_TO_SDL.put(264, InputConstants.KEY_DOWN);
        GLFW_TO_SDL.put(265, InputConstants.KEY_UP);
        GLFW_TO_SDL.put(266, InputConstants.KEY_PAGEUP);
        GLFW_TO_SDL.put(267, InputConstants.KEY_PAGEDOWN);
        GLFW_TO_SDL.put(268, InputConstants.KEY_HOME);
        GLFW_TO_SDL.put(269, InputConstants.KEY_END);
        GLFW_TO_SDL.put(280, InputConstants.KEY_CAPSLOCK);
        GLFW_TO_SDL.put(281, InputConstants.KEY_SCROLLLOCK);
        GLFW_TO_SDL.put(282, InputConstants.KEY_NUMLOCK);
        GLFW_TO_SDL.put(283, InputConstants.KEY_PRINTSCREEN);
        GLFW_TO_SDL.put(284, InputConstants.KEY_PAUSE);
        // Цифровой блок
        GLFW_TO_SDL.put(320, InputConstants.KEY_NUMPAD0);
        for (int d = 1; d <= 9; d++) {
            GLFW_TO_SDL.put(320 + d, InputConstants.KEY_NUMPAD1 + (d - 1));
        }
        GLFW_TO_SDL.put(330, 99);   // KP_DECIMAL
        GLFW_TO_SDL.put(331, 84);   // KP_DIVIDE
        GLFW_TO_SDL.put(332, InputConstants.KEY_MULTIPLY);
        GLFW_TO_SDL.put(333, 86);   // KP_MINUS
        GLFW_TO_SDL.put(334, InputConstants.KEY_ADD);
        GLFW_TO_SDL.put(335, InputConstants.KEY_NUMPADENTER);
        GLFW_TO_SDL.put(336, InputConstants.KEY_NUMPADEQUALS);
        // Модификаторы
        GLFW_TO_SDL.put(340, InputConstants.KEY_LSHIFT);
        GLFW_TO_SDL.put(341, InputConstants.KEY_LCONTROL);
        GLFW_TO_SDL.put(342, InputConstants.KEY_LALT);
        GLFW_TO_SDL.put(343, InputConstants.KEY_LGUI);
        GLFW_TO_SDL.put(344, InputConstants.KEY_RSHIFT);
        GLFW_TO_SDL.put(345, InputConstants.KEY_RCONTROL);
        GLFW_TO_SDL.put(346, InputConstants.KEY_RALT);
        GLFW_TO_SDL.put(347, InputConstants.KEY_RGUI);
        GLFW_TO_SDL.put(348, 101);  // APPLICATION / MENU
    }

    private KeyCodeMigration() {
    }

    /** Начать загрузку конфига: {@code legacy == true}, если в нём нет маркера формата. */
    public static void beginLoad(boolean isLegacy) {
        legacy = isLegacy;
    }

    public static void endLoad() {
        legacy = false;
    }

    /**
     * Переводит сохранённый бинд в текущее пространство кодов.
     * Мышиные кнопки ({@code <= -100}) и «нет бинда» ({@code -1}) не трогаются.
     */
    public static int migrate(int bind) {
        if (!legacy || bind == -1 || bind <= -100) {
            return bind;
        }
        Integer sdl = GLFW_TO_SDL.get(bind);
        return sdl != null ? sdl : bind;
    }
}
