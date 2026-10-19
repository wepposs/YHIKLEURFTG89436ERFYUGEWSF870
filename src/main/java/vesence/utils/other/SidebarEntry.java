package vesence.utils.other;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;

/**
 * Строка scoreboard-сайдбара (имя + счёт + ширина счёта) для ручной
 * перерисовки скорборда в NameProtect/Streamer Mode. Порт из
 * RelevantPremiumpp4 (ru.whylol.api.utils.SidebarEntry).
 */
@Environment(EnvType.CLIENT)
public class SidebarEntry {
    public final Component name;
    public final Component score;
    public final int scoreWidth;

    public SidebarEntry(Component name, Component score, int scoreWidth) {
        this.name = name;
        this.score = score;
        this.scoreWidth = scoreWidth;
    }
}
