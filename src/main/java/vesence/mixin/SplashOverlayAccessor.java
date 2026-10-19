package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.SplashOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(SplashOverlay.class)
public interface SplashOverlayAccessor {
   @Accessor("reloadCompleteTime")
   long getReloadCompleteTime();
}
