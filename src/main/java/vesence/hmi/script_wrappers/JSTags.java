package vesence.hmi.script_wrappers;

import net.minecraft.world.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.registry.RegistryKeys;

public class JSTags {
    public TagKey<Item> getVanillaTag(String id) {
        return TagKey.of((ResourceKey) RegistryKeys.ITEM, ResourceLocation.ofVanilla(id));
    }

    /**
     * Конвенциональные (Fabric) теги предметов живут в неймспейсе "c" (например c:music_discs,
     * c:nuggets). Оригинал HMI использовал внутренний TagRegistration; здесь напрямую строим
     * TagKey в неймспейсе "c", что эквивалентно по результату и не зависит от internal API.
     */
    public TagKey<Item> getFabricTag(String id) {
        return TagKey.of((ResourceKey) RegistryKeys.ITEM, ResourceLocation.of("c", id));
    }
}
