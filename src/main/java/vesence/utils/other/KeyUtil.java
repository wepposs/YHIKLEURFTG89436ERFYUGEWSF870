package vesence.utils.other;

import org.lwjgl.glfw.GLFW;

public class KeyUtil {

    public static String getKey(int key) {
        if (key == -1) return "null";

        // Letters (A-Z) and digits (0-9): GLFW key codes equal their ASCII values, so this is
        // layout-independent (does not turn into Russian letters on a Russian layout).
        if ((key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z)
                || (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9)) {
            return String.valueOf((char) key);
        }

        if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F25) {
            return "F" + (key - GLFW.GLFW_KEY_F1 + 1);
        }

        if (key >= GLFW.GLFW_KEY_KP_0 && key <= GLFW.GLFW_KEY_KP_9) {
            return "NUM" + (key - GLFW.GLFW_KEY_KP_0);
        }

        return switch (key) {
            case GLFW.GLFW_KEY_SPACE -> "SPACE";
            case GLFW.GLFW_KEY_ENTER -> "ENTER";
            case GLFW.GLFW_KEY_KP_ENTER -> "ENTER";
            case GLFW.GLFW_KEY_ESCAPE -> "ESC";
            case GLFW.GLFW_KEY_TAB -> "TAB";
            case GLFW.GLFW_KEY_BACKSPACE -> "BACK";
            case GLFW.GLFW_KEY_INSERT -> "INS";
            case GLFW.GLFW_KEY_DELETE -> "DEL";
            case GLFW.GLFW_KEY_RIGHT -> "RIGHT";
            case GLFW.GLFW_KEY_LEFT -> "LEFT";
            case GLFW.GLFW_KEY_DOWN -> "DOWN";
            case GLFW.GLFW_KEY_UP -> "UP";
            case GLFW.GLFW_KEY_PAGE_UP -> "PGUP";
            case GLFW.GLFW_KEY_PAGE_DOWN -> "PGDN";
            case GLFW.GLFW_KEY_HOME -> "HOME";
            case GLFW.GLFW_KEY_END -> "END";
            case GLFW.GLFW_KEY_CAPS_LOCK -> "CAPS";
            case GLFW.GLFW_KEY_SCROLL_LOCK -> "SCRL";
            case GLFW.GLFW_KEY_NUM_LOCK -> "NUMLK";
            case GLFW.GLFW_KEY_PRINT_SCREEN -> "PRINT";
            case GLFW.GLFW_KEY_PAUSE -> "PAUSE";
            case GLFW.GLFW_KEY_MINUS -> "-";
            case GLFW.GLFW_KEY_EQUAL -> "=";
            case GLFW.GLFW_KEY_LEFT_BRACKET -> "[";
            case GLFW.GLFW_KEY_RIGHT_BRACKET -> "]";
            case GLFW.GLFW_KEY_BACKSLASH -> "\\";
            case GLFW.GLFW_KEY_SEMICOLON -> ";";
            case GLFW.GLFW_KEY_APOSTROPHE -> "'";
            case GLFW.GLFW_KEY_GRAVE_ACCENT -> "`";
            case GLFW.GLFW_KEY_COMMA -> ",";
            case GLFW.GLFW_KEY_PERIOD -> ".";
            case GLFW.GLFW_KEY_SLASH -> "/";
            case GLFW.GLFW_KEY_KP_DECIMAL -> "NUM.";
            case GLFW.GLFW_KEY_KP_DIVIDE -> "NUM/";
            case GLFW.GLFW_KEY_KP_MULTIPLY -> "NUM*";
            case GLFW.GLFW_KEY_KP_SUBTRACT -> "NUM-";
            case GLFW.GLFW_KEY_KP_ADD -> "NUM+";
            case GLFW.GLFW_KEY_KP_EQUAL -> "NUM=";
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RSHIFT";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "LSHIFT";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RCTRL";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "LCTRL";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "RALT";
            case GLFW.GLFW_KEY_LEFT_ALT -> "LALT";
            case GLFW.GLFW_KEY_RIGHT_SUPER -> "RWIN";
            case GLFW.GLFW_KEY_LEFT_SUPER -> "LWIN";
            case GLFW.GLFW_KEY_MENU -> "MENU";
            default -> "KEY_" + key;
        };
    }
}
