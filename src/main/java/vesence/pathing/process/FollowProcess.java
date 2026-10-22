package vesence.pathing.process;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import vesence.pathing.BaritoneMessages;
import vesence.pathing.VesenceBaritone;

/**
 * {@code #follow <ник>} — идти за игроком.
 *
 * <p>Цель пересчитывается не каждый тик, а раз в полсекунды. Иначе получается
 * худший вид поведения: цель дёргается вслед за каждым шагом того, за кем идём,
 * поиск пути не успевает достроиться, и преследователь топчется на месте.
 *
 * <p>Когда дошли — стоим. Догонять вплотную, упираясь в спину, незачем.
 */
@Environment(EnvType.CLIENT)
public final class FollowProcess implements BaritoneProcess {

    /** Как часто пересчитывать цель, тиков. */
    private static final int REFRESH_TICKS = 10;

    /** Ближе этого расстояния считаем, что догнали. */
    private static final double CLOSE_ENOUGH = 3.0;

    /** Дальше этого — цель потеряна, дальше идти не за кем. */
    private static final double LOST_DISTANCE = 96.0;

    private final String playerName;
    private int ticks;
    private boolean finished;

    public FollowProcess(String playerName) {
        this.playerName = playerName;
    }

    @Override
    public String name() {
        return "follow";
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

        AbstractClientPlayer target = this.find(mc);
        if (target == null) {
            this.finished = true;
            BaritoneMessages.error("игрок " + this.playerName + " не найден рядом");
            return;
        }

        double distance = mc.player.position().distanceTo(target.position());
        if (distance > LOST_DISTANCE) {
            this.finished = true;
            BaritoneMessages.error("потерял " + this.playerName + ", слишком далеко");
            return;
        }

        if (distance <= CLOSE_ENOUGH) {
            // Догнали — стоим на месте, но процесс не завершаем: цель может
            // снова уйти, и тогда мы пойдём за ней.
            VesenceBaritone engine = VesenceBaritone.get();
            if (engine.isPathing()) {
                engine.cancel();
            }
            return;
        }

        if (this.ticks++ % REFRESH_TICKS == 0) {
            VesenceBaritone.get().setGoalAndPath(target.blockPosition());
        }
    }

    private AbstractClientPlayer find(Minecraft mc) {
        for (AbstractClientPlayer candidate : mc.level.players()) {
            if (candidate == mc.player) {
                continue;
            }
            if (candidate.getGameProfile().name().equalsIgnoreCase(this.playerName)) {
                return candidate;
            }
        }
        return null;
    }
}
