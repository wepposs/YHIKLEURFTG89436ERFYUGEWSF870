package vesence.pathing.process;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;
import java.util.Set;

/**
 * {@code #mine <блок> [радиус]} — добыть указанный блок.
 *
 * <p>Имя блока можно писать как угодно из привычных форм: {@code iron_ore},
 * {@code minecraft:iron_ore}, {@code IRON_ORE}. Требовать точное написание
 * {@code minecraft:iron_ore} было бы издевательством — в Baritone пишут коротко.
 *
 * <p>Мягкие блоки — трава, цветы, листва — не добываются: они ломаются мгновенно
 * и не представляют интереса, а перебор куста за кустом занимал бы всё время.
 */
@Environment(EnvType.CLIENT)
public final class MineProcess extends BlockProcess {

    private final Block block;
    private final String blockName;

    public MineProcess(Block block, String blockName, int radius) {
        super(radius);
        this.block = block;
        this.blockName = blockName;
    }

    /**
     * Разбирает имя блока из аргумента команды.
     *
     * @return блок либо {@code null}, если такого нет
     */
    public static Block resolveBlock(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String name = raw.trim().toLowerCase(Locale.ROOT);
        if (name.contains(":")) {
            name = name.substring(name.indexOf(':') + 1);
        }
        // Частые короткие формы: в Baritone пишут «iron», а не «iron_ore».
        String full = switch (name) {
            case "iron" -> "iron_ore";
            case "gold" -> "gold_ore";
            case "diamond" -> "diamond_ore";
            case "coal" -> "coal_ore";
            case "copper" -> "copper_ore";
            case "redstone" -> "redstone_ore";
            case "lapis" -> "lapis_ore";
            case "emerald" -> "emerald_ore";
            case "netherite" -> "ancient_debris";
            default -> name;
        };

        var identifier = net.minecraft.resources.Identifier.tryParse("minecraft:" + full);
        if (identifier == null) {
            return null;
        }
        Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(identifier);
        // Реестр возвращает воздух на неизвестное имя — это и есть «нет такого».
        return block == Blocks.AIR ? null : block;
    }

    /** Все известные имена — для подсказки при ошибке. */
    public static Set<String> commonNames() {
        return Set.of("iron_ore", "gold_ore", "diamond_ore", "coal_ore", "copper_ore",
                "redstone_ore", "lapis_ore", "emerald_ore", "ancient_debris",
                "oak_log", "stone", "deepslate");
    }

    @Override
    protected boolean matches(BlockState state) {
        return state.getBlock() == this.block;
    }

    @Override
    protected String doneMessage(int broken) {
        if (broken == 0) {
            return "блоков " + this.blockName + " рядом нет";
        }
        return "добыто блоков " + this.blockName + ": " + broken;
    }

    @Override
    protected String stalledMessage(int broken) {
        // Честно: копать вглубь этот процесс не умеет. Он берёт только блоки,
        // к которым можно подойти. Если таких нет, лучше сказать прямо, чем
        // бродить по округе, делая вид, что ищет.
        return "не смог подойти к блокам " + this.blockName
                + " — нужного блока нет на поверхности рядом, остановился.";
    }

    @Override
    public String name() {
        return "mine";
    }
}
