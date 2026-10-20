package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;

// TODO(port26): Display.ItemDisplay no longer exposes the inner Data class in 26.3;
// the old "data" accessor was dropped. No current callers.
@Environment(EnvType.CLIENT)
@Mixin({Display.ItemDisplay.class})
public interface IItemDisplayEntity {
}
