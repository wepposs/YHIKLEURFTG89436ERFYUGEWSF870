package vesence.hmi.script_wrappers;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;

public class JSItems {
    public Item get(String name) {
        ResourceLocation id = ResourceLocation.of((String)name);
        return (Item)BuiltInRegistries.ITEM.get(id);
    }

    public String checkItemName(ItemStack item) {
        return item.getCustomName().toString();
    }
}

