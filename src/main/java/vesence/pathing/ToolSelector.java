package vesence.pathing;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Выбор инструмента под конкретный блок.
 *
 * <p>Игрок не копает камень рукой, если в хотбаре лежит кирка: он переключается
 * на неё. Без этого бот ломал бы обсидиан голой рукой три минуты и выглядел бы
 * сломанным, хотя всё делает «правильно».
 *
 * <p>Ищем только в хотбаре (первые девять слотов). Это не упрощение, а то, что
 * делает игрок: перекладывать предмет из инвентаря в горячую панель на бегу
 * никто не станет, а переключение слота — одно нажатие.
 */
@Environment(EnvType.CLIENT)
public final class ToolSelector {

    private ToolSelector() {
    }

    /**
     * Переключается на лучший инструмент для этого блока.
     *
     * <p>Если ничего лучше руки нет, ничего не делает: пустой слот не нужен.
     *
     * @return {@code true}, если слот сменился
     */
    public static boolean equipFor(Minecraft mc, BlockPos pos) {
        if (mc.player == null || mc.level == null) {
            return false;
        }
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }

        int bestSlot = -1;
        double bestScore = scoreOf(mc.player.getInventory().getSelectedItem(), state);

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            double score = scoreOf(stack, state);
            if (score > bestScore + 1.0e-4) {
                bestScore = score;
                bestSlot = slot;
            }
        }

        if (bestSlot < 0) {
            return false;
        }
        return select(mc, bestSlot);
    }

    /**
     * Насколько предмет хорош для этого блока. Больше — лучше.
     *
     * <p>Скорость разрушения — единственная честная мера: она уже учитывает и
     * материал, и уровень, и правила «этот инструмент вообще берёт этот блок».
     * Единица означает «рука».
     */
    private static double scoreOf(ItemStack stack, BlockState state) {
        if (stack.isEmpty()) {
            return 1.0;
        }
        double speed = stack.getDestroySpeed(state);
        // Инструмент, который не даёт дропа, для добычи бесполезен: сломать
        // блок деревянной киркой можно, а руду — нет, она просто исчезнет.
        if (!stack.isCorrectToolForDrops(state)) {
            speed *= 0.5;
        }
        return speed;
    }

    /**
     * Переключает активный слот и сообщает об этом серверу.
     *
     * <p>Без пакета сервер продолжает считать активным старый слот: ломание
     * засчитывается не тем предметом, и скорость не меняется.
     */
    public static boolean select(Minecraft mc, int slot) {
        if (mc.player == null) {
            return false;
        }
        if (mc.player.getInventory().getSelectedSlot() == slot) {
            return false;
        }
        mc.player.getInventory().setSelectedSlot(slot);
        if (mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
        }
        return true;
    }
}
