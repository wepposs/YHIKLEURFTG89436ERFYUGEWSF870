package vesence.mixin;

import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(ItemCooldowns.class)
public interface ItemCooldownManagerAccessor {

    @Accessor("cooldowns")
    Map<Identifier, ?> getEntries();

    @Accessor("tickCount")
    int getTick();
}
