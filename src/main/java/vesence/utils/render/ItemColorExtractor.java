package vesence.utils.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Environment(EnvType.CLIENT)
public class ItemColorExtractor {

    private static final Map<Item, Integer> COLOR_CACHE = new ConcurrentHashMap<>();

    public static int getDominantColor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;

        DyedColorComponent dyedColor = stack.get(DataComponentTypes.DYED_COLOR);
        if (dyedColor != null) {
            return dyedColor.rgb();
        }

        Item item = stack.getItem();
        Integer cached = COLOR_CACHE.get(item);
        if (cached != null) return cached;

        int color = getColorByName(item);
        COLOR_CACHE.put(item, color);
        return color;
    }

    private static int getColorByName(Item item) {
        Identifier id = Registries.ITEM.getId(item);
        String path = id.getPath();

        if (path.contains("netherite")) return 0x4D3B2F;

        if (path.contains("diamond")) return 0x4AEDD9;

        if (path.contains("gold")) return 0xF5D442;

        if (path.contains("iron")) return 0xC8C8C8;

        if (path.contains("copper")) return 0xC06840;

        if (path.contains("stone") && !path.contains("reds") && !path.contains("glow") && !path.contains("end")) return 0x7A7A7A;

        if (path.contains("wooden")) return 0x9E6B40;

        if (path.contains("oak_log") || path.contains("oak_wood")) return 0x6B5130;
        if (path.contains("spruce_log") || path.contains("spruce_wood")) return 0x3B2810;
        if (path.contains("birch_log") || path.contains("birch_wood")) return 0xD4C99A;
        if (path.contains("jungle_log") || path.contains("jungle_wood")) return 0x5A4020;
        if (path.contains("acacia_log") || path.contains("acacia_wood")) return 0x6E4420;
        if (path.contains("dark_oak_log") || path.contains("dark_oak_wood")) return 0x3A2712;
        if (path.contains("mangrove_log") || path.contains("mangrove_wood")) return 0x6B3028;
        if (path.contains("cherry_log") || path.contains("cherry_wood")) return 0xC8728A;
        if (path.contains("bamboo_block")) return 0xB5A840;
        if (path.contains("pale_oak")) return 0xC8C0B0;
        if (path.contains("_log") || path.contains("_wood")) return 0x6B5030;

        if (path.contains("oak_planks")) return 0xA0784A;
        if (path.contains("spruce_planks")) return 0x6B4B26;
        if (path.contains("birch_planks")) return 0xC8B878;
        if (path.contains("jungle_planks")) return 0xA07848;
        if (path.contains("acacia_planks")) return 0xB06030;
        if (path.contains("dark_oak_planks")) return 0x3E2914;
        if (path.contains("mangrove_planks")) return 0x7B3830;
        if (path.contains("cherry_planks")) return 0xE4B0B8;
        if (path.contains("bamboo_planks")) return 0xC8B850;
        if (path.contains("_planks")) return 0x9E7040;

        if (path.contains("trident")) return 0x18B8B8;
        if (path.contains("mace")) return 0x5E4E3E;
        if (path.contains("crossbow")) return 0x5A4A3C;
        if (path.contains("bow")) return 0x8B5A3A;

        if (path.contains("totem")) return 0x28A060;

        if (path.contains("ender_pearl")) return 0x10D870;
        if (path.contains("ender_eye") || path.contains("eye_of_ender")) return 0x14E866;

        if (path.contains("blaze_rod")) return 0xFFAA00;
        if (path.contains("blaze_powder")) return 0xFFCC00;

        if (path.contains("fire_charge")) return 0xFF5500;
        if (path.contains("firework")) return 0xFF4444;

        if (path.contains("emerald")) return 0x17D64B;
        if (path.contains("amethyst")) return 0xA855CC;
        if (path.contains("lapis")) return 0x2244CC;
        if (path.contains("redstone")) return 0xCC0000;
        if (path.contains("glowstone")) return 0xFFDD44;
        if (path.contains("prismarine")) return 0x4AA89A;
        if (path.contains("quartz")) return 0xECE4DA;
        if (path.contains("coal")) return 0x303030;
        if (path.contains("nether_star")) return 0xFFFDE8;

        if (path.contains("torch") || path.contains("lantern")) return 0xFFA030;
        if (path.contains("candle")) return 0xE8B860;
        if (path.contains("fire")) return 0xFF5010;
        if (path.contains("magma")) return 0xCC4400;

        if (path.contains("golden_apple") || path.contains("enchanted_golden_apple")) return 0xFFD700;
        if (path.contains("apple")) return 0xCC3333;
        if (path.contains("golden_carrot")) return 0xFFB000;

        if (path.contains("potion")) return 0xBB44EE;
        if (path.contains("enchanted_book")) return 0x9944FF;
        if (path.contains("experience_bottle")) return 0x88FF44;

        if (path.contains("heart_of_the_sea")) return 0x2080FF;
        if (path.contains("nautilus")) return 0xFFBB99;
        if (path.contains("conduit")) return 0x80E0FF;

        if (path.contains("beacon")) return 0x80FFCC;
        if (path.contains("shield")) return 0x9B7540;
        if (path.contains("lava_bucket")) return 0xFF6600;
        if (path.contains("water_bucket")) return 0x3366FF;
        if (path.contains("milk_bucket")) return 0xFFF8F0;
        if (path.contains("egg")) return 0xFFF0D0;
        if (path.contains("snowball")) return 0xE8F0FF;
        if (path.contains("slime")) return 0x66CC44;

        if (path.contains("wither")) return 0x222222;
        if (path.contains("soul")) return 0x44CCCC;
        if (path.contains("warped")) return 0x14B4A0;
        if (path.contains("crimson")) return 0xA01020;
        if (path.contains("shroomlight")) return 0xFFAA44;
        if (path.contains("crying_obsidian")) return 0x6622CC;

        if (path.contains("chorus")) return 0xAA66CC;
        if (path.contains("end_crystal")) return 0xFF88CC;
        if (path.contains("shulker")) return 0xAA77BB;
        if (path.contains("elytra")) return 0x9988AA;
        if (path.contains("dragon")) return 0x1A1A2E;

        return 0;
    }

    public static void clearCache() {
        COLOR_CACHE.clear();
    }
}
