package vesence.debug;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Диагностика ввода (порт 26.3).
 *
 * <p>Включается переменной окружения {@code VESENCE_INPUTDEBUG=1}. Печатает в лог
 * каждый mouse/key событие с сырым SDL-кодом, координатами и результатом перевода
 * в соглашение vesence. Нужна, чтобы проверять раскладку кнопок (ЛКМ/ПКМ/СКМ)
 * на живом клиенте, а не догадками: порт с GLFW на SDL поменял нумерацию, и именно
 * из-за этого кнопки «перепутались».
 */
@Environment(EnvType.CLIENT)
public final class InputDebug {

    private static final boolean ENABLED = System.getenv("VESENCE_INPUTDEBUG") != null;

    private InputDebug() {
    }

    public static boolean enabled() {
        return ENABLED;
    }

    /** Логирует событие кнопки мыши: сырой SDL-код и vesence-индекс/бинд. */
    public static void mouse(String where, int sdlButton, int action, double x, double y) {
        if (!ENABLED) {
            return;
        }
        int index = vesence.utils.other.MouseUtil.button(sdlButton);
        int bind = vesence.utils.other.MouseUtil.bind(sdlButton);
        System.out.println("[input] " + where
                + " sdl=" + sdlButton
                + " action=" + actionName(action)
                + " -> index=" + index
                + " bind=" + bind
                + " at=" + (int) x + "," + (int) y
                + " screen=" + currentScreen());
    }

    /** Имя открытого экрана — чтобы понимать, кто должен был получить клик. */
    public static String currentScreen() {
        try {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc == null || mc.gui == null) {
                return "null-mc";
            }
            var screen = mc.gui.screen();
            return screen == null ? "null" : screen.getClass().getSimpleName();
        } catch (Throwable t) {
            return "err";
        }
    }

    /** Логирует событие клавиши: SDL-сканкод и layout-зависимый keycode. */
    public static void key(String where, int scancode, int keycode, int action, int modifiers) {
        if (!ENABLED) {
            return;
        }
        System.out.println("[input] " + where
                + " scancode=" + scancode
                + " keycode=" + keycode
                + " action=" + actionName(action)
                + " mods=" + modifiers
                + " name=" + com.mojang.blaze3d.platform.InputConstants.Type.KEYBOARD
                        .getOrCreate(scancode).getDisplayName().getString());
    }

    public static void scroll(String where, double dx, double dy, double x, double y) {
        if (!ENABLED) {
            return;
        }
        System.out.println("[input] " + where + " scroll dx=" + dx + " dy=" + dy
                + " at=" + (int) x + "," + (int) y);
    }

    private static String actionName(int action) {
        return switch (action) {
            case 1 -> "PRESS";
            case 0 -> "RELEASE";
            case -1 -> "REPEAT";
            default -> String.valueOf(action);
        };
    }
}
