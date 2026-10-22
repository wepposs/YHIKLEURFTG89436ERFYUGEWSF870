package vesence.pathing.command;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import vesence.pathing.VesenceBaritone;

import java.util.List;

/**
 * Куда идти: {@code #goto}, {@code #goal}, {@code #thisway}, {@code #come}.
 *
 * <p>Четыре формы одной задачи — добраться до точки. Различаются способом
 * задать точку: координаты, направление взгляда, текущая позиция.
 */
@Environment(EnvType.CLIENT)
public final class GoalCommand implements BaritoneCommand {

    @Override
    public String name() {
        return "goto";
    }

    @Override
    public List<String> aliases() {
        return List.of("goal", "thisway", "come", "g", "walk", "path");
    }

    @Override
    public String usage() {
        return "#goto <x> <y> <z> | #goto <x> <z> | #thisway <блоков> | #come";
    }

    @Override
    public String description() {
        return "идти в точку: по координатам, по взгляду или к игроку";
    }

    @Override
    public void execute(BaritoneCommandContext ctx, String args) throws BaritoneCommandException {
        Minecraft mc = ctx.client();
        if (mc.player == null || mc.level == null) {
            throw new BaritoneCommandException("нужно быть в мире");
        }

        String invoked = invokedName(ctx);
        switch (invoked) {
            case "thisway" -> thisWay(ctx, mc);
            case "come" -> BaritoneCommandsSupport.startPath(ctx, mc.player.blockPosition());
            default -> this.gotoPoint(ctx, mc);
        }
    }

    /**
     * {@code #thisway <n>} — пройти n блоков туда, куда смотрит игрок.
     *
     * <p>Берём только горизонтальную составляющую взгляда: если смотреть вниз,
     * «вперёд» ушло бы под землю и цель оказалась бы внутри породы.
     */
    private void thisWay(BaritoneCommandContext ctx, Minecraft mc) throws BaritoneCommandException {
        String[] parts = ctx.parts();
        int distance = 32;
        if (parts.length >= 1) {
            try {
                distance = Math.abs(Integer.parseInt(parts[0]));
            } catch (NumberFormatException e) {
                throw new BaritoneCommandException("нужно число блоков: #thisway 50");
            }
        }
        distance = Math.max(1, Math.min(512, distance));

        double yaw = Math.toRadians(mc.player.getYRot());
        double dx = -Math.sin(yaw) * distance;
        double dz = Math.cos(yaw) * distance;

        BlockPos target = BlockPos.containing(
                mc.player.getX() + dx,
                mc.player.getY(),
                mc.player.getZ() + dz);

        BaritoneCommandsSupport.startPath(ctx, target);
    }

    /** {@code #goto <x> <y> <z>}, {@code #goto <x> <z>} или {@code #goto <n>}. */
    private void gotoPoint(BaritoneCommandContext ctx, Minecraft mc) throws BaritoneCommandException {
        String[] parts = ctx.parts();

        if (parts.length == 1) {
            // Одно число — «на столько блоков вперёд». Так короче и совпадает с
            // привычкой из Baritone, где #goto 100 означает именно это.
            //
            // Но число проверяем ДО вызова: иначе на опечатку вроде «#goto abc»
            // игрок получил бы подсказку про #thisway, которая к его вводу
            // отношения не имеет.
            String token = parts[0];
            boolean numeric = token.startsWith("~")
                    || token.chars().anyMatch(Character::isDigit);
            if (!numeric) {
                throw new BaritoneCommandException("не понял координаты: " + token);
            }
            this.thisWay(ctx, mc);
            return;
        }

        BlockPos target = parts.length >= 3
                ? ctx.parseCoordinates(parts, 0, 3)
                : ctx.parseCoordinates(parts, 0, 2);

        if (target == null) {
            throw new BaritoneCommandException("не понял координаты");
        }

        BaritoneCommandsSupport.startPath(ctx, target);
    }

    /** Имя, которым команду позвали: {@code goal}, {@code thisway} и так далее. */
    static String invokedName(BaritoneCommandContext ctx) {
        String raw = ctx.rawInput() == null ? "" : ctx.rawInput().trim();
        if (raw.startsWith("#")) {
            raw = raw.substring(1);
        }
        int space = raw.indexOf(' ');
        return (space > 0 ? raw.substring(0, space) : raw).toLowerCase();
    }
}
