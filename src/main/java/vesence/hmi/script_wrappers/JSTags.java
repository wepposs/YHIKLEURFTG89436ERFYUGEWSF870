package vesence.hmi.script_wrappers;

import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.registry.RegistryKeys;

public class JSTags {
    public TagKey<Item> getVanillaTag(String id) {
        return TagKey.of((RegistryKey) RegistryKeys.ITEM, Identifier.ofVanilla(id));
    }

    /**
     * Конвенциональные (Fabric) теги предметов живут в неймспейсе "c" (например c:music_discs,
     * c:nuggets). Оригинал HMI использовал внутренний TagRegistration; здесь напрямую строим
     * TagKey в неймспейсе "c", что эквивалентно по результату и не зависит от internal API.
     */
    public TagKey<Item> getFabricTag(String id) {
        return TagKey.of((RegistryKey) RegistryKeys.ITEM, Identifier.of("c", id));
    }
}
