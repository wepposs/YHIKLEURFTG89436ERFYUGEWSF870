package vesence.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.entity.player.ItemCooldownManager$Entry")
public interface ItemCooldownManagerEntryAccessor {

    @Invoker("startTick")
    int invokeStartTick();

    @Invoker("endTick")
    int invokeEndTick();
}
