package vesence.utils.other;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.text.Text;

/**
 * Строка scoreboard-сайдбара (имя + счёт + ширина счёта) для ручной
 * перерисовки скорборда в NameProtect/Streamer Mode. Порт из
 * RelevantPremiumpp4 (ru.whylol.api.utils.SidebarEntry).
 */
@Environment(EnvType.CLIENT)
public class SidebarEntry {
    public final Text name;
    public final Text score;
    public final int scoreWidth;

    public SidebarEntry(Text name, Text score, int scoreWidth) {
        this.name = name;
        this.score = score;
        this.scoreWidth = scoreWidth;
    }
}
