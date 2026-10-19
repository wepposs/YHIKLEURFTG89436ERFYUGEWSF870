package vesence.hmi.script_wrappers;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;

public class JSItems {
    public Item get(String name) {
        Identifier id = Identifier.of((String)name);
        return (Item)Registries.ITEM.get(id);
    }

    public String checkItemName(ItemStack item) {
        return item.getCustomName().toString();
    }
}

