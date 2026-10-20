package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.LoadingOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(LoadingOverlay.class)
public interface SplashOverlayAccessor {
   @Accessor("fadeOutStart")
   long getReloadCompleteTime();
}
