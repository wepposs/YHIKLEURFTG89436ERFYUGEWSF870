package vesence.hmi.script_wrappers;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;

public class JSItems {
    public Item get(String name) {
        Identifier id = Identifier.parse(name);
        return BuiltInRegistries.ITEM.get(id).map(net.minecraft.core.Holder.Reference::value).orElse(null);
    }

    public String checkItemName(ItemStack item) {
        return item.getCustomName().toString();
    }
}

