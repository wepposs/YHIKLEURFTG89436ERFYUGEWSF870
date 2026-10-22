package vesence.pathing.process;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * {@code #farm} — собрать созревший урожай вокруг игрока.
 *
 * <p>Собираем только то, что уже поспело. Это не придирка: сломанная незрелая
 * пшеница не даёт ни зерна, ни семян — то есть работа была бы не просто
 * бесполезной, а вредной, потому что грядка после неё пустая.
 *
 * <p>Что считается спелым:
 * <ul>
 *   <li>обычные культуры ({@code CropBlock}) — по их собственному признаку
 *       зрелости, поэтому пшеница, морковь, картофель и свёкла работают
 *       одинаково;</li>
 *   <li>адский нарост — возраст 3;</li>
 *   <li>какао — возраст 2;</li>
 *   <li>куст сладких ягод — возраст 2 и выше;</li>
 *   <li>сахарный тростник, тыква и арбуз — всегда, у них нет возраста.</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public final class FarmProcess extends BlockProcess {

    /** Радиус сбора. Больше — дольше искать и больше шансов уйти далеко. */
    public static final int DEFAULT_RADIUS = 24;

    private final int radius;

    public FarmProcess(int radius) {
        super(radius);
        this.radius = radius;
    }

    @Override
    protected boolean matches(BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }
        if (state.getBlock() instanceof NetherWartBlock) {
            return state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE;
        }
        if (state.getBlock() instanceof CocoaBlock) {
            return state.getValue(CocoaBlock.AGE) >= CocoaBlock.MAX_AGE;
        }
        if (state.getBlock() instanceof SweetBerryBushBlock) {
            return state.getValue(SweetBerryBushBlock.AGE) >= 2;
        }
        if (state.getBlock() instanceof SugarCaneBlock) {
            return true;
        }
        return state.is(Blocks.PUMPKIN) || state.is(Blocks.MELON);
    }

    @Override
    protected String doneMessage(int broken) {
        // Говорим по факту, а не «готово»: если рядом нечего было собирать,
        // игрок должен это видеть, а не гадать, сработала ли команда.
        if (broken == 0) {
            return "спелых грядок в радиусе " + this.radius + " нет";
        }
        return "урожай собран: блоков " + broken;
    }

    @Override
    protected String stalledMessage(int broken) {
        return "не смог подойти к грядкам в радиусе " + this.radius + ", остановился.";
    }

    @Override
    public String name() {
        return "farm";
    }
}
