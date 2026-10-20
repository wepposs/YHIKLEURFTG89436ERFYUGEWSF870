package vesence.utils.other;

import com.mojang.blaze3d.platform.InputConstants;

public class KeyUtil {

    // TODO(port26): 26.3 uses SDL scancodes (InputConstants.KEY_*) instead of GLFW key codes;
    // values below follow SDL ordering (layout-independent).
    private static final int SDL_SCANCODE_KP_DIVIDE = 84;
    private static final int SDL_SCANCODE_KP_MINUS = 86;
    private static final int SDL_SCANCODE_KP_DECIMAL = 99;

    public static String getKey(int key) {
        if (key == -1) return "null";

        // Letters (A-Z): SDL scancodes are layout-independent (do not turn into Russian
        // letters on a Russian layout).
        if (key >= InputConstants.KEY_A && key <= InputConstants.KEY_Z) {
            return String.valueOf((char) ('A' + key - InputConstants.KEY_A));
        }

        // Digits 1-9 then 0.
        if (key >= InputConstants.KEY_1 && key <= InputConstants.KEY_9) {
            return String.valueOf((char) ('1' + key - InputConstants.KEY_1));
        }
        if (key == InputConstants.KEY_0) {
            return "0";
        }

        if (key >= InputConstants.KEY_F1 && key <= InputConstants.KEY_F12) {
            return "F" + (key - InputConstants.KEY_F1 + 1);
        }
        if (key >= InputConstants.KEY_F13 && key <= InputConstants.KEY_F24) {
            return "F" + (key - InputConstants.KEY_F13 + 13);
        }

        if (key >= InputConstants.KEY_NUMPAD1 && key <= InputConstants.KEY_NUMPAD9) {
            return "NUM" + (key - InputConstants.KEY_NUMPAD1 + 1);
        }
        if (key == InputConstants.KEY_NUMPAD0) {
            return "NUM0";
        }

        return switch (key) {
            case InputConstants.KEY_SPACE -> "SPACE";
            case InputConstants.KEY_RETURN -> "ENTER";
            case InputConstants.KEY_NUMPADENTER -> "ENTER";
            case InputConstants.KEY_ESCAPE -> "ESC";
            case InputConstants.KEY_TAB -> "TAB";
            case InputConstants.KEY_BACKSPACE -> "BACK";
            case InputConstants.KEY_INSERT -> "INS";
            case InputConstants.KEY_DELETE -> "DEL";
            case InputConstants.KEY_RIGHT -> "RIGHT";
            case InputConstants.KEY_LEFT -> "LEFT";
            case InputConstants.KEY_DOWN -> "DOWN";
            case InputConstants.KEY_UP -> "UP";
            case InputConstants.KEY_PAGEUP -> "PGUP";
            case InputConstants.KEY_PAGEDOWN -> "PGDN";
            case InputConstants.KEY_HOME -> "HOME";
            case InputConstants.KEY_END -> "END";
            case InputConstants.KEY_CAPSLOCK -> "CAPS";
            case InputConstants.KEY_SCROLLLOCK -> "SCRL";
            case InputConstants.KEY_NUMLOCK -> "NUMLK";
            case InputConstants.KEY_PRINTSCREEN -> "PRINT";
            case InputConstants.KEY_PAUSE -> "PAUSE";
            case InputConstants.KEY_MINUS -> "-";
            case InputConstants.KEY_EQUALS -> "=";
            case InputConstants.KEY_LBRACKET -> "[";
            case InputConstants.KEY_RBRACKET -> "]";
            case InputConstants.KEY_BACKSLASH -> "\\";
            case InputConstants.KEY_SEMICOLON -> ";";
            case InputConstants.KEY_APOSTROPHE -> "'";
            case InputConstants.KEY_GRAVE -> "`";
            case InputConstants.KEY_COMMA -> ",";
            case InputConstants.KEY_PERIOD -> ".";
            case InputConstants.KEY_SLASH -> "/";
            case SDL_SCANCODE_KP_DECIMAL -> "NUM.";
            case SDL_SCANCODE_KP_DIVIDE -> "NUM/";
            case InputConstants.KEY_MULTIPLY -> "NUM*";
            case SDL_SCANCODE_KP_MINUS -> "NUM-";
            case InputConstants.KEY_ADD -> "NUM+";
            case InputConstants.KEY_NUMPADEQUALS -> "NUM=";
            case InputConstants.KEY_RSHIFT -> "RSHIFT";
            case InputConstants.KEY_LSHIFT -> "LSHIFT";
            case InputConstants.KEY_RCONTROL -> "RCTRL";
            case InputConstants.KEY_LCONTROL -> "LCTRL";
            case InputConstants.KEY_RALT -> "RALT";
            case InputConstants.KEY_LALT -> "LALT";
            case InputConstants.KEY_RGUI -> "RWIN";
            case InputConstants.KEY_LGUI -> "LWIN";
            default -> "KEY_" + key;
        };
    }
}
