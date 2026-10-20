package vesence.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// TODO(port26): ItemCooldowns$Entry renamed to ItemCooldowns$CooldownInstance (a record
// with startTime/endTime components) in 26.3
@Mixin(targets = "net.minecraft.world.item.ItemCooldowns$CooldownInstance")
public interface ItemCooldownManagerEntryAccessor {

    @Accessor("startTime")
    int invokeStartTick();

    @Accessor("endTime")
    int invokeEndTick();
}
