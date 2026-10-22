package vesence.pathing.command;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import vesence.pathing.BaritoneMessages;

import java.util.List;

/**
 * Контекст выполнения команды Baritone.
 *
 * <p>Отдельный от {@link vesence.utils.commands.CommandContext}, потому что у
 * Baritone свой префикс ({@code [Baritone]}) и свой набор сообщений. Смешивать
 * их нельзя: игрок должен с первого взгляда отличать ответ Baritone от ответа
 * остальных команд клиента.
 */
@Environment(EnvType.CLIENT)
public final class BaritoneCommandContext {

    private final String rawInput;
    private final String arguments;

    BaritoneCommandContext(String rawInput, String arguments) {
        this.rawInput = rawInput;
        this.arguments = arguments;
    }

    public Minecraft client() {
        return Minecraft.getInstance();
    }

    public String rawInput() {
        return this.rawInput;
    }

    public String arguments() {
        return this.arguments;
    }

    public void info(String text) {
        BaritoneMessages.info(text);
    }

    public void success(String text) {
        BaritoneMessages.success(text);
    }

    public void error(String text) {
        BaritoneMessages.error(text);
    }

    // ── Разбор аргументов ─────────────────────────────────────────────────────

    /** Аргументы, разбитые по пробелам. Пустой ввод даёт пустой массив. */
    public String[] parts() {
        String trimmed = this.arguments == null ? "" : this.arguments.trim();
        if (trimmed.isEmpty()) {
            return new String[0];
        }
        return trimmed.split("\\s+");
    }

    /**
     * Разбирает координаты.
     *
     * <p>Поддерживаются три формы, как в обычных командах Minecraft:
     * <ul>
     *   <li>{@code x y z} — абсолютные;</li>
     *   <li>{@code x z} — высота берётся текущая, поиск подберёт сам;</li>
     *   <li>{@code ~ ~ ~} — относительно игрока, {@code ~} без числа = 0.</li>
     * </ul>
     *
     * @return позиция либо {@code null}, если разобрать не удалось
     */
    public BlockPos parseCoordinates(String[] args, int offset, int count) {
        Minecraft mc = this.client();
        if (mc.player == null || args.length < offset + count) {
            return null;
        }
        Vec3 pos = mc.player.position();
        try {
            if (count == 3) {
                return new BlockPos(
                        this.resolve(args[offset], pos.x),
                        this.resolve(args[offset + 1], pos.y),
                        this.resolve(args[offset + 2], pos.z));
            }
            if (count == 2) {
                return new BlockPos(
                        this.resolve(args[offset], pos.x),
                        mc.player.getBlockY(),
                        this.resolve(args[offset + 1], pos.z));
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return null;
    }

    /** Одно число: абсолютное или {@code ~n} относительно текущей координаты. */
    private int resolve(String token, double base) {
        if (token.startsWith("~")) {
            String rest = token.substring(1);
            return (int) Math.round(base + (rest.isEmpty() ? 0.0 : Double.parseDouble(rest)));
        }
        return (int) Math.round(Double.parseDouble(token));
    }

    /** Первый аргумент в нижнем регистре либо пустая строка. */
    public String first() {
        String[] p = this.parts();
        return p.length == 0 ? "" : p[0].toLowerCase();
    }

    /** Все аргументы, кроме первого, склеенные обратно через пробел. */
    public String rest() {
        String[] p = this.parts();
        if (p.length <= 1) {
            return "";
        }
        return String.join(" ", List.of(p).subList(1, p.length));
    }
}
