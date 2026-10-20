package vesence.utils.other;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.sdl.SDLMouse;

import java.nio.FloatBuffer;

/**
 * Соглашение о кнопках мыши для всего клиента.
 *
 * <p>Внутри vesence исторически используется GLFW-нумерация (0 = ЛКМ, 1 = ПКМ, 2 = СКМ,
 * 3 = X1, 4 = X2), а бинд кнопки кодируется как {@code -100 - индекс}. Весь ClickGui
 * сравнивает {@code button == 0} как «левая кнопка», поэтому менять это соглашение нельзя.
 *
 * <p>Порт 26.3: GLFW больше нет, события приходят из SDL, где нумерация ДРУГАЯ и начинается
 * с единицы: {@code 1 = ЛКМ, 2 = СКМ, 3 = ПКМ, 4 = X1, 5 = X2}
 * ({@code InputConstants.MOUSE_BUTTON_*} и {@code MouseButtonInfo#button()}).
 * Именно из-за этого «левая» превращалась в «правую»: раньше код делал {@code button - 1}
 * и получал 0/1/2 вместо 0/2/1.
 *
 * <p>Поэтому любое место, где событие SDL попадает в код vesence, должно пройти через
 * {@link #button(int)} / {@link #bind(int)}, а проверка «зажата ли кнопка» — через
 * {@link #isDown(int)}.
 */
@Environment(EnvType.CLIENT)
public final class MouseUtil {

    /** Индекс vesence для «бинда нет». */
    public static final int NONE = -1;

    private MouseUtil() {
    }

    /**
     * SDL-кнопка (1..5) → vesence-индекс (0 = ЛКМ, 1 = ПКМ, 2 = СКМ, 3 = X1, 4 = X2).
     * Неизвестные кнопки отображаются как {@code sdl - 1}, чтобы не терять X-кнопки.
     */
    public static int button(int sdlButton) {
        return switch (sdlButton) {
            case InputConstants.MOUSE_BUTTON_LEFT -> 0;
            case InputConstants.MOUSE_BUTTON_RIGHT -> 1;
            case InputConstants.MOUSE_BUTTON_MIDDLE -> 2;
            case InputConstants.MOUSE_BUTTON_4 -> 3;
            case InputConstants.MOUSE_BUTTON_5 -> 4;
            case 0, -1 -> NONE;
            default -> sdlButton - 1;
        };
    }

    /** SDL-кнопка → vesence-код бинда ({@code -100 - индекс}); -1 если кнопки нет. */
    public static int bind(int sdlButton) {
        int index = button(sdlButton);
        return index == NONE ? NONE : -100 - index;
    }

    /** vesence-код бинда ({@code -100 - индекс}) → индекс кнопки. */
    public static int indexOfBind(int bind) {
        return bind <= -100 ? -bind - 100 : NONE;
    }

    /**
     * Зажата ли кнопка прямо сейчас, по vesence-индексу (0 = ЛКМ, 1 = ПКМ, 2 = СКМ,
     * 3 = X1, 4 = X2). SDL-эквивалент удалённого {@code GLFW.glfwGetMouseButton}.
     */
    public static boolean isDown(int vesenceIndex) {
        int mask = switch (vesenceIndex) {
            case 0 -> SDLMouse.SDL_BUTTON_LMASK;
            case 1 -> SDLMouse.SDL_BUTTON_RMASK;
            case 2 -> SDLMouse.SDL_BUTTON_MMASK;
            case 3 -> SDLMouse.SDL_BUTTON_X1MASK;
            case 4 -> SDLMouse.SDL_BUTTON_X2MASK;
            default -> 0;
        };
        if (mask == 0) {
            return false;
        }
        return (SDLMouse.SDL_GetMouseState((FloatBuffer) null, (FloatBuffer) null) & mask) != 0;
    }
}
