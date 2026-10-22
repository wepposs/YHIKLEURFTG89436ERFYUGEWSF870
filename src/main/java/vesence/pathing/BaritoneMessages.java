package vesence.pathing;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import vesence.renderengine.render.Renderer2D;

/**
 * Сообщения Baritone в чат.
 *
 * <p>Один формат на всё: жирный цветной {@code [Baritone]} в цвет темы клиента и
 * обычный текст после него. Раньше сообщения собирались в трёх разных местах и
 * выглядели по-разному — здесь это исправлено: и команды, и сам движок пишут
 * через этот класс, поэтому префикс всегда одинаковый и всегда следует за темой.
 */
@Environment(EnvType.CLIENT)
public final class BaritoneMessages {

    /** Текст префикса без скобок — скобки добавляются при сборке. */
    private static final String PREFIX = "Baritone";

    private static final int ERROR_COLOR   = 0xFF5555;
    private static final int INFO_COLOR    = 0xAAAAAA;
    private static final int SUCCESS_COLOR = 0x55FF55;

    private BaritoneMessages() {
    }

    /** Обычное сообщение. */
    public static void info(String text) {
        send(text, INFO_COLOR);
    }

    /** Успех. */
    public static void success(String text) {
        send(text, SUCCESS_COLOR);
    }

    /** Ошибка. */
    public static void error(String text) {
        send(text, ERROR_COLOR);
    }

    /**
     * Собирает сообщение целиком, не отправляя его.
     *
     * <p>Нужно там, где текст надо отдать наружу, а не в чат.
     */
    public static MutableComponent build(String text, int color) {
        // Цвет темы берём на момент отправки: игрок может сменить тему, и
        // префикс должен поменяться вместе с ней, а не остаться прежним.
        int theme = Renderer2D.ColorUtil.getClientColor();

        MutableComponent prefix = Component.literal("[" + PREFIX + "]")
                .setStyle(Style.EMPTY
                        .withColor(TextColor.fromRgb(theme))
                        .withBold(true));

        MutableComponent payload = Component.literal(" " + text)
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(color)));

        return prefix.append(payload);
    }

    private static void send(String text, int color) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        // Дублируем в консоль: сообщения в чат не попадают в latest.log, и без
        // этого ни один автотест не может проверить, что бот вообще ответил.
        System.out.println("[" + PREFIX + "] " + text);

        MutableComponent full = build(text, color);
        if (mc.player != null) {
            mc.player.sendSystemMessage(full);
        } else if (mc.gui != null) {
            mc.gui.hud.getChat().addClientSystemMessage(full);
        }
    }
}
