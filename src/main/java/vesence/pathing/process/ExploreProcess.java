package vesence.pathing.process;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import vesence.pathing.BaritoneMessages;
import vesence.pathing.VesenceBaritone;

import java.util.Random;

/**
 * {@code #explore} — идти в случайную сторону, пока не отменят.
 *
 * <p>Задача без конца: выбрали точку подальше, дошли, выбрали следующую. Именно
 * так Baritone и используется чаще всего — не «дойди вот сюда», а «иди куда
 * глаза глядят».
 *
 * <p>Высоту берём из карты высот, а не из позиции игрока: точка в сотне блоков
 * почти всегда на другом уровне, и цель, поставленная на нашей высоте, оказалась
 * бы внутри горы или в воздухе над обрывом.
 */
@Environment(EnvType.CLIENT)
public final class ExploreProcess implements BaritoneProcess {

    /** Как далеко уходим за один шаг исследования, блоков. */
    private static final int STEP_DISTANCE = 96;

    /** Насколько близко к точке считаем, что дошли. */
    private static final double ARRIVED = 8.0;

    /** Сколько раз пробуем подобрать точку, прежде чем сдаться. */
    private static final int MAX_TRIES = 24;

    private final Random random = new Random();

    private BlockPos target;
    private boolean finished;
    private int ticks;

    @Override
    public String name() {
        return "explore";
    }

    @Override
    public boolean isActive() {
        return !this.finished;
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            this.finished = true;
            return;
        }

        // Дошли до точки — сразу выбираем следующую. Проверяем и по расстоянию,
        // и по тому, что путь закончился: иначе на неровном рельефе можно
        // «дойти» формально и стоять.
        if (this.target != null) {
            double distance = Math.sqrt(mc.player.blockPosition().distSqr(this.target));
            VesenceBaritone engine = VesenceBaritone.get();
            if (distance <= ARRIVED || !engine.isPathing()) {
                this.target = null;
            }
        }

        if (this.target == null) {
            this.target = this.pickTarget(mc);
            if (this.target == null) {
                this.finished = true;
                BaritoneMessages.error("не нашёл куда идти, исследование остановлено");
                return;
            }
            VesenceBaritone.get().setGoalAndPath(this.target);
            // Сообщаем не каждый раз: при шаге в сотню блоков это было бы спамом
            // раз в полминуты, но всё же полезно знать направление.
            if (this.ticks == 0) {
                BaritoneMessages.info("исследую: " + this.target.getX() + " " + this.target.getZ());
            }
        }
        this.ticks++;
    }

    /**
     * Случайная точка на поверхности в стороне от игрока.
     *
     * <p>Пробуем несколько раз, потому что случайное направление может попасть
     * в океан или в гору — туда, куда пути нет.
     */
    private BlockPos pickTarget(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            return null;
        }
        BlockPos origin = mc.player.blockPosition();
        for (int attempt = 0; attempt < MAX_TRIES; attempt++) {
            double angle = this.random.nextDouble() * Math.PI * 2.0;
            int dx = (int) Math.round(Math.cos(angle) * STEP_DISTANCE);
            int dz = (int) Math.round(Math.sin(angle) * STEP_DISTANCE);
            int x = origin.getX() + dx;
            int z = origin.getZ() + dz;
            int y = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y <= mc.level.getMinY()) {
                continue;
            }
            return new BlockPos(x, y, z);
        }
        return null;
    }

    @Override
    public void cancel() {
        VesenceBaritone.get().cancel();
    }
}
